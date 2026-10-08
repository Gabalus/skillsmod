#!/usr/bin/env python3
"""Check rift ownership, bounded allocation and interrupted-run recovery."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
main = root / "Common/src/main/java/net/puffish/skillsmod/arpg"
sources = [main / path for path in (
    "progression/CompletionReward.java", "rift/RiftSession.java", "rift/RiftBook.java")]
sources.append(root / "Common/src/test/java/net/puffish/skillsmod/arpg/rift/RiftEngineChecks.java")
with tempfile.TemporaryDirectory(prefix="rift-engine-") as output:
    subprocess.run(["java", "com.sun.tools.javac.Main", "-d", output,
                    *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", output,
                    "net.puffish.skillsmod.arpg.rift.RiftEngineChecks"], check=True)
