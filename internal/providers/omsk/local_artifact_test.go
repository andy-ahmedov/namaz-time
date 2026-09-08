package omsk_test

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/omsk"
)

// Opt-in, offline verification of a retained first-party artifact. Normal CI
// uses only synthetic tests; no network call or source dataset enters Git.
func TestLocalRetainedArtifact(t *testing.T) {
	path := os.Getenv("NAMAZTIME_OMSK_JSON")
	if path == "" {
		t.Skip("set NAMAZTIME_OMSK_JSON and NAMAZTIME_OMSK_SHA256 for retained artifact verification")
	}
	wantHash := os.Getenv("NAMAZTIME_OMSK_SHA256")
	expected, err := hex.DecodeString(wantHash)
	if err != nil || len(expected) != sha256.Size {
		t.Fatal("independently recorded SHA-256 required")
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if info.Size() < 1 || info.Size() > 1024*1024 {
		t.Fatal("retained artifact size outside parser limits")
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	digest := sha256.Sum256(raw)
	if hex.EncodeToString(digest[:]) != wantHash {
		t.Fatal("retained artifact differs from independently recorded hash")
	}
	days, err := omsk.ParseJSON(raw, domain.DateRange{From: "2026-06-01", To: "2026-09-30"})
	if err != nil {
		t.Fatal(err)
	}
	if len(days) != 122 || days[0].Date != "2026-06-01" || days[121].Date != "2026-09-30" {
		t.Fatal("retained range not reproduced")
	}
	var source struct {
		Schedule map[string]map[string]string `json:"schedule"`
	}
	if err := json.Unmarshal(raw, &source); err != nil {
		t.Fatal(err)
	}
	for _, day := range days {
		row := source.Schedule[day.Date]
		if day.Fajr != row["fajr"] || day.Sunrise != row["sunrise"] || day.Dhuhr != row["dhuhr"] ||
			day.Asr != row["asr"] || day.Maghrib != row["maghrib"] || day.Isha != row["isha"] ||
			day.HijriYear != 0 || day.DhuhrCongregation != "" || day.RecommendedFajr != "" || day.Zenith != "" {
			t.Fatal("retained artifact's explicit field semantics not preserved")
		}
	}
	september, err := omsk.ParseJSON(raw, domain.DateRange{From: "2026-09-01", To: "2026-09-30"})
	if err != nil || len(september) != 30 {
		t.Fatalf("September coverage not reproduced: %v", err)
	}
	normalized, err := json.Marshal(days)
	if err != nil {
		t.Fatal(err)
	}
	normalizedHash := sha256.Sum256(normalized)
	t.Logf("parser=%s raw_sha256=%s normalized_sha256=%x days=%d coverage=%s/%s", omsk.ParserVersion, wantHash, normalizedHash, len(days), days[0].Date, days[len(days)-1].Date)
}
