#!/usr/bin/env python3
"""Expand the authored ARPG passive graph to a full-scale shared tree.

Run after generate_arpg_skilltrees.py. Existing 1,602 node IDs and rewards are retained;
new outer wheels/masteries are additive and keystones are moved outward for readability.
The topology is inspired by grouped/orbit ARPG trees, while names and balance are original.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "Common/src/main/resources/data/puffish_skills"
TREE = ROOT / "puffish_skills/categories/arpg_universal"
MANIFEST = ROOT / "arpg/manifest.json"

DISCIPLINES = {
    "warrior": ("iron_sword", ["maximum_life", "armor", "melee_damage", "physical_damage", "attack_speed", "block_chance", "life_regeneration", "bleed_damage", "critical_multiplier", "strength", "life_leech", "area_damage"], ["Vanguard", "Bastion", "Conqueror", "Ironbound", "Warmaster", "Siegeborn"]),
    "ranger": ("bow", ["projectile_damage", "evasion", "critical_chance", "poison_damage", "movement_speed", "attack_speed", "bleed_damage", "dexterity", "area_damage", "cooldown_recovery", "nature_damage", "life_leech"], ["Horizon", "Wildshot", "Trailblazer", "Hunter", "Windrunner", "Longwatch"]),
    "rogue": ("iron_axe", ["critical_multiplier", "attack_speed", "bleed_damage", "ender_damage", "evasion", "movement_speed", "poison_damage", "critical_chance", "cooldown_recovery", "dexterity", "life_leech", "area_damage"], ["Nightblade", "Cutpurse", "Shade", "Executioner", "Phantom", "Whisperknife"]),
    "arcanist": ("amethyst_shard", ["spell_damage", "maximum_mana", "cast_speed", "fire_damage", "lightning_damage", "cold_damage", "critical_chance", "cooldown_recovery", "mana_regeneration", "intelligence", "ward", "area_damage"], ["Thaumaturge", "Invoker", "Savant", "Spellwright", "Arcanum", "Runesage"]),
    "templar": ("shield", ["holy_damage", "block_chance", "maximum_life", "armor", "ward", "spell_block", "life_regeneration", "spell_damage", "strength", "intelligence", "area_damage", "cooldown_recovery"], ["Oathkeeper", "Sanctum", "Justicar", "Aegis", "Votary", "Dawnwarden"]),
    "shaman": ("oak_sapling", ["nature_damage", "blood_damage", "cold_damage", "mana_regeneration", "ward", "summon_damage", "poison_damage", "life_regeneration", "spell_damage", "intelligence", "area_damage", "maximum_life"], ["Wildheart", "Spiritbound", "Totemist", "Witchwood", "Ancestor", "Rootspeaker"]),
}

TITLES = {
    "maximum_life": "Vitality", "maximum_mana": "Arcane Reserve", "armor": "Iron Skin", "evasion": "Footwork",
    "ward": "Aegis", "attack_speed": "Tempo", "cast_speed": "Invocation", "physical_damage": "Force",
    "fire_damage": "Embercraft", "cold_damage": "Wintercraft", "lightning_damage": "Stormcraft", "blood_damage": "Bloodcraft",
    "holy_damage": "Consecration", "ender_damage": "Voidcraft", "nature_damage": "Wildcraft", "spell_damage": "Sorcery",
    "melee_damage": "Martial Force", "projectile_damage": "Ballistics", "critical_chance": "Precision",
    "critical_multiplier": "Lethality", "cooldown_recovery": "Readiness", "block_chance": "Guard", "spell_block": "Spellguard",
    "strength": "Might", "dexterity": "Finesse", "intelligence": "Insight", "movement_speed": "Stride",
    "mana_regeneration": "Clarity", "life_regeneration": "Renewal", "life_leech": "Leech", "mana_leech": "Siphon",
    "area_damage": "Expansion", "bleed_damage": "Hemorrhage", "poison_damage": "Venom", "summon_damage": "Command",
}

MASTERIES = {
    "warrior": ["Unyielding Advance", "Crimson Engine", "Anvil Doctrine", "Execution Rhythm", "Fortress Heart", "War Standard"],
    "ranger": ["Distant Mark", "Wind Piercer", "Venom Bloom", "Barbed Volley", "Predator Sense", "Fleet Pursuit"],
    "rogue": ["Night Contract", "Critical Edge", "Ghost Step", "Bleeding Art", "Void Ambush", "Toxic Finish"],
    "arcanist": ["Arcane Battery", "Elemental Weave", "Mana Crucible", "Spell Echoes", "Critical Formula", "Runic Reservoir"],
    "templar": ["Shield Scripture", "Consecrated Oath", "Battle Doctrine", "Ward Communion", "Judgment Engine", "Sanctified Guard"],
    "shaman": ["Ancestor Pact", "Spirit Harvest", "Blood Rite", "Wild Dominion", "Winter Totem", "Verdant Decay"],
}


def read(path):
    return json.loads(path.read_text())


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def modifier(stat, tier=1):
    if stat == "maximum_mana": return {"stat": stat, "operation": "flat", "value": 6 * tier}
    if stat in {"maximum_life", "ward"}: return {"stat": stat, "operation": "flat", "value": 2 * tier}
    if stat in {"strength", "dexterity", "intelligence"}: return {"stat": stat, "operation": "flat", "value": 3 * tier}
    if stat in {"block_chance", "spell_block", "critical_chance"}: return {"stat": stat, "operation": "flat", "value": round(.01 * tier, 4)}
    if stat == "critical_multiplier": return {"stat": stat, "operation": "flat", "value": round(.025 * tier, 4)}
    if stat in {"life_leech", "mana_leech"}: return {"stat": stat, "operation": "flat", "value": round(.0025 * tier, 4)}
    if stat in {"attack_speed", "cast_speed", "movement_speed", "cooldown_recovery"}: return {"stat": stat, "operation": "increased", "value": round(.025 * tier, 4)}
    if stat in {"life_regeneration", "mana_regeneration"}: return {"stat": stat, "operation": "increased", "value": round(.06 * tier, 4)}
    return {"stat": stat, "operation": "increased", "value": round(.05 * tier, 4)}


def text(m):
    stat = m["stat"].replace("_", " ")
    value = m["value"]
    if m["operation"] == "flat":
        return f"+{value * 100:g}% {stat}" if stat in {"block chance", "spell block", "critical chance", "critical multiplier", "life leech", "mana leech"} else f"+{value:g} {stat}"
    return f"{value * 100:g}% {m['operation']} {stat}"


def definition(title, modifiers, icon, size=1.0):
    return {
        "title": title,
        "description": "; ".join(text(m) for m in modifiers),
        "rewards": [{"type": "puffish_skills:arpg_stat", "data": m} for m in modifiers],
        "icon": {"type": "texture", "data": {"texture": f"minecraft:textures/item/{icon}.png"}},
        "size": size,
        "cost": 1,
    }


def cluster_center(nodes, discipline, cluster):
    values = [nodes[f"{discipline}/{cluster:02d}/{j:02d}"] for j in range(17)]
    return round(sum(v["x"] for v in values) / 17), round(sum(v["y"] for v in values) / 17)


def main():
    nodes = read(TREE / "skills.json")
    definitions = read(TREE / "definitions.json")
    connections = read(TREE / "connections.json")
    edges = connections["normal"]["bidirectional"]
    disciplines = list(DISCIPLINES)

    keystones = [key for key in nodes if key.startswith("keystone/")]
    edges = [[a, b] for a, b in edges if not a.startswith("keystone/") and not b.startswith("keystone/")]

    for sector, discipline in enumerate(disciplines):
        icon, stats, epithets = DISCIPLINES[discipline]
        base_angle = sector * math.tau / 6

        for cluster in range(15, 24):
            if cluster < 21:
                lane = cluster - 15
                radius = 1450 + (lane % 2) * 24
                center_angle = base_angle + (lane - 2.5) * .085
                wheel_radius = 86
            else:
                lane = cluster - 21
                radius = 1740 + (lane % 2) * 28
                center_angle = base_angle + (lane - 1) * .13
                wheel_radius = 92

            cx = round(radius * math.cos(center_angle))
            cy = round(radius * math.sin(center_angle))
            primary = stats[(cluster * 2) % len(stats)]
            secondary = stats[(cluster * 2 + 3) % len(stats)]
            ids = []

            for j in range(17):
                node = f"{discipline}/{cluster:02d}/{j:02d}"
                angle = j * math.tau / 17
                nodes[node] = {"x": cx + round(wheel_radius * math.cos(angle)), "y": cy + round(wheel_radius * math.sin(angle)), "definition": node}
                ids.append(node)
                stat = primary if j % 4 != 3 else secondary
                notable = j in {4, 10, 16}
                mods = [modifier(stat, 2 if notable else 1)]
                if notable:
                    mods.append(modifier(secondary if stat == primary else primary, 1))
                    title = f"{epithets[(cluster + j) % len(epithets)]} {TITLES.get(stat, stat.replace('_', ' ').title())}"
                else:
                    suffix = ("Path", "Training", "Practice", "Discipline")[(cluster + j) % 4]
                    title = f"{TITLES.get(stat, stat.replace('_', ' ').title())} {suffix}"
                definitions[node] = definition(title, mods, icon, 1.28 if notable else .82)

            edges.extend([[ids[j], ids[(j + 1) % 17]] for j in range(17)])
            edges.extend([[ids[0], ids[8]], [ids[4], ids[12]]])
            if cluster < 21:
                previous = [10, 11, 12, 12, 13, 14][cluster - 15]
                edges.append([f"{discipline}/{previous:02d}/08", ids[0]])
                if cluster > 15:
                    edges.append([f"{discipline}/{cluster - 1:02d}/04", ids[12]])
            else:
                previous = [16, 18, 20][cluster - 21]
                edges.append([f"{discipline}/{previous:02d}/08", ids[0]])
                if cluster > 21:
                    edges.append([f"{discipline}/{cluster - 1:02d}/04", ids[12]])

        for cluster in range(24):
            cx, cy = cluster_center(nodes, discipline, cluster)
            mastery = f"mastery/{discipline}/{cluster:02d}"
            nodes[mastery] = {"x": cx, "y": cy, "definition": mastery}
            primary = stats[(cluster * 2) % len(stats)]
            secondary = stats[(cluster * 2 + 3) % len(stats)]
            definitions[mastery] = definition(MASTERIES[discipline][cluster % 6], [modifier(primary, 2), modifier(secondary, 1)], "nether_star", 1.35)
            edges.extend([[f"{discipline}/{cluster:02d}/{j:02d}", mastery] for j in (4, 10, 16)])

        for j, node in enumerate(keystones[sector * 11:(sector + 1) * 11]):
            angle = base_angle + (j - 5) * .06
            radius = 2050 + (j % 2) * 115
            nodes[node]["x"] = round(radius * math.cos(angle))
            nodes[node]["y"] = round(radius * math.sin(angle))
            edges.append([f"{discipline}/{21 + j % 3:02d}/16", node])

    for sector, discipline in enumerate(disciplines):
        nxt = disciplines[(sector + 1) % 6]
        edges.extend([
            [f"{discipline}/20/04", f"{nxt}/15/12"],
            [f"{discipline}/23/10", f"{nxt}/21/16"],
        ])

    connections["normal"]["bidirectional"] = edges
    category = read(TREE / "category.json")
    category["description"] = "2,664-node shared ARPG tree: six origins, dense orbit clusters, 144 masteries, 66 keystones, six jewel sockets and cross-sector outer paths."

    expected = 2664
    assert len(nodes) == expected, (len(nodes), expected)
    assert len(definitions) == expected, (len(definitions), expected)
    assert all(a in nodes and b in nodes for a, b in edges)

    write(TREE / "skills.json", nodes)
    write(TREE / "definitions.json", definitions)
    write(TREE / "connections.json", connections)
    write(TREE / "category.json", category)

    manifest = read(MANIFEST)
    manifest.update({"universal_nodes": expected, "universal_edges": len(edges), "masteries": 144, "keystones": 66, "tree_layout": "radial-poe-scale-v3"})
    write(MANIFEST, manifest)
    print(json.dumps({"universal_nodes": expected, "edges": len(edges), "masteries": 144, "layout": "radial-poe-scale-v3"}))


if __name__ == "__main__":
    main()
