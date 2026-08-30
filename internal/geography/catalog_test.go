package geography

import (
	"archive/zip"
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"reflect"
	"strings"
	"testing"
)

const (
	ulyanovskRow   = "479123\tUlyanovsk\tUlyanovsk\tUlyanovsk,Ульяновск\t54.32824\t48.38657\tP\tPPLA\tRU\t\t81\t479105\t\t\t626540\t\t176\tEurope/Ulyanovsk\t2022-10-16\n"
	kirovCityRow   = "548408\tKirov\tKirov\tKirov,Киров\t58.59809\t49.65783\tP\tPPLA\tRU\t\t33\t548394\t\t\t507155\t\t179\tEurope/Kirov\t2026-04-14\n"
	kirovKalugaRow = "548410\tKirov\tKirov\tKirov,Киров\t54.06889\t34.29891\tP\tPPLA2\tRU\t\t25\t\t\t\t39319\t\t213\tEurope/Moscow\t2023-05-11\n"

	ulyanovskNames = "1744169\t479123\ten\tUlyanovsk\t1\t\t\t\t\t\n" +
		"2426202\t479123\tru\tУльяновск\t1\t\t\t\t\t\n" +
		"2432954\t479123\tru\tСинбирск\t\t\t\t\t\t\n"
	kirovNames = "2000001\t548408\ten\tKirov\t1\t\t\t\t\t\n" +
		"2000002\t548408\tru\tКиров\t1\t\t\t\t\t\n" +
		"2000003\t548410\ten\tKirov\t1\t\t\t\t\t\n" +
		"2000004\t548410\tru\tКиров\t1\t\t\t\t\t\n"
	adminRows = "RU.25\tKaluga Oblast\tKaluga Oblast\t553899\n" +
		"RU.33\tKirov Oblast\tKirov Oblast\t548389\n" +
		"RU.81\tUlyanovsk\tUlyanovsk\t479119\n"
)

var testRegionMappings = []RegionMapping{
	{SourceAdminCode: "25", ID: "ru-klu", FederalSubjectCode: "RU-KLU", NameRU: "Калужская область"},
	{SourceAdminCode: "33", ID: "ru-kir", FederalSubjectCode: "RU-KIR", NameRU: "Кировская область"},
	{SourceAdminCode: "81", ID: "ru-uly", FederalSubjectCode: "RU-ULY", NameRU: "Ульяновская область"},
}

func TestImportBuildsCanonicalLicensedCatalog(t *testing.T) {
	request := testImportRequest(t, ulyanovskRow, ulyanovskNames)
	catalog, err := Import(request)
	if err != nil {
		t.Fatalf("Import() error = %v", err)
	}
	if catalog.Revision.ID == "" || catalog.Revision.ContentSHA256 == "" {
		t.Fatalf("revision = %#v", catalog.Revision)
	}
	if got, want := len(catalog.Regions), 3; got != want {
		t.Fatalf("regions = %d, want %d", got, want)
	}
	if got, want := len(catalog.Cities), 1; got != want {
		t.Fatalf("cities = %d, want %d", got, want)
	}
	city := catalog.Cities[0]
	if city.ID != StableCityID("geonames", "RU", "479123") {
		t.Fatalf("city ID = %q", city.ID)
	}
	if city.Name != "Ульяновск" || city.RegionID != "ru-uly" || city.SettlementType != "PPLA" {
		t.Fatalf("canonical city = %#v", city)
	}
	if city.Timezone != "Europe/Ulyanovsk" || city.Latitude != 54.32824 || city.Longitude != 48.38657 {
		t.Fatalf("time/geography = %#v", city)
	}
	if city.GeographicSourceID != "geonames:479123" || city.GeographicRevision != "2026-08-29" {
		t.Fatalf("provenance = %#v", city)
	}
	if city.GeographicLicense != "CC BY 4.0" || !containsString(city.Aliases, "Ulyanovsk") || !containsString(city.Aliases, "Синбирск") {
		t.Fatalf("license/aliases = %#v", city)
	}
	if city.FallbackPolicyID != "" {
		t.Fatalf("geographic import assigned prayer fallback %q", city.FallbackPolicyID)
	}
}

