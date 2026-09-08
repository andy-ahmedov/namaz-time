"""Synthetic metadata tests: no authority data, admission or signatures invented.

The compiler consumes an explicitly trusted prior-admission attestation. These
fixtures exercise that reporting boundary, not source/signature verification.
"""

import copy
import contextlib
import hashlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from t049_nationwide_registry import compile_registry, main


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


class NationwideRegistryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.bundle = self.root / "outside-git" / "bundle"
        self.bundle.mkdir(parents=True)
        self.catalog_path = self.root / "catalog.json"
        self.index_path = self.root / "proof-index.json"
        self.lane_path = "research/t049/synthetic-lane.json"
        self.catalog = {
            "schema_version": "namaztime-city-catalog/v1",
            "revision": {"id": "catalog-synthetic", "content_sha256": "a" * 64},
            "source": {"license": "synthetic"},
            "regions": [
                {"id": "ru-aa", "federal_subject_code": "RU-AA", "name": "Регион А", "country_code": "RU"},
                {"id": "ru-bb", "federal_subject_code": "RU-BB", "name": "Регион Б", "country_code": "RU"},
            ],
            "cities": [self.city("city-a1", "ru-aa"), self.city("city-a2", "ru-aa"), self.city("city-b", "ru-bb")],
        }
        self.lane = {
            "schema_version": "t049-research-lane/v1", "researched_at": "2026-09-08",
            "subjects": [self.subject("RU-AA"), self.subject("RU-BB")],
        }
        self.policy = self.make_policy("policy-a", "authority-a")
        self.choices = {
            "schema_version": "namaztime-local-setup-choices/v1", "registry_revision_id": "registry-synthetic",
            "policies": [self.policy], "bindings": [self.binding("city-a1", "policy-a")],
        }
        self.revision = {
            "id": "registry-synthetic", "schema_version": 2, "catalog_revision_id": "catalog-synthetic",
            "content_sha256": "b" * 64, "created_at": "2026-09-08T00:00:00Z",
            "created_by": "test:operator", "reason": "Synthetic prior-admission reporting input",
        }
        self.record_path = self.root / "outside-git" / "admission-record.txt"
        self.record_path.write_bytes(b"Synthetic prior verification record; NOT real source admission.\n")
        self.index = {
            "schema_version": "t049-operational-proof-index/v1", "index_id": "synthetic-index",
            "catalog": {"raw_sha256": "", "revision_id": "catalog-synthetic", "content_sha256": "a" * 64},
            "research_inputs": [],
            "admission": {
                "kind": "previously_verified_local_setup_export",
                "verified_at": "2026-09-08T00:00:00Z",
                "record": {"path": str(self.record_path), "sha256": digest(self.record_path.read_bytes())},
                "manifest": {"path": str(self.bundle / "manifest.json"), "sha256": ""},
                "registry_revision_id": "registry-synthetic", "registry_content_sha256": "b" * 64,
                "verification_command": "synthetic fixture; no command executed",
                "claim": "Synthetic attestation fixture; not a cryptographic verification test.",
            },
            "policy_research_bindings": [self.mapping("policy-a")],
        }
        self.write(self.bundle / "catalog.sqlite", b"Synthetic opaque index bytes: catalog counting uses the pinned canonical JSON.")
        for name in ("production", "previous-production", "test", "staging"):
            self.write(self.bundle / "trust" / (name + ".json"), encoded({"synthetic_anchor": name}))

    @staticmethod
    def city(city_id, region_id):
        return {"id": city_id, "name": "Одно имя", "aliases": ["Alias", "  alias  "],
                "country_code": "RU", "region_id": region_id, "timezone": "Europe/Moscow"}

    @staticmethod
    def subject(code):
        source = {"id": "source-a", "type": "official_file", "canonical_url": "https://synthetic.invalid/table",
                  "scope": {"kind": "locality", "label": "One locality only"}, "localities": [{"name": "Same name"}],
                  "effective_range": {"start": "2026-09-08", "end": "2026-09-08"},
                  "timezone": "UNKNOWN", "transport": {"access": "synthetic"}, "policy": {"asr": "UNKNOWN"},
                  "comparison_evidence": [], "terms_notes": "synthetic", "qualification_gaps": ["not qualified"]}
        return {"code": code, "name": "Synthetic " + code, "research_status": "researched",
                "authorities": [{"id": "authority-a", "name": "Synthetic authority", "website": "https://synthetic.invalid/",
                                 "ownership_evidence": [{"label": "UNKNOWN", "note": "No real organization"}], "sources": [source]}],
                "search_trail": [{"query": "synthetic", "urls_checked": [], "result": "synthetic"}],
                "unresolved": ["No actual first-party research in this test"], "unavailable_reason": "Research does not admit a source"}

    @staticmethod
    def binding(city_id, policy_id):
        return {"city_id": city_id, "choice_id": "choice-" + city_id + "-" + policy_id,
                "policy_id": policy_id, "display_label": "Synthetic choice", "tier": "locality_official_timetable",
                "effective": {"from": "2026-09-08", "to": "2026-09-08"}}

    @staticmethod
    def make_policy(policy_id, authority_id):
        scope = {"id": "scope-a", "kind": "city", "region_id": "ru-aa", "city_id": "city-a1", "description": "One city"}
        effective = {"from": "2026-09-08", "to": "2026-09-08"}
        authority = {"id": authority_id, "name": "Synthetic " + authority_id, "website": "https://synthetic.invalid/", "evidence_label": "UNKNOWN"}
        q = {"schema_version": "namaztime-source-qualification/v1", "qualification_id": "qualification-" + policy_id,
             "sha256": "c" * 64, "state": "qualified", "qualified_at": "2026-09-08T00:00:00Z",
             "source_id": "source-a", "source_kind": "official_file", "scope": scope, "authority": authority,
             "catalog_revision": "catalog-synthetic", "timezone": "Europe/Moscow", "coverage": effective,
             "fresh_through": "2026-09-08", "parser_version": "synthetic-table/v1",
             "canonical_url": "https://synthetic.invalid/table", "unknowns": ["Synthetic only"]}
        return {"policy_id": policy_id, "authority_label": authority["name"],
                "policy": {"id": policy_id, "kind": "timetable", "source_id": "source-a", "geographic_scope_id": "scope-a",
                           "authority_ids": [authority_id], "qualification_id": q["qualification_id"], "mosque_ids": None,
                           "effective": effective, "timetable_id": "table-" + policy_id},
                "scope": scope, "authorities": [authority],
                "source": {"id": "source-a", "kind": "official_file", "status": "qualified", "authority_ids": [authority_id],
                           "geographic_scope_id": "scope-a", "qualification_id": q["qualification_id"],
                           "canonical_url": q["canonical_url"], "fresh_through": "2026-09-08"},
                "qualification": q, "timetable": {"id": "table-" + policy_id, "source_id": "source-a", "geographic_scope_id": "scope-a",
                                                    "timezone": "Europe/Moscow", "effective": effective,
                                                    "published_snapshot_id": "snapshot-" + policy_id, "mosque_id": "public-a"},
                "source_overrides": [], "snapshot": {"snapshot_id": "snapshot-" + policy_id, "path": "", "sha256": "", "byte_length": 0,
                                                      "display_context": {"id": "public-a", "name": "Synthetic context", "timezone": "Europe/Moscow"}}}

    def mapping(self, policy_id):
        return {"policy_id": policy_id, "relation": "public_source_evidence",
                "note": "Explicit synthetic mapping, not inferred from matching names",
                "references": [{"path": self.lane_path, "sha256": "", "pointer": "/subjects/0/authorities/0/sources/0"}]}

    @staticmethod
    def write(path, raw):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)

    def prepare(self):
        self.write(self.catalog_path, encoded(self.catalog))
        self.index["catalog"]["raw_sha256"] = digest(self.catalog_path.read_bytes())
        self.write(self.root / self.lane_path, encoded(self.lane))
        lane_sha = digest((self.root / self.lane_path).read_bytes())
        self.index["research_inputs"] = [{"path": self.lane_path, "sha256": lane_sha}]
        for mapping in self.index["policy_research_bindings"]:
            for ref in mapping["references"]:
                ref["sha256"] = lane_sha
        for p in self.choices["policies"]:
            snapshot = {"schema_version": "2.0", "snapshot_id": p["snapshot"]["snapshot_id"],
                        "data_classification": "production", "generated_at": "2026-09-08T00:00:00Z",
                        "mosque": p["snapshot"]["display_context"], "coverage": p["timetable"]["effective"],
                        "source": {"source_id": p["source"]["id"], "parser_version": "synthetic-table/v1",
                                   "qualification": p.get("qualification")},
                        "prayer_days": [], "integrity": {"signature": "SYNTHETIC-NOT-A-SIGNATURE"}}
            if "qualification" not in p:
                snapshot["schema_version"] = "1.0"
                del snapshot["source"]["qualification"]
                snapshot["source"]["approval"] = {"approval_id": p["policy"]["approval_id"]}
            raw = encoded(snapshot)
            sha = digest(raw)
            p["snapshot"].update({"path": "snapshots/" + sha + ".json", "sha256": sha, "byte_length": len(raw)})
            self.write(self.bundle / p["snapshot"]["path"], raw)
        self.write(self.bundle / "choices.json", encoded(self.choices))
        files = []
        for p in sorted(self.bundle.rglob("*")):
            if p.is_file() and p.name != "manifest.json":
                raw = p.read_bytes()
                files.append({"path": p.relative_to(self.bundle).as_posix(), "sha256": digest(raw), "byte_length": len(raw)})
        manifest = {
            "schema_version": "namaztime-local-setup-bundle/v1", "bundle_id": "", "manifest_sha256": "",
            "created_at": "2026-09-08T00:00:00Z", "registry_revision": self.revision, "registry_state": "active",
            "admission": {"kind": "persistent_service_verified_local", "verified_at": "2026-09-08T00:00:00Z",
                          "actor_id": "test:operator", "reason": "Synthetic"},
            "catalog": {"revision_id": "catalog-synthetic", "content_sha256": "a" * 64,
                        "region_count": len(self.catalog["regions"]), "city_count": len(self.catalog["cities"]),
                        "alias_count": sum(len(c.get("aliases", [])) for c in self.catalog["cities"]), "search_name_count": 6,
                        "license": "synthetic", "license_url": "https://synthetic.invalid/license", "attribution": "Synthetic"},
            "minimum_trust_revision": 3, "files": files,
        }
        body = {k: v for k, v in manifest.items() if k not in ("bundle_id", "manifest_sha256")}
        manifest["manifest_sha256"] = digest(encoded(body))
        manifest["bundle_id"] = "local-setup-" + manifest["manifest_sha256"][:32]
        self.write(self.bundle / "manifest.json", encoded(manifest))
        self.index["admission"]["manifest"]["sha256"] = digest(encoded(manifest))
        self.write(self.index_path, encoded(self.index))
        return digest(self.index_path.read_bytes())

    def compile(self, state_at="2026-09-08T12:00:00Z"):
        pin = self.prepare()
        return compile_registry(self.catalog_path, self.index_path, pin, state_at, self.root)

    def test_compiles_complete_catalog_and_preserves_all_research_references(self):
        self.lane["subjects"][0]["authorities"].append(copy.deepcopy(self.lane["subjects"][0]["authorities"][0]))
        self.lane["subjects"][0]["authorities"][1]["id"] = "independent-b"
        result = self.compile()
        self.assertEqual(result["summary"]["subject_count"], 2)
        self.assertEqual(result["summary"]["city_count"], 3)
        self.assertEqual(result["summary"]["covered_city_count"], 1)
        self.assertEqual(result["summary"]["unavailable_city_count"], 2)
        self.assertEqual(result["summary"]["research_authority_records"], 3)
        self.assertEqual([s["code"] for s in result["subjects"]], ["RU-AA", "RU-BB"])
        subject = result["subjects"][0]
        self.assertEqual(subject["availability"]["status"], "partially_covered")
        self.assertEqual(subject["research"]["search_trail_ref"]["pointer"], "/subjects/0/search_trail")
        self.assertEqual(len(subject["research"]["authorities"]), 2)
        candidate = subject["research"]["authorities"][0]["sources"][0]
        self.assertTrue(candidate["candidate_id"].startswith("research-source-"))
        self.assertNotEqual(candidate["candidate_id"], "source-a")
        self.assertEqual(result["operational"]["policies"][0]["proof_kind"], "qualified_public")
        self.assertIn("not a new qualification", result["trust_boundary"])

    def test_same_count_wrong_subject_code_is_rejected(self):
        self.lane["subjects"][1]["code"] = "RU-CC"
        with self.assertRaisesRegex(ValueError, "subject set mismatch"):
            self.compile()

    def test_duplicate_subject_is_rejected(self):
        self.lane["subjects"].append(copy.deepcopy(self.lane["subjects"][0]))
        with self.assertRaisesRegex(ValueError, "duplicate subject"):
            self.compile()

    def test_stale_at_local_midnight_is_unavailable_without_removing_research(self):
        result = self.compile("2026-09-08T21:00:00Z")
        self.assertEqual(result["summary"]["covered_city_count"], 0)
        self.assertEqual(result["summary"]["unavailable_city_count"], 3)
        self.assertEqual(result["summary"]["admitted_policy_count"], 1)
        self.assertEqual(result["summary"]["current_policy_count"], 0)
        self.assertEqual(result["operational"]["policies"][0]["current_city_count"], 0)
        self.assertEqual(result["summary"]["research_source_candidates"], 2)

    def test_independent_choices_count_once_per_city_but_preserve_both_policies(self):
        self.choices["policies"].append(self.make_policy("policy-b", "authority-b"))
        self.choices["bindings"].append(self.binding("city-a1", "policy-b"))
        self.index["policy_research_bindings"].append(self.mapping("policy-b"))
        result = self.compile()
        self.assertEqual(result["summary"]["covered_city_count"], 1)
        self.assertEqual(result["summary"]["current_choice_count"], 2)
        self.assertEqual(result["summary"]["cities_with_multiple_choices"], 1)
        self.assertEqual(len(result["operational"]["policies"]), 2)

    def test_binding_cannot_expand_city_scope_to_same_named_neighbor(self):
        self.choices["bindings"].append(self.binding("city-a2", "policy-a"))
        with self.assertRaisesRegex(ValueError, "binding outside.*scope"):
            self.compile()

    def test_explicit_mapping_required_no_name_based_join(self):
        self.index["policy_research_bindings"] = []
        with self.assertRaisesRegex(ValueError, "policy mapping set mismatch"):
            self.compile()

    def test_active_enum_without_prior_admission_record_is_not_accepted(self):
        self.index["admission"]["record"] = {"path": str(self.record_path), "sha256": "0" * 64}
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            self.compile()

    def test_research_drift_after_pin_fails_closed(self):
        pin = self.prepare()
        self.write(self.root / self.lane_path, b"{}")
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            compile_registry(self.catalog_path, self.index_path, pin, "2026-09-08T12:00:00Z", self.root)

    def test_duplicate_json_member_rejected_even_if_re_pinned(self):
        self.prepare()
        raw = self.index_path.read_bytes().replace(b'"index_id":"synthetic-index"', b'"index_id":"synthetic-index","index_id":"ambiguous"')
        self.index_path.write_bytes(raw)
        with self.assertRaisesRegex(ValueError, "duplicate JSON member"):
            compile_registry(self.catalog_path, self.index_path, digest(raw), "2026-09-08T12:00:00Z", self.root)

    def test_undeclared_bundle_file_rejected(self):
        pin = self.prepare()
        self.write(self.bundle / "extra.txt", b"not declared")
        with self.assertRaisesRegex(ValueError, "closed inventory"):
            compile_registry(self.catalog_path, self.index_path, pin, "2026-09-08T12:00:00Z", self.root)

    def test_snapshot_corruption_rejected(self):
        pin = self.prepare()
        self.write(self.bundle / self.policy["snapshot"]["path"], b"{}")
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch|byte length"):
            compile_registry(self.catalog_path, self.index_path, pin, "2026-09-08T12:00:00Z", self.root)

    def test_research_path_traversal_and_bad_json_pointer_rejected(self):
        for pointer in ("/subjects/01/authorities/0/sources/0", "/subjects/0/authorities/0/sources/99", "/subjects/0/~2bad"):
            with self.subTest(pointer=pointer):
                self.index["policy_research_bindings"][0]["references"][0]["pointer"] = pointer
                with self.assertRaises(ValueError):
                    self.compile()

    def test_reproducible_output_for_identical_explicit_state(self):
        first = self.compile()
        self.assertEqual(encoded(first), encoded(self.compile()))

    def test_omitted_empty_aliases_are_valid_catalog_omitempty(self):
        del self.catalog["cities"][2]["aliases"]
        result = self.compile()
        self.assertEqual(result["summary"]["alias_count"], 4)

    def test_cli_cannot_overwrite_a_pinned_research_input(self):
        pin = self.prepare()
        path = self.root / self.lane_path
        before = path.read_bytes()
        with contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
            code = main(["--catalog", str(self.catalog_path), "--proof-index", str(self.index_path),
                         "--proof-index-sha256", pin, "--state-at", "2026-09-08T12:00:00Z",
                         "--repository-root", str(self.root), "--output", str(path)])
        self.assertEqual(code, 1)
        self.assertEqual(path.read_bytes(), before)

    def test_cli_corruption_preserves_existing_report(self):
        pin = self.prepare()
        output = self.root / "report.json"
        output.write_bytes(b"last-known-report")
        self.write(self.bundle / self.policy["snapshot"]["path"], b"corrupt")
        with contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
            code = main(["--catalog", str(self.catalog_path), "--proof-index", str(self.index_path),
                         "--proof-index-sha256", pin, "--state-at", "2026-09-08T12:00:00Z",
                         "--repository-root", str(self.root), "--output", str(output)])
        self.assertEqual(code, 1)
        self.assertEqual(output.read_bytes(), b"last-known-report")
        self.assertEqual(list(self.root.glob(".t049-report-*")), [])

    def test_unknown_evidence_label_cannot_be_silently_promoted(self):
        self.lane["subjects"][0]["authorities"][0]["ownership_evidence"][0]["evidence_label"] = "qualified"
        with self.assertRaisesRegex(ValueError, "evidence label"):
            self.compile()

    def test_research_status_alone_never_changes_operational_counts(self):
        self.lane["subjects"][1]["research_status"] = "qualified"
        self.assertEqual(self.compile()["summary"]["covered_city_count"], 1)

    def test_gap_or_out_of_bounds_interval_is_never_extended(self):
        self.choices["bindings"][0]["effective"]["to"] = "2026-09-09"
        with self.assertRaisesRegex(ValueError, "exceeds verified eligibility"):
            self.compile()

    def test_overlapping_duplicate_and_unknown_city_bindings_fail(self):
        self.choices["bindings"].append(copy.deepcopy(self.choices["bindings"][0]))
        with self.assertRaisesRegex(ValueError, "overlapping"):
            self.compile()
        self.choices["bindings"][1]["city_id"] = "missing"
        with self.assertRaisesRegex(ValueError, "unknown city"):
            self.compile()

    def test_same_named_other_region_cannot_supply_research_evidence(self):
        self.index["policy_research_bindings"][0]["references"][0]["pointer"] = "/subjects/1/authorities/0/sources/0"
        with self.assertRaisesRegex(ValueError, "crosses canonical subject"):
            self.compile()

    def test_explicit_state_rejects_before_admission_and_numeric_offset(self):
        for state in ("2026-09-07T23:59:59Z", "2026-09-08T15:00:00+03:00", "invalid"):
            with self.subTest(state=state), self.assertRaises(ValueError):
                self.compile(state)

    def test_full_catalog_timezone_map_not_device_timezone(self):
        self.catalog["cities"][2]["timezone"] = "Asia/Vladivostok"
        result = self.compile("2026-09-08T16:00:00Z")
        self.assertEqual(result["local_date_by_timezone"], {"Europe/Moscow": "2026-09-08", "Asia/Vladivostok": "2026-09-09"})

    def test_no_candidate_or_admission_pin_can_be_silently_replaced(self):
        pin = self.prepare()
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            compile_registry(self.catalog_path, self.index_path, "0" * 64, "2026-09-08T12:00:00Z", self.root)
        self.catalog_path.write_bytes(encoded({"wrong": "catalog"}))
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            compile_registry(self.catalog_path, self.index_path, pin, "2026-09-08T12:00:00Z", self.root)

    def test_retained_legacy_never_becomes_public_qualification(self):
        del self.policy["qualification"]
        del self.policy["policy"]["qualification_id"]
        del self.policy["source"]["qualification_id"]
        self.policy["policy"]["approval_id"] = "synthetic-retained-approval"
        self.policy["policy"]["mosque_ids"] = ["public-a"]
        self.policy["source"]["status"] = "approved"
        self.index["policy_research_bindings"][0]["relation"] = "retained_legacy_context_only"
        result = self.compile()
        self.assertEqual(result["summary"]["qualified_public_policy_count"], 0)
        self.assertEqual(result["summary"]["retained_legacy_policy_count"], 1)
        p = result["operational"]["policies"][0]
        self.assertNotIn("qualification_id", p)
        self.assertEqual(p["approval_id"], "synthetic-retained-approval")

    def test_public_cannot_add_fake_approval_or_mosque(self):
        self.policy["policy"]["approval_id"] = "not-real"
        with self.assertRaisesRegex(ValueError, "public branch"):
            self.compile()

    def test_invented_district_or_ambiguous_scope_fails_closed(self):
        self.policy["scope"]["kind"] = "district"
        with self.assertRaisesRegex(ValueError, "unsupported operational scope"):
            self.compile()

    def test_duplicate_canonical_city_and_wrong_region_fail_closed(self):
        self.catalog["cities"].append(copy.deepcopy(self.catalog["cities"][0]))
        with self.assertRaisesRegex(ValueError, "duplicate canonical city"):
            self.compile()
        self.catalog["cities"].pop()
        self.catalog["cities"][2]["region_id"] = "unknown"
        with self.assertRaisesRegex(ValueError, "unknown region"):
            self.compile()

    def test_symlink_source_is_rejected(self):
        pin = self.prepare()
        path = self.root / self.lane_path
        backing = path.with_name("backing.json")
        path.rename(backing)
        path.symlink_to(backing)
        with self.assertRaisesRegex(ValueError, "symlink"):
            compile_registry(self.catalog_path, self.index_path, pin, "2026-09-08T12:00:00Z", self.root)

    def test_traversal_in_pinned_research_path_is_rejected_before_reading(self):
        self.prepare()
        self.index["research_inputs"][0]["path"] = "../escape.json"
        raw = encoded(self.index)
        self.index_path.write_bytes(raw)
        with self.assertRaisesRegex(ValueError, "unsafe relative path"):
            compile_registry(self.catalog_path, self.index_path, digest(raw), "2026-09-08T12:00:00Z", self.root)

    def test_unknown_declared_inventory_member_is_rejected(self):
        self.write(self.bundle / "trust" / "new-root.json", encoded({"unexpected": "new root"}))
        with self.assertRaisesRegex(ValueError, "unknown.*inventory member"):
            self.compile()

    def test_unused_content_addressed_snapshot_is_rejected(self):
        raw = encoded({"unused": "synthetic snapshot"})
        self.write(self.bundle / "snapshots" / (digest(raw) + ".json"), raw)
        with self.assertRaisesRegex(ValueError, "unused snapshot"):
            self.compile()

    def test_cli_writes_then_reproduces_only_its_own_report(self):
        pin = self.prepare()
        output = self.root / "report.json"
        args = ["--catalog", str(self.catalog_path), "--proof-index", str(self.index_path),
                "--proof-index-sha256", pin, "--state-at", "2026-09-08T12:00:00Z",
                "--repository-root", str(self.root), "--output", str(output)]
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(main(args), 0)
            before = output.read_bytes()
            self.assertEqual(main(args), 0)
        self.assertEqual(output.read_bytes(), before)
        self.assertEqual(list(self.root.glob(".t049-report-*")), [])


if __name__ == "__main__":
    unittest.main()
