"""T049 pinned nationwide report compiler (Python standard library only).

This is NOT a source qualifier, signature verifier, registry admission service,
or device availability endpoint. The caller must independently trust/pin an
operational-proof-index that records an actual previously verified local
export. The active enum alone, or a newly self-consistent bundle, is not such
evidence. Re-running this program does not re-run that admission. All output
availability is limited to the selected immutable bundle at explicit state_at.

Input index v1 has: schema_version, index_id, catalog (raw_sha256, revision_id,
content_sha256), research_inputs (repository-relative path, raw sha256),
admission (kind=previously_verified_local_setup_export, verified_at, pinned
absolute manifest and verification record paths, registry_revision_id,
registry_content_sha256, actual verification_command and claim), and explicit
policy_research_bindings (policy_id, relation=public_source_evidence or
retained_legacy_context_only, note, references of path/raw sha256/JSON pointer).
Each mapping points to an existing research source, not a name-based match.

Example (keep substantial catalog/bundle data outside Git):
  python3 -B research/tools/t049_nationwide_registry.py \
    --catalog /outside-git/catalog.json \
    --proof-index research/t049/operational-proof-index.json \
    --proof-index-sha256 KNOWN_RAW_SHA256 --state-at 2026-09-08T03:00:00Z \
    --output research/t049/nationwide-registry.json

Output is deterministic for pinned inputs and state, with no network/system
clock reads. It preserves every research subject/authority/source by raw file
hash plus RFC 6901 pointer. Candidate IDs have a separate namespace and do not
represent qualifications. Unavailable city IDs are a machine-readable full
canonical-region complement of explicitly covered city IDs, not a truncated
list or a claim that a source can never exist. Source rows are never emitted.
Research references are relative to repository_root; operational policy,
snapshot and binding references are relative to the pinned manifest directory.
Admitted/qualified counts describe retained proof records. Only current_*
counts and dated city availability describe eligibility at state_at.

Run tests: python3 -B -m unittest discover -s research/tools
  -p test_t049_nationwide_registry.py
"""

import argparse
import collections
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import tempfile
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError


MIB = 1024 * 1024
TRUST_BOUNDARY = (
    "Pinned previously verified local export; not a new qualification, signature verification, "
    "registry admission, external endorsement, or remote/device activation. The caller must "
    "independently trust the proof-index raw SHA-256 and its actual prior-admission record. "
    "Availability is a dated projection of explicit bindings in this bundle only. Research "
    "candidates and implemented parser names do not by themselves establish availability."
)
LABELS = {"CONFIRMED_PUBLIC", "CONFIRMED_STATIC", "CONFIRMED_RUNTIME", "INFERENCE", "PROPOSAL", "UNKNOWN"}
TRUST_PATHS = {"trust/production.json", "trust/previous-production.json", "trust/test.json", "trust/staging.json"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def string(value, context):
    require(isinstance(value, str) and value.strip(), context + " must be a nonempty string")
    return value


def array(value, context):
    require(isinstance(value, list), context + " must be an array")
    return value


def fields(value, required, context, optional=(), closed=True):
    require(isinstance(value, dict), context + " must be an object")
    require(set(required) <= value.keys(), context + " missing required fields")
    if closed:
        require(value.keys() <= set(required) | set(optional), context + " has unknown fields")
    return value


def sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def hash_string(value):
    require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value), "invalid SHA-256 pin")
    return value


def canonical(value):
    # Report identity and integer-only bundle manifests; not floating-point
    # qualification/candidate canonicalization or a new signature protocol.
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def strict_json(raw, context):
    def pairs(items):
        obj = {}
        for key, value in items:
            require(key not in obj, context + ": duplicate JSON member " + key)
            obj[key] = value
        return obj

    def reject_constant(_):
        raise ValueError(context + ": non-finite JSON number")

    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=pairs, parse_constant=reject_constant)
    except (UnicodeError, json.JSONDecodeError, RecursionError) as error:
        raise ValueError(context + ": invalid JSON/UTF-8") from error

    def check(item):
        if isinstance(item, str):
            require(not any(0xD800 <= ord(c) <= 0xDFFF for c in item), context + ": invalid Unicode surrogate")
        elif isinstance(item, float):
            require(math.isfinite(item), context + ": non-finite JSON number")
        elif isinstance(item, dict):
            for key, val in item.items():
                check(key)
                check(val)
        elif isinstance(item, list):
            for val in item:
                check(val)
    try:
        check(value)
    except RecursionError as error:
        raise ValueError(context + ": nested JSON exceeds decoder limits") from error
    return value


