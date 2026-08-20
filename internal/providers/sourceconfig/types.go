// Package sourceconfig contains the contract-aligned source registry types
// shared by provider adapters. Each adapter remains responsible for enforcing
// its own source-kind and retrieval policy at its trust boundary.
package sourceconfig

import "github.com/andy-ahmedov/namaz-time/internal/domain"

type GeographicScope struct {
	CountryCode string   `json:"country_code"`
	RegionCodes []string `json:"region_codes,omitempty"`
	LocalityIDs []string `json:"locality_ids,omitempty"`
	Description string   `json:"description"`
}

type TimezonePolicy struct {
	Mode         string `json:"mode"`
	IANATimezone string `json:"iana_timezone,omitempty"`
}

type RetrievalPolicy struct {
	Mode              string `json:"mode"`
	Cadence           string `json:"cadence"`
	StaleAfterHours   int    `json:"stale_after_hours"`
	HonorETag         bool   `json:"honor_etag"`
	HonorLastModified bool   `json:"honor_last_modified"`
	RateLimitNote     string `json:"rate_limit_note,omitempty"`
}

type Permission struct {
	Status            string `json:"status"`
	LicenseReference  string `json:"license_reference,omitempty"`
	AttributionText   string `json:"attribution_text,omitempty"`
	EvidenceReference string `json:"evidence_reference,omitempty"`
	ExpiresOn         string `json:"expires_on,omitempty"`
}

type ValidationPolicy struct {
	ApprovalRequired         bool   `json:"approval_required"`
	MaxAutomaticDeltaMinutes int    `json:"max_automatic_delta_minutes"`
	MinimumCoverageDays      int    `json:"minimum_coverage_days,omitempty"`
	FixtureSet               string `json:"fixture_set,omitempty"`
}

type SourceRecord struct {
	SourceID        string              `json:"source_id"`
	Kind            domain.ProviderKind `json:"kind"`
	AuthorityName   string              `json:"authority_name"`
	AuthorityBranch string              `json:"authority_branch,omitempty"`
	CanonicalURL    string              `json:"canonical_url,omitempty"`
	Contact         string              `json:"contact,omitempty"`
	GeographicScope GeographicScope     `json:"geographic_scope"`
	TimezonePolicy  TimezonePolicy      `json:"timezone_policy"`
	Retrieval       RetrievalPolicy     `json:"retrieval"`
	Permission      Permission          `json:"permission"`
	Validation      ValidationPolicy    `json:"validation"`
	Status          string              `json:"status"`
}
