"""Offline integrity checks for the repository's skill entrypoints and commands."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parent.parent
SKILLS = ROOT / ".agents/skills"


class SkillPackageTest(unittest.TestCase):
    def test_entrypoint_resources_exist(self):
        entrypoints = sorted(SKILLS.glob("*/SKILL.md"))
        self.assertTrue(entrypoints, "No repository skills discovered")
        for entrypoint in entrypoints:
            for reference in re.findall(
                r"\b((?:references|scripts|templates|evals)/[\w./-]+\.(?:md|py|cjs|json|js|csv))\b",
                entrypoint.read_text(encoding="utf-8"),
            ):
                if any(character in reference for character in "*{}<>"):
                    continue
                with self.subTest(file=str(entrypoint.relative_to(ROOT)), reference=reference):
                    self.assertTrue((entrypoint.parent / reference).exists())

    def test_documented_skill_commands_resolve(self):
        for document in sorted(SKILLS.rglob("*.md")):
            for reference in re.findall(
                r"(?:~/)?\.(?:agents|claude)/skills/[\w./-]+\.(?:py|cjs|js)",
                document.read_text(encoding="utf-8"),
            ):
                with self.subTest(file=str(document.relative_to(ROOT)), reference=reference):
                    self.assertTrue((ROOT / reference).is_file(), reference)


if __name__ == "__main__":
    unittest.main()
