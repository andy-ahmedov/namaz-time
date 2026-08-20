// Package publication owns approval-bound diffing, deterministic snapshot
// construction and signing. Providers cannot call themselves approved.
package publication

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"sort"
	"strconv"
	"strings"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

type DiffChange struct {
	Date   string `json:"date"`
	Field  string `json:"field"`
	Before string `json:"before,omitempty"`
	After  string `json:"after,omitempty"`
}

type MetadataChange struct {
	Field  string `json:"field"`
	Before string `json:"before,omitempty"`
	After  string `json:"after,omitempty"`
}

type PrayerDeltaSummary struct {
	Prayer         string  `json:"prayer"`
	ChangedValues  int     `json:"changed_values"`
	MaximumMinutes int     `json:"maximum_minutes"`
	MedianMinutes  float64 `json:"median_minutes"`
}

type DiffReport struct {
	PreviousCandidateID       string                       `json:"previous_candidate_id,omitempty"`
	CandidateID               string                       `json:"candidate_id"`
	PreviousRawSHA256         string                       `json:"previous_raw_sha256,omitempty"`
	CandidateRawSHA256        string                       `json:"candidate_raw_sha256"`
	CandidateNormalizedSHA256 string                       `json:"candidate_normalized_sha256"`
	PreviousParserVersion     string                       `json:"previous_parser_version,omitempty"`
	ParserVersion             string                       `json:"parser_version"`
	Coverage                  domain.DateRange             `json:"coverage"`
	ChangedDays               int                          `json:"changed_days"`
	Changes                   []DiffChange                 `json:"changes"`
	MetadataChanges           []MetadataChange             `json:"metadata_changes"`
	PrayerDeltas              []PrayerDeltaSummary         `json:"prayer_deltas"`
	Warnings                  []domain.CandidateDiagnostic `json:"warnings"`
	SHA256                    string                       `json:"sha256"`
}

func Diff(previous *domain.CandidateSchedule, current domain.CandidateSchedule) (DiffReport, error) {
	if current.ID == "" || current.Artifact.SHA256 == "" || current.NormalizedSHA256 == "" || current.ParserVersion == "" {
		return DiffReport{}, newError("build diff", "candidate_incomplete", fmt.Errorf("candidate identity and provenance are required"))
	}
	report := DiffReport{
		CandidateID:               current.ID,
		CandidateRawSHA256:        current.Artifact.SHA256,
		CandidateNormalizedSHA256: current.NormalizedSHA256,
		ParserVersion:             current.ParserVersion,
		Coverage:                  current.Coverage,
		Warnings:                  append([]domain.CandidateDiagnostic(nil), current.Validation.Warnings...),
	}
	var beforeDays []domain.CandidatePrayerDay
	if previous != nil {
		report.PreviousCandidateID = previous.ID
		report.PreviousRawSHA256 = previous.Artifact.SHA256
		report.PreviousParserVersion = previous.ParserVersion
		beforeDays = previous.Days
		report.MetadataChanges = metadataDiff(*previous, current)
	}
	report.Changes = dayDiff(beforeDays, current.Days)
	changedDates := make(map[string]struct{})
	for _, change := range report.Changes {
		changedDates[change.Date] = struct{}{}
	}
	report.ChangedDays = len(changedDates)
	report.PrayerDeltas = prayerDeltaSummaries(report.Changes)
	sort.Slice(report.Warnings, func(i, j int) bool {
		if report.Warnings[i].Path == report.Warnings[j].Path {
			return report.Warnings[i].Code < report.Warnings[j].Code
		}
		return report.Warnings[i].Path < report.Warnings[j].Path
	})
	report.SHA256 = diffHash(report)
	return report, nil
}

