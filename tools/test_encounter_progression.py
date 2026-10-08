#!/usr/bin/env python3
"""Run encounter reward regression checks without Minecraft or Gradle downloads."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
sources = [root / path for path in (
    "Common/src/main/java/net/puffish/skillsmod/arpg/character/ArpgCharacter.java",
    "Common/src/main/java/net/puffish/skillsmod/arpg/progression/EncounterThreat.java",
    "Common/src/main/java/net/puffish/skillsmod/arpg/progression/EncounterProgressionPolicy.java",
    "Common/src/test/java/net/puffish/skillsmod/arpg/progression/EncounterProgressionChecks.java")]
with tempfile.TemporaryDirectory(prefix="encounter-progression-") as output:
    subprocess.run(["java", "com.sun.tools.javac.Main", "-d", output,
                    *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", output,
                    "net.puffish.skillsmod.arpg.progression.EncounterProgressionChecks"], check=True)
