-- Public source qualification is not a mosque or a human approval. Keep the
-- v1 columns/receipts unchanged and add a separate, immutable admission branch.
ALTER TABLE registry_revisions DROP CONSTRAINT registry_revisions_schema_version_check;
ALTER TABLE registry_revisions
    ADD CONSTRAINT registry_revisions_schema_version_check CHECK (schema_version IN (1, 2)),
    ADD CONSTRAINT registry_revisions_id_schema_unique UNIQUE (id, schema_version),
    ADD CONSTRAINT registry_revisions_id_catalog_unique UNIQUE (id, catalog_revision_id);

CREATE TABLE registry_source_qualifications (
    revision_id text NOT NULL,
    registry_schema_version integer GENERATED ALWAYS AS (2) STORED,
    id text NOT NULL CHECK (id ~ '^qualification-[0-9a-f]{32}$'),
    qualification_sha256 text NOT NULL CHECK (qualification_sha256 ~ '^[0-9a-f]{64}$'),
    source_id text NOT NULL,
    scope_id text NOT NULL,
    authority_id text NOT NULL,
    catalog_revision_id text NOT NULL,
    timezone text NOT NULL CHECK (timezone ~ '^[A-Za-z_+-]+/[A-Za-z0-9_+/-]+$'),
    effective_from date NOT NULL,
    effective_to date NOT NULL,
    public_context_id text NOT NULL CHECK (
        public_context_id = 'public-scope-' || substring(encode(sha256(convert_to(scope_id, 'UTF8')), 'hex'), 1, 32)
    ),
    proof_json jsonb NOT NULL CHECK (jsonb_typeof(proof_json) = 'object' AND octet_length(proof_json::text) <= 1048576),
    PRIMARY KEY (revision_id, id),
    UNIQUE (revision_id, source_id),
    UNIQUE (revision_id, id, source_id, scope_id),
    UNIQUE (revision_id, id, source_id, scope_id, public_context_id, timezone, effective_from, effective_to),
    UNIQUE (revision_id, id, qualification_sha256, public_context_id, timezone, effective_from, effective_to),
    FOREIGN KEY (revision_id, registry_schema_version) REFERENCES registry_revisions(id, schema_version),
    FOREIGN KEY (revision_id, catalog_revision_id) REFERENCES registry_revisions(id, catalog_revision_id),
    FOREIGN KEY (revision_id, source_id, scope_id) REFERENCES registry_sources(revision_id, id, scope_id),
    FOREIGN KEY (revision_id, source_id, authority_id) REFERENCES registry_source_authorities(revision_id, source_id, authority_id),
    CHECK (effective_from <= effective_to),
    CHECK (id = 'qualification-' || substring(qualification_sha256, 1, 32)),
    CHECK (proof_json @> jsonb_build_object(
        'schema_version', 'namaztime-source-qualification/v1',
        'qualification_id', id,
        'sha256', qualification_sha256,
        'state', 'qualified',
        'decision_system', 'namaztime:source-qualification/v1',
        'source_id', source_id,
        'scope', jsonb_build_object('id', scope_id),
        'authority', jsonb_build_object('id', authority_id),
        'catalog_revision', catalog_revision_id,
        'timezone', timezone,
        'coverage', jsonb_build_object('from', to_char(effective_from, 'YYYY-MM-DD'), 'to', to_char(effective_to, 'YYYY-MM-DD'))
    ))
);

