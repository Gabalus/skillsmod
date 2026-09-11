#!/usr/bin/env python3
"""Author the large ARPG passive/ascendancy graphs used by the shipped data pack.

The layout model is inspired by large ARPG passive-tree exports (groups/orbits, explicit adjacency,
separate ascendancy boards), but all node names, balance values, rules and Minecraft assets here are
original to this project. No Path of Exile data or artwork is copied into generated resources.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "Common/src/main/resources/data/puffish_skills"
TREE = ROOT / "puffish_skills/categories"
CATALOG_PATH = ROOT / "arpg/catalog.json"

DISCIPLINES = {
    "warrior": ("Warrior", "iron_sword"),
    "ranger": ("Ranger", "bow"),
    "rogue": ("Rogue", "iron_axe"),
    "arcanist": ("Arcanist", "amethyst_shard"),
    "templar": ("Templar", "shield"),
    "shaman": ("Shaman", "oak_sapling"),
}

# Four identity branches per ascendancy. Each branch costs 4 points to finish (1 + 1 + 2),
# so the eight trial points force a meaningful choice instead of buying the whole board.
ASCENDANCIES = {
    "juggernaut": ("warrior", [
        ("Stonewall", "Immovable Heart", "armor", "iron_fortress"),
        ("Last Bastion", "Unbroken Citadel", "maximum_life", "unbroken"),
        ("War Engine", "March of Iron", "melee_damage", "war_cry"),
        ("Counterweight", "Perfect Riposte", "block_chance", "riposte"),
    ]),
    "berserker": ("warrior", [
        ("Blood Furnace", "Crimson Covenant", "maximum_life", "blood_magic"),
        ("Deathless Rage", "No Retreat", "melee_damage", "last_stand"),
        ("Ruinous Rhythm", "Execution Tempo", "attack_speed", "executioners_rush"),
        ("Open Vein", "Red Harvest", "bleed_damage", "hemorrhagic_burst"),
    ]),
    "duelist": ("warrior", [
        ("Counter School", "Master Riposte", "block_chance", "riposte"),
        ("Twin Tempo", "Dance of Fangs", "attack_speed", "twin_fangs"),
        ("Footwork", "Unbroken Momentum", "movement_speed", "momentum"),
        ("Battle Reserve", "Endless Exchange", "melee_damage", "martial_reservoir"),
    ]),
    "warlord": ("warrior", [
        ("Rally", "Voice of Conquest", "melee_damage", "war_cry"),
        ("Vengeance", "Retribution Oath", "maximum_life", "retribution"),
        ("Shield Line", "Bastion Charge", "armor", "bastion_strike"),
        ("Spoils", "Sanguine Victory", "life_regeneration", "sanguine_recovery"),
    ]),
    "deadeye": ("ranger", [
        ("Close Volley", "Point-Blank Doctrine", "projectile_damage", "point_blank"),
        ("Longline", "Horizon Hunter", "projectile_damage", "far_shot"),
        ("Predator Mark", "Hunter's Instinct", "critical_chance", "hunter_instinct"),
        ("Barbed Flight", "Puncturing Rain", "bleed_damage", "puncture"),
    ]),
    "pathfinder": ("ranger", [
        ("Venomcraft", "Venomous Precision", "poison_damage", "venomous_precision"),
        ("Reservoir", "Caustic Reserve", "mana_regeneration", "caustic_reservoir"),
        ("Reaction", "Chain Reaction", "nature_damage", "chain_reaction"),
        ("Toxic Aim", "Venom Tip", "critical_chance", "venom_tip"),
    ]),
    "beastmaster": ("ranger", [
        ("Predator Bond", "Apex Predator", "summon_damage", "predator"),
        ("Thorn Pack", "Thorned Alpha", "nature_damage", "thorned_blade"),
        ("Wild Recovery", "Second Heart", "maximum_life", "second_wind"),
        ("Hunter Pack", "Pack Instinct", "projectile_damage", "hunter_instinct"),
    ]),
    "warden": ("ranger", [
        ("Perfect Form", "Untouched", "evasion", "perfect_form"),
        ("Windstep", "Second Wind", "movement_speed", "second_wind"),
        ("Winter Guard", "Frost Aegis", "cold_damage", "frost_guard"),
        ("Stormstep", "Lightning Reflex", "evasion", "lightning_reflexes"),
    ]),
    "assassin": ("rogue", [
        ("Killing Edge", "Arcane Impact", "critical_multiplier", "arcane_impact"),
        ("Open Wound", "Hemorrhage Artist", "bleed_damage", "open_wounds"),
        ("Venom Edge", "Perfect Venom", "poison_damage", "venom_tip"),
        ("Void Knife", "Eldritch Battery", "ender_damage", "eldritch_battery"),
    ]),
    "shadowdancer": ("rogue", [
        ("Flow", "Living Momentum", "movement_speed", "momentum"),
        ("Afterimage", "Second Wind", "evasion", "second_wind"),
        ("Vanishing Act", "Fleeting Insight", "cooldown_recovery", "fleeting_insight"),
        ("Spellstep", "Mobile Incantation", "cast_speed", "mobile_incantation"),
    ]),
    "saboteur": ("rogue", [
        ("Chain Fuse", "Chain Reaction", "area_damage", "chain_reaction"),
        ("Infernal Charge", "Infernal Execution", "fire_damage", "infernal_execution"),
        ("Shrapnel", "Puncture Protocol", "bleed_damage", "puncture"),
        ("Disruption", "Null Field", "cooldown_recovery", "disruptive_guard"),
    ]),
    "trickster": ("rogue", [
        ("Pristine Mind", "Untouched Reserve", "maximum_mana", "pristine_mind"),
        ("Soul Theft", "Soul Drinker", "mana_leech", "soul_drinker"),
        ("Eldritch Game", "Eldritch Battery", "ender_damage", "eldritch_battery"),
        ("False Opening", "Fleeting Insight", "evasion", "fleeting_insight"),
    ]),
    "pyromancer": ("arcanist", [
        ("Immolation", "Living Pyre", "fire_damage", "immolation"),
        ("Kindling", "Critical Kindling", "ignite_chance", "kindling"),
        ("Execution Flame", "Infernal Execution", "ignite_damage", "infernal_execution"),
        ("Cinder Heart", "Ashen Rebirth", "maximum_life", "cinder_heart"),
    ]),
    "stormcaller": ("arcanist", [
        ("Stormforge", "Stormforged", "lightning_damage", "stormforged"),
        ("Reflex Arc", "Lightning Reflex", "cooldown_recovery", "lightning_reflexes"),
        ("Storm Ward", "Storm Barrier", "ward", "storm_barrier"),
        ("Impact Arc", "Arcane Impact", "critical_chance", "arcane_impact"),
    ]),
    "chronomancer": ("arcanist", [
        ("Borrowed Moment", "Fleeting Insight", "cooldown_recovery", "fleeting_insight"),
        ("Echo Fuel", "Spell Echo Engine", "maximum_mana", "spell_echo_fuel"),
        ("Moving Time", "Mobile Incantation", "cast_speed", "mobile_incantation"),
        ("Desperate Hour", "Desperate Focus", "spell_damage", "desperate_focus"),
    ]),
    "archmage": ("arcanist", [
        ("Might of Mind", "Iron Will", "intelligence", "iron_will"),
        ("Perfect Reserve", "Pristine Mind", "maximum_mana", "pristine_mind"),
        ("Soul Current", "Soul Drinker", "mana_regeneration", "soul_drinker"),
        ("Echo Reservoir", "Endless Echo", "spell_damage", "spell_echo_fuel"),
    ]),
    "inquisitor": ("templar", [
        ("Consecrated Steel", "Hallowed Weapon", "holy_damage", "consecrated_steel"),
        ("Judgment", "Holy Recovery", "critical_chance", "holy_recovery"),
        ("Shield Scripture", "Shield Scholar", "spell_block", "shield_scholar"),
        ("Battle Gospel", "Battle Mage", "spell_damage", "battle_mage"),
    ]),
    "sentinel": ("templar", [
        ("Arcane Wall", "Arcane Bulwark", "block_chance", "arcane_bulwark"),
        ("Runic Guard", "Rune Guard", "ward", "rune_guard"),
        ("Unbroken", "Lasting Bastion", "maximum_life", "unbroken"),
        ("Disruption", "Disruptive Guard", "spell_block", "disruptive_guard"),
    ]),
    "oracle": ("templar", [
        ("Pristine Vision", "Pristine Mind", "maximum_mana", "pristine_mind"),
        ("Vital Omen", "Vital Surge", "life_regeneration", "vital_surge"),
        ("Hallowed Sign", "Holy Recovery", "holy_damage", "holy_recovery"),
        ("Foreseen Block", "Arcane Bulwark", "block_chance", "arcane_bulwark"),
    ]),
    "crusader": ("templar", [
        ("Bastion Strike", "Shield of the March", "melee_damage", "bastion_strike"),
        ("Consecration", "Consecrated Steel", "holy_damage", "consecrated_steel"),
        ("Rallying Faith", "War Cry", "maximum_life", "war_cry"),
        ("Retribution", "Judgment Returned", "armor", "retribution"),
    ]),
    "druid": ("shaman", [
        ("Thornskin", "Thorned Blade", "nature_damage", "thorned_blade"),
        ("Perfect Shape", "Perfect Form", "evasion", "perfect_form"),
        ("Predator Form", "Apex Instinct", "melee_damage", "predator"),
        ("Verdant Rot", "Verdant Decay", "poison_damage", "verdant_decay"),
    ]),
    "necromancer": ("shaman", [
        ("Blood Rite", "Bloodletting", "blood_damage", "bloodletting"),
        ("Harvest", "Sanguine Recovery", "life_regeneration", "sanguine_recovery"),
        ("Soul Well", "Soul Drinker", "summon_damage", "soul_drinker"),
        ("Blood Price", "Life for Power", "maximum_life", "blood_price"),
    ]),
    "elementalist": ("shaman", [
        ("Avatar", "Elemental Avatar", "spell_damage", "elemental_avatar"),
        ("Flame", "Infernal Edge", "fire_damage", "infernal_edge"),
        ("Winter", "Winter Steel", "cold_damage", "winter_steel"),
        ("Storm", "Stormforged", "lightning_damage", "stormforged"),
    ]),
    "spiritwalker": ("shaman", [
        ("Walking Rite", "Mobile Incantation", "movement_speed", "mobile_incantation"),
        ("Vital Spirit", "Vital Surge", "ward", "vital_surge"),
        ("Soul Current", "Soul Drinker", "mana_regeneration", "soul_drinker"),
        ("Caustic Spirit", "Caustic Reservoir", "nature_damage", "caustic_reservoir"),
    ]),
}

PASSIVE_TITLES = {
    "maximum_life": "Vitality", "maximum_mana": "Arcane Reserve", "armor": "Iron Skin",
    "evasion": "Footwork", "ward": "Aegis", "attack_speed": "Tempo", "cast_speed": "Invocation",
    "physical_damage": "Force", "fire_damage": "Embercraft", "cold_damage": "Wintercraft",
    "lightning_damage": "Stormcraft", "blood_damage": "Bloodcraft", "holy_damage": "Consecration",
    "ender_damage": "Voidcraft", "nature_damage": "Wildcraft", "spell_damage": "Sorcery",
    "attack_damage": "Arms Training", "melee_damage": "Martial Force", "projectile_damage": "Ballistics",
    "critical_chance": "Precision", "critical_multiplier": "Lethality", "cooldown_recovery": "Readiness",
    "block_chance": "Guard", "spell_block": "Spellguard", "strength": "Might", "dexterity": "Finesse",
    "intelligence": "Insight", "movement_speed": "Stride", "mana_regeneration": "Clarity",
    "life_regeneration": "Renewal", "life_leech": "Leech", "mana_leech": "Siphon",
    "area_damage": "Expansion", "damage_over_time": "Attrition", "ignite_damage": "Combustion",
    "bleed_damage": "Hemorrhage", "poison_damage": "Venom", "summon_damage": "Command",
}

EPITHETS = {
    "warrior": ["Vanguard", "Bastion", "Conqueror", "Ironbound", "Warmaster"],
    "ranger": ["Horizon", "Wildshot", "Trailblazer", "Hunter", "Windrunner"],
    "rogue": ["Nightblade", "Cutpurse", "Shade", "Executioner", "Phantom"],
    "arcanist": ["Thaumaturge", "Invoker", "Savant", "Spellwright", "Arcanum"],
    "templar": ["Oathkeeper", "Sanctum", "Justicar", "Aegis", "Votary"],
    "shaman": ["Wildheart", "Spiritbound", "Totemist", "Witchwood", "Ancestor"],
}


def read(path: Path):
    return json.loads(path.read_text())


def write(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def stat_reward(stat: str, tier: int):
    if stat == "maximum_mana":
        return {"stat": stat, "operation": "flat", "value": 8 * tier}
    if stat in {"maximum_life", "ward"}:
        return {"stat": stat, "operation": "flat", "value": 2 * tier}
    if stat in {"strength", "dexterity", "intelligence"}:
        return {"stat": stat, "operation": "flat", "value": 4 * tier}
    if stat in {"block_chance", "spell_block", "critical_chance", "ignite_chance", "bleed_chance", "poison_chance"}:
        return {"stat": stat, "operation": "flat", "value": round(0.02 * tier, 4)}
    if stat == "critical_multiplier":
        return {"stat": stat, "operation": "flat", "value": round(0.06 * tier, 4)}
    if stat in {"life_leech", "mana_leech"}:
        return {"stat": stat, "operation": "flat", "value": round(0.005 * tier, 4)}
    if stat in {"attack_speed", "cast_speed", "movement_speed", "cooldown_recovery"}:
        return {"stat": stat, "operation": "increased", "value": round(0.06 * tier, 4)}
    if stat in {"life_regeneration", "mana_regeneration"}:
        return {"stat": stat, "operation": "increased", "value": round(0.12 * tier, 4)}
    return {"stat": stat, "operation": "increased", "value": round(0.12 * tier, 4)}


def reward_text(modifier):
    stat = modifier["stat"].replace("_", " ")
    operation = modifier["operation"]
    value = modifier["value"]
    if operation == "flat":
        if stat.endswith("chance") or stat in {"block chance", "spell block", "critical multiplier", "life leech", "mana leech"}:
            return f"+{value * 100:g}% {stat}"
        return f"+{value:g} {stat}"
    return f"{value * 100:g}% {operation} {stat}"


def definition(title, description, rewards, icon, size=1.0, cost=1):
    return {
        "title": title,
        "description": description,
        "rewards": rewards,
        "icon": {"type": "texture", "data": {"texture": f"minecraft:textures/item/{icon}.png"}},
        "size": size,
        "cost": cost,
    }


def generate_ascendancies(catalog):
    rules = {rule["id"]: rule for rule in catalog["rules"]}
    for ascendancy, (discipline, branches) in ASCENDANCIES.items():
        discipline_title, icon = DISCIPLINES[discipline]
        title = ascendancy.replace("_", " ").title()
        folder = TREE / f"arpg_asc_{ascendancy}"
        nodes = {}
        definitions = {}
        edges = []

        start = f"{ascendancy}_start"
        nodes[start] = {"x": 0, "y": 0, "definition": start, "root": True}
        definitions[start] = definition(
            f"{title} Ascendancy",
            f"{discipline_title} identity node. Four branches radiate from this origin; finish two capstones with eight Trial points.",
            [], icon, 1.6, 0,
        )

        for branch_index, (branch_name, capstone_name, stat, rule_id) in enumerate(branches):
            angle = -math.pi / 2 + branch_index * math.pi / 2
            previous = start
            for tier, radius in enumerate((82, 158, 242), start=1):
                node = f"{ascendancy}_b{branch_index + 1}_{tier}"
                x = round(math.cos(angle) * radius)
                y = round(math.sin(angle) * radius)
                nodes[node] = {"x": x, "y": y, "definition": node, "root": False}
                modifier = stat_reward(stat, 1 if tier == 1 else 2)
                rewards = [{"type": "puffish_skills:arpg_stat", "data": modifier}]
                description = reward_text(modifier)
                size = 1.0 if tier == 1 else 1.25
                cost = 1
                node_title = f"{branch_name} Path" if tier == 1 else f"{branch_name} Mastery"

                if tier == 3:
                    rule = rules.get(rule_id)
                    if rule is None:
                        raise RuntimeError(f"Ascendancy {ascendancy} references missing rule {rule_id}")
                    rewards.append({"type": "puffish_skills:arpg_rule", "data": {"rule": rule_id}})
                    description += "; " + rule["description"]
                    node_title = capstone_name
                    size = 1.6
                    cost = 2

                definitions[node] = definition(node_title, description, rewards, icon, size, cost)
                edges.append([previous, node])
                previous = node

        write(folder / "category.json", {
            "title": title,
            "description": "Four branching paths with two-point capstones. Earn eight Ascendancy points from four Trials.",
            "icon": {"type": "texture", "data": {"texture": f"minecraft:textures/item/{icon}.png"}},
            "background": "minecraft:textures/block/deepslate_tiles.png",
            "unlocked_by_default": False,
            "starting_points": 0,
            "spent_points_limit": 8,
        })
        write(folder / "skills.json", nodes)
        write(folder / "definitions.json", definitions)
        write(folder / "connections.json", {"normal": {"bidirectional": edges}})


def improve_universal_titles():
    folder = TREE / "arpg_universal"
    definitions_path = folder / "definitions.json"
    definitions = read(definitions_path)
    renamed = 0

    for node_id, data in definitions.items():
        if "/" not in node_id or node_id.startswith("keystone/"):
            continue
        parts = node_id.split("/")
        if len(parts) != 3 or parts[0] not in DISCIPLINES:
            continue
        discipline, cluster_text, index_text = parts
        stat = None
        for reward in data.get("rewards", []):
            if reward.get("type") == "puffish_skills:arpg_stat":
                stat = reward.get("data", {}).get("stat")
                if stat:
                    break
        if not stat:
            continue

        base = PASSIVE_TITLES.get(stat, stat.replace("_", " ").title())
        cluster = int(cluster_text)
        index = int(index_text)
        if float(data.get("size", 1.0)) >= 1.2:
            epithet = EPITHETS[discipline][cluster % len(EPITHETS[discipline])]
            data["title"] = f"{epithet} {base}"
        else:
            suffix = ("Path", "Training", "Discipline", "Practice")[(cluster + index) % 4]
            data["title"] = f"{base} {suffix}"
        renamed += 1

    write(definitions_path, definitions)
    category_path = folder / "category.json"
    category = read(category_path)
    category["description"] = (
        "1,602-node radial passive graph: six class origins, orbit clusters, cross-sector bridges, "
        "270 notables, 66 keystones and six jewel sockets. Drag to pan and use the wheel to zoom."
    )
    write(category_path, category)
    return renamed


def validate():
    for ascendancy in ASCENDANCIES:
        folder = TREE / f"arpg_asc_{ascendancy}"
        skills = read(folder / "skills.json")
        definitions = read(folder / "definitions.json")
        connections = read(folder / "connections.json")["normal"]["bidirectional"]
        assert len(skills) == 13, (ascendancy, len(skills))
        assert len(definitions) == 13, (ascendancy, len(definitions))
        assert len(connections) == 12, (ascendancy, len(connections))
        assert sum(defn.get("cost", 1) for key, defn in definitions.items() if not key.endswith("_start")) == 16
        assert definitions[f"{ascendancy}_start"]["cost"] == 0


if __name__ == "__main__":
    catalog = read(CATALOG_PATH)
    generate_ascendancies(catalog)
    renamed = improve_universal_titles()
    validate()
    print(json.dumps({"ascendancies": len(ASCENDANCIES), "universal_nodes_renamed": renamed, "layout": "radial-branch-v2"}))
