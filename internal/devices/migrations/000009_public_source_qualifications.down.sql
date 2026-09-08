-- Never erase retained qualification proof or silently reinterpret a v2
-- revision as a v1 approval. Explicit active-pointer rollback is independent.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM registry_revisions WHERE schema_version = 2)
       OR EXISTS (SELECT 1 FROM registry_source_qualifications)
       OR EXISTS (SELECT 1 FROM registry_verified_snapshots WHERE qualification_id IS NOT NULL)
    THEN
        RAISE EXCEPTION 'cannot downgrade while public qualification evidence is retained'
            USING ERRCODE = '55000';
    END IF;
END;
$$;

ALTER TABLE registry_verified_snapshots
    DROP CONSTRAINT registry_verified_snapshots_public_timetable_fkey,
    DROP CONSTRAINT registry_verified_snapshots_qualification_fkey,
    DROP CONSTRAINT registry_verified_snapshots_admission_check,
    DROP COLUMN qualification_sha256,
    DROP COLUMN qualification_id,
    DROP COLUMN public_context_id,
    ALTER COLUMN mosque_id SET NOT NULL;

ALTER TABLE registry_timetables
    DROP CONSTRAINT registry_timetables_public_snapshot_unique,
    DROP CONSTRAINT registry_timetables_qualification_fkey,
    DROP CONSTRAINT registry_timetables_context_branch_check,
    DROP COLUMN qualification_id,
    DROP COLUMN public_context_id,
    ALTER COLUMN mosque_id SET NOT NULL;

ALTER TABLE registry_policies
    DROP CONSTRAINT registry_policies_qualification_fkey,
    DROP CONSTRAINT registry_policies_admission_branch_check,
    DROP COLUMN qualification_id,
    ALTER COLUMN approval_id SET NOT NULL;

ALTER TABLE registry_sources
    DROP CONSTRAINT registry_sources_qualification_fkey,
    DROP CONSTRAINT registry_sources_qualification_branch_check,
    DROP CONSTRAINT registry_sources_status_check,
    DROP COLUMN qualification_id;
ALTER TABLE registry_sources ADD CONSTRAINT registry_sources_status_check
    CHECK (status IN ('research_only', 'approved', 'stale', 'unavailable'));

DROP TABLE registry_source_qualifications;
ALTER TABLE registry_revisions
    DROP CONSTRAINT registry_revisions_id_schema_unique,
    DROP CONSTRAINT registry_revisions_id_catalog_unique,
    DROP CONSTRAINT registry_revisions_schema_version_check;
ALTER TABLE registry_revisions ADD CONSTRAINT registry_revisions_schema_version_check CHECK (schema_version = 1);
