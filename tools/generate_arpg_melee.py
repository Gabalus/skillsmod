#!/usr/bin/env python3
"""Add Epic Fight specialization graphs while preserving unrelated authored content."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "Common/src/main/resources/data/puffish_skills"
ATTACKS = (("measured_strike", "Measured Strike", 3, 8, 80),
           ("driving_slash", "Driving Slash", 5, 6, 60))
BRANCHES = (("melee_damage", "increased", .04), ("physical_damage", "increased", .04),
            ("resource_cost", "reduced", .025))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def generate(root=ROOT):
    catalog = json.loads((root / "arpg/catalog.json").read_text())
    config = json.loads((root / "puffish_skills/config.json").read_text())
    ids = {"puffish_skills:" + attack[0] for attack in ATTACKS}
    prefixes = tuple("specialization/" + sid.replace(":", "_") + "_" for sid in sorted(ids))
    catalog["skills"] = [skill for skill in catalog["skills"] if skill["id"] not in ids]
    catalog["rules"] = [rule for rule in catalog["rules"] if not rule["id"].startswith(prefixes)]
    for name, title, level, cost, cooldown in ATTACKS:
        sid = "puffish_skills:" + name
        safe = sid.replace(":", "_")
        category = "arpg_skill_" + safe
        catalog["skills"].append(dict(id=sid, title=title, provider="epicfight",
            tags=["attack", "melee", "physical", "hit"], damage_type="physical", coefficient=1,
            cost=cost, cooldown=cooldown, range=3, level=level, discipline="", weapon="any", effect="provider_animation"))
        nodes, definitions, edges = {}, {}, []
        for j in range(24):
            key = safe + "_" + str(j)
            stat, operation, value = BRANCHES[j // 8]
            description = ("2.5% reduced stamina cost" if stat == "resource_cost"
                           else "4% increased " + stat.replace("_", " ")) + " for " + title
            rid = "specialization/" + key
            catalog["rules"].append(dict(id=rid, title=title + " Specialization " + str(j + 1),
                description=description, kind="conditional", condition="always", skill=sid,
                modifiers=[dict(stat=stat, operation=operation, value=value)]))
            definitions[key] = dict(title=title + " " + str(j + 1), description=description,
                rewards=[dict(type="puffish_skills:arpg_rule", data=dict(rule=rid))],
                icon=dict(type="texture", data=dict(texture="minecraft:textures/item/iron_sword.png")), size=1, cost=1)
            nodes[key] = dict(x=(j // 8) * 100, y=(j % 8) * 60, definition=key, root=j % 8 == 0)
            if j % 8:
                edges.append([safe + "_" + str(j - 1), key])
        folder = root / "puffish_skills/categories" / category
        write(folder / "category.json", dict(title=title,
            description="Specialize with /arpg specialize " + sid + ". Accepted activations earn experience, including misses. Epic Fight owns stamina and hit collision.",
            icon=dict(type="texture", data=dict(texture="minecraft:textures/item/iron_sword.png")),
            background="minecraft:textures/block/deepslate_tiles.png", unlocked_by_default=False,
            starting_points=0, spent_points_limit=20))
        write(folder / "definitions.json", definitions)
        write(folder / "skills.json", nodes)
        write(folder / "connections.json", dict(normal=dict(bidirectional=edges)))
        if category not in config["categories"]:
            config["categories"].append(category)
    write(root / "arpg/catalog.json", catalog)
    write(root / "puffish_skills/config.json", config)
    manifest = json.loads((root / "arpg/manifest.json").read_text())
    manifest["skills"] = len(catalog["skills"])
    write(root / "arpg/manifest.json", manifest)


if __name__ == "__main__":
    generate()
    print("Generated two Epic Fight melee specialization trees")
