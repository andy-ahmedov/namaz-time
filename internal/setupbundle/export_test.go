package setupbundle

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
	"github.com/andy-ahmedov/namaz-time/internal/providers/controlled"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
	"github.com/andy-ahmedov/namaz-time/internal/trust"
)

func TestExportAdmitsSignedPublicChoicesAndPreservesAllCatalogPlaces(t *testing.T) {
	f := newSyntheticExport(t)
	manifest, err := exportAt(t.Context(), f.config, f.now, f.anchors)
	if err != nil {
		t.Fatalf("export: %v", err)
	}
	if manifest.RegistryRevision.SchemaVersion != 2 || manifest.RegistryState != "active" ||
		manifest.Catalog.CityCount != 3 || manifest.Catalog.RegionCount != 2 || manifest.Catalog.AliasCount != 7 ||
		manifest.Catalog.SearchNameCount != 10 || len(manifest.Files) != 9 {
		t.Fatalf("incomplete manifest: %+v", manifest)
	}
	var choices Choices
	readTestJSON(t, filepath.Join(f.config.OutputDirectory, "choices.json"), &choices)
	if len(choices.Policies) != 3 || len(choices.Bindings) != 6 {
		t.Fatalf("lossy projection: %+v", choices)
	}
	for _, day := range []string{"2026-09-08", "2026-09-09", "2026-09-10"} {
		var ids []string
		for _, binding := range choices.Bindings {
			if binding.CityID == "city-a" && binding.Effective.From <= day && day <= binding.Effective.To {
				ids = append(ids, binding.PolicyID)
			}
			if binding.CityID == "city-c" {
				t.Fatal("unavailable city gained a neighboring or capital fallback")
			}
		}
		want := "policy-a-region"
		if day == "2026-09-09" {
			want = "policy-a-city"
		}
		if len(ids) != 2 || !containsTest(ids, want) || !containsTest(ids, "policy-b-region") {
			t.Fatalf("choices for %s: %v", day, ids)
		}
	}
	for _, policy := range choices.Policies {
		if policy.Qualification == nil || policy.Policy.ApprovalID != "" || len(policy.Policy.MosqueIDs) != 0 {
			t.Fatal("public choice acquired a human/mosque approval")
		}
		got, err := os.ReadFile(filepath.Join(f.config.OutputDirectory, policy.Snapshot.Path))
		if err != nil || !bytes.Equal(got, f.snapshots[policy.Snapshot.SnapshotID]) {
			t.Fatal("signed snapshot bytes were rewritten")
		}
	}
	second := f.config
	second.OutputDirectory += "-repeat"
	again, err := exportAt(t.Context(), second, f.now, f.anchors)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(manifest, again) {
		t.Fatal("identical pinned inputs and clock produced a different manifest or SQLite hash")
	}
	if _, err := exportAt(t.Context(), f.config, f.now, f.anchors); err == nil {
		t.Fatal("existing output directory was overwritten")
	}
}

func TestPublicExportCannotUseSelfDeclaredTestTrust(t *testing.T) {
	f := newSyntheticExport(t)
	if _, err := Export(t.Context(), f.config); err == nil {
		t.Fatal("public API admitted self-declared alternate production keys")
	}
	assertNoOutput(t, f.config.OutputDirectory)
}

func TestExportRejectsExpiredQualificationWithoutCreatingOutput(t *testing.T) {
	f := newSyntheticExport(t)
	if _, err := exportAt(t.Context(), f.config, f.now.AddDate(0, 0, 3), f.anchors); err == nil {
		t.Fatal("stale qualification exported as current")
	}
	assertNoOutput(t, f.config.OutputDirectory)
}

