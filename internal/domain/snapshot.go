package domain

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
)

// ProviderKind identifies how source data is obtained without coupling the
// domain model to a concrete provider implementation.
type ProviderKind string

const (
	ProviderKindOfficialAPI        ProviderKind = "official_api"
	ProviderKindOfficialFile       ProviderKind = "official_file"
	ProviderKindOfficialHTML       ProviderKind = "official_html"
	ProviderKindMosqueCalendar     ProviderKind = "mosque_calendar"
	ProviderKindCalculationProfile ProviderKind = "calculation_profile"
	ProviderKindManualImport       ProviderKind = "manual_import"
)

type Snapshot struct {
	SchemaVersion      string            `json:"schema_version"`
	SnapshotID         string            `json:"snapshot_id"`
	DataClassification string            `json:"data_classification"`
	GeneratedAt        string            `json:"generated_at"`
	Mosque             Mosque            `json:"mosque"`
	Source             SourceMetadata    `json:"source"`
	Coverage           DateRange         `json:"coverage"`
	PrayerDays         []PrayerDay       `json:"prayer_days"`
	IqamahRules        []json.RawMessage `json:"iqamah_rules,omitempty"`
	IqamahOverrides    []json.RawMessage `json:"iqamah_date_overrides,omitempty"`
	JumuahSessions     []json.RawMessage `json:"jumuah_sessions,omitempty"`
	Campaigns          []json.RawMessage `json:"campaigns,omitempty"`
	Theme              json.RawMessage   `json:"theme,omitempty"`
	Integrity          IntegrityMetadata `json:"integrity"`
}

type Mosque struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	CountryCode string `json:"country_code,omitempty"`
	Region      string `json:"region,omitempty"`
	Locality    string `json:"locality,omitempty"`
	Timezone    string `json:"timezone"`
}

type SourceMetadata struct {
	SourceID           string       `json:"source_id"`
	Kind               ProviderKind `json:"kind"`
	AuthorityName      string       `json:"authority_name"`
	AuthorityBranch    string       `json:"authority_branch,omitempty"`
	GeographicScope    string       `json:"geographic_scope"`
	CanonicalURL       string       `json:"canonical_url,omitempty"`
	RetrievedAt        string       `json:"retrieved_at"`
	EffectiveFrom      string       `json:"effective_from"`
	EffectiveTo        string       `json:"effective_to"`
	RawSHA256          string       `json:"raw_sha256"`
	ParserVersion      string       `json:"parser_version"`
	CalculationProfile string       `json:"calculation_profile,omitempty"`
	LicenseReference   string       `json:"license_reference,omitempty"`
	Attribution        string       `json:"attribution,omitempty"`
	Approval           Approval     `json:"approval"`
}

type Approval struct {
	Status     string `json:"status"`
	ID         string `json:"approval_id"`
	ApprovedBy string `json:"approved_by"`
	ApprovedAt string `json:"approved_at"`
	Scope      string `json:"approval_scope"`
	Note       string `json:"note,omitempty"`
}

type DateRange struct {
	From string `json:"from"`
	To   string `json:"to"`
}

type PrayerDay struct {
	Date             string   `json:"date"`
	Fajr             string   `json:"fajr"`
	Sunrise          string   `json:"sunrise"`
	Dhuhr            string   `json:"dhuhr"`
	Asr              string   `json:"asr"`
	Maghrib          string   `json:"maghrib"`
	Isha             string   `json:"isha"`
	Duha             string   `json:"duha,omitempty"`
	MiddleOfNight    string   `json:"middle_of_night,omitempty"`
	LastThirdOfNight string   `json:"last_third_of_night,omitempty"`
	Flags            []string `json:"flags,omitempty"`
}

type IntegrityMetadata struct {
	CanonicalSHA256        string `json:"canonical_sha256"`
	SigningKeyID           string `json:"signing_key_id"`
	SignatureEd25519Base64 string `json:"signature_ed25519_base64"`
}

func DecodeSnapshot(data []byte) (Snapshot, error) {
	var snapshot Snapshot
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&snapshot); err != nil {
		return Snapshot{}, fmt.Errorf("decode snapshot: %w", err)
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		if err == nil {
			return Snapshot{}, fmt.Errorf("decode snapshot: multiple JSON values")
		}
		return Snapshot{}, fmt.Errorf("decode snapshot trailing data: %w", err)
	}
	return snapshot, nil
}
