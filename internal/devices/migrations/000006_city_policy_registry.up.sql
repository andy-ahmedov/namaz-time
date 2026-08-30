CREATE TABLE registry_revisions (
    id text PRIMARY KEY CHECK (id <> '' AND length(id) <= 160),
    schema_version integer NOT NULL CHECK (schema_version = 1),
    parent_revision_id text REFERENCES registry_revisions(id),
    catalog_revision_id text NOT NULL CHECK (catalog_revision_id <> '' AND length(catalog_revision_id) <= 200),
    content_sha256 text NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL,
    created_by text NOT NULL CHECK (created_by <> '' AND length(created_by) <= 160),
    reason text NOT NULL CHECK (reason <> '' AND length(reason) <= 1000)
);

CREATE TABLE registry_regions (
    revision_id text NOT NULL REFERENCES registry_revisions(id),
    id text NOT NULL CHECK (id <> '' AND length(id) <= 160),
    name text NOT NULL CHECK (name <> '' AND length(name) <= 300),
    country_code text NOT NULL CHECK (country_code = 'RU'),
    federal_subject_code text NOT NULL CHECK (federal_subject_code ~ '^RU-[A-Z]{2,3}$'),
    PRIMARY KEY (revision_id, id),
    UNIQUE (revision_id, federal_subject_code)
);

CREATE TABLE registry_cities (
    revision_id text NOT NULL REFERENCES registry_revisions(id),
    id text NOT NULL CHECK (id <> '' AND length(id) <= 160),
    canonical_name text NOT NULL CHECK (canonical_name <> '' AND length(canonical_name) <= 400),
    normalized_name text NOT NULL CHECK (normalized_name <> '' AND length(normalized_name) <= 400),
    country_code text NOT NULL CHECK (country_code = 'RU'),
    region_id text NOT NULL,
    settlement_type text NOT NULL CHECK (settlement_type <> '' AND length(settlement_type) <= 32),
    latitude double precision NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude double precision NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    timezone text NOT NULL CHECK (timezone ~ '^[A-Za-z_+-]+/[A-Za-z0-9_+/-]+$'),
    population bigint NOT NULL CHECK (population >= 0),
    geographic_source text NOT NULL CHECK (geographic_source <> '' AND length(geographic_source) <= 2000),
    geographic_source_id text NOT NULL CHECK (geographic_source_id <> '' AND length(geographic_source_id) <= 300),
    geographic_revision text NOT NULL CHECK (geographic_revision <> '' AND length(geographic_revision) <= 200),
    geographic_license text NOT NULL CHECK (geographic_license <> '' AND length(geographic_license) <= 300),
    source_modified_date date,
    fallback_policy_id text,
    PRIMARY KEY (revision_id, id),
    FOREIGN KEY (revision_id, region_id) REFERENCES registry_regions(revision_id, id),
    UNIQUE (revision_id, id, region_id),
    UNIQUE (revision_id, geographic_source_id)
);

CREATE INDEX registry_cities_search_idx ON registry_cities (revision_id, normalized_name);

CREATE TABLE registry_city_aliases (
    revision_id text NOT NULL,
    city_id text NOT NULL,
    alias text NOT NULL CHECK (alias <> '' AND length(alias) <= 400),
    normalized_alias text NOT NULL CHECK (normalized_alias <> '' AND length(normalized_alias) <= 400),
    PRIMARY KEY (revision_id, city_id, normalized_alias),
    FOREIGN KEY (revision_id, city_id) REFERENCES registry_cities(revision_id, id)
);

CREATE INDEX registry_city_aliases_search_idx ON registry_city_aliases (revision_id, normalized_alias);

