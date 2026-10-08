#!/usr/bin/env python3
"""Build an opt-in Minecraft 1.21.1 tower-room data pack from a deterministic plan."""
from pathlib import Path
import argparse
import json
import zipfile

NAMESPACE = "puffish_skills"
STORAGE = f"{NAMESPACE}:tower_demo"
BASE = f"{NAMESPACE}:tower_demo"
WORLDS = (f"{NAMESPACE}:tower_demo_entry", f"{NAMESPACE}:tower_demo_depth")
MIN, MAX, FLOOR, CEILING = 93, 107, 64, 80
RELAYS = [(96, "minecraft:copper_block"), (100, "minecraft:amethyst_block"), (104, "minecraft:gold_block")]


def relay_commands():
    return [f"execute in {WORLDS[0]} run setblock {x} {FLOOR} 104 {block}" for x, block in RELAYS]


def room_stages():
    """One slice per tick across both dimensions; at most 225 block writes per slice."""
    stages = []
    for world, material in zip(WORLDS, ("minecraft:stone_bricks", "minecraft:polished_blackstone_bricks")):
        for y in range(FLOOR, CEILING + 1):
            if y in (FLOOR, CEILING):
                commands = [f"execute in {world} run fill {MIN} {y} {MIN} {MAX} {y} {MAX} {material}"]
            else:
                commands = [
                    f"execute in {world} run fill {MIN + 1} {y} {MIN + 1} {MAX - 1} {y} {MAX - 1} minecraft:air",
                    f"execute in {world} run fill {MIN} {y} {MIN} {MAX} {y} {MIN} {material}",
                    f"execute in {world} run fill {MIN} {y} {MAX} {MAX} {y} {MAX} {material}",
                    f"execute in {world} run fill {MIN} {y} {MIN + 1} {MIN} {y} {MAX - 1} {material}",
                    f"execute in {world} run fill {MAX} {y} {MIN + 1} {MAX} {y} {MAX - 1} {material}",
                ]
            stages.append(commands)
    fixtures = []
    for world in WORLDS:
        fixtures.append(f"execute in {world} run setblock 100 {FLOOR} 100 minecraft:lodestone")
        for x, z in ((95, 95), (95, 105), (105, 95), (105, 105)):
            fixtures.append(f"execute in {world} run setblock {x} {FLOOR} {z} minecraft:sea_lantern")
    stages.append(fixtures + relay_commands())
    return stages


