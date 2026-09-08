package sochi_test

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"reflect"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/sochi"
)

// Opt-in offline comparison with an independently extracted complete matrix.
// Only hashes and small seasonal samples are retained in Git, not source rows.
func TestLocalRetainedArtifact2026(t *testing.T) {
	artifact := os.Getenv("NAMAZTIME_SOCHI_XLSX")
	if artifact == "" {
		t.Skip("set NAMAZTIME_SOCHI_XLSX to the retained 2026 first-party workbook")
	}
	const rawSHA = "91f57f3a1658788481b64a9f219602d04f6f8b4b0dfa86557daa546706d573ee"
	// Python stdlib ZIP/XML extraction, independent of this Go parser:
	// compact UTF-8 JSON [[ISO-date,[Fajr,Sunrise,Dhuhr,Asr,Maghrib,Isha]],...].
	const matrixSHA = "3d2de35b5a629d0c8a4ea54cbe8c1ee94e2a0ea1df62c5406635ded8194ebfd4"
	info, err := os.Stat(artifact)
	if err != nil {
		t.Fatal(err)
	}
	if info.Size() != 37018 {
		t.Fatal("unexpected retained workbook size")
	}
	raw, err := os.ReadFile(artifact)
	if err != nil {
		t.Fatal(err)
	}
	digest := sha256.Sum256(raw)
	if hex.EncodeToString(digest[:]) != rawSHA {
		t.Fatal("retained workbook differs from independently recorded hash")
	}
	days, err := sochi.ParseXLSX(raw, domain.DateRange{From: "2026-01-01", To: "2026-12-31"})
	if err != nil {
		t.Fatal(err)
	}
	if len(days) != 365 || days[0].Date != "2026-01-01" || days[364].Date != "2026-12-31" {
		t.Fatal("full-year coverage not reproduced")
	}
	matrix := make([][]any, 0, len(days))
	for _, day := range days {
		matrix = append(matrix, []any{day.Date, []string{day.Fajr, day.Sunrise, day.Dhuhr, day.Asr, day.Maghrib, day.Isha}})
		want := domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{Date: day.Date, Fajr: day.Fajr, Sunrise: day.Sunrise,
			Dhuhr: day.Dhuhr, Asr: day.Asr, Maghrib: day.Maghrib, Isha: day.Isha}}
		if !reflect.DeepEqual(day, want) {
			t.Fatal("invented unpublished metadata")
		}
	}
	normalizedMatrix, err := json.Marshal(matrix)
	if err != nil {
		t.Fatal(err)
	}
	matrixDigest := sha256.Sum256(normalizedMatrix)
	if hex.EncodeToString(matrixDigest[:]) != matrixSHA {
		t.Fatal("complete 365-date/six-field matrix differs from independent extraction")
	}
	september, err := sochi.ParseXLSX(raw, domain.DateRange{From: "2026-09-01", To: "2026-09-30"})
	if err != nil || !reflect.DeepEqual(september, days[243:273]) {
		t.Fatalf("September selection differs from validated annual rows: %v", err)
	}
	for _, sample := range []struct{ date, fajr, isha string }{
		{"2026-01-01", "06:11", "18:33"}, {"2026-06-21", "02:17", "22:17"},
		{"2026-09-08", "04:13", "20:18"}, {"2026-12-31", "06:11", "18:32"},
	} {
		found := false
		for _, day := range days {
			if day.Date == sample.date {
				found = day.Fajr == sample.fajr && day.Isha == sample.isha
			}
		}
		if !found {
			t.Fatalf("seasonal sample not reproduced: %s", sample.date)
		}
	}
	normalized, err := json.Marshal(days)
	if err != nil {
		t.Fatal(err)
	}
	t.Logf("parser=%s raw_sha256=%s matrix_sha256=%x candidate_sha256=%x days=%d comparisons=%d", sochi.ParserVersion, rawSHA,
		matrixDigest, sha256.Sum256(normalized), len(days), len(days)*6)
}