CREATE TABLE registry_scopes (
    revision_id text NOT NULL,
    id text NOT NULL CHECK (id <> '' AND length(id) <= 160),
    kind text NOT NULL CHECK (kind IN ('city', 'region')),
    city_id text,
    region_id text NOT NULL,
    description text NOT NULL CHECK (description <> '' AND length(description) <= 1000),
    PRIMARY KEY (revision_id, id),
    FOREIGN KEY (revision_id, region_id) REFERENCES registry_regions(revision_id, id),
    FOREIGN KEY (revision_id, city_id, region_id) REFERENCES registry_cities(revision_id, id, region_id),
    CHECK ((kind = 'city' AND city_id IS NOT NULL) OR (kind = 'region' AND city_id IS NULL)),
    UNIQUE (revision_id, id, kind, city_id, region_id)
);

CREATE TABLE registry_authorities (
    revision_id text NOT NULL REFERENCES registry_revisions(id),
    id text NOT NULL CHECK (id <> '' AND length(id) <= 160),
    name text NOT NULL CHECK (name <> '' AND length(name) <= 500),
    branch text NOT NULL CHECK (length(branch) <= 1000),
    website text NOT NULL CHECK (length(website) <= 2000),
    evidence_label text NOT NULL CHECK (evidence_label IN (
        'CONFIRMED_PUBLIC', 'CONFIRMED_STATIC', 'CONFIRMED_RUNTIME',
        'INFERENCE', 'PROPOSAL', 'UNKNOWN'
    )),
    PRIMARY KEY (revision_id, id)
);

CREATE TABLE registry_sources (
    revision_id text NOT NULL,
    id text NOT NULL CHECK (id <> '' AND length(id) <= 160),
    kind text NOT NULL CHECK (kind IN (
        'official_api', 'official_file', 'official_html',
        'mosque_calendar', 'calculation_profile', 'manual_import'
    )),
    scope_id text NOT NULL,
    canonical_url text NOT NULL CHECK (length(canonical_url) <= 2000),
    status text NOT NULL CHECK (status IN ('research_only', 'approved', 'stale', 'unavailable')),
    fresh_through date,
    PRIMARY KEY (revision_id, id),
    FOREIGN KEY (revision_id, scope_id) REFERENCES registry_scopes(revision_id, id),
    UNIQUE (revision_id, id, scope_id)
);

CREATE TABLE registry_source_authorities (
    revision_id text NOT NULL,
    source_id text NOT NULL,
    authority_id text NOT NULL,
    position integer NOT NULL CHECK (position >= 0),
    PRIMARY KEY (revision_id, source_id, authority_id),
    UNIQUE (revision_id, source_id, position),
    FOREIGN KEY (revision_id, source_id) REFERENCES registry_sources(revision_id, id),
    FOREIGN KEY (revision_id, authority_id) REFERENCES registry_authorities(revision_id, id)
);

CREATE TABLE registry_policies (
    revision_id text NOT NULL,
    id text NOT NULL CHECK (id <> '' AND length(id) <= 160),
    kind text NOT NULL CHECK (kind IN ('timetable', 'calculation_profile')),
    scope_id text NOT NULL,
    source_id text NOT NULL,
    timetable_id text,
    calculation_profile_id text,
    effective_from date NOT NULL,
    effective_to date NOT NULL,
    approval_id text NOT NULL CHECK (approval_id <> '' AND length(approval_id) <= 200),
    PRIMARY KEY (revision_id, id),
    FOREIGN KEY (revision_id, scope_id) REFERENCES registry_scopes(revision_id, id),
    FOREIGN KEY (revision_id, source_id, scope_id) REFERENCES registry_sources(revision_id, id, scope_id),
    CHECK (effective_from <= effective_to),
    CHECK (
        (kind = 'timetable' AND timetable_id IS NOT NULL AND calculation_profile_id IS NULL) OR
        (kind = 'calculation_profile' AND calculation_profile_id IS NOT NULL AND timetable_id IS NULL)
    )
);

