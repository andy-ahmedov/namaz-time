package setupbundle

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/geography"
)

func TestSharedSearchNormalizationContract(t *testing.T) {
	var cases []struct {
		Input      string `json:"input"`
		Normalized string `json:"normalized"`
	}
	readTestJSON(t, "testdata/search-normalization.json", &cases)
	for _, test := range cases {
		if got := NormalizeSearch(test.Input); got != test.Normalized {
			t.Errorf("NormalizeSearch(%q)=%q, want %q", test.Input, got, test.Normalized)
		}
	}
}

func TestSQLiteExactSearchRetainsAllSameNamedPlacesAndAliasSemantics(t *testing.T) {
	f := newSyntheticExport(t)
	_, err := exportAt(t.Context(), f.config, f.now, f.anchors)
	if err != nil {
		t.Fatal(err)
	}
	for _, query := range []string{"SYNTHETIC\t TOWN", "Синтетический 'Город'", "\u00a0Alias Town\u0085", "İSTANBUL", "O'Neil", "Other", "Synthetic", "UNKNOWN", "  "} {
		var result []struct {
			Count int `json:"count"`
		}
		sql := "SELECT count(DISTINCT city_id) AS count FROM search_names WHERE normalized_name=" + sqlText(NormalizeSearch(query)) + ";"
		if err := sqliteSmallJSON(t.Context(), "", filepath.Join(f.config.OutputDirectory, "catalog.sqlite"), sql, &result); err != nil {
			t.Fatal(err)
		}
		want := len(f.catalog.Search(query))
		if len(result) != 1 || result[0].Count != want {
			t.Errorf("complete exact search %q=%v, canonical=%d", query, result, want)
		}
	}
}

func TestCompleteActualCanonicalCatalogSQLiteRoundtripOptIn(t *testing.T) {
	path := os.Getenv("NAMAZTIME_LOCAL_SETUP_CATALOG")
	if path == "" {
		t.Skip("set NAMAZTIME_LOCAL_SETUP_CATALOG and NAMAZTIME_LOCAL_SETUP_CATALOG_SHA256 to retained canonical catalog")
	}
	raw, err := readPinnedInput(PinnedInput{path, os.Getenv("NAMAZTIME_LOCAL_SETUP_CATALOG_SHA256")}, maximumInputBytes)
	if err != nil {
		t.Fatal(err)
	}
	catalog, err := geography.DecodeCatalog(raw)
	if err != nil {
		t.Fatal(err)
	}
	if catalog.Revision.ID != "catalog-geonames-ru-2026-09-08-5267d8bf5e48a158" {
		t.Fatal("this count gate is pinned to the actual 2026-09-08 catalog")
	}
	var hashes []string
	for _, name := range []string{"first.sqlite", "second.sqlite"} {
		file := filepath.Join(t.TempDir(), name)
		info, err := buildIndex(t.Context(), file, "", catalog)
		if err != nil {
			t.Fatal(err)
		}
		if info.RegionCount != 83 || info.CityCount != 166559 || info.AliasCount != 204641 || info.SearchNameCount != 371200 {
			t.Fatalf("full catalog counts differ: %+v", info)
		}
		data, err := os.ReadFile(file)
		if err != nil {
			t.Fatal(err)
		}
		hashes = append(hashes, testSHA(data))
		t.Logf("catalog=%s regions=%d cities=%d aliases=%d search_names=%d SQLite_bytes=%d sha256=%s", info.RevisionID, info.RegionCount, info.CityCount, info.AliasCount, info.SearchNameCount, len(data), hashes[len(hashes)-1])
	}
	if hashes[0] != hashes[1] {
		t.Fatal("full canonical SQLite bytes are not reproducible")
	}
}