def clean_relative(value):
    string(value, "relative path")
    path = PurePosixPath(value)
    require(not path.is_absolute() and path.as_posix() == value and
            not any(part in (".", "..") for part in path.parts) and "\\" not in value and "\0" not in value,
            "unsafe relative path")
    return value


def clean_path(value):
    value = os.fspath(value)
    require("\0" not in value and "\\" not in value and ".." not in Path(value).parts, "unsafe input path")
    path = Path(os.path.abspath(value))
    for part in (path, *path.parents):
        require(not part.is_symlink(), "symlink input path")
    return path


def read_pinned(path, expected, maximum):
    hash_string(expected)
    path = clean_path(path)
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
    try:
        with os.fdopen(os.open(path, flags), "rb") as handle:
            before = os.fstat(handle.fileno())
            require(stat.S_ISREG(before.st_mode), "input must be a regular file")
            require(0 < before.st_size <= maximum, "input exceeds byte bound or is empty")
            raw = handle.read(maximum + 1)
            after = os.fstat(handle.fileno())
            require((before.st_size, before.st_mtime_ns, before.st_ino) ==
                    (after.st_size, after.st_mtime_ns, after.st_ino) and len(raw) == before.st_size,
                    "input changed while reading")
    except OSError as error:
        raise ValueError("cannot read pinned input " + path.name) from error
    require(len(raw) <= maximum, "input exceeds byte bound")
    require(sha256(raw) == expected, "SHA-256 mismatch for " + path.name)
    return raw


def pinned_file(value, context, maximum):
    fields(value, ("path", "sha256"), context)
    require(Path(string(value["path"], context + " path")).is_absolute(), context + " path must be absolute")
    return read_pinned(value["path"], value["sha256"], maximum)


def timestamp(value):
    require(isinstance(value, str) and re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ", value),
            "timestamp must be canonical UTC RFC 3339 seconds")
    try:
        return dt.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)
    except ValueError as error:
        raise ValueError("invalid timestamp") from error


def date(value):
    require(isinstance(value, str) and re.fullmatch(r"\d{4}-\d\d-\d\d", value), "invalid local date")
    try:
        return dt.date.fromisoformat(value)
    except ValueError as error:
        raise ValueError("invalid local date") from error


def interval(value):
    fields(value, ("from", "to"), "date range")
    low, high = date(value["from"]), date(value["to"])
    require(low <= high, "inverted date range")
    return low, high


def pointer_get(document, pointer):
    require(isinstance(pointer, str) and (pointer == "" or pointer.startswith("/")), "invalid JSON pointer")
    value = document
    for part in pointer.split("/")[1:] if pointer else []:
        require(not re.search(r"~(?![01])", part), "invalid JSON pointer escape")
        part = part.replace("~1", "/").replace("~0", "~")
        if isinstance(value, list):
            require(bool(re.fullmatch(r"0|[1-9][0-9]*", part)), "invalid JSON pointer array index")
            require(int(part) < len(value), "JSON pointer not found")
            value = value[int(part)]
        else:
            require(isinstance(value, dict) and part in value, "JSON pointer not found")
            value = value[part]
    return value


def reference(path, pin, pointer):
    return {"path": path, "sha256": pin, "pointer": pointer}


def candidate_id(kind, *parts):
    return "research-" + kind + "-" + sha256(canonical(parts))[:32]


def validate_evidence_labels(value):
    if isinstance(value, dict):
        for key, item in value.items():
            if key == "evidence_label":
                require(isinstance(item, str) and item in LABELS, "unknown research evidence label")
            validate_evidence_labels(item)
    elif isinstance(value, list):
        for item in value:
            validate_evidence_labels(item)


