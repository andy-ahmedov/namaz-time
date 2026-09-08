package domain

// City is geographic catalog data. It identifies a place but does not grant
// any prayer authority or select a prayer policy.
type City struct {
	ID                 string   `json:"id"`
	Name               string   `json:"name"`
	Aliases            []string `json:"aliases,omitempty"`
	CountryCode        string   `json:"country_code"`
	RegionID           string   `json:"region_id"`
	SettlementType     string   `json:"settlement_type"`
	Latitude           float64  `json:"latitude"`
	Longitude          float64  `json:"longitude"`
	Timezone           string   `json:"timezone"`
	Population         int64    `json:"population,omitempty"`
	GeographicSource   string   `json:"geographic_source"`
	GeographicSourceID string   `json:"geographic_source_id"`
	GeographicRevision string   `json:"geographic_revision"`
	GeographicLicense  string   `json:"geographic_license"`
	SourceModifiedDate string   `json:"source_modified_date,omitempty"`
	FallbackPolicyID   string   `json:"fallback_policy_id,omitempty"`
}

type Region struct {
	ID                 string `json:"id"`
	Name               string `json:"name"`
	CountryCode        string `json:"country_code"`
	FederalSubjectCode string `json:"federal_subject_code"`
}

type GeographicScopeKind string

const (
	GeographicScopeCity   GeographicScopeKind = "city"
	GeographicScopeRegion GeographicScopeKind = "region"
)

// GeographicScope selects where a policy may apply. Authority is always a
// separate explicit reference and is never inferred from polygon membership.
type GeographicScope struct {
	ID          string              `json:"id"`
	Kind        GeographicScopeKind `json:"kind"`
	CityID      string              `json:"city_id,omitempty"`
	RegionID    string              `json:"region_id"`
	Description string              `json:"description"`
}

type PrayerAuthority struct {
	ID            string `json:"id"`
	Name          string `json:"name"`
	Branch        string `json:"branch,omitempty"`
	Website       string `json:"website,omitempty"`
	EvidenceLabel string `json:"evidence_label"`
}

type PrayerSource struct {
	ID                string             `json:"id"`
	Kind              ProviderKind       `json:"kind"`
	AuthorityIDs      []string           `json:"authority_ids"`
	GeographicScopeID string             `json:"geographic_scope_id"`
	CanonicalURL      string             `json:"canonical_url,omitempty"`
	Status            PrayerSourceStatus `json:"status"`
	FreshThrough      string             `json:"fresh_through,omitempty"`
	QualificationID   string             `json:"qualification_id,omitempty"`
}

type PrayerSourceStatus string

const (
	PrayerSourceResearchOnly PrayerSourceStatus = "research_only"
	PrayerSourceApproved     PrayerSourceStatus = "approved"
	PrayerSourceQualified    PrayerSourceStatus = "qualified"
	PrayerSourceStale        PrayerSourceStatus = "stale"
	PrayerSourceUnavailable  PrayerSourceStatus = "unavailable"
)

type PrayerPolicyKind string

const (
	PrayerPolicyTimeTable          PrayerPolicyKind = "timetable"
	PrayerPolicyCalculationProfile PrayerPolicyKind = "calculation_profile"
)

type PrayerPolicy struct {
	ID                   string           `json:"id"`
	Kind                 PrayerPolicyKind `json:"kind"`
	GeographicScopeID    string           `json:"geographic_scope_id"`
	AuthorityIDs         []string         `json:"authority_ids"`
	SourceID             string           `json:"source_id"`
	TimeTableID          string           `json:"timetable_id,omitempty"`
	CalculationProfileID string           `json:"calculation_profile_id,omitempty"`
	MosqueIDs            []string         `json:"mosque_ids"`
	Effective            DateRange        `json:"effective"`
	ApprovalID           string           `json:"approval_id,omitempty"`
	QualificationID      string           `json:"qualification_id,omitempty"`
}

type CalculationProfile struct {
	ID                string    `json:"id"`
	SourceID          string    `json:"source_id"`
	GeographicScopeID string    `json:"geographic_scope_id"`
	Version           string    `json:"version"`
	Effective         DateRange `json:"effective"`
	ApprovalID        string    `json:"approval_id"`
}

type TimeTable struct {
	ID                  string    `json:"id"`
	SourceID            string    `json:"source_id"`
	GeographicScopeID   string    `json:"geographic_scope_id"`
	MosqueID            string    `json:"mosque_id"`
	Timezone            string    `json:"timezone"`
	Effective           DateRange `json:"effective"`
	PublishedSnapshotID string    `json:"published_snapshot_id"`
	SourceOverrideIDs   []string  `json:"source_override_ids,omitempty"`
}

type SourceOverride struct {
	ID               string    `json:"id"`
	BaseSourceID     string    `json:"base_source_id"`
	OverrideSourceID string    `json:"override_source_id"`
	Effective        DateRange `json:"effective"`
	AppliedFields    []string  `json:"applied_fields"`
	ApprovalID       string    `json:"approval_id"`
}
