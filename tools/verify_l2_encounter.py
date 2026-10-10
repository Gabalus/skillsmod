"""Cross-check the authored Sentinel profile against the pinned provider's data."""
import json
from pathlib import Path
import zipfile


PROFILE = Path(__file__).resolve().parents[1] / (
    "NeoForge/src/main/resources/data/puffish_skills/"
    "l2hostility_config/entity/tower_sentinel.json")


def verify_sentinel_profile(hostility_jar):
    with zipfile.ZipFile(hostility_jar) as jar:
        native = json.loads(jar.read(
            "data/l2hostility/l2hostility_config/entity/bosses.json"))["list"][0]
        traits = json.loads(jar.read(
            "data/l2hostility/data_maps/l2hostility/trait/trait_data.json"))["values"]
    document = json.loads(PROFILE.read_text())
    if set(document) != {"list"} or len(document["list"]) != 1:
        raise ValueError("Expected one entity-specific Sentinel profile")
    profile = document["list"][0]
    if set(profile) != set(native) or profile["entities"] != "puffish_skills:tower_sentinel":
        raise ValueError("Sentinel profile schema or entity selector changed")
    difficulty = profile["difficulty"]
    if set(difficulty) != set(native["difficulty"]):
        raise ValueError("Provider difficulty schema changed")
    level = profile["maxLevel"]
    if not isinstance(level, int) or not 20 <= level <= 30:
        raise ValueError("Early Sentinel level must stay within 20..30")
    if difficulty != dict(apply_chance=1.0, base=level, min=level,
                          scale=0.0, suppression=0.0, trait_chance=1.0, variation=0.0):
        raise ValueError("Sentinel needs a bounded, non-random native difficulty profile")
    if profile["minSpawnLevel"] != 0 or profile["specialConditions"] or profile["items"]:
        raise ValueError("Sentinel must not depend on spawn suppression or random equipment")
    if any(not isinstance(profile[key], (int, float)) or not 0 <= profile[key] <= 1
           for key in ("healthScale", "attackScale")):
        raise ValueError("Sentinel native scaling weights must stay within 0..1")
    authored = profile["traits"]
    if not 1 <= len(authored) <= 2 or profile["maxTraitCount"] != len(authored):
        raise ValueError("Early Sentinel supports at most two authored traits")
    ids = set()
    for trait in authored:
        if set(trait) != {"trait", "free", "min", "cap"}:
            raise ValueError("Unsupported authored trait schema")
        tid = trait["trait"]
        if tid not in traits or tid in ids:
            raise ValueError(f"Unknown or repeated Sentinel trait: {tid}")
        ids.add(tid)
        rank = trait["free"]
        if (not isinstance(rank, int) or not 1 <= rank <= min(2, traits[tid]["max_rank"])
                or trait["min"] != rank or trait["cap"] is not True
                or traits[tid]["min_level"] > level):
            raise ValueError(f"Unbounded or unavailable Sentinel trait: {tid}")
    blacklist = profile["blacklist"]
    if len(blacklist) != len(set(blacklist)) or set(blacklist) != set(traits) - ids:
        raise ValueError("Sentinel must exclude every other pinned L2 trait")
    return profile


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("hostility_jar", type=Path)
    args = parser.parse_args()
    profile = verify_sentinel_profile(args.hostility_jar)
    print(f"Verified Sentinel L2 level cap {profile['maxLevel']}, bounded traits and provider schema.")
