#!/usr/bin/env python3
"""Validate spell content links and deterministic regeneration independently of Minecraft."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from generate_arpg_spells import ROOT, SPELLS, generate


class SpellContentChecks(unittest.TestCase):
    def test_catalog_and_specializations_are_complete(self):
        catalog = json.loads((ROOT / "arpg/catalog.json").read_text())
        skills = {skill["id"]: skill for skill in catalog["skills"]}
        rules = {rule["id"]: rule for rule in catalog["rules"]}
        categories = json.loads((ROOT / "puffish_skills/config.json").read_text())["categories"]
        for name, title, element, discipline, level in SPELLS:
            sid = "puffish_skills:" + name
            skill = skills[sid]
            self.assertEqual(("irons", discipline, level, 0),
                             (skill["provider"], skill["discipline"], skill["level"], skill["cost"]))
            category = "arpg_skill_" + sid.replace(":", "_")
            self.assertEqual(1, categories.count(category))
            folder = ROOT / "puffish_skills/categories" / category
            nodes = json.loads((folder / "skills.json").read_text())
            definitions = json.loads((folder / "definitions.json").read_text())
            edges = json.loads((folder / "connections.json").read_text())["normal"]["bidirectional"]
            self.assertEqual(24, len(nodes))
            self.assertEqual(set(nodes), set(definitions))
            self.assertEqual(3, sum(node.get("root", False) for node in nodes.values()))
            self.assertEqual(21, len(edges))
            for first, second in edges:
                self.assertIn(first, nodes)
                self.assertIn(second, nodes)
            for definition in definitions.values():
                rule = rules[definition["rewards"][0]["data"]["rule"]]
                self.assertEqual(sid, rule["skill"])
                self.assertIn(rule["modifiers"][0]["stat"], ["spell_damage", element + "_damage", "area_damage"])
            icon = ROOT.parents[1] / "assets/puffish_skills/textures/gui/spell_icons" / (name + ".png")
            self.assertEqual(b"\x89PNG\r\n\x1a\n", icon.read_bytes()[:8])
        self.assertEqual(len(skills), json.loads((ROOT / "arpg/manifest.json").read_text())["skills"])

    def test_regeneration_preserves_unrelated_authored_content_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "resources/data/puffish_skills"
            shutil.copytree(ROOT, root)
            before = json.loads((root / "arpg/catalog.json").read_text())
            catalog = root / "arpg/catalog.json"
            names = {"puffish_skills:" + spell[0] for spell in SPELLS}
            unrelated = [skill for skill in before["skills"] if skill["id"] not in names]
            sentinel = root / "puffish_skills/categories/arpg_universal/definitions.json"
            authored = sentinel.read_bytes()
            generate(root)
            self.assertEqual(authored, sentinel.read_bytes())
            after = json.loads(catalog.read_text())
            self.assertEqual(unrelated, [skill for skill in after["skills"] if skill["id"] not in names])
            first = {str(path.relative_to(root)): path.read_bytes() for path in root.rglob("*") if path.is_file()}
            generate(root)
            self.assertEqual(first, {str(path.relative_to(root)): path.read_bytes() for path in root.rglob("*") if path.is_file()})


if __name__ == "__main__":
    unittest.main()