func prayerDeltaSummaries(changes []DiffChange) []PrayerDeltaSummary {
	byPrayer := make(map[string][]int)
	for _, change := range changes {
		before, beforeOK := clockMinutes(change.Before)
		after, afterOK := clockMinutes(change.After)
		if !beforeOK || !afterOK || !isPrayerField(change.Field) {
			continue
		}
		delta := after - before
		if delta < 0 {
			delta = -delta
		}
		byPrayer[change.Field] = append(byPrayer[change.Field], delta)
	}
	prayers := make([]string, 0, len(byPrayer))
	for prayer := range byPrayer {
		prayers = append(prayers, prayer)
	}
	sort.Strings(prayers)
	summaries := make([]PrayerDeltaSummary, 0, len(prayers))
	for _, prayer := range prayers {
		values := byPrayer[prayer]
		sort.Ints(values)
		median := float64(values[len(values)/2])
		if len(values)%2 == 0 {
			median = float64(values[len(values)/2-1]+values[len(values)/2]) / 2
		}
		summaries = append(summaries, PrayerDeltaSummary{
			Prayer: prayer, ChangedValues: len(values),
			MaximumMinutes: values[len(values)-1], MedianMinutes: median,
		})
	}
	return summaries
}

func isPrayerField(field string) bool {
	switch field {
	case "fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha":
		return true
	default:
		return false
	}
}

func clockMinutes(value string) (int, bool) {
	if len(value) != 5 || value[2] != ':' || value[0] < '0' || value[0] > '2' || value[1] < '0' || value[1] > '9' || value[3] < '0' || value[3] > '5' || value[4] < '0' || value[4] > '9' {
		return 0, false
	}
	hour := int(value[0]-'0')*10 + int(value[1]-'0')
	if hour > 23 {
		return 0, false
	}
	return hour*60 + int(value[3]-'0')*10 + int(value[4]-'0'), true
}

func dayDiff(before, after []domain.CandidatePrayerDay) []DiffChange {
	beforeByDate := make(map[string]domain.CandidatePrayerDay, len(before))
	afterByDate := make(map[string]domain.CandidatePrayerDay, len(after))
	dateSet := make(map[string]struct{}, len(before)+len(after))
	for _, day := range before {
		beforeByDate[day.Date] = day
		dateSet[day.Date] = struct{}{}
	}
	for _, day := range after {
		afterByDate[day.Date] = day
		dateSet[day.Date] = struct{}{}
	}
	dates := make([]string, 0, len(dateSet))
	for date := range dateSet {
		dates = append(dates, date)
	}
	sort.Strings(dates)
	fields := []struct {
		name string
		get  func(domain.CandidatePrayerDay) string
	}{
		{"fajr", func(day domain.CandidatePrayerDay) string { return day.Fajr }},
		{"sunrise", func(day domain.CandidatePrayerDay) string { return day.Sunrise }},
		{"zenith", func(day domain.CandidatePrayerDay) string { return day.Zenith }},
		{"dhuhr", func(day domain.CandidatePrayerDay) string { return day.Dhuhr }},
		{"dhuhr_congregation", func(day domain.CandidatePrayerDay) string { return day.DhuhrCongregation }},
		{"asr", func(day domain.CandidatePrayerDay) string { return day.Asr }},
		{"maghrib", func(day domain.CandidatePrayerDay) string { return day.Maghrib }},
		{"isha", func(day domain.CandidatePrayerDay) string { return day.Isha }},
		{"duha", func(day domain.CandidatePrayerDay) string { return day.Duha }},
		{"middle_of_night", func(day domain.CandidatePrayerDay) string { return day.MiddleOfNight }},
		{"last_third_of_night", func(day domain.CandidatePrayerDay) string { return day.LastThirdOfNight }},
		{"flags", func(day domain.CandidatePrayerDay) string { return strings.Join(day.Flags, ",") }},
		{"hijri_day", func(day domain.CandidatePrayerDay) string { return strconv.Itoa(day.HijriDay) }},
		{"hijri_month", func(day domain.CandidatePrayerDay) string { return day.HijriMonth }},
		{"hijri_year", func(day domain.CandidatePrayerDay) string { return strconv.Itoa(day.HijriYear) }},
	}
	changes := make([]DiffChange, 0)
	for _, date := range dates {
		beforeDay := beforeByDate[date]
		afterDay := afterByDate[date]
		for _, field := range fields {
			beforeValue := field.get(beforeDay)
			afterValue := field.get(afterDay)
			if beforeValue != afterValue {
				changes = append(changes, DiffChange{Date: date, Field: field.name, Before: beforeValue, After: afterValue})
			}
		}
	}
	return changes
}

