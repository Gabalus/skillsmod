#!/usr/bin/env python3
"""Run completion currency checks without Minecraft or Gradle dependencies."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
main = root / "Common/src/main/java/net/puffish/skillsmod/arpg"
sources = [main / path for path in (
    "character/ArpgCharacter.java", "progression/CompletionReward.java",
    "progression/CompletionReceipt.java", "progression/CompletionCatalog.java",
    "sandbox/CraftBinding.java", "sandbox/SandboxCatalog.java",
    "sandbox/CraftSession.java", "sandbox/CraftMechanics.java", "sandbox/SandboxState.java")]
sources.append(root / "Common/src/test/java/net/puffish/skillsmod/arpg/progression/CompletionRewardChecks.java")
with tempfile.TemporaryDirectory(prefix="completion-rewards-") as output:
    subprocess.run(["java", "com.sun.tools.javac.Main", "-d", output,
                    *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", output,
                    "net.puffish.skillsmod.arpg.progression.CompletionRewardChecks"], check=True)
