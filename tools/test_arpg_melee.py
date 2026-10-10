#!/usr/bin/env python3
"""Check exact skill bindings, reachable specialization budgets and additive regeneration."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from generate_arpg_melee import ROOT, ATTACKS, BRANCHES, generate


class MeleeContentChecks(unittest.TestCase):
    def test_catalog_trees_bind_only_their_own_skill(self):
        catalog = json.loads((ROOT / "arpg/catalog.json").read_text())
        skills = {skill["id"]: skill for skill in catalog["skills"]}
        rules = {rule["id"]: rule for rule in catalog["rules"]}
        categories = json.loads((ROOT / "puffish_skills/config.json").read_text())["categories"]
        for name, title, level, cost, cooldown in ATTACKS:
            sid = "puffish_skills:" + name
            skill = skills[sid]
            self.assertEqual(("epicfight", level, cost, cooldown),
                             (skill["provider"], skill["level"], skill["cost"], skill["cooldown"]))
            category = "arpg_skill_" + sid.replace(":", "_")
            self.assertEqual(1, categories.count(category))
            folder = ROOT / "puffish_skills/categories" / category
            metadata = json.loads((folder / "category.json").read_text())
            self.assertFalse(metadata["unlocked_by_default"])
            self.assertEqual(20, metadata["spent_points_limit"])
            nodes = json.loads((folder / "skills.json").read_text())
            definitions = json.loads((folder / "definitions.json").read_text())
            edges = json.loads((folder / "connections.json").read_text())["normal"]["bidirectional"]
            self.assertEqual(24, len(nodes))
            self.assertEqual(set(nodes), set(definitions))
            self.assertEqual(21, len(edges))
            reached = {key for key, node in nodes.items() if node.get("root")}
            self.assertEqual(3, len(reached))
            for _ in range(8):
                reached.update(second for first, second in edges if first in reached)
            self.assertEqual(set(nodes), reached)
            for j, definition in enumerate(definitions.values()):
                rule = rules[definition["rewards"][0]["data"]["rule"]]
                self.assertEqual(sid, rule["skill"])
                self.assertEqual("conditional", rule["kind"])
                stat, value = BRANCHES[j // 8]
                self.assertEqual([dict(stat=stat, operation="increased", value=value)], rule["modifiers"])
        self.assertEqual(len(skills), json.loads((ROOT / "arpg/manifest.json").read_text())["skills"])

    def test_regeneration_preserves_other_graphs_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "data/puffish_skills"
            shutil.copytree(ROOT, root)
            before = {str(path.relative_to(root)): path.read_bytes() for path in root.rglob("*") if path.is_file()}
            generate(root)
            after = {str(path.relative_to(root)): path.read_bytes() for path in root.rglob("*") if path.is_file()}
            self.assertEqual(before, after)
            generate(root)
            self.assertEqual(after, {str(path.relative_to(root)): path.read_bytes() for path in root.rglob("*") if path.is_file()})


if __name__ == "__main__":
    unittest.main()