def catalog_data(path, pin):
    fields(pin, ("raw_sha256", "revision_id", "content_sha256"), "catalog pin")
    raw = read_pinned(path, pin["raw_sha256"], 256 * MIB)
    data = strict_json(raw, "canonical catalog")
    fields(data, ("schema_version", "revision", "source", "regions", "cities"), "canonical catalog")
    require(data["schema_version"] == "namaztime-city-catalog/v1", "unsupported canonical catalog schema")
    revision = fields(data["revision"], ("id", "content_sha256"), "catalog revision", closed=False)
    require(revision["id"] == pin["revision_id"] and revision["content_sha256"] == pin["content_sha256"],
            "catalog revision/content pin mismatch")
    hash_string(revision["content_sha256"])
    regions, codes = {}, set()
    for region in array(data["regions"], "catalog regions"):
        fields(region, ("id", "federal_subject_code", "name", "country_code"), "catalog region", closed=False)
        region_id = string(region["id"], "region id")
        code = string(region["federal_subject_code"], "subject code")
        require(region_id not in regions and code not in codes, "duplicate canonical region/code")
        require(region["country_code"] == "RU" and re.fullmatch(r"RU-[A-Z]{2,3}", code), "non-Russia subject")
        regions[region_id] = region
        codes.add(code)
    require(regions, "empty canonical regions")
    cities, by_region, zones = {}, collections.defaultdict(set), {}
    aliases = 0
    for city in array(data["cities"], "catalog cities"):
        fields(city, ("id", "name", "region_id", "country_code", "timezone"), "catalog city", closed=False)
        city_id = string(city["id"], "city id")
        require(city_id not in cities, "duplicate canonical city")
        require(city["region_id"] in regions and city["country_code"] == "RU", "city has unknown region/country")
        require(not city.get("fallback_policy_id"), "canonical city fallback is forbidden")
        for alias in array(city.get("aliases", []), "city aliases"):
            string(alias, "city alias")
        aliases += len(city.get("aliases", []))
        zone = string(city["timezone"], "IANA timezone")
        if zone not in zones:
            try:
                zones[zone] = ZoneInfo(zone)
            except (ZoneInfoNotFoundError, ValueError) as error:
                raise ValueError("unknown IANA timezone") from error
        cities[city_id] = city
        by_region[city["region_id"]].add(city_id)
    require(cities, "empty canonical cities")
    require(set(by_region) == set(regions), "canonical region without cities")
    return data, regions, cities, by_region, zones, aliases


def research_data(root, inputs, region_codes):
    subjects, sources, documents, pins = {}, {}, {}, {}
    for entry in array(inputs, "research inputs"):
        fields(entry, ("path", "sha256"), "research input")
        path = clean_relative(entry["path"])
        require(path not in documents, "duplicate research input")
        raw = read_pinned(root / path, entry["sha256"], 16 * MIB)
        doc = strict_json(raw, path)
        validate_evidence_labels(doc)
        fields(doc, ("schema_version", "researched_at", "subjects"), "research lane")
        require(doc["schema_version"] == "t049-research-lane/v1", "unsupported research schema")
        date(doc["researched_at"])
        documents[path], pins[path] = doc, entry["sha256"]
        for i, subject in enumerate(array(doc["subjects"], "research subjects")):
            fields(subject, ("code", "name", "research_status", "authorities", "search_trail", "unresolved", "unavailable_reason"),
                   "research subject", closed=False)
            code = string(subject["code"], "research code")
            require(code not in subjects, "duplicate subject " + code)
            base = "/subjects/" + str(i)
            ref = lambda suffix: reference(path, entry["sha256"], base + suffix)
            result = {"status": string(subject["research_status"], "research status"), "researched_at": doc["researched_at"],
                      "declared_name": subject["name"], "record_ref": ref(""), "search_trail_ref": ref("/search_trail"),
                      "unresolved_ref": ref("/unresolved"), "unavailable_reason_ref": ref("/unavailable_reason"),
                      "authorities": []}
            array(subject["search_trail"], "search trail")
            array(subject["unresolved"], "research unknowns")
            authority_ids = set()
            for j, authority in enumerate(array(subject["authorities"], "research authorities")):
                fields(authority, ("id", "name", "website", "ownership_evidence", "sources"), "research authority", closed=False)
                authority_id = string(authority["id"], "research authority id")
                require(authority_id not in authority_ids, "duplicate research authority in subject")
                authority_ids.add(authority_id)
                authority_base = "/authorities/" + str(j)
                ar = {"candidate_id": candidate_id("authority", path, code, authority_id), "declared_id": authority_id,
                      "name": authority["name"], "website": authority["website"], "record_ref": ref(authority_base),
                      "ownership_evidence_ref": ref(authority_base + "/ownership_evidence"), "sources": []}
                source_ids = set()
                for k, source in enumerate(array(authority["sources"], "research sources")):
                    fields(source, ("id", "type", "canonical_url", "scope", "localities", "effective_range", "timezone", "transport",
                                    "policy", "comparison_evidence", "terms_notes", "qualification_gaps"), "research source", closed=False)
                    source_id = string(source["id"], "research source id")
                    require(source_id not in source_ids, "duplicate research source in authority")
                    source_ids.add(source_id)
                    source_base = authority_base + "/sources/" + str(k)
                    sr = {"candidate_id": candidate_id("source", path, code, authority_id, source_id), "declared_id": source_id,
                          "source_type": source["type"], "canonical_url": source["canonical_url"], "record_ref": ref(source_base),
                          "scope_ref": ref(source_base + "/scope"), "localities_ref": ref(source_base + "/localities"),
                          "operational_links": []}
                    sources[(path, base + source_base)] = (code, sr)
                    ar["sources"].append(sr)
                result["authorities"].append(ar)
            subjects[code] = result
    require(set(subjects) == region_codes,
            "subject set mismatch: missing=" + ",".join(sorted(region_codes - set(subjects))) +
            "; unexpected=" + ",".join(sorted(set(subjects) - region_codes)))
    return subjects, sources, documents, pins


