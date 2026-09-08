// Package onboarding joins reviewed public-source evidence, a pinned canonical
// catalog and a strict parser. It does not retrieve, sign or activate data.
package onboarding

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/providers/cdum"
	"github.com/andy-ahmedov/namaz-time/internal/providers/controlled"
	"github.com/andy-ahmedov/namaz-time/internal/providers/dumrt"
	"github.com/andy-ahmedov/namaz-time/internal/providers/kbr"
	"github.com/andy-ahmedov/namaz-time/internal/providers/omsk"
	"github.com/andy-ahmedov/namaz-time/internal/providers/sochi"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
)

const ManifestSchema = "namaztime-public-source-import/v1"
const KBRExtractionMethod = "pymupdf/1.28.2:text:sort=false:join=form-feed"

type Extraction struct {
	SHA256 string `json:"sha256"`
	Method string `json:"method"`
}

type Manifest struct {
	SchemaVersion        string                       `json:"schema_version"`
	DataClassification   domain.DataClassification    `json:"data_classification"`
	ParserVersion        string                       `json:"parser_version"`
	SourceID             string                       `json:"source_id"`
	SourceKind           domain.ProviderKind          `json:"source_kind"`
	CanonicalURL         string                       `json:"canonical_url"`
	Locality             string                       `json:"locality,omitempty"`
	Coverage             domain.DateRange             `json:"coverage"`
	Artifact             domain.RawArtifact           `json:"artifact"`
	Extraction           *Extraction                  `json:"extraction,omitempty"`
	CatalogContentSHA256 string                       `json:"catalog_content_sha256"`
	Attribution          string                       `json:"attribution,omitempty"`
	MaxDeltaMinutes      int                          `json:"max_delta_minutes"`
	Review               qualification.EvidenceReview `json:"evidence_review"`
}

type Inspection struct {
	Previous      *domain.CandidateSchedule  `json:"previous_candidate,omitempty"`
	Candidate     domain.CandidateSchedule   `json:"candidate"`
	Diff          publication.DiffReport     `json:"diff"`
	Qualification domain.SourceQualification `json:"qualification"`
}

func Inspect(manifest Manifest, catalog geography.Catalog, raw, extracted []byte, previous *domain.CandidateSchedule, at time.Time) (Inspection, error) {
	if manifest.SchemaVersion != ManifestSchema || at.IsZero() || manifest.SourceID == "" || len(manifest.SourceID) > 128 || strings.TrimSpace(manifest.SourceID) != manifest.SourceID ||
		len(raw) == 0 || len(raw) > 48*1024*1024 || manifest.Artifact.ByteLength != int64(len(raw)) || manifest.Artifact.SHA256 != artifactSHA256(raw) {
		return Inspection{}, errors.New("public source import: manifest or retained raw artifact identity differs")
	}
	encoded, err := geography.Encode(catalog)
	if err != nil {
		return Inspection{}, fmt.Errorf("public source import: encode catalog: %w", err)
	}
	validated, err := geography.DecodeCatalog(encoded)
	if err != nil {
		return Inspection{}, fmt.Errorf("public source import: validate catalog: %w", err)
	}
	if manifest.CatalogContentSHA256 != validated.Revision.ContentSHA256 || manifest.Review.CatalogRevision != validated.Revision.ID {
		return Inspection{}, errors.New("public source import: evidence targets another canonical catalog")
	}
	binding := qualification.CatalogBinding{Revision: validated.Revision.ID}
	for _, region := range validated.Regions {
		if region.ID == manifest.Review.Scope.RegionID {
			binding.Region = region
			break
		}
	}
	for _, city := range validated.Cities {
		if city.RegionID != binding.Region.ID || (manifest.Review.Scope.Kind == domain.GeographicScopeCity && city.ID != manifest.Review.Scope.CityID) {
			continue
		}
		if len(binding.Cities) == 0 {
			binding.SourceRevision = city.GeographicRevision
		}
		binding.Cities = append(binding.Cities, city)
	}
	context, err := qualification.PublicDisplayContext(manifest.Review.Scope, binding, manifest.Review.Timezone)
	if err != nil {
		return Inspection{}, err
	}
	days, transcriptionSHA, err := parse(manifest, binding, raw, extracted)
	if err != nil {
		return Inspection{}, fmt.Errorf("public source import %q: %w", manifest.SourceID, err)
	}
	candidate := domain.CandidateSchedule{
		DataClassification: manifest.DataClassification, Mosque: context,
		Source:   domain.CandidateSource{SourceID: manifest.SourceID, Kind: manifest.SourceKind, AuthorityName: manifest.Review.Authority.Name, AuthorityBranch: manifest.Review.Authority.Branch, GeographicScope: manifest.Review.Scope.Description, CanonicalURL: manifest.CanonicalURL, Attribution: manifest.Attribution, MaxDeltaMinutes: manifest.MaxDeltaMinutes, MinimumCoverageDays: len(days)},
		Artifact: manifest.Artifact, TranscriptionSHA256: transcriptionSHA, ParserVersion: manifest.ParserVersion, Coverage: manifest.Coverage, Days: days, Status: domain.CandidateNeedsReview,
	}
	candidate.Validation = controlled.ValidatePublicCandidateData(controlled.ValidationConfig{SourceCountryCode: "RU", SourceTimezone: manifest.Review.Timezone}, candidate)
	if len(candidate.Validation.Errors) != 0 {
		return Inspection{}, fmt.Errorf("public source import: blocking data validation: %s at %s", candidate.Validation.Errors[0].Code, candidate.Validation.Errors[0].Path)
	}
	if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
		return Inspection{}, err
	}
	diff, err := publication.Diff(previous, candidate)
	if err != nil {
		return Inspection{}, err
	}
	q, err := qualification.Create(manifest.Review, candidate, raw, diff.SHA256, binding, at)
	if err != nil {
		return Inspection{}, err
	}
	return Inspection{Previous: previous, Candidate: candidate, Diff: diff, Qualification: q}, nil
}