CREATE TABLE registry_policy_authorities (
    revision_id text NOT NULL,
    policy_id text NOT NULL,
    authority_id text NOT NULL,
    position integer NOT NULL CHECK (position >= 0),
    PRIMARY KEY (revision_id, policy_id, authority_id),
    UNIQUE (revision_id, policy_id, position),
    FOREIGN KEY (revision_id, policy_id) REFERENCES registry_policies(revision_id, id),
    FOREIGN KEY (revision_id, authority_id) REFERENCES registry_authorities(revision_id, id)
);

CREATE TABLE registry_policy_mosques (
    revision_id text NOT NULL,
    policy_id text NOT NULL,
    mosque_id text NOT NULL REFERENCES mosques(id),
    PRIMARY KEY (revision_id, policy_id, mosque_id),
    FOREIGN KEY (revision_id, policy_id) REFERENCES registry_policies(revision_id, id)
);

CREATE TABLE registry_calculation_profiles (
    revision_id text NOT NULL,
    id text NOT NULL CHECK (id <> '' AND length(id) <= 160),
    source_id text NOT NULL,
    scope_id text NOT NULL,
    version text NOT NULL CHECK (version <> '' AND length(version) <= 200),
    effective_from date NOT NULL,
    effective_to date NOT NULL,
    approval_id text NOT NULL CHECK (approval_id <> '' AND length(approval_id) <= 200),
    PRIMARY KEY (revision_id, id),
    FOREIGN KEY (revision_id, source_id, scope_id) REFERENCES registry_sources(revision_id, id, scope_id),
    CHECK (effective_from <= effective_to)
);

CREATE TABLE registry_timetables (
    revision_id text NOT NULL,
    id text NOT NULL CHECK (id <> '' AND length(id) <= 160),
    source_id text NOT NULL,
    scope_id text NOT NULL,
    mosque_id text NOT NULL REFERENCES mosques(id),
    timezone text NOT NULL CHECK (timezone ~ '^[A-Za-z_+-]+/[A-Za-z0-9_+/-]+$'),
    effective_from date NOT NULL,
    effective_to date NOT NULL,
    published_snapshot_id text NOT NULL CHECK (published_snapshot_id <> '' AND length(published_snapshot_id) <= 200),
    PRIMARY KEY (revision_id, id),
    FOREIGN KEY (revision_id, source_id, scope_id) REFERENCES registry_sources(revision_id, id, scope_id),
    CHECK (effective_from <= effective_to)
);

ALTER TABLE registry_policies
    ADD FOREIGN KEY (revision_id, timetable_id) REFERENCES registry_timetables(revision_id, id),
    ADD FOREIGN KEY (revision_id, calculation_profile_id) REFERENCES registry_calculation_profiles(revision_id, id);

CREATE TABLE registry_source_overrides (
    revision_id text NOT NULL,
    id text NOT NULL CHECK (id <> '' AND length(id) <= 160),
    base_source_id text NOT NULL,
    override_source_id text NOT NULL,
    effective_from date NOT NULL,
    effective_to date NOT NULL,
    approval_id text NOT NULL CHECK (approval_id <> '' AND length(approval_id) <= 200),
    PRIMARY KEY (revision_id, id),
    FOREIGN KEY (revision_id, base_source_id) REFERENCES registry_sources(revision_id, id),
    FOREIGN KEY (revision_id, override_source_id) REFERENCES registry_sources(revision_id, id),
    CHECK (base_source_id <> override_source_id),
    CHECK (effective_from <= effective_to)
);

CREATE TABLE registry_source_override_fields (
    revision_id text NOT NULL,
    override_id text NOT NULL,
    field_name text NOT NULL CHECK (field_name <> '' AND length(field_name) <= 100),
    PRIMARY KEY (revision_id, override_id, field_name),
    FOREIGN KEY (revision_id, override_id) REFERENCES registry_source_overrides(revision_id, id)
);