func TestExportRejectsPinnedInputDrift(t *testing.T) {
	for _, name := range []string{"catalog", "bindings", "artifacts"} {
		t.Run(name, func(t *testing.T) {
			f := newSyntheticExport(t)
			pin := map[string]PinnedInput{"catalog": f.config.Catalog, "bindings": f.config.Bindings, "artifacts": f.config.Artifacts}[name]
			file, err := os.OpenFile(pin.Path, os.O_APPEND|os.O_WRONLY, 0)
			if err != nil {
				t.Fatal(err)
			}
			if _, err := file.WriteString(" "); err != nil {
				t.Fatal(err)
			}
			if err := file.Close(); err != nil {
				t.Fatal(err)
			}
			if _, err := exportAt(t.Context(), f.config, f.now, f.anchors); err == nil {
				t.Fatal("drifting pinned input accepted")
			}
			assertNoOutput(t, f.config.OutputDirectory)
		})
	}
}

func TestExportSyntheticAndroidFixtureOptIn(t *testing.T) {
	output := os.Getenv("NAMAZTIME_LOCAL_SETUP_FIXTURE_OUTPUT")
	if output == "" {
		t.Skip("set NAMAZTIME_LOCAL_SETUP_FIXTURE_OUTPUT to an existing outside-Git directory")
	}
	if !filepath.IsAbs(output) {
		t.Fatal("fixture output must be absolute")
	}
	f := newSyntheticExport(t)
	f.config.OutputDirectory = filepath.Join(output, "bundle")
	manifest, err := exportAt(t.Context(), f.config, f.now, f.anchors)
	if err != nil {
		t.Fatal(err)
	}
	// Only public synthetic fixture anchors leave the process, never its private key.
	metadata := map[string]any{"fixture_kind": "synthetic_protocol_test_only", "anchors": f.anchors, "now": f.now, "bundle_id": manifest.BundleID}
	raw, err := json.MarshalIndent(metadata, "", "  ")
	if err != nil {
		t.Fatal(err)
	}
	file, err := os.OpenFile(filepath.Join(output, "synthetic-fixture.json"), os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)
	if err != nil {
		t.Fatal(err)
	}
	_, writeErr := file.Write(raw)
	closeErr := file.Close()
	if writeErr != nil || closeErr != nil {
		t.Fatalf("fixture metadata write=%v close=%v", writeErr, closeErr)
	}
}

type syntheticExport struct {
	config     Config
	now        time.Time
	anchors    trustAnchors
	catalog    geography.Catalog
	bindings   registry.PolicyBindings
	references map[string]any
	snapshots  map[string][]byte
}