def pack_files():
    """Return complete archive entries; no scripts run automatically at world load."""
    files = {}

    def json_file(path, value):
        files[path] = json.dumps(value, indent=2) + "\n"

    def function(name, lines):
        files[f"data/{NAMESPACE}/function/tower_demo/{name}.mcfunction"] = "\n".join(lines) + "\n"

    json_file("pack.mcmeta", {"pack": {"pack_format": 48, "description": "Monolith: opt-in two-dimension tower demo"}})
    root = Path(__file__).resolve().parents[1]
    dim_type = json.loads((root / "Common/src/main/resources/data/puffish_skills/dimension_type/rifts.json").read_text())
    json_file(f"data/{NAMESPACE}/dimension_type/tower_demo.json", dim_type)
    for world in WORLDS:
        json_file(f"data/{NAMESPACE}/dimension/{world.split(':')[1]}.json", {
            "type": f"{NAMESPACE}:tower_demo",
            "generator": {"type": "minecraft:flat", "settings": {
                "biome": "minecraft:the_void", "features": False, "lakes": False,
                "layers": [{"block": "minecraft:air", "height": 1}], "structure_overrides": []}},
        })
    anchors = [{"dimension": world, "x": 100.5, "y": 66.5, "z": 100.5} for world in WORLDS]
    json_file(f"data/{NAMESPACE}/arpg/tower_links.json", {
        "schema": 1,
        "links": [{"id": "arpg:tower_demo_descent", "from": anchors[0], "to": anchors[1],
                   "width": 3, "height": 3, "minimumLevel": 1, "prerequisites": ["arpg:first_rift"]}],
        "recovery": [{"id": "arpg:tower_demo_recovery",
                      "sectors": ["arpg:tower_demo_0", "arpg:tower_demo_1"],
                      "checkpoint": {"dimension": WORLDS[0], "x": 100.5, "y": 65, "z": 104.5}}],
        "puzzles": [{"id": "arpg:tower_demo_relays", "link": "arpg:tower_demo_descent",
                     "sequence": [{"dimension": WORLDS[0], "x": x, "y": FLOOR, "z": 104, "block": block}
                                  for x, block in RELAYS]}],
        "sectors": [{"id": f"arpg:tower_demo_{index}", "dimension": world,
                     "minX": MIN, "minY": FLOOR, "minZ": MIN, "maxX": MAX, "maxY": CEILING, "maxZ": MAX}
                    for index, world in enumerate(WORLDS)],
    })
    function("build", [
        f"execute unless data storage {STORAGE} {{building:1b}} unless data storage {STORAGE} {{ready:1b}} run function {BASE}/start",
        f"execute if data storage {STORAGE} {{building:1b}} run schedule function {BASE}/tick 1t replace",
    ])
    function("start", [
        *[f"execute in {world} run forceload add {MIN} {MIN} {MAX} {MAX}" for world in WORLDS],
        f"data modify storage {STORAGE} building set value 1b",
        f"data modify storage {STORAGE} ready set value 0b",
        f"data modify storage {STORAGE} stage set value 0",
        'tellraw @a {"text":"Tower demo construction started in dedicated dimensions.","color":"aqua"}',
    ])
    loaded = []
    for world in WORLDS:
        loaded += [f"in {world}", *[f"if loaded {x} {FLOOR} {z}" for x in (MIN, MAX) for z in (MIN, MAX)]]
    function("tick", [
        f"execute if data storage {STORAGE} {{building:1b}} run schedule function {BASE}/tick 1t replace",
        f"execute if data storage {STORAGE} {{building:1b}} {' '.join(loaded)} run function {BASE}/dispatch",
    ])
    stages = room_stages()
    # Descending dispatch prevents a stage increment from triggering later stages in the same tick.
    function("dispatch", [f"execute if data storage {STORAGE} {{stage:{index}}} run function {BASE}/stage_{index:02}"
                          for index in reversed(range(len(stages)))])
    for index, commands in enumerate(stages):
        function(f"stage_{index:02}", [*commands, f"data modify storage {STORAGE} stage set value {index + 1}",
                                     *([f"function {BASE}/finish"] if index == len(stages) - 1 else [])])
    function("finish", [
        f"function {BASE}/spawn_sentinel",
        f"data modify storage {STORAGE} building set value 0b",
        f"data modify storage {STORAGE} ready set value 1b",
        f"schedule clear {BASE}/tick",
        'tellraw @a {"text":"Tower demo ready. Operators can use /function puffish_skills:tower_demo/visit.","color":"green"}',
    ])
    function("restore_relays", relay_commands())
    function("visit", [
        f'execute if data storage {STORAGE} {{ready:1b}} run tellraw @s {{"text":"Relay clue: copper, amethyst, gold. Right-click the floor relays in order, then the centre lodestone.","color":"aqua"}}',
        f"execute if data storage {STORAGE} {{ready:1b}} in {WORLDS[0]} run tp @s 100.5 65 104.5 180 0",
        f'execute unless data storage {STORAGE} {{ready:1b}} run tellraw @s {{"text":"Build the demo rooms first.","color":"red"}}',
    ])
    function("resume", [
        *[f"execute in {world} run forceload add {MIN} {MIN} {MAX} {MAX}" for world in WORLDS],
        f"execute if data storage {STORAGE} {{building:1b}} run schedule function {BASE}/tick 1t replace",
    ])
    function("spawn_sentinel", [
        f"execute in {WORLDS[1]} unless entity @e[type=minecraft:husk,tag=puffish_skills:tower_demo_sentinel] run function {BASE}/create_sentinel",
    ])
    function("create_sentinel", [
        f"execute in {WORLDS[1]} run summon minecraft:husk 104.5 65 98.5 "
        + "{PersistenceRequired:1b,CustomName:'{\"text\":\"Tower Sentinel\"}',CustomNameVisible:1b,"
        + "Tags:[\"puffish_skills:tower_demo_sentinel\",\"arpg:encounter\",\"arpg:world_boss\",\"arpg:completion=arpg:first_world_boss\"]}",
        f"execute in {WORLDS[1]} run attribute @e[type=minecraft:husk,tag=puffish_skills:tower_demo_sentinel,limit=1] minecraft:generic.max_health base set 120",
        f"execute in {WORLDS[1]} run attribute @e[type=minecraft:husk,tag=puffish_skills:tower_demo_sentinel,limit=1] minecraft:generic.attack_damage base set 7",
        f"execute in {WORLDS[1]} run data merge entity @e[type=minecraft:husk,tag=puffish_skills:tower_demo_sentinel,limit=1] {{Health:120.0f}}",
    ])
    function("release_chunks", [
        f"schedule clear {BASE}/tick",
        *[f"execute in {world} run forceload remove {MIN} {MIN} {MAX} {MAX}" for world in WORLDS],
        'tellraw @a {"text":"Tower demo chunk tickets released. Resume construction/loading before using its portals.","color":"yellow"}',
    ])
    return files


def build(output):
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, content in sorted(pack_files().items()):
            entry = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, content.encode("utf-8"))
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="build/tower-demo-datapack.zip")
    args = parser.parse_args()
    print(build(args.output))
