package domain

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

// ProviderKind identifies how source data is obtained without coupling the
// domain model to a concrete provider implementation.
type ProviderKind string

type DataClassification string

const (
	DataClassificationProduction DataClassification = "production"
	DataClassificationSynthetic  DataClassification = "synthetic"
)

const (
	ProviderKindOfficialAPI        ProviderKind = "official_api"
	ProviderKindOfficialFile       ProviderKind = "official_file"
	ProviderKindOfficialHTML       ProviderKind = "official_html"
	ProviderKindMosqueCalendar     ProviderKind = "mosque_calendar"
	ProviderKindCalculationProfile ProviderKind = "calculation_profile"
	ProviderKindManualImport       ProviderKind = "manual_import"
)

type Snapshot struct {
	SchemaVersion      string             `json:"schema_version"`
	SnapshotID         string             `json:"snapshot_id"`
	DataClassification DataClassification `json:"data_classification"`
	GeneratedAt        string             `json:"generated_at"`
	Mosque             Mosque             `json:"mosque"`
	Source             SourceMetadata     `json:"source"`
	Coverage           DateRange          `json:"coverage"`
	PrayerDays         []PrayerDay        `json:"prayer_days"`
	IqamahRules        []IqamahRule       `json:"iqamah_rules,omitempty"`
	IqamahOverrides    []IqamahOverride   `json:"iqamah_date_overrides,omitempty"`
	JumuahSessions     []JumuahSession    `json:"jumuah_sessions,omitempty"`
	Campaigns          []Campaign         `json:"campaigns,omitempty"`
	Theme              *Theme             `json:"theme,omitempty"`
	Integrity          IntegrityMetadata  `json:"integrity"`
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

type IqamahRule struct {
	ID        string      `json:"id"`
	Prayer    string      `json:"prayer"`
	ValidFrom string      `json:"valid_from"`
	ValidTo   string      `json:"valid_to"`
	Weekdays  []int       `json:"weekdays"`
	Priority  int         `json:"priority"`
	Value     IqamahValue `json:"value"`
	Reason    string      `json:"reason,omitempty"`
}

type IqamahOverride struct {
	Date   string      `json:"date"`
	Prayer string      `json:"prayer"`
	Value  IqamahValue `json:"value"`
	Reason string      `json:"reason,omitempty"`
}

type IqamahValue struct {
	Mode          string `json:"mode"`
	FixedTime     string `json:"fixed_time,omitempty"`
	OffsetMinutes *int   `json:"offset_minutes,omitempty"`
}

type JumuahSession struct {
	ID          string `json:"id"`
	Label       string `json:"label"`
	KhutbahTime string `json:"khutbah_time,omitempty"`
	SalahTime   string `json:"salah_time"`
	ValidFrom   string `json:"valid_from"`
	ValidTo     string `json:"valid_to"`
}

type Campaign struct {
	ID        string `json:"id"`
	Kind      string `json:"kind"`
	URL       string `json:"url"`
	Title     string `json:"title"`
	Subtitle  string `json:"subtitle,omitempty"`
	StartsAt  string `json:"starts_at"`
	EndsAt    string `json:"ends_at"`
	Placement string `json:"placement"`
}

type Theme struct {
	ThemeID        string          `json:"theme_id"`
	LandscapeAsset *AssetReference `json:"landscape_asset,omitempty"`
	PortraitAsset  *AssetReference `json:"portrait_asset,omitempty"`
	OverlayOpacity float64         `json:"overlay_opacity"`
}

type AssetReference struct {
	AssetID    string `json:"asset_id"`
	SHA256     string `json:"sha256"`
	MediaType  string `json:"media_type"`
	ByteLength int64  `json:"byte_length"`
	Width      int    `json:"width"`
	Height     int    `json:"height"`
}

type IntegrityMetadata struct {
	CanonicalSHA256        string `json:"canonical_sha256"`
	SigningKeyID           string `json:"signing_key_id"`
	SignatureEd25519Base64 string `json:"signature_ed25519_base64"`
}

func DecodeSnapshot(data []byte) (Snapshot, error) {
	if !utf8.Valid(data) {
		return Snapshot{}, fmt.Errorf("decode snapshot: invalid UTF-8")
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return Snapshot{}, fmt.Errorf("decode snapshot: ambiguous JSON: %w", err)
	}
	if err := validateSnapshotJSONShape(data); err != nil {
		return Snapshot{}, fmt.Errorf("decode snapshot: %w", err)
	}
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

// validateSnapshotJSONShape preserves JSON Schema distinctions that ordinary
// Go value fields cannot represent, notably a missing numeric zero versus a
// required property set to zero. The contract does not permit explicit nulls.
func validateSnapshotJSONShape(data []byte) error {
	var document any
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.UseNumber()
	if err := decoder.Decode(&document); err != nil {
		return err
	}
	if err := rejectNulls(document, "$"); err != nil {
		return err
	}
	root, ok := document.(map[string]any)
	if !ok {
		return fmt.Errorf("snapshot root must be an object")
	}
	if mosque, exists := root["mosque"].(map[string]any); exists {
		if country, present := mosque["country_code"]; present {
			value, ok := country.(string)
			if !ok || value == "" {
				return fmt.Errorf("mosque.country_code must be a non-empty string when present")
			}
		}
	}
	if source, exists := root["source"].(map[string]any); exists {
		for _, name := range []string{"canonical_url", "calculation_profile"} {
			if value, present := source[name]; present {
				text, ok := value.(string)
				if !ok || text == "" {
					return fmt.Errorf("source.%s must be a non-empty string when present", name)
				}
			}
		}
	}
	if err := validateArrayObjectMembers(root, "iqamah_rules", []string{
		"id", "prayer", "valid_from", "valid_to", "weekdays", "priority", "value",
	}); err != nil {
		return err
	}
	if err := validateArrayObjectMembers(root, "iqamah_date_overrides", []string{"date", "prayer", "value"}); err != nil {
		return err
	}
	if err := validateArrayObjectMembers(root, "jumuah_sessions", []string{"id", "label", "salah_time", "valid_from", "valid_to"}); err != nil {
		return err
	}
	if err := validateArrayObjectMembers(root, "campaigns", []string{"id", "kind", "url", "title", "starts_at", "ends_at", "placement"}); err != nil {
		return err
	}
	for _, field := range []string{"iqamah_rules", "iqamah_date_overrides"} {
		items, _ := root[field].([]any)
		for index, item := range items {
			object, _ := item.(map[string]any)
			if value, exists := object["value"]; exists {
				if err := requireObjectMembers(value, fmt.Sprintf("%s[%d].value", field, index), "mode"); err != nil {
					return err
				}
			}
		}
	}
	if theme, exists := root["theme"]; exists {
		if err := requireObjectMembers(theme, "theme", "theme_id", "overlay_opacity"); err != nil {
			return err
		}
		object, _ := theme.(map[string]any)
		for _, assetName := range []string{"landscape_asset", "portrait_asset"} {
			if asset, present := object[assetName]; present {
				if err := requireObjectMembers(asset, "theme."+assetName, "asset_id", "sha256", "media_type", "byte_length", "width", "height"); err != nil {
					return err
				}
			}
		}
	}
	return nil
}

func rejectNulls(value any, path string) error {
	switch typed := value.(type) {
	case nil:
		return fmt.Errorf("%s: explicit null is not allowed", path)
	case map[string]any:
		for name, child := range typed {
			if err := rejectNulls(child, path+"."+name); err != nil {
				return err
			}
		}
	case []any:
		for index, child := range typed {
			if err := rejectNulls(child, fmt.Sprintf("%s[%d]", path, index)); err != nil {
				return err
			}
		}
	}
	return nil
}

func validateArrayObjectMembers(root map[string]any, field string, members []string) error {
	value, exists := root[field]
	if !exists {
		return nil
	}
	items, ok := value.([]any)
	if !ok {
		return fmt.Errorf("%s must be an array", field)
	}
	for index, item := range items {
		if err := requireObjectMembers(item, fmt.Sprintf("%s[%d]", field, index), members...); err != nil {
			return err
		}
	}
	return nil
}

func requireObjectMembers(value any, path string, members ...string) error {
	object, ok := value.(map[string]any)
	if !ok {
		return fmt.Errorf("%s must be an object", path)
	}
	for _, member := range members {
		if _, exists := object[member]; !exists {
			return fmt.Errorf("%s.%s is required", path, member)
		}
	}
	return nil
}
