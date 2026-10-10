#!/usr/bin/env python3
"""Add the custom Iron's pulse spells without rewriting other authored ARPG graphs."""
import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "Common/src/main/resources/data/puffish_skills"
SPELLS = (
    ("storm_pulse", "Storm Pulse", "lightning", "arcanist", 3),
    ("rime_pulse", "Rime Pulse", "cold", "shaman", 5),
)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def icon(path, frost):
    """Draw small original pixel icons with no external libraries or borrowed art."""
    pixels = bytearray(32 * 32 * 4)
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            radius = math.hypot(dx, dy)
            ring = 11.5 <= radius <= 13
            if frost:
                mark = (abs(dx) < 1.5 or abs(dy) < 1.5 or abs(abs(dx) - abs(dy)) < 1.2) and radius < 10
                color = (164, 239, 255, 255) if mark else (39, 135, 172, 220)
            else:
                mark = (6 <= y <= 16 and 17 - (y - 6) // 2 <= x <= 21 - (y - 6) // 2
                        or 14 <= y <= 26 and 16 - (y - 14) // 3 <= x <= 20 - (y - 14) // 3)
                color = (255, 238, 146, 255) if mark else (101, 135, 232, 220)
            if ring or mark:
                pixels[(y * 32 + x) * 4:(y * 32 + x + 1) * 4] = bytes(color)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    rows = b"".join(b"\0" + pixels[y * 128:(y + 1) * 128] for y in range(32))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 32, 32, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def generate(root=ROOT):
    catalog = json.loads((root / "arpg/catalog.json").read_text())
    config_path = root / "puffish_skills/config.json"
    config = json.loads(config_path.read_text())
    ids = {"puffish_skills:" + spell[0] for spell in SPELLS}
    catalog["skills"] = [skill for skill in catalog["skills"] if skill["id"] not in ids]
    prefixes = tuple("specialization/puffish_skills_" + spell[0] + "_" for spell in SPELLS)
    catalog["rules"] = [rule for rule in catalog["rules"] if not rule["id"].startswith(prefixes)]
    for name, title, element, discipline, level in SPELLS:
        sid = "puffish_skills:" + name
        safe = sid.replace(":", "_")
        category = "arpg_skill_" + safe
        catalog["skills"].append(dict(id=sid, title=title, provider="irons", tags=["spell", element, "area", "hit"],
            damage_type=element, coefficient=1, cost=0, cooldown=1, range=4, level=level,
            discipline=discipline, weapon="any", effect="pulse"))
        nodes, definitions, edges = {}, {}, []
        for j in range(24):
            key = safe + "_" + str(j)
            stat = ["spell_damage", element + "_damage", "area_damage"][j // 8]
            modifier = dict(stat=stat, operation="increased", value=.04)
            rid = "specialization/" + key
            description = "4% increased " + stat.replace("_", " ") + " for " + title
            catalog["rules"].append(dict(id=rid, title=title + " Specialization " + str(j + 1),
                description=description, kind="conditional", condition="always", skill=sid, modifiers=[modifier]))
            definitions[key] = dict(title=title + " " + str(j + 1), description=description,
                rewards=[dict(type="puffish_skills:arpg_rule", data=dict(rule=rid))],
                icon=dict(type="texture", data=dict(texture="minecraft:textures/item/enchanted_book.png")), size=1, cost=1)
            nodes[key] = dict(x=(j // 8) * 100, y=(j % 8) * 60, definition=key, root=j % 8 == 0)
            if j % 8:
                edges.append([safe + "_" + str(j - 1), key])
        folder = root / "puffish_skills/categories" / category
        write(folder / "category.json", dict(title=title,
            description="Specialize with /arpg specialize " + sid + ". Successful casts earn experience. Iron's owns mana and cooldowns.",
            icon=dict(type="texture", data=dict(texture="minecraft:textures/item/enchanted_book.png")),
            background="minecraft:textures/block/deepslate_tiles.png", unlocked_by_default=False,
            starting_points=0, spent_points_limit=20))
        write(folder / "definitions.json", definitions)
        write(folder / "skills.json", nodes)
        write(folder / "connections.json", dict(normal=dict(bidirectional=edges)))
        if category not in config["categories"]:
            config["categories"].append(category)
        icon(root.parents[1] / "assets/puffish_skills/textures/gui/spell_icons" / (name + ".png"), name == "rime_pulse")
    write(root / "arpg/catalog.json", catalog)
    write(config_path, config)
    manifest_path = root / "arpg/manifest.json"
    manifest = json.loads(manifest_path.read_text())
    manifest["skills"] = len(catalog["skills"])
    write(manifest_path, manifest)


if __name__ == "__main__":
    generate()
    print("Generated two Iron's pulse spells and specialization trees")