CREATE TABLE registry_timetable_overrides (
    revision_id text NOT NULL,
    timetable_id text NOT NULL,
    override_id text NOT NULL,
    position integer NOT NULL CHECK (position >= 0),
    PRIMARY KEY (revision_id, timetable_id, override_id),
    UNIQUE (revision_id, timetable_id, position),
    FOREIGN KEY (revision_id, timetable_id) REFERENCES registry_timetables(revision_id, id),
    FOREIGN KEY (revision_id, override_id) REFERENCES registry_source_overrides(revision_id, id)
);

CREATE TABLE registry_active_revision (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    revision_id text NOT NULL REFERENCES registry_revisions(id),
    previous_revision_id text REFERENCES registry_revisions(id),
    activated_at timestamptz NOT NULL,
    activated_by text NOT NULL CHECK (activated_by <> '' AND length(activated_by) <= 160),
    reason text NOT NULL CHECK (reason <> '' AND length(reason) <= 1000)
);

CREATE TABLE registry_audit_events (
    id text PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    event_type text NOT NULL CHECK (event_type IN ('staged', 'activated', 'rollback')),
    revision_id text NOT NULL REFERENCES registry_revisions(id),
    previous_revision_id text REFERENCES registry_revisions(id),
    actor_id text NOT NULL CHECK (actor_id <> '' AND length(actor_id) <= 160),
    reason text NOT NULL CHECK (reason <> '' AND length(reason) <= 1000),
    occurred_at timestamptz NOT NULL,
    content_sha256 text NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE registry_verified_approvals (
    event_id text NOT NULL REFERENCES registry_audit_events(id),
    revision_id text NOT NULL REFERENCES registry_revisions(id),
    approval_id text NOT NULL CHECK (approval_id <> '' AND length(approval_id) <= 200),
    mosque_id text NOT NULL REFERENCES mosques(id),
    evidence_sha256 text NOT NULL CHECK (evidence_sha256 ~ '^[0-9a-f]{64}$'),
    verified_at timestamptz NOT NULL,
    PRIMARY KEY (event_id, approval_id)
);

CREATE TABLE registry_verified_snapshots (
    event_id text NOT NULL REFERENCES registry_audit_events(id),
    revision_id text NOT NULL REFERENCES registry_revisions(id),
    snapshot_id text NOT NULL CHECK (snapshot_id <> '' AND length(snapshot_id) <= 200),
    mosque_id text NOT NULL REFERENCES mosques(id),
    timezone text NOT NULL CHECK (timezone ~ '^[A-Za-z_+-]+/[A-Za-z0-9_+/-]+$'),
    effective_from date NOT NULL,
    effective_to date NOT NULL,
    payload_sha256 text NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    signing_key_id text NOT NULL CHECK (signing_key_id <> '' AND length(signing_key_id) <= 200),
    verified_at timestamptz NOT NULL,
    PRIMARY KEY (event_id, snapshot_id),
    CHECK (effective_from <= effective_to)
);

CREATE OR REPLACE FUNCTION reject_registry_row_mutation() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'registry revision data is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE OR REPLACE FUNCTION reject_registry_truncate() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'registry revision data is append-only' USING ERRCODE = '55000';
END;
$$;

DO $$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'registry_revisions', 'registry_regions', 'registry_cities', 'registry_city_aliases',
        'registry_scopes', 'registry_authorities', 'registry_sources', 'registry_source_authorities',
        'registry_policies', 'registry_policy_authorities', 'registry_policy_mosques',
        'registry_calculation_profiles', 'registry_timetables', 'registry_source_overrides',
        'registry_source_override_fields', 'registry_timetable_overrides', 'registry_audit_events',
        'registry_verified_approvals', 'registry_verified_snapshots'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER %I_append_only_row BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION reject_registry_row_mutation()',
            table_name, table_name
        );
        EXECUTE format(
            'CREATE TRIGGER %I_append_only_truncate BEFORE TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION reject_registry_truncate()',
            table_name, table_name
        );
    END LOOP;
END;
$$;
