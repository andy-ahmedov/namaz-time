package kbr_test

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"reflect"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/kbr"
)

// This explicitly opt-in comparison reads retained artifacts outside Git and
// never accesses the network. Hashes must come from independent capture
// metadata; generating them from whatever the test reads defeats the binding.
func TestLocalRetainedAnnualArtifact(t *testing.T) {
	if os.Getenv("NAMAZTIME_KBR_TEXT") == "" {
		t.Skip("set NAMAZTIME_KBR_TEXT/PDF/REFERENCE and corresponding _SHA256 values for the retained-artifact check")
	}
	read := func(key string) []byte {
		t.Helper()
		path, wantHash := os.Getenv(key), os.Getenv(key+"_SHA256")
		decoded, err := hex.DecodeString(wantHash)
		if path == "" || err != nil || len(decoded) != sha256.Size {
			t.Fatalf("%s path and an independently captured SHA-256 are required", key)
		}
		raw, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		digest := sha256.Sum256(raw)
		if hex.EncodeToString(digest[:]) != wantHash {
			t.Fatalf("%s differs from its retained capture hash", key)
		}
		return raw
	}
	pdf := read("NAMAZTIME_KBR_PDF")
	if len(pdf) < 5 || string(pdf[:5]) != "%PDF-" {
		t.Fatal("retained raw artifact is not a PDF")
	}
	rawText := read("NAMAZTIME_KBR_TEXT")
	referenceJSON := read("NAMAZTIME_KBR_REFERENCE")
	var reference []domain.CandidatePrayerDay
	if err := json.Unmarshal(referenceJSON, &reference); err != nil {
		t.Fatal(err)
	}
	days, err := kbr.ParseText(rawText, annualCoverage)
	if err != nil {
		t.Fatal(err)
	}
	if len(reference) != 365 || !reflect.DeepEqual(days, reference) {
		t.Fatal("the full 365-day normalization differs from the independent retained reference")
	}
	t.Logf("parser=%s normalized_days=%d coverage=%s/%s; raw PDF, extracted text and independent reference hashes verified", kbr.ParserVersion, len(days), days[0].Date, days[364].Date)
}
