package registry

import (
	"context"
	"crypto/sha256"
	"crypto/tls"
	"encoding/hex"
	"errors"
	"fmt"
	"net"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

const registryAdvisoryLockID int64 = 70149822411036

type PostgresRevisionStore struct {
	pool *pgxpool.Pool
}

type CitySearchResult struct {
	City   domain.City
	Region domain.Region
}

func NewPostgresRevisionStore(pool *pgxpool.Pool) *PostgresRevisionStore {
	return &PostgresRevisionStore{pool: pool}
}

func OpenPostgresRevisionStore(ctx context.Context, databaseURL string) (*PostgresRevisionStore, error) {
	if strings.TrimSpace(databaseURL) == "" {
		return nil, errors.New("open registry PostgreSQL: database URL is required")
	}
	config, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, errors.New("open registry PostgreSQL: parse database URL")
	}
	if err := validateRegistryPostgresTransport(config); err != nil {
		return nil, fmt.Errorf("open registry PostgreSQL: %w", err)
	}
	config.MaxConns = 10
	config.MinConns = 1
	config.MaxConnLifetime = 30 * time.Minute
	config.MaxConnIdleTime = 5 * time.Minute
	pool, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		return nil, errors.New("open registry PostgreSQL: create pool")
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, errors.New("open registry PostgreSQL: ping database")
	}
	return NewPostgresRevisionStore(pool), nil
}

func validateRegistryPostgresTransport(config *pgxpool.Config) error {
	if config == nil || config.ConnConfig == nil {
		return errors.New("PostgreSQL connection config is missing")
	}
	if err := validateRegistryPostgresEndpoint(config.ConnConfig.Host, config.ConnConfig.TLSConfig); err != nil {
		return err
	}
	for _, fallback := range config.ConnConfig.Fallbacks {
		if err := validateRegistryPostgresEndpoint(fallback.Host, fallback.TLSConfig); err != nil {
			return err
		}
	}
	return nil
}

func validateRegistryPostgresEndpoint(host string, tlsConfig *tls.Config) error {
	if registryPostgresEndpointIsLocal(host) {
		return nil
	}
	if tlsConfig == nil {
		return errors.New("remote PostgreSQL endpoint requires authenticated TLS")
	}
	if tlsConfig.InsecureSkipVerify {
		return errors.New("remote PostgreSQL endpoint requires certificate and hostname verification")
	}
	return nil
}

func registryPostgresEndpointIsLocal(host string) bool {
	if strings.HasPrefix(host, "/") || strings.EqualFold(host, "localhost") {
		return true
	}
	ip := net.ParseIP(strings.Trim(host, "[]"))
	return ip != nil && ip.IsLoopback()
}

func (store *PostgresRevisionStore) Close() {
	if store != nil && store.pool != nil {
		store.pool.Close()
	}
}

