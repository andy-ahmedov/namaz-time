package controlled

import (
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

func TestPublicCandidateDataDoesNotRequireInventedPermissionOrZenith(t *testing.T) {
	candidate := publicDataCandidate()
	before := candidate
	report := ValidatePublicCandidateData(ValidationConfig{SourceCountryCode: "RU", SourceTimezone: "Europe/Moscow"}, candidate)
	if len(report.Errors) != 0 || len(report.Warnings) != 0 {
		t.Fatalf("valid exact six-field public table rejected: %+v", report)
	}
	if !reflect.DeepEqual(candidate, before) || candidate.Source.PermissionStatus != "" || candidate.Source.ApprovalRequired {
		t.Fatal("data validation must not fabricate legacy permission or approval")
	}
	legacy := ValidateCandidate(ValidationConfig{SourceCountryCode: "RU", SourceTimezone: "Europe/Moscow", SourceStatus: "testing"}, candidate)
	if len(legacy.Errors) == 0 {
		t.Fatal("legacy controlled import must still require its actual permission and zenith fields")
	}
}

func TestPublicCandidateDataRejectsOrderingDriftAndDisabledChecks(t *testing.T) {
	tests := []struct {
		name   string
		change func(*domain.CandidateSchedule)
	}{
		{"midnight Isha", func(c *domain.CandidateSchedule) { c.Days[1].Isha = "00:01" }},
		{"previous day Fajr", func(c *domain.CandidateSchedule) { c.Days[1].Fajr = "23:59" }},
		{"fake zenith", func(c *domain.CandidateSchedule) { c.Days[1].Zenith = "12:01" }},
		{"unvalidated optional time", func(c *domain.CandidateSchedule) { c.Days[1].Duha = "25:00" }},
		{"duplicate day", func(c *domain.CandidateSchedule) { c.Days[1].Date = c.Days[0].Date }},
		{"scope timezone mismatch", func(c *domain.CandidateSchedule) { c.Mosque.Timezone = "Asia/Omsk" }},
		{"offset timezone", func(c *domain.CandidateSchedule) { c.Mosque.Timezone = "+03:00" }},
		{"zero minimum", func(c *domain.CandidateSchedule) { c.Source.MinimumCoverageDays = 0 }},
		{"negative delta disables validation", func(c *domain.CandidateSchedule) { c.Source.MaxDeltaMinutes = -1 }},
		{"extreme delta threshold", func(c *domain.CandidateSchedule) { c.Source.MaxDeltaMinutes = 1440 }},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			c := publicDataCandidate()
			tt.change(&c)
			if report := ValidatePublicCandidateData(ValidationConfig{SourceCountryCode: "RU", SourceTimezone: "Europe/Moscow"}, c); len(report.Errors) == 0 {
				t.Fatal("invalid public data accepted")
			}
		})
	}
}

func TestPublicCandidateDataRejectsNonexistentAndAmbiguousLocalTime(t *testing.T) {
	for _, date := range []string{"2026-03-29", "2026-10-25"} {
		t.Run(date, func(t *testing.T) {
			c := publicDataCandidate()
			c.Mosque.Timezone, c.Source.MinimumCoverageDays = "Europe/Berlin", 1
			c.Coverage = domain.DateRange{From: date, To: date}
			c.Days = c.Days[:1]
			c.Days[0].Date, c.Days[0].Fajr = date, "02:30"
			report := ValidatePublicCandidateData(ValidationConfig{SourceCountryCode: "RU", SourceTimezone: "Europe/Berlin"}, c)
			found := false
			for _, failure := range report.Errors {
				found = found || failure.Code == "ambiguous_or_nonexistent_local_time"
			}
			if !found {
				t.Fatalf("DST gap/fold must fail explicitly: %+v", report)
			}
		})
	}
}

func publicDataCandidate() domain.CandidateSchedule {
	c := domain.CandidateSchedule{
		DataClassification:  domain.DataClassificationSynthetic,
		Mosque:              domain.Mosque{ID: "public-scope-synthetic", Name: "Synthetic city", CountryCode: "RU", Timezone: "Europe/Moscow"},
		Source:              domain.CandidateSource{MinimumCoverageDays: 3, MaxDeltaMinutes: 15},
		Artifact:            domain.RawArtifact{SHA256: strings.Repeat("a", 64)},
		TranscriptionSHA256: strings.Repeat("a", 64),
		Coverage:            domain.DateRange{From: "2026-09-01", To: "2026-09-03"},
	}
	for date := time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC); date.Day() <= 3; date = date.AddDate(0, 0, 1) {
		c.Days = append(c.Days, domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{Date: date.Format(time.DateOnly), Fajr: "04:00", Sunrise: "06:00", Dhuhr: "12:00", Asr: "15:00", Maghrib: "18:00", Isha: "20:00"}})
	}
	return c
}