func metadataDiff(before, after domain.CandidateSchedule) []MetadataChange {
	values := []struct{ field, before, after string }{
		{"data_classification", string(before.DataClassification), string(after.DataClassification)},
		{"mosque.id", before.Mosque.ID, after.Mosque.ID},
		{"mosque.name", before.Mosque.Name, after.Mosque.Name},
		{"mosque.country_code", before.Mosque.CountryCode, after.Mosque.CountryCode},
		{"mosque.region", before.Mosque.Region, after.Mosque.Region},
		{"mosque.locality", before.Mosque.Locality, after.Mosque.Locality},
		{"mosque.timezone", before.Mosque.Timezone, after.Mosque.Timezone},
		{"source.source_id", before.Source.SourceID, after.Source.SourceID},
		{"source.kind", string(before.Source.Kind), string(after.Source.Kind)},
		{"source.authority_name", before.Source.AuthorityName, after.Source.AuthorityName},
		{"source.authority_branch", before.Source.AuthorityBranch, after.Source.AuthorityBranch},
		{"source.geographic_scope", before.Source.GeographicScope, after.Source.GeographicScope},
		{"source.canonical_url", before.Source.CanonicalURL, after.Source.CanonicalURL},
		{"source.permission_status", before.Source.PermissionStatus, after.Source.PermissionStatus},
		{"source.license_reference", before.Source.LicenseReference, after.Source.LicenseReference},
		{"source.attribution", before.Source.Attribution, after.Source.Attribution},
		{"source.approval_required", strconv.FormatBool(before.Source.ApprovalRequired), strconv.FormatBool(after.Source.ApprovalRequired)},
		{"source.max_delta_minutes", strconv.Itoa(before.Source.MaxDeltaMinutes), strconv.Itoa(after.Source.MaxDeltaMinutes)},
		{"source.minimum_coverage_days", strconv.Itoa(before.Source.MinimumCoverageDays), strconv.Itoa(after.Source.MinimumCoverageDays)},
		{"artifact.filename", before.Artifact.Filename, after.Artifact.Filename},
		{"artifact.content_type", before.Artifact.ContentType, after.Artifact.ContentType},
		{"artifact.captured_at", before.Artifact.CapturedAt, after.Artifact.CapturedAt},
		{"artifact.byte_length", strconv.FormatInt(before.Artifact.ByteLength, 10), strconv.FormatInt(after.Artifact.ByteLength, 10)},
		{"artifact.sha256", before.Artifact.SHA256, after.Artifact.SHA256},
		{"transcription_sha256", before.TranscriptionSHA256, after.TranscriptionSHA256},
		{"parser_version", before.ParserVersion, after.ParserVersion},
		{"coverage.from", before.Coverage.From, after.Coverage.From},
		{"coverage.to", before.Coverage.To, after.Coverage.To},
	}
	changes := make([]MetadataChange, 0)
	for _, value := range values {
		if value.before != value.after {
			changes = append(changes, MetadataChange{Field: value.field, Before: value.before, After: value.after})
		}
	}
	return changes
}

func diffHash(report DiffReport) string {
	report.SHA256 = ""
	data, _ := json.Marshal(report)
	hash := sha256.Sum256(data)
	return hex.EncodeToString(hash[:])
}
