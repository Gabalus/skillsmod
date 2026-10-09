#!/usr/bin/env python3
"""Check authored tower anchors, first-clear gates and cross-dimension graph validation."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
main = root / "Common/src/main/java/net/puffish/skillsmod/arpg"
sources = [main / path for path in (
    "progression/CompletionReward.java", "tower/TowerLink.java", "tower/TowerCatalog.java",
    "tower/TowerSector.java", "tower/TowerProtection.java", "tower/TowerPuzzle.java",
    "tower/TowerPuzzleCatalog.java", "tower/TowerPuzzleState.java", "tower/TowerRecovery.java",
    "tower/TowerRecoveryCatalog.java", "tower/TowerRecoveryTicket.java", "tower/TowerExit.java", "tower/TowerExitCatalog.java",
    "tower/TowerLandingSearch.java")]
sources.append(root / "Common/src/test/java/net/puffish/skillsmod/arpg/tower/TowerEngineChecks.java")
with tempfile.TemporaryDirectory(prefix="tower-engine-") as output:
    subprocess.run(["java", "com.sun.tools.javac.Main", "-d", output,
                    *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", output,
                    "net.puffish.skillsmod.arpg.tower.TowerEngineChecks"], check=True)
