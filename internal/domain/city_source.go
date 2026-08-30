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
	ID          string
	Kind        GeographicScopeKind
	CityID      string
	RegionID    string
	Description string
}

type PrayerAuthority struct {
	ID            string
	Name          string
	Branch        string
	Website       string
	EvidenceLabel string
}

type PrayerSource struct {
	ID                string
	Kind              ProviderKind
	AuthorityIDs      []string
	GeographicScopeID string
	CanonicalURL      string
}

type PrayerPolicyKind string

const (
	PrayerPolicyTimeTable          PrayerPolicyKind = "timetable"
	PrayerPolicyCalculationProfile PrayerPolicyKind = "calculation_profile"
)

type PrayerPolicy struct {
	ID                   string
	Kind                 PrayerPolicyKind
	GeographicScopeID    string
	AuthorityIDs         []string
	SourceID             string
	TimeTableID          string
	CalculationProfileID string
	MosqueIDs            []string
	Effective            DateRange
	ApprovalID           string
}

type CalculationProfile struct {
	ID                string
	SourceID          string
	GeographicScopeID string
	Version           string
	Effective         DateRange
	ApprovalID        string
}

type TimeTable struct {
	ID                  string
	SourceID            string
	GeographicScopeID   string
	MosqueID            string
	Timezone            string
	Effective           DateRange
	PublishedSnapshotID string
	SourceOverrideIDs   []string
}

type SourceOverride struct {
	ID               string
	BaseSourceID     string
	OverrideSourceID string
	Effective        DateRange
	AppliedFields    []string
	ApprovalID       string
}
