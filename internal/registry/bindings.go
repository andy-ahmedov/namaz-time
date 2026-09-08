package registry

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

const policyBindingsSchemaVersion = "namaztime-policy-bindings/v1"
const qualifiedPolicyBindingsSchemaVersion = "namaztime-policy-bindings/v2"

type PolicyBindingsRevision struct {
	ID                   string    `json:"id"`
	SchemaVersion        int       `json:"schema_version"`
	ParentRevisionID     string    `json:"parent_revision_id,omitempty"`
	CatalogRevisionID    string    `json:"catalog_revision_id"`
	CatalogContentSHA256 string    `json:"catalog_content_sha256"`
	CreatedAt            time.Time `json:"created_at"`
	CreatedBy            string    `json:"created_by"`
	Reason               string    `json:"reason"`
}

type PolicyBindings struct {
	Qualifications      []domain.SourceQualification `json:"qualifications,omitempty"`
	SchemaVersion       string                       `json:"schema_version"`
	RegistryRevision    PolicyBindingsRevision       `json:"registry_revision"`
	Scopes              []domain.GeographicScope     `json:"scopes"`
	Authorities         []domain.PrayerAuthority     `json:"authorities"`
	Sources             []domain.PrayerSource        `json:"sources"`
	Policies            []domain.PrayerPolicy        `json:"policies"`
	CalculationProfiles []domain.CalculationProfile  `json:"calculation_profiles"`
	TimeTables          []domain.TimeTable           `json:"timetables"`
	SourceOverrides     []domain.SourceOverride      `json:"source_overrides"`
}

func DecodePolicyBindings(data []byte) (PolicyBindings, error) {
	var bindings PolicyBindings
	if err := decodeRegistryJSON(data, &bindings); err != nil {
		return PolicyBindings{}, fmt.Errorf("decode policy bindings: %w", err)
	}
	revision := bindings.RegistryRevision
	if !validPolicyBindingsSchema(bindings) ||
		!validAuditText(revision.ID, 160) || !validAuditText(revision.CatalogRevisionID, 200) || !validSHA256(revision.CatalogContentSHA256) ||
		!validAuditText(revision.CreatedBy, 160) || !validAuditText(revision.Reason, 1000) || revision.CreatedAt.IsZero() ||
		!isCanonicalUTC(revision.CreatedAt) || (revision.ParentRevisionID != "" && !validAuditText(revision.ParentRevisionID, 160)) {
		return PolicyBindings{}, fmt.Errorf("decode policy bindings: %w: invalid revision metadata", ErrRevisionInvalid)
	}
	for _, q := range bindings.Qualifications {
		if err := q.Validate(); err != nil || q.CatalogRevision != revision.CatalogRevisionID {
			return PolicyBindings{}, fmt.Errorf("decode policy bindings: %w: invalid qualification %q", ErrRevisionInvalid, q.ID)
		}
	}
	return bindings, nil
}

func ComposeCatalog(catalog geography.Catalog, bindings PolicyBindings) (RevisionRecord, Dataset, error) {
	encoded, err := geography.Encode(catalog)
	if err != nil {
		return RevisionRecord{}, Dataset{}, fmt.Errorf("compose registry catalog: %w", err)
	}
	validated, err := geography.DecodeCatalog(encoded)
	if err != nil {
		return RevisionRecord{}, Dataset{}, fmt.Errorf("compose registry catalog: %w", err)
	}
	revision := bindings.RegistryRevision
	if !validPolicyBindingsSchema(bindings) ||
		revision.CatalogRevisionID != validated.Revision.ID || revision.CatalogContentSHA256 != validated.Revision.ContentSHA256 {
		return RevisionRecord{}, Dataset{}, fmt.Errorf("%w: policy bindings target another geographic catalog", ErrRevisionInvalid)
	}
	dataset := Dataset{
		Qualifications: append([]domain.SourceQualification(nil), bindings.Qualifications...),
		Cities:         append([]domain.City(nil), validated.Cities...), Regions: append([]domain.Region(nil), validated.Regions...),
		Scopes: append([]domain.GeographicScope(nil), bindings.Scopes...), Authorities: append([]domain.PrayerAuthority(nil), bindings.Authorities...),
		Sources: append([]domain.PrayerSource(nil), bindings.Sources...), Policies: append([]domain.PrayerPolicy(nil), bindings.Policies...),
		CalculationProfiles: append([]domain.CalculationProfile(nil), bindings.CalculationProfiles...),
		TimeTables:          append([]domain.TimeTable(nil), bindings.TimeTables...), SourceOverrides: append([]domain.SourceOverride(nil), bindings.SourceOverrides...),
	}
	dataset = cloneDataset(dataset)
	if err := validateRevisionQualification(RevisionRecord{SchemaVersion: revision.SchemaVersion, CatalogRevisionID: revision.CatalogRevisionID}, dataset); err != nil {
		return RevisionRecord{}, Dataset{}, err
	}
	if _, err := New(dataset); err != nil {
		return RevisionRecord{}, Dataset{}, fmt.Errorf("%w: %v", ErrRevisionInvalid, err)
	}
	contentSHA256, err := DatasetSHA256(dataset)
	if err != nil {
		return RevisionRecord{}, Dataset{}, fmt.Errorf("%w: hash composed dataset: %v", ErrRevisionInvalid, err)
	}
	return RevisionRecord{
		ID: revision.ID, SchemaVersion: revision.SchemaVersion, ParentRevisionID: revision.ParentRevisionID,
		CatalogRevisionID: revision.CatalogRevisionID, ContentSHA256: contentSHA256,
		CreatedAt: revision.CreatedAt, CreatedBy: revision.CreatedBy, Reason: revision.Reason,
	}, dataset, nil
}

func validPolicyBindingsSchema(bindings PolicyBindings) bool {
	if len(bindings.Qualifications) == 0 {
		return bindings.SchemaVersion == policyBindingsSchemaVersion && bindings.RegistryRevision.SchemaVersion == RegistrySchemaVersion
	}
	return bindings.SchemaVersion == qualifiedPolicyBindingsSchemaVersion && bindings.RegistryRevision.SchemaVersion == QualifiedRegistrySchemaVersion
}

func decodeRegistryJSON(data []byte, target any) error {
	if len(data) == 0 || len(data) > 8*1024*1024 || !utf8.Valid(data) {
		return errors.New("JSON size or encoding is invalid")
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return err
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		if err == nil {
			return errors.New("multiple JSON values")
		}
		return err
	}
	return nil
}

func sha256Hex(data []byte) string {
	digest := sha256.Sum256(data)
	return hex.EncodeToString(digest[:])
}