func parse(m Manifest, binding qualification.CatalogBinding, raw, extracted []byte) ([]domain.CandidatePrayerDay, string, error) {
	transcriptionSHA := m.Artifact.SHA256
	if m.ParserVersion != kbr.ParserVersion && (m.Extraction != nil || len(extracted) != 0) {
		return nil, "", errors.New("unexpected separate extraction for native data format")
	}
	isCity := m.Review.Scope.Kind == domain.GeographicScopeCity && len(binding.Cities) == 1
	if m.ParserVersion != kbr.ParserVersion && (!isCity || !exactLocality(m.Locality, binding.Cities[0])) {
		return nil, "", errors.New("parser requires its explicitly bound canonical city or exact catalog alias")
	}
	var days []domain.CandidatePrayerDay
	var err error
	switch m.ParserVersion {
	case dumrt.ParserVersion:
		if binding.Region.FederalSubjectCode != "RU-TA" || m.SourceKind != domain.ProviderKindOfficialFile {
			return nil, "", errors.New("DUM RT locality CSV requires exact Tatarstan city scope and official_file kind")
		}
		days, err = dumrt.ParseCSVRange(raw, m.Coverage)
	case cdum.ParserVersion:
		if m.SourceKind != domain.ProviderKindOfficialHTML {
			return nil, "", errors.New("CDUM city HTML requires official_html kind")
		}
		days, err = cdum.ParseHTML(raw, m.Locality, m.Coverage)
	case omsk.ParserVersion:
		if binding.Region.FederalSubjectCode != "RU-OMS" || m.Locality != omsk.SourceCity || m.Review.Timezone != omsk.SourceTimezone || m.SourceKind != domain.ProviderKindOfficialFile {
			return nil, "", errors.New("Omsk JSON requires exact Omsk official_file and timezone binding")
		}
		days, err = omsk.ParseJSON(raw, m.Coverage)
	case sochi.ParserVersion:
		if binding.Region.FederalSubjectCode != "RU-KDA" || m.Locality != sochi.SourceCity || m.Review.Timezone != sochi.SourceTimezone || m.SourceKind != domain.ProviderKindOfficialFile {
			return nil, "", errors.New("Sochi workbook requires exact Sochi official_file and timezone binding")
		}
		days, err = sochi.ParseXLSX(raw, m.Coverage)
	case kbr.ParserVersion:
		if m.Review.Scope.Kind != domain.GeographicScopeRegion || binding.Region.FederalSubjectCode != "RU-KB" || m.Locality != "" || m.Review.Timezone != "Europe/Moscow" || m.SourceKind != domain.ProviderKindOfficialFile {
			return nil, "", errors.New("KBR annual PDF requires its explicit republic-wide official_file scope")
		}
		if m.Extraction == nil || m.Extraction.Method != KBRExtractionMethod || len(extracted) == 0 || len(extracted) > 1024*1024 || !bytes.HasPrefix(raw, []byte("%PDF-")) || artifactSHA256(extracted) != m.Extraction.SHA256 {
			return nil, "", errors.New("KBR PDF requires retained raw PDF and exact hash-bound pinned-method text extraction")
		}
		transcriptionSHA = m.Extraction.SHA256
		days, err = kbr.ParseText(extracted, m.Coverage)
	default:
		return nil, "", errors.New("unsupported public source adapter; no calculation or generic fallback")
	}
	return days, transcriptionSHA, err
}

func exactLocality(label string, city domain.City) bool {
	if label == "" {
		return false
	}
	if label == city.Name {
		return true
	}
	for _, alias := range city.Aliases {
		if alias == label {
			return true
		}
	}
	return false
}

func artifactSHA256(raw []byte) string { sum := sha256.Sum256(raw); return hex.EncodeToString(sum[:]) }