func newSyntheticExport(t *testing.T) syntheticExport {
	t.Helper()
	root := t.TempDir()
	f := syntheticExport{now: time.Date(2026, 9, 8, 8, 0, 0, 0, time.UTC), snapshots: map[string][]byte{}}
	f.config = Config{ArtifactRoot: root, OutputDirectory: filepath.Join(root, "output"), ActorID: "operator:synthetic-test", Reason: "Synthetic protocol acceptance test, not a real authority"}
	regions := []domain.Region{{ID: "region-a", Name: "Synthetic A", CountryCode: "RU", FederalSubjectCode: "RU-TA"}, {ID: "region-b", Name: "Synthetic B", CountryCode: "RU", FederalSubjectCode: "RU-ULY"}}
	cities := []domain.City{
		{ID: "city-a", Name: "Синтетический 'Город'", Aliases: []string{"Synthetic Town", "\u00a0Alias\t Town\u0085", "И\u0306"}, RegionID: "region-a"},
		{ID: "city-b", Name: "Синтетический 'Город'", Aliases: []string{"Synthetic Town", "İSTANBUL", "O'Neil"}, RegionID: "region-a"},
		{ID: "city-c", Name: "Unavailable Place", Aliases: []string{"Other"}, RegionID: "region-b"},
	}
	for i := range cities {
		c := &cities[i]
		c.CountryCode, c.Timezone, c.SettlementType = "RU", "Europe/Moscow", "PPL"
		c.Latitude, c.Longitude, c.Population = 55.12345678901234, 49.23456789012345, int64(i+1)
		c.GeographicSourceID, c.GeographicSource = fmt.Sprintf("geonames:%d", i+1), fmt.Sprintf("https://www.geonames.org/%d", i+1)
		c.GeographicRevision, c.GeographicLicense, c.SourceModifiedDate = "2026-09-08", "CC BY 4.0", "2026-01-01"
	}
	content := testJSON(t, struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{regions, cities})
	f.catalog = geography.Catalog{SchemaVersion: "namaztime-city-catalog/v1", Revision: geography.Revision{ID: "catalog-synthetic-2026-09-08", ContentSHA256: testSHA(content), ImportedCities: 3}, Regions: regions, Cities: cities,
		Source: geography.SourceManifest{License: "CC BY 4.0", LicenseURL: "https://creativecommons.org/licenses/by/4.0/", Attribution: "Synthetic geographic fixture only"}}
	f.config.Catalog = writeTestPin(t, filepath.Join(root, "catalog.json"), testJSON(t, f.catalog))
	f.bindings = registry.PolicyBindings{SchemaVersion: "namaztime-policy-bindings/v2", RegistryRevision: registry.PolicyBindingsRevision{ID: "registry-synthetic-local-setup-v1", SchemaVersion: 2, CatalogRevisionID: f.catalog.Revision.ID, CatalogContentSHA256: f.catalog.Revision.ContentSHA256, CreatedAt: f.now.Add(-time.Hour), CreatedBy: f.config.ActorID, Reason: f.config.Reason}, CalculationProfiles: []domain.CalculationProfile{}, SourceOverrides: []domain.SourceOverride{}}
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	trustRaw := func(environment string, revision int, generated string, key ed25519.PublicKey) []byte {
		return testJSON(t, trust.Bundle{SchemaVersion: "1.0", Revision: uint64(revision), Environment: environment, GeneratedAt: generated, Keys: []trust.KeyEntry{{KeyID: "synthetic-" + environment + "-key", Algorithm: "ed25519", PublicKeyEd25519Base64: base64.StdEncoding.EncodeToString(key), Status: trust.StatusActive, NotBefore: "2025-01-01T00:00:00Z"}}})
	}
	production := trustRaw("production", 3, "2026-09-07T01:00:00Z", publicKey)
	previous := trustRaw("production", 2, "2026-09-07T00:00:00Z", publicKey)
	testTrust := trustRaw("test", 1, "2025-01-01T00:00:00Z", ed25519.PublicKey(bytes.Repeat([]byte{0x41}, 32)))
	stagingTrust := trustRaw("staging", 1, "2025-01-01T00:00:00Z", ed25519.PublicKey(bytes.Repeat([]byte{0x42}, 32)))
	f.anchors = trustAnchors{3, testSHA(production), testSHA(previous), testSHA(testTrust), testSHA(stagingTrust)}
	prodPolicy, err := trust.Decode(production)
	if err != nil {
		t.Fatal(err)
	}
	prevPolicy, err := trust.Decode(previous)
	if err != nil {
		t.Fatal(err)
	}
	if err := trust.ValidateTransition(prevPolicy, prodPolicy); err != nil {
		t.Fatal(err)
	}
	testPolicy, err := trust.Decode(testTrust)
	if err != nil {
		t.Fatal(err)
	}
	stagingPolicy, err := trust.Decode(stagingTrust)
	if err != nil {
		t.Fatal(err)
	}
	if err := trust.ValidateEnvironmentSeparation(testPolicy, stagingPolicy, prodPolicy); err != nil {
		t.Fatal(err)
	}
	trustRefs := map[string]any{}
	for field, raw := range map[string][]byte{"trust_bundle": production, "previous_trust_bundle": previous, "test_trust_bundle": testTrust, "staging_trust_bundle": stagingTrust} {
		pin := writeTestPin(t, filepath.Join(root, field+".json"), raw)
		trustRefs[field] = map[string]string{"file": filepath.Base(pin.Path), "sha256": pin.SHA256}
	}
	var snapshots []any
	for _, spec := range []struct {
		id, authority string
		city          bool
		from, to      string
	}{{"a-region", "a", false, "2026-09-08", "2026-09-10"}, {"a-city", "a", true, "2026-09-09", "2026-09-09"}, {"b-region", "b", false, "2026-09-08", "2026-09-10"}} {
		scope := domain.GeographicScope{ID: "scope-" + spec.id, Kind: domain.GeographicScopeRegion, RegionID: "region-a", Description: "Synthetic exact scope " + spec.id}
		binding := qualification.CatalogBinding{Revision: f.catalog.Revision.ID, SourceRevision: "2026-09-08", Region: regions[0], Cities: cities[:2]}
		if spec.city {
			scope.Kind, scope.CityID = domain.GeographicScopeCity, "city-a"
			binding.Cities = cities[:1]
		}
		display, err := qualification.PublicDisplayContext(scope, binding, "Europe/Moscow")
		if err != nil {
			t.Fatal(err)
		}
		authority := domain.PrayerAuthority{ID: "authority-" + spec.authority, Name: "Synthetic independent publisher " + spec.authority, Website: "https://authority-" + spec.authority + ".example", EvidenceLabel: "CONFIRMED_PUBLIC"}
		if !spec.city {
			f.bindings.Authorities = append(f.bindings.Authorities, authority)
		}
		raw := []byte("Synthetic raw source " + spec.id)
		candidate := domain.CandidateSchedule{DataClassification: domain.DataClassificationProduction, Mosque: display, Source: domain.CandidateSource{SourceID: "source-" + spec.id, Kind: domain.ProviderKindOfficialFile, AuthorityName: authority.Name, GeographicScope: scope.Description, CanonicalURL: authority.Website + "/" + spec.id, MinimumCoverageDays: 1, MaxDeltaMinutes: 15}, Artifact: domain.RawArtifact{Filename: authority.Website + "/" + spec.id, ContentType: "text/csv", CapturedAt: "2026-09-07T22:00:00Z", ByteLength: int64(len(raw)), SHA256: testSHA(raw)}, TranscriptionSHA256: testSHA(raw), ParserVersion: "synthetic-local-test/v1", Coverage: domain.DateRange{From: spec.from, To: spec.to}, Status: domain.CandidateNeedsReview}
		for day, _ := time.Parse(time.DateOnly, spec.from); day.Format(time.DateOnly) <= spec.to; day = day.AddDate(0, 0, 1) {
			candidate.Days = append(candidate.Days, domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{Date: day.Format(time.DateOnly), Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "15:00", Maghrib: "18:00", Isha: "20:00"}})
		}
		review := qualification.EvidenceReview{Authority: authority, Scope: scope, CatalogRevision: binding.Revision, Timezone: display.Timezone, FreshThrough: spec.to, Retrieval: domain.PublicSourceRetrieval{URL: candidate.Source.CanonicalURL, HTTPStatus: 200, ContentType: "text/csv"}, TermsAssessment: "public_transport_no_restriction_observed"}
		for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
			review.Evidence = append(review.Evidence, domain.SourceEvidence{ID: "evidence-" + purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: authority.Website + "/" + purpose, RetrievedAt: candidate.Artifact.CapturedAt, SHA256: strings.Repeat("e", 64), Claim: "Synthetic protocol evidence only: " + purpose})
		}
		for _, day := range candidate.Days {
			review.Comparisons = append(review.Comparisons, domain.SourceValueComparison{EvidenceID: "evidence-value_comparison", Day: day.PrayerDay})
		}
		candidate.Validation = controlled.ValidatePublicCandidateData(controlled.ValidationConfig{SourceCountryCode: "RU", SourceTimezone: display.Timezone}, candidate)
		if err := domain.FinalizeCandidateIdentity(&candidate); err != nil {
			t.Fatal(err)
		}
		diff, err := publication.Diff(nil, candidate)
		if err != nil {
			t.Fatal(err)
		}
		qualifiedAt := time.Date(2026, 9, 8, 0, 1, 0, 0, time.UTC)
		q, err := qualification.Create(review, candidate, raw, diff.SHA256, binding, qualifiedAt)
		if err != nil {
			t.Fatalf("qualify %s: %v", spec.id, err)
		}
		request := publication.PublishRequest{Candidate: candidate, Diff: diff, Qualification: &q, SnapshotID: "snapshot-" + spec.id, GeneratedAt: qualifiedAt.Add(time.Minute), SigningKeyID: "synthetic-production-key"}
		published, receipt, err := publication.PublishWithSigner(t.Context(), request, testSigner{privateKey}, prodPolicy, publication.AuditMetadata{SignerIdentity: "isolated://synthetic-local-fixture", PublishedAt: request.GeneratedAt.Add(time.Minute), ChainGenesisReason: "Synthetic local setup protocol fixture"})
		if err != nil {
			t.Fatalf("publish %s: %v", spec.id, err)
		}
		f.snapshots[request.SnapshotID] = published.JSON
		snapshotPin := writeTestPin(t, filepath.Join(root, request.SnapshotID+".json"), published.JSON)
		receiptPin := writeTestPin(t, filepath.Join(root, request.SnapshotID+"-receipt.json"), testJSON(t, receipt))
		ref := map[string]any{"snapshot_id": request.SnapshotID, "snapshot": map[string]string{"file": filepath.Base(snapshotPin.Path), "sha256": snapshotPin.SHA256}, "receipt": map[string]string{"file": filepath.Base(receiptPin.Path), "sha256": receiptPin.SHA256}}
		for k, v := range trustRefs {
			ref[k] = v
		}
		snapshots = append(snapshots, ref)
		f.bindings.Scopes = append(f.bindings.Scopes, scope)
		f.bindings.Qualifications = append(f.bindings.Qualifications, q)
		f.bindings.Sources = append(f.bindings.Sources, domain.PrayerSource{ID: q.SourceID, Kind: q.Kind, AuthorityIDs: []string{authority.ID}, GeographicScopeID: scope.ID, CanonicalURL: q.CanonicalURL, Status: domain.PrayerSourceQualified, FreshThrough: q.FreshThrough, QualificationID: q.ID})
		f.bindings.Policies = append(f.bindings.Policies, domain.PrayerPolicy{ID: "policy-" + spec.id, Kind: domain.PrayerPolicyTimeTable, GeographicScopeID: scope.ID, AuthorityIDs: []string{authority.ID}, SourceID: q.SourceID, TimeTableID: "table-" + spec.id, MosqueIDs: []string{}, Effective: q.Coverage, QualificationID: q.ID})
		f.bindings.TimeTables = append(f.bindings.TimeTables, domain.TimeTable{ID: "table-" + spec.id, SourceID: q.SourceID, GeographicScopeID: scope.ID, MosqueID: display.ID, Timezone: q.Timezone, Effective: q.Coverage, PublishedSnapshotID: request.SnapshotID})
	}
	f.config.Bindings = writeTestPin(t, filepath.Join(root, "bindings.json"), testJSON(t, f.bindings))
	f.references = map[string]any{"schema_version": "namaztime-registry-reference-artifacts/v1", "approvals": []any{}, "snapshots": snapshots}
	f.config.Artifacts = writeTestPin(t, filepath.Join(root, "artifacts.json"), testJSON(t, f.references))
	return f
}

type testSigner struct{ private ed25519.PrivateKey }

func (testSigner) KeyID() string { return "synthetic-production-key" }
func (s testSigner) Sign(_ context.Context, raw []byte) ([]byte, error) {
	return ed25519.Sign(s.private, raw), nil
}
func testSHA(raw []byte) string { sum := sha256.Sum256(raw); return hex.EncodeToString(sum[:]) }
func testJSON(t *testing.T, value any) []byte {
	t.Helper()
	raw, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	return raw
}
func writeTestPin(t *testing.T, path string, raw []byte) PinnedInput {
	t.Helper()
	if err := os.WriteFile(path, raw, 0o600); err != nil {
		t.Fatal(err)
	}
	return PinnedInput{path, testSHA(raw)}
}
func readTestJSON(t *testing.T, path string, value any) {
	t.Helper()
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal(raw, value); err != nil {
		t.Fatal(err)
	}
}
func assertNoOutput(t *testing.T, path string) {
	t.Helper()
	if _, err := os.Lstat(path); !os.IsNotExist(err) {
		t.Fatalf("unexpected output after failed export: %v", err)
	}
}
func containsTest(values []string, want string) bool {
	for _, v := range values {
		if v == want {
			return true
		}
	}
	return false
}
