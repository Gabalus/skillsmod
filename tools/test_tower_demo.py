#!/usr/bin/env python3
"""Validate generated-room geometry, write budgets, scheduling and installable archive layout."""
from pathlib import Path
import hashlib
import json
import re
import tempfile
import unittest
import zipfile

from build_tower_demo import BASE, WORLDS, FLOOR, CEILING, MIN, MAX, build, pack_files, room_stages


def writes(commands):
    blocks = []
    for line in commands:
        prefix, action = line.split(" run ", 1)
        world = prefix.split()[2]
        parts = action.split()
        if parts[0] == "fill":
            x0, y0, z0, x1, y1, z1 = map(int, parts[1:7])
            blocks.extend((world, x, y, z, parts[7]) for x in range(x0, x1 + 1)
                          for y in range(y0, y1 + 1) for z in range(z0, z1 + 1))
        elif parts[0] == "setblock":
            x, y, z = map(int, parts[1:4])
            blocks.append((world, x, y, z, parts[4]))
        else:
            raise AssertionError(f"Unexpected construction operation: {line}")
    return blocks


class TowerDemoTest(unittest.TestCase):
    def test_write_budget_is_global_across_both_rooms(self):
        for stage in room_stages():
            self.assertLessEqual(len(writes(stage)), 256)
            self.assertLessEqual(len(set(world for world, *_ in writes(stage))), 2)
        self.assertEqual(35, len(room_stages()))

    def test_completed_rooms_enclose_walkable_interiors_and_portal_landing_strips(self):
        blocks = {}
        for stage in room_stages():
            for world, x, y, z, material in writes(stage):
                self.assertIn(world, WORLDS)
                self.assertTrue(MIN <= x <= MAX and FLOOR <= y <= CEILING and MIN <= z <= MAX)
                blocks[world, x, y, z] = material
        for world in WORLDS:
            for x in range(MIN, MAX + 1):
                for y in range(FLOOR, CEILING + 1):
                    for z in range(MIN, MAX + 1):
                        material = blocks[world, x, y, z]
                        shell = x in (MIN, MAX) or z in (MIN, MAX) or y in (FLOOR, CEILING)
                        self.assertEqual(shell, material != "minecraft:air")
            self.assertEqual("minecraft:lodestone", blocks[world, 100, FLOOR, 100])
            for x in range(99, 102):
                for z in range(99, 102):
                    self.assertNotEqual("minecraft:air", blocks[world, x, FLOOR, z])
                    for y in range(65, 68):
                        self.assertEqual("minecraft:air", blocks[world, x, y, z])
            for y in (65, 66):
                self.assertEqual("minecraft:air", blocks[world, 100, y, 104])

    def test_catalog_gates_the_real_route_and_protects_each_room(self):
        data = json.loads(pack_files()["data/puffish_skills/arpg/tower_links.json"])
        link = data["links"][0]
        self.assertEqual(["arpg:first_rift"], link["prerequisites"])
        self.assertEqual(list(WORLDS), [link["from"]["dimension"], link["to"]["dimension"]])
        for anchor, sector in zip((link["from"], link["to"]), data["sectors"]):
            self.assertEqual(anchor["dimension"], sector["dimension"])
            for axis in ("X", "Y", "Z"):
                self.assertTrue(sector["min" + axis] <= anchor[axis.lower()] < sector["max" + axis] + 1)
        self.assertEqual({WORLDS[0], WORLDS[1]}, {s["dimension"] for s in data["sectors"]})

    def test_puzzle_relays_match_constructed_blocks_and_remain_off_the_landing_strip(self):
        files = pack_files()
        puzzle = json.loads(files["data/puffish_skills/arpg/tower_links.json"])["puzzles"][0]
        blocks = {(world, x, y, z): block for stage in room_stages()
                  for world, x, y, z, block in writes(stage)}
        self.assertEqual("arpg:tower_demo_descent", puzzle["link"])
        self.assertEqual(["minecraft:copper_block", "minecraft:amethyst_block", "minecraft:gold_block"],
                         [relay["block"] for relay in puzzle["sequence"]])
        for relay in puzzle["sequence"]:
            key = (relay["dimension"], relay["x"], relay["y"], relay["z"])
            self.assertEqual(relay["block"], blocks[key])
            self.assertNotEqual((100, 64, 100), key[1:])
            self.assertEqual(WORLDS[0], relay["dimension"])
        self.assertIn("copper, amethyst, gold", files["data/puffish_skills/function/tower_demo/visit.mcfunction"])
        self.assertEqual(3, len(writes(files["data/puffish_skills/function/tower_demo/restore_relays.mcfunction"].splitlines())))

    def test_dispatch_cannot_cascade_multiple_stages_in_one_tick(self):
        files = pack_files()
        dispatch = files["data/puffish_skills/function/tower_demo/dispatch.mcfunction"]
        order = [int(value) for value in re.findall(r"\{stage:(\d+)\}", dispatch)]
        self.assertEqual(list(reversed(range(35))), order)
        for initial in range(35):
            current = initial
            called = []
            for candidate in order:
                if current == candidate:
                    called.append(candidate)
                    current += 1
            self.assertEqual([initial], called)
            self.assertIn(f"stage set value {initial + 1}", files[f"data/puffish_skills/function/tower_demo/stage_{initial:02}.mcfunction"])

    def test_chunk_loading_is_bounded_and_release_is_exact(self):
        files = pack_files()
        start = files["data/puffish_skills/function/tower_demo/start.mcfunction"]
        release = files["data/puffish_skills/function/tower_demo/release_chunks.mcfunction"]
        for world in WORLDS:
            self.assertIn(f"execute in {world} run forceload add 93 93 107 107", start)
            self.assertIn(f"execute in {world} run forceload remove 93 93 107 107", release)
        tick = files["data/puffish_skills/function/tower_demo/tick.mcfunction"]
        self.assertEqual(8, tick.count("if loaded "))
        self.assertIn("schedule clear " + BASE + "/tick", release)
        self.assertIn("schedule clear " + BASE + "/tick", files["data/puffish_skills/function/tower_demo/finish.mcfunction"])

    def test_functions_exist_and_installation_never_runs_them_automatically(self):
        files = pack_files()
        self.assertFalse(any("/tags/" in path for path in files))
        self.assertFalse(any("/functions/" in path for path in files))
        for path, content in files.items():
            if path.endswith(".mcfunction"):
                for reference in re.findall(r"(?:run |schedule )?function (puffish_skills:[a-z0-9_/]+)", content):
                    target = "data/puffish_skills/function/" + reference.split(":")[1] + ".mcfunction"
                    self.assertIn(target, files)
        self.assertIn("unless data storage", files["data/puffish_skills/function/tower_demo/build.mcfunction"])
        self.assertIn("{ready:1b}", files["data/puffish_skills/function/tower_demo/visit.mcfunction"])

    def test_sentinel_uses_authoritative_completion_marker_and_duplicate_guard(self):
        files = pack_files()
        create = files["data/puffish_skills/function/tower_demo/create_sentinel.mcfunction"]
        self.assertIn("arpg:completion=arpg:first_world_boss", create)
        self.assertIn("arpg:encounter", create)
        self.assertNotIn("completion grant", create)
        self.assertNotIn("give ", create)
        self.assertIn("unless entity", files["data/puffish_skills/function/tower_demo/spawn_sentinel.mcfunction"])
        self.assertIn("120.0f", create)
        self.assertIn(f"function {BASE}/spawn_sentinel", files["data/puffish_skills/function/tower_demo/finish.mcfunction"])

    def test_void_dimension_contract_and_archive_root(self):
        files = pack_files()
        for world in WORLDS:
            definition = json.loads(files[f"data/puffish_skills/dimension/{world.split(':')[1]}.json"])
            self.assertEqual("minecraft:the_void", definition["generator"]["settings"]["biome"])
            self.assertEqual([], definition["generator"]["settings"]["structure_overrides"])
        with tempfile.TemporaryDirectory() as directory:
            first = build(Path(directory) / "first.zip")
            second = build(Path(directory) / "second.zip")
            self.assertEqual(hashlib.sha256(first.read_bytes()).digest(), hashlib.sha256(second.read_bytes()).digest())
            with zipfile.ZipFile(first) as archive:
                self.assertIn("pack.mcmeta", archive.namelist())
                self.assertEqual(set(files), set(archive.namelist()))
                self.assertEqual(48, json.loads(archive.read("pack.mcmeta"))["pack"]["pack_format"])
                self.assertFalse(any(path.startswith("/") or ".." in path.split("/") for path in archive.namelist()))


if __name__ == "__main__":
    unittest.main()
