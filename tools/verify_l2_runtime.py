"""Verify the exact optional Hostility profile without starting Minecraft."""
import argparse
import re
import subprocess
import tomllib
import zipfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--javap", required=True)
    parser.add_argument("--report-dir", type=Path, required=True)
    parser.add_argument("jars", nargs="+", type=Path)
    args = parser.parse_args()
    mods = {}
    for path in args.jars:
        with zipfile.ZipFile(path) as jar:
            if "META-INF/neoforge.mods.toml" not in jar.namelist():
                continue
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
            for mod in metadata.get("mods", []):
                version = mod["version"]
                if version == "${file.jarVersion}":
                    manifest = jar.read("META-INF/MANIFEST.MF").decode()
                    match = re.search(r"^Implementation-Version: (.+)$", manifest, re.MULTILINE)
                    if not match:
                        raise ValueError(f"Missing JAR implementation version: {path}")
                    version = match.group(1).strip()
                mods[mod["modId"]] = (version, path, metadata)
    expected = {"l2hostility": "3.0.16", "l2library": "3.0.8",
                "l2complements": "3.1.2", "curios": "9.5.1+1.21.1",
                "patchouli": "1.21.1-93-NEOFORGE"}
    for name, version in expected.items():
        if name not in mods or mods[name][0].lower() != version.lower():
            raise ValueError(f"Expected {name} {version}; found {mods.get(name)}")
    # Check declared provider requirements as well as the coordinate pins. A new
    # required mod or version range must be reviewed, not silently ignored.
    supported_ranges = {"neoforge": "[21.1.61,)", "minecraft": "[1.21.1,1.22)",
                        "l2library": "[3.0.3,)", "l2complements": "[3.1.2,)",
                        "curios": "[5,)", "patchouli": "[0,)"}
    dependencies = mods["l2hostility"][2]["dependencies"]["l2hostility"]
    required = {d["modId"]: d["versionRange"] for d in dependencies if d.get("type") == "required"}
    if required != supported_ranges:
        raise ValueError(f"Hostility required dependency declarations changed: {required}")
    library_dependencies = mods["l2complements"][2]["dependencies"]["l2complements"]
    if not any(d["modId"] == "l2library" and d["versionRange"] == "[3.0.4,)" for d in library_dependencies):
        raise ValueError("Unexpected Complements library requirement")
    args.report_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(mods["l2library"][1]) as jar:
        entries = [n for n in jar.namelist() if re.fullmatch(r"META-INF/jarjar/l2core-.*\.jar", n)]
        if len(entries) != 1:
            raise ValueError(f"Expected one nested L2 Core jar: {entries}")
        core = args.report_dir / "l2core.jar"
        core.write_bytes(jar.read(entries[0]))
    import os
    classpath = os.pathsep.join(str(p) for p in [mods["l2hostility"][1], core])
    classes = ["dev.xkmc.l2hostility.init.registrate.LHMiscs",
               "dev.xkmc.l2hostility.content.capability.mob.MobTraitCap",
               "dev.xkmc.l2core.init.reg.simple.AttVal",
               "dev.xkmc.l2core.capability.attachment.GeneralCapabilityHolder",
               "dev.xkmc.l2core.init.reg.registrate.NamedEntry"]
    result = subprocess.run([args.javap, "-classpath", classpath, "-s", *classes],
                            text=True, capture_output=True, check=True)
    if result.stderr.strip():
        raise ValueError(f"L2 API inspection reported errors: {result.stderr}")
    api = result.stdout
    for signature in [" MOB;", " traits;", "public boolean summoned;", "public boolean minion;",
                      "public boolean noDrop;", "public int getLevel();", "public abstract H type();",
                      "getExisting(E)", "getRegistryName();"]:
        if signature not in api:
            raise ValueError(f"Missing L2 reflection API signature: {signature}")
    (args.report_dir / "api.txt").write_text(api)
    (args.report_dir / "versions.txt").write_text("\n".join(f"{name} {mods[name][0]}" for name in expected))
    print("Verified five pinned L2 runtime mods, Hostility dependency requirements and reflection API.")


if __name__ == "__main__":
    main()