func TestSearchReturnsEveryDuplicateWithoutGuessing(t *testing.T) {
	request := testImportRequest(t, kirovKalugaRow+kirovCityRow+ulyanovskRow, kirovNames+ulyanovskNames)
	catalog, err := Import(request)
	if err != nil {
		t.Fatalf("Import() error = %v", err)
	}

	latin := catalog.Search("  ULYANOVSK ")
	if len(latin) != 1 || latin[0].City.Name != "Ульяновск" || latin[0].Region.FederalSubjectCode != "RU-ULY" {
		t.Fatalf("latin search = %#v", latin)
	}
	duplicates := catalog.Search("киров")
	if got, want := len(duplicates), 2; got != want {
		t.Fatalf("duplicate results = %d, want %d: %#v", got, want, duplicates)
	}
	if duplicates[0].Region.FederalSubjectCode != "RU-KIR" || duplicates[1].Region.FederalSubjectCode != "RU-KLU" {
		t.Fatalf("duplicate order/context = %#v", duplicates)
	}
	if selected, ok := catalog.Unique("Киров"); ok || selected.City.ID != "" {
		t.Fatalf("Unique() guessed among duplicates: %#v, %v", selected, ok)
	}
	if selected, ok := catalog.Unique("unknown"); ok || selected.City.ID != "" {
		t.Fatalf("Unique() selected unknown: %#v, %v", selected, ok)
	}
}

func TestCanonicalContentIsDeterministicAcrossSourceRowOrder(t *testing.T) {
	first, err := Import(testImportRequest(t, kirovCityRow+ulyanovskRow, kirovNames+ulyanovskNames))
	if err != nil {
		t.Fatalf("first Import() error = %v", err)
	}
	second, err := Import(testImportRequest(t, ulyanovskRow+kirovCityRow, ulyanovskNames+kirovNames))
	if err != nil {
		t.Fatalf("second Import() error = %v", err)
	}
	if first.Revision.ID != second.Revision.ID || first.Revision.ContentSHA256 != second.Revision.ContentSHA256 {
		t.Fatalf("canonical revisions differ: %#v != %#v", first.Revision, second.Revision)
	}
	if !reflect.DeepEqual(first.Cities, second.Cities) || !reflect.DeepEqual(first.Regions, second.Regions) {
		t.Fatalf("canonical content differs: %#v != %#v", first, second)
	}
	firstBytes, err := Encode(first)
	if err != nil || len(firstBytes) == 0 {
		t.Fatalf("Encode() = %q, %v", firstBytes, err)
	}
}

func TestDiffReportsStableAddsRemovesAndChanges(t *testing.T) {
	before, err := Import(testImportRequest(t, ulyanovskRow+kirovCityRow, ulyanovskNames+kirovNames))
	if err != nil {
		t.Fatalf("before Import() error = %v", err)
	}
	changedUlyanovsk := bytes.ReplaceAll([]byte(ulyanovskRow), []byte("626540"), []byte("700000"))
	after, err := Import(testImportRequest(t, string(changedUlyanovsk)+kirovKalugaRow, ulyanovskNames+kirovNames))
	if err != nil {
		t.Fatalf("after Import() error = %v", err)
	}
	diff := Compare(before, after)
	if len(diff.AddedCityIDs) != 1 || len(diff.RemovedCityIDs) != 1 || len(diff.ChangedCityIDs) != 1 {
		t.Fatalf("diff = %#v", diff)
	}
	if diff.SHA256 == "" {
		t.Fatal("diff lacks deterministic SHA-256")
	}
}