func (store *PostgresRevisionStore) Stage(ctx context.Context, record RevisionRecord, dataset Dataset) error {
	if store == nil || store.pool == nil {
		return ErrRevisionInvalid
	}
	datasetSHA256, err := DatasetSHA256(dataset)
	if err != nil || datasetSHA256 != record.ContentSHA256 {
		return fmt.Errorf("%w: persisted dataset hash mismatch", ErrRevisionInvalid)
	}
	tx, err := store.pool.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.Serializable})
	if err != nil {
		return fmt.Errorf("stage registry revision: begin: %w", err)
	}
	defer rollbackRegistryTx(tx)
	if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, registryAdvisoryLockID); err != nil {
		return fmt.Errorf("stage registry revision: lock: %w", err)
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO registry_revisions (
			id, schema_version, parent_revision_id, catalog_revision_id, content_sha256,
			created_at, created_by, reason
		) VALUES ($1, $2, NULLIF($3, ''), $4, $5, $6, $7, $8)`,
		record.ID, record.SchemaVersion, record.ParentRevisionID, record.CatalogRevisionID, record.ContentSHA256,
		record.CreatedAt, record.CreatedBy, record.Reason,
	); err != nil {
		return mapRegistryWriteError("insert revision", err)
	}
	if err := copyRegistryDataset(ctx, tx, record.ID, dataset); err != nil {
		return err
	}
	eventID := registryEventID("staged", record.ID, "", record.CreatedBy, record.Reason, record.CreatedAt)
	if _, err := tx.Exec(ctx, `
		INSERT INTO registry_audit_events (
			id, event_type, revision_id, previous_revision_id, actor_id,
			reason, occurred_at, content_sha256
		) VALUES ($1, 'staged', $2, NULL, $3, $4, $5, $6)`,
		eventID, record.ID, record.CreatedBy, record.Reason, record.CreatedAt, record.ContentSHA256,
	); err != nil {
		return mapRegistryWriteError("insert staged audit", err)
	}
	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("stage registry revision: commit: %w", err)
	}
	return nil
}

func copyRegistryDataset(ctx context.Context, tx pgx.Tx, revisionID string, dataset Dataset) error {
	rows := make([][]any, 0, len(dataset.Regions))
	for _, item := range dataset.Regions {
		rows = append(rows, []any{revisionID, item.ID, item.Name, item.CountryCode, item.FederalSubjectCode})
	}
	if err := copyRows(ctx, tx, "registry_regions", []string{"revision_id", "id", "name", "country_code", "federal_subject_code"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.Cities {
		rows = append(rows, []any{
			revisionID, item.ID, item.Name, normalizeSearch(item.Name), item.CountryCode, item.RegionID,
			item.SettlementType, item.Latitude, item.Longitude, item.Timezone, item.Population,
			item.GeographicSource, item.GeographicSourceID, item.GeographicRevision,
			item.GeographicLicense, nullableText(item.SourceModifiedDate), nullableText(item.FallbackPolicyID),
		})
	}
	if err := copyRows(ctx, tx, "registry_cities", []string{
		"revision_id", "id", "canonical_name", "normalized_name", "country_code", "region_id",
		"settlement_type", "latitude", "longitude", "timezone", "population", "geographic_source",
		"geographic_source_id", "geographic_revision", "geographic_license", "source_modified_date", "fallback_policy_id",
	}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.Cities {
		for _, alias := range item.Aliases {
			rows = append(rows, []any{revisionID, item.ID, alias, normalizeSearch(alias)})
		}
	}
	if err := copyRows(ctx, tx, "registry_city_aliases", []string{"revision_id", "city_id", "alias", "normalized_alias"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.Scopes {
		rows = append(rows, []any{revisionID, item.ID, string(item.Kind), nullableText(item.CityID), item.RegionID, item.Description})
	}
	if err := copyRows(ctx, tx, "registry_scopes", []string{"revision_id", "id", "kind", "city_id", "region_id", "description"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.Authorities {
		rows = append(rows, []any{revisionID, item.ID, item.Name, item.Branch, item.Website, item.EvidenceLabel})
	}
	if err := copyRows(ctx, tx, "registry_authorities", []string{"revision_id", "id", "name", "branch", "website", "evidence_label"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.Sources {
		rows = append(rows, []any{revisionID, item.ID, string(item.Kind), item.GeographicScopeID, item.CanonicalURL, string(item.Status), nullableText(item.FreshThrough)})
	}
	if err := copyRows(ctx, tx, "registry_sources", []string{"revision_id", "id", "kind", "scope_id", "canonical_url", "status", "fresh_through"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.Sources {
		for position, authorityID := range item.AuthorityIDs {
			rows = append(rows, []any{revisionID, item.ID, authorityID, position})
		}
	}
	if err := copyRows(ctx, tx, "registry_source_authorities", []string{"revision_id", "source_id", "authority_id", "position"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.CalculationProfiles {
		rows = append(rows, []any{revisionID, item.ID, item.SourceID, item.GeographicScopeID, item.Version, item.Effective.From, item.Effective.To, item.ApprovalID})
	}
	if err := copyRows(ctx, tx, "registry_calculation_profiles", []string{"revision_id", "id", "source_id", "scope_id", "version", "effective_from", "effective_to", "approval_id"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.TimeTables {
		rows = append(rows, []any{revisionID, item.ID, item.SourceID, item.GeographicScopeID, item.MosqueID, item.Timezone, item.Effective.From, item.Effective.To, item.PublishedSnapshotID})
	}
	if err := copyRows(ctx, tx, "registry_timetables", []string{"revision_id", "id", "source_id", "scope_id", "mosque_id", "timezone", "effective_from", "effective_to", "published_snapshot_id"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.SourceOverrides {
		rows = append(rows, []any{revisionID, item.ID, item.BaseSourceID, item.OverrideSourceID, item.Effective.From, item.Effective.To, item.ApprovalID})
	}
	if err := copyRows(ctx, tx, "registry_source_overrides", []string{"revision_id", "id", "base_source_id", "override_source_id", "effective_from", "effective_to", "approval_id"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.SourceOverrides {
		for _, field := range item.AppliedFields {
			rows = append(rows, []any{revisionID, item.ID, field})
		}
	}
	if err := copyRows(ctx, tx, "registry_source_override_fields", []string{"revision_id", "override_id", "field_name"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.TimeTables {
		for position, overrideID := range item.SourceOverrideIDs {
			rows = append(rows, []any{revisionID, item.ID, overrideID, position})
		}
	}
	if err := copyRows(ctx, tx, "registry_timetable_overrides", []string{"revision_id", "timetable_id", "override_id", "position"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.Policies {
		rows = append(rows, []any{
			revisionID, item.ID, string(item.Kind), item.GeographicScopeID, item.SourceID,
			nullableText(item.TimeTableID), nullableText(item.CalculationProfileID), item.Effective.From, item.Effective.To, item.ApprovalID,
		})
	}
	if err := copyRows(ctx, tx, "registry_policies", []string{"revision_id", "id", "kind", "scope_id", "source_id", "timetable_id", "calculation_profile_id", "effective_from", "effective_to", "approval_id"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.Policies {
		for position, authorityID := range item.AuthorityIDs {
			rows = append(rows, []any{revisionID, item.ID, authorityID, position})
		}
	}
	if err := copyRows(ctx, tx, "registry_policy_authorities", []string{"revision_id", "policy_id", "authority_id", "position"}, rows); err != nil {
		return err
	}
	rows = rows[:0]
	for _, item := range dataset.Policies {
		for _, mosqueID := range item.MosqueIDs {
			rows = append(rows, []any{revisionID, item.ID, mosqueID})
		}
	}
	return copyRows(ctx, tx, "registry_policy_mosques", []string{"revision_id", "policy_id", "mosque_id"}, rows)
}

func copyRows(ctx context.Context, tx pgx.Tx, table string, columns []string, rows [][]any) error {
	if len(rows) == 0 {
		return nil
	}
	if _, err := tx.CopyFrom(ctx, pgx.Identifier{table}, columns, pgx.CopyFromRows(rows)); err != nil {
		return mapRegistryWriteError("copy "+table, err)
	}
	return nil
}

func (store *PostgresRevisionStore) Load(ctx context.Context, revisionID string) (RevisionRecord, Dataset, error) {
	if store == nil || store.pool == nil || revisionID == "" {
		return RevisionRecord{}, Dataset{}, ErrRevisionUnavailable
	}
	return loadRegistryRevision(ctx, store.pool, revisionID)
}

type registryQuerier interface {
	Query(context.Context, string, ...any) (pgx.Rows, error)
	QueryRow(context.Context, string, ...any) pgx.Row
}

func loadRegistryRevision(ctx context.Context, querier registryQuerier, revisionID string) (RevisionRecord, Dataset, error) {
	var record RevisionRecord
	if err := querier.QueryRow(ctx, `
		SELECT id, schema_version, COALESCE(parent_revision_id, ''), catalog_revision_id,
		       content_sha256, created_at, created_by, reason
		FROM registry_revisions WHERE id = $1`, revisionID).Scan(
		&record.ID, &record.SchemaVersion, &record.ParentRevisionID, &record.CatalogRevisionID,
		&record.ContentSHA256, &record.CreatedAt, &record.CreatedBy, &record.Reason,
	); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return RevisionRecord{}, Dataset{}, ErrRevisionUnavailable
		}
		return RevisionRecord{}, Dataset{}, fmt.Errorf("load registry revision: %w", err)
	}
	if record.SchemaVersion != RegistrySchemaVersion {
		return RevisionRecord{}, Dataset{}, fmt.Errorf("%w: stored schema version %d", ErrRevisionInvalid, record.SchemaVersion)
	}
	dataset, err := loadRegistryDataset(ctx, querier, revisionID)
	if err != nil {
		return RevisionRecord{}, Dataset{}, err
	}
	datasetSHA256, err := DatasetSHA256(dataset)
	if err != nil || datasetSHA256 != record.ContentSHA256 {
		return RevisionRecord{}, Dataset{}, fmt.Errorf("%w: stored content hash mismatch", ErrRevisionInvalid)
	}
	return record, dataset, nil
}

func loadRegistryDataset(ctx context.Context, querier registryQuerier, revisionID string) (Dataset, error) {
	var dataset Dataset
	rows, err := querier.Query(ctx, `SELECT id, name, country_code, federal_subject_code FROM registry_regions WHERE revision_id = $1 ORDER BY id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry regions: %w", err)
	}
	for rows.Next() {
		var item domain.Region
		if err := rows.Scan(&item.ID, &item.Name, &item.CountryCode, &item.FederalSubjectCode); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry region: %w", err)
		}
		dataset.Regions = append(dataset.Regions, item)
	}
	if err := finishRows(rows, "regions"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `
		SELECT id, canonical_name, country_code, region_id, settlement_type,
		       latitude, longitude, timezone, population, geographic_source,
		       geographic_source_id, geographic_revision, geographic_license,
		       COALESCE(source_modified_date::text, ''), COALESCE(fallback_policy_id, '')
		FROM registry_cities WHERE revision_id = $1 ORDER BY id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry cities: %w", err)
	}
	cityByID := make(map[string]int)
	for rows.Next() {
		var item domain.City
		if err := rows.Scan(
			&item.ID, &item.Name, &item.CountryCode, &item.RegionID, &item.SettlementType,
			&item.Latitude, &item.Longitude, &item.Timezone, &item.Population, &item.GeographicSource,
			&item.GeographicSourceID, &item.GeographicRevision, &item.GeographicLicense,
			&item.SourceModifiedDate, &item.FallbackPolicyID,
		); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry city: %w", err)
		}
		cityByID[item.ID] = len(dataset.Cities)
		dataset.Cities = append(dataset.Cities, item)
	}
	if err := finishRows(rows, "cities"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT city_id, alias FROM registry_city_aliases WHERE revision_id = $1 ORDER BY city_id, normalized_alias`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry aliases: %w", err)
	}
	for rows.Next() {
		var cityID, alias string
		if err := rows.Scan(&cityID, &alias); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry alias: %w", err)
		}
		index, ok := cityByID[cityID]
		if !ok {
			rows.Close()
			return Dataset{}, ErrRevisionInvalid
		}
		dataset.Cities[index].Aliases = append(dataset.Cities[index].Aliases, alias)
	}
	if err := finishRows(rows, "aliases"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT id, kind, COALESCE(city_id, ''), region_id, description FROM registry_scopes WHERE revision_id = $1 ORDER BY id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry scopes: %w", err)
	}
	for rows.Next() {
		var item domain.GeographicScope
		if err := rows.Scan(&item.ID, &item.Kind, &item.CityID, &item.RegionID, &item.Description); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry scope: %w", err)
		}
		dataset.Scopes = append(dataset.Scopes, item)
	}
	if err := finishRows(rows, "scopes"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT id, name, branch, website, evidence_label FROM registry_authorities WHERE revision_id = $1 ORDER BY id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry authorities: %w", err)
	}
	for rows.Next() {
		var item domain.PrayerAuthority
		if err := rows.Scan(&item.ID, &item.Name, &item.Branch, &item.Website, &item.EvidenceLabel); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry authority: %w", err)
		}
		dataset.Authorities = append(dataset.Authorities, item)
	}
	if err := finishRows(rows, "authorities"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT id, kind, scope_id, canonical_url, status, COALESCE(fresh_through::text, '') FROM registry_sources WHERE revision_id = $1 ORDER BY id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry sources: %w", err)
	}
	sourceByID := make(map[string]int)
	for rows.Next() {
		var item domain.PrayerSource
		if err := rows.Scan(&item.ID, &item.Kind, &item.GeographicScopeID, &item.CanonicalURL, &item.Status, &item.FreshThrough); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry source: %w", err)
		}
		sourceByID[item.ID] = len(dataset.Sources)
		dataset.Sources = append(dataset.Sources, item)
	}
	if err := finishRows(rows, "sources"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT source_id, authority_id FROM registry_source_authorities WHERE revision_id = $1 ORDER BY source_id, position`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry source authorities: %w", err)
	}
	for rows.Next() {
		var sourceID, authorityID string
		if err := rows.Scan(&sourceID, &authorityID); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry source authority: %w", err)
		}
		index, ok := sourceByID[sourceID]
		if !ok {
			rows.Close()
			return Dataset{}, fmt.Errorf("%w: source authority references unknown source %q", ErrRevisionInvalid, sourceID)
		}
		dataset.Sources[index].AuthorityIDs = append(dataset.Sources[index].AuthorityIDs, authorityID)
	}
	if err := finishRows(rows, "source authorities"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT id, source_id, scope_id, version, effective_from::text, effective_to::text, approval_id FROM registry_calculation_profiles WHERE revision_id = $1 ORDER BY id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry calculation profiles: %w", err)
	}
	for rows.Next() {
		var item domain.CalculationProfile
		if err := rows.Scan(&item.ID, &item.SourceID, &item.GeographicScopeID, &item.Version, &item.Effective.From, &item.Effective.To, &item.ApprovalID); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry calculation profile: %w", err)
		}
		dataset.CalculationProfiles = append(dataset.CalculationProfiles, item)
	}
	if err := finishRows(rows, "calculation profiles"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT id, source_id, scope_id, mosque_id, timezone, effective_from::text, effective_to::text, published_snapshot_id FROM registry_timetables WHERE revision_id = $1 ORDER BY id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry timetables: %w", err)
	}
	timetableByID := make(map[string]int)
	for rows.Next() {
		var item domain.TimeTable
		if err := rows.Scan(&item.ID, &item.SourceID, &item.GeographicScopeID, &item.MosqueID, &item.Timezone, &item.Effective.From, &item.Effective.To, &item.PublishedSnapshotID); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry timetable: %w", err)
		}
		timetableByID[item.ID] = len(dataset.TimeTables)
		dataset.TimeTables = append(dataset.TimeTables, item)
	}
	if err := finishRows(rows, "timetables"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT id, base_source_id, override_source_id, effective_from::text, effective_to::text, approval_id FROM registry_source_overrides WHERE revision_id = $1 ORDER BY id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry overrides: %w", err)
	}
	overrideByID := make(map[string]int)
	for rows.Next() {
		var item domain.SourceOverride
		if err := rows.Scan(&item.ID, &item.BaseSourceID, &item.OverrideSourceID, &item.Effective.From, &item.Effective.To, &item.ApprovalID); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry override: %w", err)
		}
		overrideByID[item.ID] = len(dataset.SourceOverrides)
		dataset.SourceOverrides = append(dataset.SourceOverrides, item)
	}
	if err := finishRows(rows, "overrides"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT override_id, field_name FROM registry_source_override_fields WHERE revision_id = $1 ORDER BY override_id, field_name`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry override fields: %w", err)
	}
	for rows.Next() {
		var overrideID, field string
		if err := rows.Scan(&overrideID, &field); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry override field: %w", err)
		}
		index, ok := overrideByID[overrideID]
		if !ok {
			rows.Close()
			return Dataset{}, fmt.Errorf("%w: override field references unknown override %q", ErrRevisionInvalid, overrideID)
		}
		dataset.SourceOverrides[index].AppliedFields = append(dataset.SourceOverrides[index].AppliedFields, field)
	}
	if err := finishRows(rows, "override fields"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT timetable_id, override_id FROM registry_timetable_overrides WHERE revision_id = $1 ORDER BY timetable_id, position`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry timetable overrides: %w", err)
	}
	for rows.Next() {
		var timetableID, overrideID string
		if err := rows.Scan(&timetableID, &overrideID); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry timetable override: %w", err)
		}
		index, ok := timetableByID[timetableID]
		if !ok {
			rows.Close()
			return Dataset{}, fmt.Errorf("%w: timetable override references unknown timetable %q", ErrRevisionInvalid, timetableID)
		}
		dataset.TimeTables[index].SourceOverrideIDs = append(dataset.TimeTables[index].SourceOverrideIDs, overrideID)
	}
	if err := finishRows(rows, "timetable overrides"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT id, kind, scope_id, source_id, COALESCE(timetable_id, ''), COALESCE(calculation_profile_id, ''), effective_from::text, effective_to::text, approval_id FROM registry_policies WHERE revision_id = $1 ORDER BY id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry policies: %w", err)
	}
	policyByID := make(map[string]int)
	for rows.Next() {
		var item domain.PrayerPolicy
		if err := rows.Scan(&item.ID, &item.Kind, &item.GeographicScopeID, &item.SourceID, &item.TimeTableID, &item.CalculationProfileID, &item.Effective.From, &item.Effective.To, &item.ApprovalID); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry policy: %w", err)
		}
		policyByID[item.ID] = len(dataset.Policies)
		dataset.Policies = append(dataset.Policies, item)
	}
	if err := finishRows(rows, "policies"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT policy_id, authority_id FROM registry_policy_authorities WHERE revision_id = $1 ORDER BY policy_id, position`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry policy authorities: %w", err)
	}
	for rows.Next() {
		var policyID, authorityID string
		if err := rows.Scan(&policyID, &authorityID); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry policy authority: %w", err)
		}
		index, ok := policyByID[policyID]
		if !ok {
			rows.Close()
			return Dataset{}, fmt.Errorf("%w: policy authority references unknown policy %q", ErrRevisionInvalid, policyID)
		}
		dataset.Policies[index].AuthorityIDs = append(dataset.Policies[index].AuthorityIDs, authorityID)
	}
	if err := finishRows(rows, "policy authorities"); err != nil {
		return Dataset{}, err
	}
	rows, err = querier.Query(ctx, `SELECT policy_id, mosque_id FROM registry_policy_mosques WHERE revision_id = $1 ORDER BY policy_id, mosque_id`, revisionID)
	if err != nil {
		return Dataset{}, fmt.Errorf("load registry policy mosques: %w", err)
	}
	for rows.Next() {
		var policyID, mosqueID string
		if err := rows.Scan(&policyID, &mosqueID); err != nil {
			rows.Close()
			return Dataset{}, fmt.Errorf("load registry policy mosque: %w", err)
		}
		index, ok := policyByID[policyID]
		if !ok {
			rows.Close()
			return Dataset{}, fmt.Errorf("%w: policy mosque references unknown policy %q", ErrRevisionInvalid, policyID)
		}
		dataset.Policies[index].MosqueIDs = append(dataset.Policies[index].MosqueIDs, mosqueID)
	}
	if err := finishRows(rows, "policy mosques"); err != nil {
		return Dataset{}, err
	}
	return dataset, nil
}