ALTER TABLE registry_sources DROP CONSTRAINT registry_sources_status_check;
ALTER TABLE registry_sources
    ADD COLUMN qualification_id text,
    ADD CONSTRAINT registry_sources_status_check CHECK (status IN ('research_only', 'approved', 'qualified', 'stale', 'unavailable')),
    ADD CONSTRAINT registry_sources_qualification_branch_check CHECK (
        (qualification_id IS NULL AND status <> 'qualified') OR
        (qualification_id IS NOT NULL AND status IN ('qualified', 'stale', 'unavailable'))
    ),
    ADD CONSTRAINT registry_sources_qualification_fkey
        FOREIGN KEY (revision_id, qualification_id, id, scope_id)
        REFERENCES registry_source_qualifications(revision_id, id, source_id, scope_id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE registry_policies ALTER COLUMN approval_id DROP NOT NULL;
ALTER TABLE registry_policies
    ADD COLUMN qualification_id text,
    ADD CONSTRAINT registry_policies_admission_branch_check CHECK (
        (approval_id IS NOT NULL AND qualification_id IS NULL) OR
        (approval_id IS NULL AND qualification_id IS NOT NULL AND kind = 'timetable')
    ),
    ADD CONSTRAINT registry_policies_qualification_fkey
        FOREIGN KEY (revision_id, qualification_id, source_id, scope_id)
        REFERENCES registry_source_qualifications(revision_id, id, source_id, scope_id);

ALTER TABLE registry_timetables ALTER COLUMN mosque_id DROP NOT NULL;
ALTER TABLE registry_timetables
    ADD COLUMN public_context_id text,
    ADD COLUMN qualification_id text,
    ADD CONSTRAINT registry_timetables_public_snapshot_unique
        UNIQUE (revision_id, published_snapshot_id, qualification_id, public_context_id, timezone, effective_from, effective_to),
    ADD CONSTRAINT registry_timetables_context_branch_check CHECK (
        (mosque_id IS NOT NULL AND public_context_id IS NULL AND qualification_id IS NULL) OR
        (mosque_id IS NULL AND public_context_id IS NOT NULL AND qualification_id IS NOT NULL)
    ),
    ADD CONSTRAINT registry_timetables_qualification_fkey
        FOREIGN KEY (revision_id, qualification_id, source_id, scope_id, public_context_id, timezone, effective_from, effective_to)
        REFERENCES registry_source_qualifications(revision_id, id, source_id, scope_id, public_context_id, timezone, effective_from, effective_to);

ALTER TABLE registry_verified_snapshots ALTER COLUMN mosque_id DROP NOT NULL;
ALTER TABLE registry_verified_snapshots
    ADD COLUMN public_context_id text,
    ADD COLUMN qualification_id text,
    ADD COLUMN qualification_sha256 text,
    ADD CONSTRAINT registry_verified_snapshots_admission_check CHECK (
        (mosque_id IS NOT NULL AND public_context_id IS NULL AND qualification_id IS NULL AND qualification_sha256 IS NULL) OR
        (mosque_id IS NULL AND public_context_id IS NOT NULL AND qualification_id IS NOT NULL AND qualification_sha256 IS NOT NULL)
    ),
    ADD CONSTRAINT registry_verified_snapshots_qualification_fkey
        FOREIGN KEY (revision_id, qualification_id, qualification_sha256, public_context_id, timezone, effective_from, effective_to)
        REFERENCES registry_source_qualifications(revision_id, id, qualification_sha256, public_context_id, timezone, effective_from, effective_to),
    ADD CONSTRAINT registry_verified_snapshots_public_timetable_fkey
        FOREIGN KEY (revision_id, snapshot_id, qualification_id, public_context_id, timezone, effective_from, effective_to)
        REFERENCES registry_timetables(revision_id, published_snapshot_id, qualification_id, public_context_id, timezone, effective_from, effective_to);

CREATE TRIGGER registry_source_qualifications_append_only_row
BEFORE UPDATE OR DELETE ON registry_source_qualifications
FOR EACH ROW EXECUTE FUNCTION reject_registry_row_mutation();

CREATE TRIGGER registry_source_qualifications_append_only_truncate
BEFORE TRUNCATE ON registry_source_qualifications
FOR EACH STATEMENT EXECUTE FUNCTION reject_registry_truncate();

-- v7/v8 requests remain the legacy pending-human-review path. Qualified public
-- selection must not manufacture registry_policy_mosques or review requests.
