"""Validate setup wire branches against the same full proof as signed snapshots."""
import copy
import json
import unittest
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator, RefResolver

ROOT = Path(__file__).resolve().parents[1]


class PublicSetupContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.api = yaml.safe_load((ROOT / "contracts/openapi.yaml").read_text())
        cls.base = "https://contracts.namaztime.test/openapi.yaml"
        snapshot_schema = json.loads((ROOT / "contracts/prayer-snapshot.schema.json").read_text())
        cls.resources = {
            cls.base: cls.api,
            "https://contracts.namaztime.test/prayer-snapshot.schema.json": snapshot_schema,
            snapshot_schema["$id"]: snapshot_schema,
        }
        fixture = ROOT / "apps/tv-android/src/test/resources/qualified-interop/snapshot.json"
        cls.proof = json.loads(fixture.read_text())["source"]["qualification"]

    def check(self, name, value, valid=True):
        validator = Draft202012Validator(
            {"$ref": self.base + "#/components/schemas/" + name},
            resolver=RefResolver(self.base, self.api, store=self.resources),
        )
        errors = list(validator.iter_errors(value))
        if valid:
            self.assertEqual([], [e.message for e in errors])
        else:
            self.assertTrue(errors, "invalid public/legacy branch accepted")

    def test_full_signed_qualification_and_missing_evidence(self):
        self.check("SourceQualification", self.proof)
        for field in ("evidence", "comparisons", "artifact", "parser_version", "qualified_at"):
            changed = copy.deepcopy(self.proof)
            del changed[field]
            with self.subTest(missing=field):
                self.check("SourceQualification", changed, valid=False)
        changed = {**self.proof, "approved_by": "invented person"}
        self.check("SourceQualification", changed, valid=False)

    def test_public_and_legacy_registry_policy_branches(self):
        proof = self.proof
        common = dict(id="synthetic-policy", kind="timetable", geographic_scope_id=proof["scope"]["id"],
                      authority_ids=[proof["authority"]["id"]], source_id=proof["source_id"],
                      timetable_id="synthetic-table", effective=proof["coverage"])
        public = dict(common, mosque_ids=[], qualification_id=proof["qualification_id"])
        legacy = dict(common, mosque_ids=["synthetic-mosque"], approval_id="synthetic-approval")
        self.check("RegistryPrayerPolicy", public)
        self.check("RegistryPrayerPolicy", dict(public, mosque_ids=None))
        self.check("RegistryPrayerPolicy", legacy)
        self.check("RegistryPrayerPolicy", dict(public, approval_id="invented"), valid=False)
        self.check("RegistryPrayerPolicy", dict(public, mosque_ids=["invented"]), valid=False)
        self.check("RegistryPrayerPolicy", dict(legacy, mosque_ids=[]), valid=False)
        self.check("RegistryPrayerPolicy", dict(legacy, mosque_ids=None), valid=False)
        self.check("RegistryPrayerPolicy", dict(common, mosque_ids=[]), valid=False)

    def test_public_source_status_requires_qualification_reference(self):
        proof = self.proof
        source = dict(id=proof["source_id"], kind=proof["source_kind"],
                      authority_ids=[proof["authority"]["id"]], geographic_scope_id=proof["scope"]["id"],
                      status="qualified", qualification_id=proof["qualification_id"])
        self.check("RegistryPrayerSource", source)
        changed = dict(source)
        del changed["qualification_id"]
        self.check("RegistryPrayerSource", changed, valid=False)
        self.check("RegistryPrayerSource", dict(source, status="approved"), valid=False)


if __name__ == "__main__":
    unittest.main()
