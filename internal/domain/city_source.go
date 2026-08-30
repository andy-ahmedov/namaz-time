package domain

// City is geographic catalog data. It identifies a place but does not grant
// any prayer authority or select a prayer policy.
type City struct {
	ID                string
	Name              string
	Aliases           []string
	CountryCode       string
	RegionID          string
	Latitude          float64
	Longitude         float64
	Timezone          string
	GeographicSource  string
	GeographicLicense string
	FallbackPolicyID  string
}

type Region struct {
	ID                 string
	Name               string
	CountryCode        string
	FederalSubjectCode string
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
