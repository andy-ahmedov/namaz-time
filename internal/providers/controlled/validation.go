// Package controlled contains validation shared by strict, human-reviewed
// transcription providers. Provider identity and retrieval policy stay in each
// concrete adapter.
package controlled

import (
	"fmt"
	"regexp"
	"sort"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

var (
	datePattern   = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}$`)
	timePattern   = regexp.MustCompile(`^(?:[01]\d|2[0-3]):[0-5]\d$`)
	sha256Pattern = regexp.MustCompile(`^[a-f0-9]{64}$`)
)

// ValidationConfig contains the source-scope facts shared by strict
// controlled-transcription adapters. Provider-specific kind/retrieval policy
// remains the responsibility of each adapter's trust boundary.
type ValidationConfig struct {
	SourceCountryCode string
	SourceTimezone    string
	SourceStatus      string
}

// ValidateCandidate applies source-independent candidate checks.
func ValidateCandidate(config ValidationConfig, candidate domain.CandidateSchedule) domain.CandidateValidationReport {
	var report domain.CandidateValidationReport
	errorAt := func(path, code, message string) {
		report.Errors = append(report.Errors, domain.CandidateDiagnostic{Path: path, Code: code, Message: message})
	}
	warnAt := func(path, code, message string) {
		report.Warnings = append(report.Warnings, domain.CandidateDiagnostic{Path: path, Code: code, Message: message})
	}

	if candidate.DataClassification != domain.DataClassificationProduction && candidate.DataClassification != domain.DataClassificationSynthetic {
		errorAt("data_classification", "invalid_classification", "must be production or synthetic")
	}
	if candidate.Mosque.ID == "" || candidate.Mosque.Name == "" {
		errorAt("mosque", "missing_mosque", "mosque ID and name are required")
	}
	if candidate.Mosque.CountryCode != config.SourceCountryCode {
		errorAt("mosque.country_code", "source_scope_mismatch", "must match configured source country")
	}
	if candidate.Mosque.Timezone != config.SourceTimezone {
		errorAt("mosque.timezone", "source_scope_mismatch", "must match configured source timezone")
	}
	if candidate.Mosque.Timezone == "Local" || strings.HasPrefix(candidate.Mosque.Timezone, "+") || strings.HasPrefix(candidate.Mosque.Timezone, "-") {
		errorAt("mosque.timezone", "invalid_timezone", "must be a named IANA timezone")
	} else if _, err := time.LoadLocation(candidate.Mosque.Timezone); err != nil {
		errorAt("mosque.timezone", "invalid_timezone", "must be a loadable IANA timezone")
	}
	if candidate.Source.PermissionStatus != "granted" {
		errorAt("source.permission_status", "permission_not_granted", "controlled import requires granted usage permission")
	}
	if config.SourceStatus != "testing" && config.SourceStatus != "active" {
		errorAt("source.status", "source_not_enabled", "source must be testing or active")
	}
	if !sha256Pattern.MatchString(candidate.Artifact.SHA256) || !sha256Pattern.MatchString(candidate.TranscriptionSHA256) {
		errorAt("artifact", "invalid_sha256", "raw and transcription hashes must be lowercase SHA-256")
	}
	if len(candidate.Days) == 0 {
		errorAt("days", "missing_days", "at least one day is required")
		return report
	}
	if len(candidate.Days) > 732 {
		errorAt("days", "coverage_too_large", "controlled candidate may contain at most 732 days")
	}
	if minimum := candidate.Source.MinimumCoverageDays; minimum > 0 && len(candidate.Days) < minimum {
		errorAt("days", "insufficient_coverage", fmt.Sprintf("has %d days, requires at least %d", len(candidate.Days), minimum))
	}

	seen := make(map[string]struct{}, len(candidate.Days))
	var previousDate time.Time
	for index, day := range candidate.Days {
		path := fmt.Sprintf("days[%d]", index)
		date, err := time.Parse("2006-01-02", day.Date)
		if err != nil || !datePattern.MatchString(day.Date) {
			errorAt(path+".date", "invalid_date", "must be YYYY-MM-DD")
		} else {
			if _, duplicate := seen[day.Date]; duplicate {
				errorAt(path+".date", "duplicate_date", "date must be unique")
			}
			seen[day.Date] = struct{}{}
			if index > 0 && !date.Equal(previousDate.AddDate(0, 0, 1)) {
				errorAt(path+".date", "date_gap", "rows must be contiguous and ordered")
			}
			previousDate = date
		}
		validateDay(&report, index, day)
		for _, flag := range day.Flags {
			if strings.HasPrefix(flag, "source_") || strings.HasPrefix(flag, "requires_review_") {
				warnAt(path+".flags", "source_marker_preserved", flag)
			}
			if len([]rune(flag)) > 128 {
				errorAt(path+".flags", "invalid_flag", "flag must contain at most 128 Unicode code points")
			}
		}
	}

	if candidate.Coverage.From != candidate.Days[0].Date || candidate.Coverage.To != candidate.Days[len(candidate.Days)-1].Date {
		errorAt("coverage", "coverage_mismatch", "must match first and last rows")
	}
	if hasCongregation(candidate.Days) {
		warnAt("days.dhuhr_congregation", "mosque_iqamah_approval_required", "collective-in-mosques values remain separate candidates pending mosque approval")
	}
	appendDeltaWarnings(&report, candidate)
	sort.SliceStable(report.Warnings, func(i, j int) bool {
		if report.Warnings[i].Path == report.Warnings[j].Path {
			return report.Warnings[i].Code < report.Warnings[j].Code
		}
		return report.Warnings[i].Path < report.Warnings[j].Path
	})
	return report
}

func validateDay(report *domain.CandidateValidationReport, index int, day domain.CandidatePrayerDay) {
	values := []struct{ name, value string }{
		{"fajr", day.Fajr}, {"sunrise", day.Sunrise}, {"zenith", day.Zenith},
		{"dhuhr", day.Dhuhr}, {"asr", day.Asr}, {"maghrib", day.Maghrib}, {"isha", day.Isha},
	}
	minutes := make([]int, len(values))
	valid := true
	for valueIndex, value := range values {
		path := fmt.Sprintf("days[%d].%s", index, value.name)
		parsed, ok := parseMinutes(value.value)
		if !ok {
			report.Errors = append(report.Errors, domain.CandidateDiagnostic{Path: path, Code: "invalid_time", Message: "must be HH:MM"})
			valid = false
		}
		minutes[valueIndex] = parsed
	}
	if valid {
		ordered := minutes[0] < minutes[1] && minutes[1] < minutes[3] && minutes[3] < minutes[4] && minutes[4] < minutes[5] && minutes[5] < minutes[6]
		if !ordered || minutes[2] > minutes[3] {
			report.Errors = append(report.Errors, domain.CandidateDiagnostic{Path: fmt.Sprintf("days[%d]", index), Code: "invalid_prayer_order", Message: "daily prayer order is invalid"})
		}
	}
	if day.RecommendedFajr != "" {
		recommended, ok := parseMinutes(day.RecommendedFajr)
		if !ok {
			report.Errors = append(report.Errors, domain.CandidateDiagnostic{Path: fmt.Sprintf("days[%d].recommended_fajr", index), Code: "invalid_time", Message: "must be HH:MM"})
		} else if valid && (recommended <= minutes[0] || recommended >= minutes[1]) {
			report.Errors = append(report.Errors, domain.CandidateDiagnostic{Path: fmt.Sprintf("days[%d].recommended_fajr", index), Code: "invalid_recommended_fajr_order", Message: "must be after Fajr and before sunrise"})
		}
	}
	if day.DhuhrCongregation != "" {
		congregation, ok := parseMinutes(day.DhuhrCongregation)
		if !ok {
			report.Errors = append(report.Errors, domain.CandidateDiagnostic{Path: fmt.Sprintf("days[%d].dhuhr_congregation", index), Code: "invalid_time", Message: "must be HH:MM"})
		} else if valid && (congregation < minutes[3] || congregation > minutes[4]) {
			report.Errors = append(report.Errors, domain.CandidateDiagnostic{Path: fmt.Sprintf("days[%d].dhuhr_congregation", index), Code: "invalid_iqamah_order", Message: "must be between Dhuhr onset and Asr"})
		}
	}
	seenFlags := make(map[string]struct{}, len(day.Flags))
	for _, flag := range day.Flags {
		if flag == "" {
			report.Errors = append(report.Errors, domain.CandidateDiagnostic{Path: fmt.Sprintf("days[%d].flags", index), Code: "invalid_flag", Message: "flag must not be empty"})
		} else if _, duplicate := seenFlags[flag]; duplicate {
			report.Errors = append(report.Errors, domain.CandidateDiagnostic{Path: fmt.Sprintf("days[%d].flags", index), Code: "duplicate_flag", Message: "flags must be unique"})
		}
		seenFlags[flag] = struct{}{}
	}
}

func appendDeltaWarnings(report *domain.CandidateValidationReport, candidate domain.CandidateSchedule) {
	threshold := candidate.Source.MaxDeltaMinutes
	if threshold < 0 {
		return
	}
	fields := []struct {
		name string
		get  func(domain.CandidatePrayerDay) string
	}{
		{"fajr", func(day domain.CandidatePrayerDay) string { return day.Fajr }},
		{"recommended_fajr", func(day domain.CandidatePrayerDay) string { return day.RecommendedFajr }},
		{"sunrise", func(day domain.CandidatePrayerDay) string { return day.Sunrise }},
		{"dhuhr", func(day domain.CandidatePrayerDay) string { return day.Dhuhr }},
		{"dhuhr_congregation", func(day domain.CandidatePrayerDay) string { return day.DhuhrCongregation }},
		{"asr", func(day domain.CandidatePrayerDay) string { return day.Asr }},
		{"maghrib", func(day domain.CandidatePrayerDay) string { return day.Maghrib }},
		{"isha", func(day domain.CandidatePrayerDay) string { return day.Isha }},
	}
	for index := 1; index < len(candidate.Days); index++ {
		for _, field := range fields {
			before, beforeOK := parseMinutes(field.get(candidate.Days[index-1]))
			after, afterOK := parseMinutes(field.get(candidate.Days[index]))
			if !beforeOK || !afterOK {
				continue
			}
			delta := after - before
			if delta < 0 {
				delta = -delta
			}
			if delta > threshold {
				report.Warnings = append(report.Warnings, domain.CandidateDiagnostic{
					Path: fmt.Sprintf("days[%d].%s", index, field.name), Code: "day_to_day_delta",
					Message: fmt.Sprintf("changed by %d minutes; threshold is %d", delta, threshold),
				})
			}
		}
	}
}

func hasCongregation(days []domain.CandidatePrayerDay) bool {
	for _, day := range days {
		if day.DhuhrCongregation != "" {
			return true
		}
	}
	return false
}

func parseMinutes(value string) (int, bool) {
	if !timePattern.MatchString(value) {
		return 0, false
	}
	return int(value[0]-'0')*600 + int(value[1]-'0')*60 + int(value[3]-'0')*10 + int(value[4]-'0'), true
}
