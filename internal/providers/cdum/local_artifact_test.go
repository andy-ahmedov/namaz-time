package cdum_test

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/cdum"
)

// Opt-in retained first-party artifact checks never fetch the network or keep
// raw source rows in Git. Supply each path and its independently captured hash.
func TestRetainedPublicArtifacts(t *testing.T) {
	for _, tc := range []struct {
		name, pathEnv, hashEnv, locality, rejectedDate string
		septemberEighth                                [6]string
	}{
		{"Moscow", "T049_CDUM_MOSCOW_HTML", "T049_CDUM_MOSCOW_SHA256", "Москва", "2026-05-08", [6]string{"03:28", "05:46", "12:29", "16:58", "19:08", "21:16"}},
		{"SaintPetersburg", "T049_CDUM_SPB_HTML", "T049_CDUM_SPB_SHA256", "Санкт-Петербург", "2026-04-22", [6]string{"03:25", "06:08", "12:58", "17:25", "19:44", "22:14"}},
		{"Kazan", "T049_CDUM_KAZAN_HTML", "T049_CDUM_KAZAN_SHA256", "Казань", "2026-08-08", [6]string{"02:44", "05:01", "11:41", "16:10", "18:20", "20:10"}},
	} {
		t.Run(tc.name, func(t *testing.T) {
			path := os.Getenv(tc.pathEnv)
			if path == "" {
				t.Skip("retained public artifact not configured")
			}
			expectedHash := os.Getenv(tc.hashEnv)
			if decoded, err := hex.DecodeString(expectedHash); err != nil || len(decoded) != sha256.Size {
				t.Fatal("opt-in retained artifact requires its captured SHA-256")
			}
			raw, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			hash := sha256.Sum256(raw)
			if hex.EncodeToString(hash[:]) != expectedHash {
				t.Fatal("retained raw-artifact hash mismatch")
			}
			got, err := cdum.ParseHTML(raw, tc.locality, domain.DateRange{From: "2026-09-01", To: "2026-09-30"})
			if err != nil || len(got) != 30 {
				t.Fatalf("September retained data: days=%d error=%v", len(got), err)
			}
			eighth := got[7]
			if [6]string{eighth.Fajr, eighth.Sunrise, eighth.Dhuhr, eighth.Asr, eighth.Maghrib, eighth.Isha} != tc.septemberEighth {
				t.Fatal("retained September8 row differs from independently inspected source")
			}
			q4, err := cdum.ParseHTML(raw, tc.locality, domain.DateRange{From: "2026-10-01", To: "2026-12-31"})
			if err != nil || len(q4) != 92 {
				t.Fatalf("Q4 retained data: days=%d error=%v", len(q4), err)
			}
			annual, err := cdum.ParseHTML(raw, tc.locality, domain.DateRange{From: "2026-01-01", To: "2026-12-31"})
			if tc.rejectedDate == "" {
				if err != nil || len(annual) != 365 {
					t.Fatalf("annual retained data: days=%d error=%v", len(annual), err)
				}
				return
			}
			if err == nil || annual != nil || !strings.Contains(err.Error(), tc.rejectedDate) || !strings.Contains(err.Error(), "unsupported next-day") {
				t.Fatalf("annual midnight data must fail closed: days=%d error=%v", len(annual), err)
			}
		})
	}
}

// The optional locality matrix is independently inspected public metadata, not
// a source dataset. Every actual artifact remains outside Git and is hash-bound.
func TestRetainedResearchedLocalities(t *testing.T) {
	dir := os.Getenv("T049_CDUM_LOCALITIES_DIR")
	if dir == "" {
		t.Skip("retained researched-locality matrix not configured")
	}
	manifestRaw, err := os.ReadFile("../../../research/t049/cdum-localities.json")
	if err != nil {
		t.Fatal(err)
	}
	var manifest struct {
		SchemaVersion string `json:"schema_version"`
		Localities    []struct {
			Locality  string `json:"locality"`
			Transport struct {
				RawFilename string `json:"raw_filename"`
				RawSHA256   string `json:"raw_sha256"`
			} `json:"transport"`
			Inspection struct {
				NonascendingDates []string `json:"nonascending_dates"`
			} `json:"inspection"`
			ComparisonEvidence []struct {
				Values [6]string `json:"values"`
			} `json:"comparison_evidence"`
			ParserCheck struct {
				SupportedBinding bool `json:"supported_binding"`
			} `json:"parser_check"`
		} `json:"localities"`
	}
	if err := json.Unmarshal(manifestRaw, &manifest); err != nil || manifest.SchemaVersion != "t049-cdum-localities/v1" || len(manifest.Localities) == 0 {
		t.Fatalf("invalid researched-locality metadata: %v", err)
	}
	for _, locality := range manifest.Localities {
		t.Run(locality.Locality, func(t *testing.T) {
			if filepath.Base(locality.Transport.RawFilename) != locality.Transport.RawFilename {
				t.Fatal("retained artifact must be a direct child of the configured directory")
			}
			raw, err := os.ReadFile(filepath.Join(dir, locality.Transport.RawFilename))
			if err != nil {
				t.Fatal(err)
			}
			hash := sha256.Sum256(raw)
			if hex.EncodeToString(hash[:]) != locality.Transport.RawSHA256 {
				t.Fatal("retained locality raw-artifact hash mismatch")
			}
			for _, tc := range []struct {
				coverage domain.DateRange
				count    int
			}{
				{domain.DateRange{From: "2026-09-01", To: "2026-09-30"}, 30},
				{domain.DateRange{From: "2026-10-01", To: "2026-12-31"}, 92},
				{domain.DateRange{From: "2026-01-01", To: "2026-12-31"}, 365},
			} {
				firstRejected := ""
				for _, date := range locality.Inspection.NonascendingDates {
					if date >= tc.coverage.From && date <= tc.coverage.To {
						firstRejected = date
						break
					}
				}
				got, err := cdum.ParseHTML(raw, locality.Locality, tc.coverage)
				if !locality.ParserCheck.SupportedBinding {
					if err == nil || got != nil {
						t.Fatal("unsupported retained source accepted")
					}
					continue
				}
				if firstRejected != "" {
					if err == nil || got != nil || !strings.Contains(err.Error(), firstRejected) || !strings.Contains(err.Error(), "invalid time order") {
						t.Fatalf("expected retained coverage rejection at %s: days=%d error=%v", firstRejected, len(got), err)
					}
					continue
				}
				if err != nil || len(got) != tc.count || got[0].Date != tc.coverage.From || got[len(got)-1].Date != tc.coverage.To {
					t.Fatalf("retained coverage %+v: days=%d error=%v", tc.coverage, len(got), err)
				}
				if tc.coverage.From == "2026-09-01" {
					if len(locality.ComparisonEvidence) != 1 {
						t.Fatal("requires independently inspected September8 sample")
					}
					day := got[7]
					if [6]string{day.Fajr, day.Sunrise, day.Dhuhr, day.Asr, day.Maghrib, day.Isha} != locality.ComparisonEvidence[0].Values {
						t.Fatal("September8 differs from independently inspected source")
					}
				}
			}
		})
	}
}
