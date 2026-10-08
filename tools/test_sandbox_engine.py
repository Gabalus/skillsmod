#!/usr/bin/env python3
"""Compile and run the pure craftwork engine without downloading Minecraft or Gradle."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
main = root / "Common/src/main/java/net/puffish/skillsmod/arpg/sandbox"
test = root / "Common/src/test/java/net/puffish/skillsmod/arpg/sandbox/SandboxEngineChecks.java"
sources = [main / (name + ".java") for name in (
    "CraftBinding", "CraftQualityEffects", "SandboxCatalog", "CraftSession", "CraftMechanics", "SandboxState", "CraftedItemData")]
with tempfile.TemporaryDirectory(prefix="sandbox-engine-") as output:
    subprocess.run(["java", "com.sun.tools.javac.Main", "-d", output,
                    *map(str, sources), str(test)], check=True)
    subprocess.run(["java", "-cp", output,
                    "net.puffish.skillsmod.arpg.sandbox.SandboxEngineChecks"], check=True)