func TestImportFailsClosedOnInputAndSchemaDrift(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(ImportRequest) ImportRequest
		want   error
	}{
		{
			name: "checksum mismatch",
			mutate: func(request ImportRequest) ImportRequest {
				request.Manifest.Artifacts[0].SHA256 = strings.Repeat("0", 64)
				return request
			},
			want: ErrArtifactMismatch,
		},
		{
			name: "unknown admin mapping",
			mutate: func(request ImportRequest) ImportRequest {
				request.RegionMappings = request.RegionMappings[:2]
				return request
			},
			want: ErrSchemaDrift,
		},
		{
			name: "invalid timezone",
			mutate: func(request ImportRequest) ImportRequest {
				request.Artifacts["gazetteer"] = zipFixture(t, "RU.txt", bytes.ReplaceAll([]byte(ulyanovskRow), []byte("Europe/Ulyanovsk"), []byte("UTC+4")))
				request.Manifest.Artifacts[0] = artifactManifest("gazetteer", "gazetteer.zip", "RU.txt", request.Artifacts["gazetteer"])
				return request
			},
			want: ErrInvalidCatalog,
		},
		{
			name: "wrong column count",
			mutate: func(request ImportRequest) ImportRequest {
				request.Artifacts["gazetteer"] = zipFixture(t, "RU.txt", []byte("479123\tUlyanovsk\n"))
				request.Manifest.Artifacts[0] = artifactManifest("gazetteer", "gazetteer.zip", "RU.txt", request.Artifacts["gazetteer"])
				return request
			},
			want: ErrSchemaDrift,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			_, err := Import(test.mutate(testImportRequest(t, ulyanovskRow, ulyanovskNames)))
			if !errors.Is(err, test.want) {
				t.Fatalf("Import() error = %v, want %v", err, test.want)
			}
		})
	}
}

func testImportRequest(t *testing.T, gazetteer, alternateNames string) ImportRequest {
	t.Helper()
	artifacts := map[string][]byte{
		"gazetteer":       zipFixture(t, "RU.txt", []byte(gazetteer)),
		"alternate_names": zipFixture(t, "RU.txt", []byte(alternateNames)),
		"admin1_codes":    []byte(adminRows),
	}
	return ImportRequest{
		Manifest: SourceManifest{
			SchemaVersion:  "geonames-source-manifest/v1",
			SourceID:       "geonames",
			SourceRevision: "2026-08-29",
			CountryCode:    "RU",
			License:        "CC BY 4.0",
			LicenseURL:     "https://creativecommons.org/licenses/by/4.0/",
			Attribution:    "GeoNames (https://www.geonames.org/)",
			Artifacts: []ArtifactManifest{
				artifactManifest("gazetteer", "gazetteer.zip", "RU.txt", artifacts["gazetteer"]),
				artifactManifest("alternate_names", "alternate-names.zip", "RU.txt", artifacts["alternate_names"]),
				artifactManifest("admin1_codes", "admin1CodesASCII.txt", "", artifacts["admin1_codes"]),
			},
		},
		Artifacts:      artifacts,
		RegionMappings: append([]RegionMapping(nil), testRegionMappings...),
	}
}

func artifactManifest(role, cacheFile, member string, data []byte) ArtifactManifest {
	digest := sha256.Sum256(data)
	return ArtifactManifest{Role: role, URL: "https://download.geonames.org/export/dump/" + role, CacheFile: cacheFile, ArchiveMember: member, ByteLength: int64(len(data)), SHA256: hex.EncodeToString(digest[:])}
}

func zipFixture(t *testing.T, name string, contents []byte) []byte {
	t.Helper()
	var target bytes.Buffer
	writer := zip.NewWriter(&target)
	entry, err := writer.Create(name)
	if err != nil {
		t.Fatalf("create zip fixture: %v", err)
	}
	if _, err := entry.Write(contents); err != nil {
		t.Fatalf("write zip fixture: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close zip fixture: %v", err)
	}
	return target.Bytes()
}

func containsString(values []string, wanted string) bool {
	for _, value := range values {
		if value == wanted {
			return true
		}
	}
	return false
}
