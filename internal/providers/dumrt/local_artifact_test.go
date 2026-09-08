package dumrt_test

import (
	"crypto/sha256"
	"encoding/hex"
	"os"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/dumrt"
)

// This opt-in check consumes a retained first-party artifact outside Git; it
// never downloads data in CI and never substitutes synthetic values for it.
func TestLocalRetainedArtifact(t *testing.T) {
	path := os.Getenv("NAMAZTIME_DUMRT_CSV")
	if path == "" {
		t.Skip("set NAMAZTIME_DUMRT_CSV and NAMAZTIME_DUMRT_SHA256 for retained 2026 source verification")
	}
	wantHash := os.Getenv("NAMAZTIME_DUMRT_SHA256")
	if len(wantHash) != 64 {
		t.Fatal("an independently recorded source SHA-256 is required")
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	digest := sha256.Sum256(raw)
	if hex.EncodeToString(digest[:]) != wantHash {
		t.Fatal("retained artifact differs from the independent evidence hash")
	}
	days, err := dumrt.ParseCSVRange(raw, domain.DateRange{From: "2026-09-01", To: "2026-09-30"})
	if err != nil {
		t.Fatal(err)
	}
	if len(days) != 30 || days[0].Date != "2026-09-01" || days[len(days)-1].Date != "2026-09-30" {
		t.Fatal("retained artifact did not produce the explicitly bounded September 2026 coverage")
	}
	t.Logf("parser=%s raw_sha256=%s normalized_days=%d coverage=%s/%s", dumrt.ParserVersion, wantHash, len(days), days[0].Date, days[len(days)-1].Date)
}