func finishRows(rows pgx.Rows, kind string) error {
	defer rows.Close()
	if err := rows.Err(); err != nil {
		return fmt.Errorf("load registry %s: %w", kind, err)
	}
	return nil
}

func (store *PostgresRevisionStore) Activate(ctx context.Context, activation ActivationRecord) error {
	if store == nil || store.pool == nil {
		return ErrRevisionInvalid
	}
	tx, err := store.pool.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.Serializable})
	if err != nil {
		return fmt.Errorf("activate registry revision: begin: %w", err)
	}
	defer rollbackRegistryTx(tx)
	if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, registryAdvisoryLockID); err != nil {
		return fmt.Errorf("activate registry revision: lock: %w", err)
	}
	var contentSHA256 string
	if err := tx.QueryRow(ctx, `SELECT content_sha256 FROM registry_revisions WHERE id = $1`, activation.RevisionID).Scan(&contentSHA256); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrRevisionUnavailable
		}
		return fmt.Errorf("activate registry revision: read target: %w", err)
	}
	currentID := ""
	err = tx.QueryRow(ctx, `SELECT revision_id FROM registry_active_revision WHERE singleton FOR UPDATE`).Scan(&currentID)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return fmt.Errorf("activate registry revision: read active: %w", err)
	}
	if currentID != activation.PreviousRevisionID {
		return fmt.Errorf("%w: active registry changed concurrently", ErrRevisionConflict)
	}
	eventType := "activated"
	if activation.Rollback {
		eventType = "rollback"
	}
	eventID := registryEventID(eventType, activation.RevisionID, activation.PreviousRevisionID, activation.ActorID, activation.Reason, activation.ActivatedAt)
	if _, err := tx.Exec(ctx, `
		INSERT INTO registry_audit_events (
			id, event_type, revision_id, previous_revision_id, actor_id,
			reason, occurred_at, content_sha256
		) VALUES ($1, $2, $3, NULLIF($4, ''), $5, $6, $7, $8)`,
		eventID, eventType, activation.RevisionID, activation.PreviousRevisionID,
		activation.ActorID, activation.Reason, activation.ActivatedAt, contentSHA256,
	); err != nil {
		return mapRegistryWriteError("insert activation audit", err)
	}
	for _, approval := range activation.Approvals {
		if _, err := tx.Exec(ctx, `
			INSERT INTO registry_verified_approvals (
				event_id, revision_id, approval_id, mosque_id, evidence_sha256, verified_at
			) VALUES ($1, $2, $3, $4, $5, $6)`,
			eventID, activation.RevisionID, approval.ID, approval.MosqueID, approval.EvidenceSHA256, approval.VerifiedAt,
		); err != nil {
			return mapRegistryWriteError("insert verified approval", err)
		}
	}
	for _, snapshot := range activation.Snapshots {
		if _, err := tx.Exec(ctx, `
			INSERT INTO registry_verified_snapshots (
				event_id, revision_id, snapshot_id, mosque_id, timezone,
				effective_from, effective_to, payload_sha256, signing_key_id, verified_at
			) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
			eventID, activation.RevisionID, snapshot.ID, snapshot.MosqueID, snapshot.Timezone,
			snapshot.Effective.From, snapshot.Effective.To, snapshot.PayloadSHA256, snapshot.SigningKeyID, snapshot.VerifiedAt,
		); err != nil {
			return mapRegistryWriteError("insert verified snapshot", err)
		}
	}
	if _, err := tx.Exec(ctx, `
		INSERT INTO registry_active_revision (
			singleton, revision_id, previous_revision_id, activated_at, activated_by, reason
		) VALUES (true, $1, NULLIF($2, ''), $3, $4, $5)
		ON CONFLICT (singleton) DO UPDATE SET
			revision_id = EXCLUDED.revision_id,
			previous_revision_id = EXCLUDED.previous_revision_id,
			activated_at = EXCLUDED.activated_at,
			activated_by = EXCLUDED.activated_by,
			reason = EXCLUDED.reason`,
		activation.RevisionID, activation.PreviousRevisionID, activation.ActivatedAt, activation.ActorID, activation.Reason,
	); err != nil {
		return mapRegistryWriteError("update active registry", err)
	}
	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("activate registry revision: commit: %w", err)
	}
	return nil
}

func (store *PostgresRevisionStore) Active(ctx context.Context) (RevisionRecord, Dataset, error) {
	if store == nil || store.pool == nil {
		return RevisionRecord{}, Dataset{}, ErrRevisionUnavailable
	}
	tx, err := store.pool.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.RepeatableRead, AccessMode: pgx.ReadOnly})
	if err != nil {
		return RevisionRecord{}, Dataset{}, fmt.Errorf("load active registry: begin: %w", err)
	}
	defer rollbackRegistryTx(tx)
	var revisionID string
	if err := tx.QueryRow(ctx, `SELECT revision_id FROM registry_active_revision WHERE singleton`).Scan(&revisionID); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return RevisionRecord{}, Dataset{}, ErrRevisionUnavailable
		}
		return RevisionRecord{}, Dataset{}, fmt.Errorf("load active registry: %w", err)
	}
	record, dataset, err := loadRegistryRevision(ctx, tx, revisionID)
	if err != nil {
		return RevisionRecord{}, Dataset{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return RevisionRecord{}, Dataset{}, fmt.Errorf("load active registry: commit: %w", err)
	}
	return record, dataset, nil
}

func (store *PostgresRevisionStore) SearchActiveCities(ctx context.Context, query string) ([]CitySearchResult, error) {
	if store == nil || store.pool == nil {
		return nil, ErrRevisionUnavailable
	}
	normalized := normalizeSearch(query)
	if normalized == "" {
		return nil, nil
	}
	rows, err := store.pool.Query(ctx, `
		SELECT c.id, c.canonical_name,
		       ARRAY(SELECT a.alias FROM registry_city_aliases a
		             WHERE a.revision_id = c.revision_id AND a.city_id = c.id
		             ORDER BY a.normalized_alias),
		       c.country_code, c.region_id, c.settlement_type, c.latitude, c.longitude,
		       c.timezone, c.population, c.geographic_source, c.geographic_source_id,
		       c.geographic_revision, c.geographic_license,
		       COALESCE(c.source_modified_date::text, ''), COALESCE(c.fallback_policy_id, ''),
		       r.id, r.name, r.country_code, r.federal_subject_code
		FROM registry_active_revision active
		JOIN registry_cities c ON c.revision_id = active.revision_id
		JOIN registry_regions r ON r.revision_id = c.revision_id AND r.id = c.region_id
		WHERE active.singleton
		  AND (
		    c.normalized_name = $1 OR EXISTS (
		      SELECT 1 FROM registry_city_aliases match_alias
		      WHERE match_alias.revision_id = c.revision_id
		        AND match_alias.city_id = c.id
		        AND match_alias.normalized_alias = $1
		    )
		  )
		ORDER BY r.federal_subject_code, c.canonical_name, c.id`, normalized)
	if err != nil {
		return nil, fmt.Errorf("search active registry cities: %w", err)
	}
	defer rows.Close()
	var results []CitySearchResult
	for rows.Next() {
		var result CitySearchResult
		if err := rows.Scan(
			&result.City.ID, &result.City.Name, &result.City.Aliases,
			&result.City.CountryCode, &result.City.RegionID, &result.City.SettlementType,
			&result.City.Latitude, &result.City.Longitude, &result.City.Timezone,
			&result.City.Population, &result.City.GeographicSource, &result.City.GeographicSourceID,
			&result.City.GeographicRevision, &result.City.GeographicLicense,
			&result.City.SourceModifiedDate, &result.City.FallbackPolicyID,
			&result.Region.ID, &result.Region.Name, &result.Region.CountryCode, &result.Region.FederalSubjectCode,
		); err != nil {
			return nil, fmt.Errorf("scan active registry city: %w", err)
		}
		results = append(results, result)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("search active registry cities: %w", err)
	}
	return results, nil
}

func (store *PostgresRevisionStore) UniqueActiveCity(ctx context.Context, query string) (CitySearchResult, bool, error) {
	results, err := store.SearchActiveCities(ctx, query)
	if err != nil || len(results) != 1 {
		return CitySearchResult{}, false, err
	}
	return results[0], true, nil
}

func mapRegistryWriteError(operation string, err error) error {
	var postgresError *pgconn.PgError
	if errors.As(err, &postgresError) {
		switch postgresError.Code {
		case "23505", "40001":
			return fmt.Errorf("%s: %w", operation, ErrRevisionConflict)
		case "23503", "23514", "22P02", "22007", "22008":
			return fmt.Errorf("%s: %w", operation, ErrRevisionInvalid)
		}
	}
	return fmt.Errorf("%s: %w", operation, err)
}

func registryEventID(eventType, revisionID, previousID, actorID, reason string, occurredAt time.Time) string {
	digest := sha256.Sum256([]byte(strings.Join([]string{
		eventType, revisionID, previousID, actorID, reason, occurredAt.UTC().Format(time.RFC3339Nano),
	}, "\x00")))
	return hex.EncodeToString(digest[:])
}

func nullableText(value string) any {
	if value == "" {
		return nil
	}
	return value
}

func rollbackRegistryTx(tx pgx.Tx) {
	rollbackContext, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	_ = tx.Rollback(rollbackContext)
}