def load_bundle(admission, state, catalog_pin, counts):
    fields(admission, ("kind", "verified_at", "record", "manifest", "registry_revision_id", "registry_content_sha256",
                       "verification_command", "claim"), "prior admission")
    require(admission["kind"] == "previously_verified_local_setup_export", "unsupported prior admission kind")
    string(admission["verification_command"], "prior verification command")
    string(admission["claim"], "prior verification claim")
    verified = timestamp(admission["verified_at"])
    require(verified <= state, "state precedes prior admission")
    pinned_file(admission["record"], "prior admission record", MIB)
    raw = pinned_file(admission["manifest"], "bundle manifest", MIB // 2)
    manifest = strict_json(raw, "bundle manifest")
    fields(manifest, ("schema_version", "bundle_id", "manifest_sha256", "created_at", "registry_revision", "registry_state",
                      "admission", "catalog", "minimum_trust_revision", "files"), "bundle manifest")
    require(manifest["schema_version"] == "namaztime-local-setup-bundle/v1", "unsupported bundle schema")
    require(manifest["registry_state"] == "active", "pinned bundle was not locally active")
    require(timestamp(manifest["created_at"]) == verified, "prior admission time mismatch")
    require(manifest["admission"].get("kind") == "persistent_service_verified_local" and
            manifest["admission"].get("verified_at") == manifest["created_at"], "bundle admission metadata mismatch")
    revision = manifest["registry_revision"]
    require(revision.get("id") == admission["registry_revision_id"] and
            revision.get("content_sha256") == admission["registry_content_sha256"] and
            revision.get("catalog_revision_id") == catalog_pin["revision_id"], "admitted registry pin mismatch")
    require(revision.get("schema_version") in (1, 2), "unsupported registry revision schema")
    body = {k: v for k, v in manifest.items() if k not in ("bundle_id", "manifest_sha256")}
    body_hash = sha256(canonical(body))
    require(manifest["manifest_sha256"] == body_hash and manifest["bundle_id"] == "local-setup-" + body_hash[:32],
            "bundle canonical identity mismatch")
    catalog = manifest["catalog"]
    require(catalog.get("revision_id") == catalog_pin["revision_id"] and catalog.get("content_sha256") == catalog_pin["content_sha256"],
            "bundle catalog pin mismatch")
    for key, expected in counts.items():
        require(type(catalog.get(key)) is int and catalog[key] == expected, "bundle full catalog " + key + " mismatch")
    require(manifest["minimum_trust_revision"] == 3, "unsupported pinned bundle trust revision")
    root = clean_path(admission["manifest"]["path"]).parent
    files, payloads = {}, {}
    total = len(raw)
    for member in array(manifest["files"], "bundle inventory"):
        fields(member, ("path", "sha256", "byte_length"), "inventory member")
        path = clean_relative(member["path"])
        require(path not in files, "duplicate inventory member")
        if path == "catalog.sqlite":
            maximum = 128 * MIB
        elif path == "choices.json":
            maximum = 16 * MIB
        elif path in TRUST_PATHS:
            maximum = MIB // 4
        else:
            require(bool(re.fullmatch(r"snapshots/[0-9a-f]{64}\.json", path)) and path[10:-5] == member["sha256"],
                    "unknown or invalid content-addressed inventory member")
            maximum = 5 * MIB
        require(type(member["byte_length"]) is int and 0 < member["byte_length"] <= maximum, "invalid inventory byte length")
        total += member["byte_length"]
        require(total <= 256 * MIB, "bundle total byte bound exceeded")
        content = read_pinned(root / path, member["sha256"], maximum)
        require(len(content) == member["byte_length"], "inventory byte length mismatch")
        files[path] = member
        if path == "choices.json" or path.startswith("snapshots/"):
            payloads[path] = strict_json(content, path)
    require({"catalog.sqlite", "choices.json"} | TRUST_PATHS <= files.keys(), "incomplete bundle inventory")
    require(sum(p.startswith("snapshots/") for p in files) <= 1024, "snapshot count bound exceeded")
    actual = set()
    for directory, directories, filenames in os.walk(root, followlinks=False):
        for name in directories:
            path = Path(directory) / name
            require(not path.is_symlink() and path.relative_to(root).as_posix() in ("trust", "snapshots"), "closed inventory directory mismatch")
        for name in filenames:
            path = Path(directory) / name
            require(not path.is_symlink(), "closed inventory symlink")
            actual.add(path.relative_to(root).as_posix())
    require(actual == set(files) | {"manifest.json"}, "closed inventory mismatch")
    return manifest, files, payloads


def policies_and_mappings(index, manifest, files, payloads, catalog, regions, cities, by_region, research_sources, documents, pins):
    choices = payloads["choices.json"]
    fields(choices, ("schema_version", "registry_revision_id", "policies", "bindings"), "bundle choices")
    require(choices["schema_version"] == "namaztime-local-setup-choices/v1" and
            choices["registry_revision_id"] == manifest["registry_revision"]["id"], "choices revision/schema mismatch")
    mappings = {}
    for mapping in array(index["policy_research_bindings"], "policy research bindings"):
        fields(mapping, ("policy_id", "relation", "note", "references"), "policy research mapping")
        policy_id = string(mapping["policy_id"], "mapped policy id")
        require(policy_id not in mappings, "duplicate policy mapping")
        require(mapping["relation"] in ("public_source_evidence", "retained_legacy_context_only"), "unsupported research mapping relation")
        string(mapping["note"], "mapping note")
        refs, source_keys = array(mapping["references"], "mapping references"), set()
        require(refs, "mapping has no research references")
        for ref in refs:
            fields(ref, ("path", "sha256", "pointer"), "research reference")
            path = clean_relative(ref["path"])
            require(path in documents and ref["sha256"] == pins[path], "research reference pin mismatch")
            pointer_get(documents[path], ref["pointer"])
            key = (path, ref["pointer"])
            require(key in research_sources, "mapping must reference an exact research source")
            require(key not in source_keys, "duplicate mapping reference")
            source_keys.add(key)
        mappings[policy_id] = mapping
    policies, used_snapshots = {}, set()
    for i, record in enumerate(array(choices["policies"], "choice policies")):
        fields(record, ("policy_id", "authority_label", "policy", "scope", "authorities", "source", "timetable", "source_overrides", "snapshot"),
               "choice policy", optional=("qualification",))
        policy_id = string(record["policy_id"], "policy id")
        require(policy_id not in policies, "duplicate choice policy")
        require(policy_id in mappings, "policy mapping set mismatch")
        p, source, scope, table, snap = (record[k] for k in ("policy", "source", "scope", "timetable", "snapshot"))
        require(p.get("id") == policy_id and p.get("kind") == "timetable" and not p.get("calculation_profile_id"), "unsupported policy identity/kind")
        require(source.get("id") == p.get("source_id") == table.get("source_id"), "policy source binding mismatch")
        require(scope.get("id") == p.get("geographic_scope_id") == source.get("geographic_scope_id") == table.get("geographic_scope_id"),
                "policy scope binding mismatch")
        require(p.get("timetable_id") == table.get("id"), "policy timetable binding mismatch")
        authority_ids = [a.get("id") for a in array(record["authorities"], "operational authorities")]
        require(authority_ids and len(set(authority_ids)) == len(authority_ids) and
                set(authority_ids) == set(p.get("authority_ids", [])) == set(source.get("authority_ids", [])), "authority binding mismatch")
        region_id = scope.get("region_id")
        require(region_id in by_region, "operational scope region missing")
        if scope.get("kind") == "region":
            allowed = by_region[region_id]
            require(not scope.get("city_id") and not scope.get("city_ids"), "ambiguous regional scope")
        elif scope.get("kind") == "city":
            allowed = {scope.get("city_id")}
            require(not scope.get("city_ids"), "ambiguous city scope")
        else:
            raise ValueError("unsupported operational scope")
        require(allowed <= by_region[region_id], "operational scope outside canonical region")
        timezone = string(table.get("timezone"), "timetable timezone")
        require(all(cities[c]["timezone"] == timezone for c in allowed), "scope canonical timezone mismatch")
        fields(snap, ("snapshot_id", "path", "sha256", "byte_length", "display_context"), "snapshot reference")
        require(snap["path"] in files and snap["path"].startswith("snapshots/"), "missing referenced snapshot")
        require(all(snap[k] == files[snap["path"]][k] for k in ("path", "sha256", "byte_length")), "snapshot file pin mismatch")
        signed = payloads[snap["path"]]
        require(signed.get("snapshot_id") == snap["snapshot_id"] == table.get("published_snapshot_id") and
                signed.get("data_classification") == "production", "snapshot identity/classification mismatch")
        require(signed.get("mosque") == snap["display_context"] and table.get("mosque_id") == snap["display_context"].get("id"),
                "snapshot display context mismatch")
        signed_source = fields(signed.get("source"), ("source_id", "parser_version"), "signed source", closed=False)
        require(signed_source["source_id"] == source["id"], "signed source mismatch")
        used_snapshots.add(snap["path"])
        ranges = [interval(p.get("effective")), interval(table.get("effective")), interval(signed.get("coverage"))]
        fresh = date(source.get("fresh_through"))
        q = record.get("qualification")
        if q is not None:
            require(manifest["registry_revision"]["schema_version"] == 2 and signed.get("schema_version") == "2.0", "public schema mismatch")
            require(q.get("schema_version") == "namaztime-source-qualification/v1" and q.get("state") == "qualified" and
                    q.get("qualification_id") == p.get("qualification_id") == source.get("qualification_id"), "public qualification binding mismatch")
            hash_string(q.get("sha256"))
            require(not p.get("approval_id") and not p.get("mosque_ids") and not signed_source.get("approval"), "public branch cannot contain legacy approval")
            require(source.get("status") == "qualified" and signed_source.get("qualification") == q, "signed qualification proof mismatch")
            require(q.get("source_id") == source["id"] and q.get("source_kind") == source.get("kind") and
                    q.get("canonical_url") == source.get("canonical_url") and q.get("scope") == scope and
                    q.get("catalog_revision") == catalog["revision_id"] and q.get("timezone") == timezone and
                    [q.get("authority", {}).get("id")] == authority_ids, "qualification source/scope/catalog binding mismatch")
            require(timestamp(q.get("qualified_at")) <= timestamp(manifest["created_at"]), "qualification postdates admission")
            ranges.append(interval(q.get("coverage")))
            fresh = min(fresh, date(q.get("fresh_through")))
            require(mappings[policy_id]["relation"] == "public_source_evidence", "public research relation mismatch")
            proof = {"proof_kind": "qualified_public", "qualification_id": q["qualification_id"], "qualification_sha256": q["sha256"]}
        else:
            approval_id = string(p.get("approval_id"), "legacy approval id")
            require(signed.get("schema_version") == "1.0" and source.get("status") == "approved" and not p.get("qualification_id") and
                    not source.get("qualification_id") and not signed_source.get("qualification"), "legacy branch mismatch")
            require(signed_source.get("approval", {}).get("approval_id") == approval_id and
                    p.get("mosque_ids") == [table.get("mosque_id")] and scope.get("kind") == "city", "legacy approval/mosque binding mismatch")
            require(mappings[policy_id]["relation"] == "retained_legacy_context_only", "legacy cannot qualify a public research candidate")
            proof = {"proof_kind": "retained_legacy", "approval_id": approval_id}
        low, high = max(r[0] for r in ranges), min(fresh, *(r[1] for r in ranges))
        require(low <= high, "operational eligibility ranges do not intersect")
        for ref in mappings[policy_id]["references"]:
            code, candidate = research_sources[(ref["path"], ref["pointer"])]
            require(code == regions[region_id]["federal_subject_code"],
                    "research mapping crosses canonical subject")
            candidate["operational_links"].append({"policy_id": policy_id, "relation": mappings[policy_id]["relation"]})
        output = {"policy_id": policy_id, "source_id": source["id"], "source_kind": source["kind"],
                  "authority_ids": authority_ids, "authority_label": record["authority_label"], "scope": scope,
                  "timezone": timezone, "eligible_range": {"from": low.isoformat(), "to": high.isoformat()},
                  "parser_version": signed_source["parser_version"], **proof,
                  "snapshot_id": snap["snapshot_id"], "snapshot_sha256": snap["sha256"],
                  "record_ref": reference("choices.json", files["choices.json"]["sha256"], "/policies/" + str(i)),
                  "snapshot_ref": reference(snap["path"], snap["sha256"], ""), "research_mapping": mappings[policy_id],
                  "current_city_count": 0, "current_city_ids": []}
        policies[policy_id] = {"output": output, "allowed": allowed, "range": (low, high)}
    require(set(policies) == set(mappings), "policy mapping set mismatch")
    require(used_snapshots == {p for p in files if p.startswith("snapshots/")}, "unused snapshot in bundle")
    return choices, policies


def compile_registry(catalog_path, index_path, index_sha256, state_at, repository_root):
    state = timestamp(state_at)
    root = clean_path(repository_root)
    index = strict_json(read_pinned(index_path, index_sha256, 4 * MIB), "operational proof index")
    fields(index, ("schema_version", "index_id", "catalog", "research_inputs", "admission", "policy_research_bindings"), "operational proof index")
    require(index["schema_version"] == "t049-operational-proof-index/v1", "unsupported operational proof index")
    string(index["index_id"], "proof index id")
    catalog, regions, cities, by_region, zones, aliases = catalog_data(catalog_path, index["catalog"])
    research, research_sources, documents, pins = research_data(root, index["research_inputs"], {r["federal_subject_code"] for r in regions.values()})
    require(all(date(doc["researched_at"]) <= state.date() for doc in documents.values()), "research postdates report state")
    counts = {"region_count": len(regions), "city_count": len(cities), "alias_count": aliases}
    manifest, files, payloads = load_bundle(index["admission"], state, index["catalog"], counts)
    choices, policies = policies_and_mappings(index, manifest, files, payloads, index["catalog"], regions, cities, by_region, research_sources, documents, pins)
    local_dates = {name: state.astimezone(zone).date() for name, zone in zones.items()}
    current, ever_bound, seen_intervals, policy_current = collections.defaultdict(set), set(), collections.defaultdict(list), collections.defaultdict(set)
    all_choice_ids = {}
    for binding in array(choices["bindings"], "choice bindings"):
        fields(binding, ("city_id", "choice_id", "policy_id", "display_label", "tier", "effective"), "choice binding")
        city_id, policy_id = binding["city_id"], binding["policy_id"]
        require(city_id in cities and policy_id in policies, "binding has unknown city/policy")
        require(city_id in policies[policy_id]["allowed"], "binding outside exact canonical scope")
        choice_id = string(binding["choice_id"], "choice id")
        pair = (city_id, policy_id)
        require(choice_id not in all_choice_ids or all_choice_ids[choice_id] == pair, "choice id reused for another city/policy")
        all_choice_ids[choice_id] = pair
        low, high = interval(binding["effective"])
        eligible_low, eligible_high = policies[policy_id]["range"]
        require(eligible_low <= low <= high <= eligible_high, "binding exceeds verified eligibility interval")
        for old_low, old_high in seen_intervals[pair]:
            require(high < old_low or low > old_high, "duplicate/overlapping city policy binding interval")
        seen_intervals[pair].append((low, high))
        ever_bound.add(city_id)
        if low <= local_dates[cities[city_id]["timezone"]] <= high:
            current[city_id].add(choice_id)
            policy_current[policy_id].add(city_id)
    require(set(policies) == {p for _, p in seen_intervals}, "policy without explicit bindings")
    for policy_id, policy in policies.items():
        policy["output"]["current_city_ids"] = sorted(policy_current[policy_id])
        policy["output"]["current_city_count"] = len(policy_current[policy_id])
    subjects = []
    for i, region in enumerate(sorted(regions.values(), key=lambda r: r["federal_subject_code"])):
        ids = by_region[region["id"]]
        covered = sorted(ids & current.keys())
        status = "covered" if len(covered) == len(ids) else "partially_covered" if covered else "unavailable"
        relevant = sorted(p for p, v in policies.items() if v["output"]["scope"]["region_id"] == region["id"])
        subjects.append({"code": region["federal_subject_code"], "name": region["name"], "canonical_region_id": region["id"],
                         "research": research[region["federal_subject_code"]], "operational_policy_ids": relevant,
                         "availability": {"status": status, "canonical_city_count": len(ids), "covered_city_count": len(covered),
                                          "covered_city_ids": covered, "unavailable_city_count": len(ids) - len(covered),
                                          "unavailable_city_set": {"kind": "canonical_region_complement", "catalog_region_id": region["id"],
                                                                   "subtract_city_ids_ref": "#/subjects/" + str(i) + "/availability/covered_city_ids"},
                                          "no_admitted_binding_city_count": len(ids - ever_bound),
                                          "outside_current_interval_city_count": len((ids & ever_bound) - current.keys()),
                                          "current_choice_count": sum(len(current[c]) for c in covered),
                                          "reason": "Only explicit current bindings in the pinned previously admitted bundle count; all other canonical localities are unavailable."}})
    summary = {"subject_count": len(regions), "city_count": len(cities), "alias_count": aliases,
               "research_authority_records": sum(len(s["authorities"]) for s in research.values()),
               "research_source_candidates": len(research_sources), "admitted_policy_count": len(policies),
               "qualified_public_policy_count": sum(p["output"]["proof_kind"] == "qualified_public" for p in policies.values()),
               "retained_legacy_policy_count": sum(p["output"]["proof_kind"] == "retained_legacy" for p in policies.values()),
               "current_policy_count": sum(bool(policy_current[p]) for p in policies),
               "current_qualified_public_policy_count": sum(bool(policy_current[k]) and p["output"]["proof_kind"] == "qualified_public" for k, p in policies.items()),
               "current_retained_legacy_policy_count": sum(bool(policy_current[k]) and p["output"]["proof_kind"] == "retained_legacy" for k, p in policies.items()),
               "covered_city_count": len(current), "unavailable_city_count": len(cities) - len(current),
               "current_choice_count": sum(len(v) for v in current.values()), "cities_with_multiple_choices": sum(len(v) > 1 for v in current.values()),
               "subject_availability": dict(sorted(collections.Counter(s["availability"]["status"] for s in subjects).items())),
               "research_status_distribution": dict(sorted(collections.Counter(s["status"] for s in research.values()).items()))}
    index_path = clean_path(index_path)
    index_ref = index_path.relative_to(root).as_posix() if index_path.is_relative_to(root) else str(index_path)
    return {"schema_version": "t049-nationwide-registry/v1", "compiler_version": "t049-nationwide-registry/v1",
            "state_at": state_at, "trust_boundary": TRUST_BOUNDARY,
            "inputs": {"canonical_catalog": index["catalog"], "research_lanes": sorted(index["research_inputs"], key=lambda r: r["path"]),
                       "operational_proof_index": {"index_id": index["index_id"], "path": index_ref, "sha256": index_sha256, "pointer": ""}},
            "canonical_subject_codes": sorted(r["federal_subject_code"] for r in regions.values()),
            "local_date_by_timezone": {k: v.isoformat() for k, v in sorted(local_dates.items())},
            "summary": summary, "operational": {"admission": index["admission"], "bundle_id": manifest["bundle_id"],
                                                   "manifest_sha256": manifest["manifest_sha256"], "registry_revision": manifest["registry_revision"],
                                                   "bindings_ref": reference("choices.json", files["choices.json"]["sha256"], "/bindings"),
                                                   "policies": [policies[p]["output"] for p in sorted(policies)]}, "subjects": subjects}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--proof-index", required=True, type=Path)
    parser.add_argument("--proof-index-sha256", required=True)
    parser.add_argument("--state-at", required=True)
    parser.add_argument("--repository-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        result = compile_registry(args.catalog, args.proof_index, args.proof_index_sha256, args.state_at, args.repository_root)
        raw = json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False).encode("utf-8") + b"\n"
        output = clean_path(args.output)
        inputs = [clean_path(args.catalog), clean_path(args.proof_index)]
        inputs += [clean_path(args.repository_root / entry["path"]) for entry in result["inputs"]["research_lanes"]]
        admission = result["operational"]["admission"]
        inputs += [clean_path(admission[k]["path"]) for k in ("manifest", "record")]
        require(not output.is_relative_to(clean_path(admission["manifest"]["path"]).parent), "output is inside immutable bundle")
        require(all(output != source and (not output.exists() or not os.path.samefile(output, source)) for source in inputs),
                "output aliases a pinned input")
        require(not output.exists() or output.is_file(), "output must be a regular report file")
        if output.exists():
            require(output.stat().st_size <= 16 * MIB, "existing output exceeds report bound")
            old = strict_json(output.read_bytes(), "existing report")
            require(isinstance(old, dict) and old.get("schema_version") == "t049-nationwide-registry/v1",
                    "refusing to overwrite a non-report file")
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(dir=output.parent, prefix=".t049-report-", delete=False) as handle:
                temporary = Path(handle.name)
                handle.write(raw)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, output)
        finally:
            if temporary is not None and temporary.exists():
                temporary.unlink()  # Only this exact newly created temporary file.
        print(json.dumps({"output": str(output), "sha256": sha256(raw), "summary": result["summary"]}, ensure_ascii=False))
        return 0
    except (ValueError, OSError) as error:
        print("nationwide-registry: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
