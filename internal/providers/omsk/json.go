// Package omsk normalizes the Omsk authority's public JSON timetable artifact.
// Retrieval, qualification and publication are deliberately separate.
package omsk

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

const (
	ParserVersion    = "omsk-city-json/v1"
	SourceCity       = "Омск"
	SourceTimezone   = "Asia/Omsk"
	maxArtifactBytes = 1024 * 1024
)

type artifact struct {
	City      string                     `json:"city"`
	HijriYear string                     `json:"hijriYear"`
	Months    map[string]json.RawMessage `json:"months"`
	Schedule  map[string]json.RawMessage `json:"schedule"`
}

type monthMetadata struct {
	PDF     string `json:"pdf"`
	Image   string `json:"image"`
	WebP    string `json:"webp"`
	Width   int    `json:"w"`
	Height  int    `json:"h"`
	Hijri   string `json:"hijri"`
	Updated string `json:"updated"`
}

type prayerRow struct {
	Suhur   string `json:"suhur"`
	Fajr    string `json:"fajr"`
	Sunrise string `json:"sunrise"`
	Dhuhr   string `json:"dhuhr"`
	Asr     string `json:"asr"`
	Maghrib string `json:"maghrib"`
	Isha    string `json:"isha"`
	Hijri   string `json:"hijri"`
}

// ParseJSON validates the whole artifact, including rows outside coverage, and
// returns only the explicit complete range (at most 366 days). Every declared
// month must contain all its Gregorian days. Unknown keys, case changes,
// duplicate keys, incomplete months and next-day clock values fail closed.
//
// Suhur is validated but has no CandidatePrayerDay field; it never replaces
// Fajr. The global Hijri-year banner is not a per-row year, especially across
// the lunar new year: only the explicit row Hijri day/month are normalized.
// The caller must bind SourceCity/SourceTimezone, capture provenance and obtain
// machine-verifiable qualification; parser success cannot publish a schedule.
func ParseJSON(raw []byte, coverage domain.DateRange) ([]domain.CandidatePrayerDay, error) {
	start, startErr := canonicalDate(coverage.From)
	last, lastErr := canonicalDate(coverage.To)
	if startErr != nil || lastErr != nil || last.Before(start) || last.Sub(start) > 365*24*time.Hour {
		return nil, errors.New("omsk JSON: explicit canonical coverage of 1 through 366 days is required")
	}
	if len(raw) == 0 || len(raw) > maxArtifactBytes || !utf8.Valid(raw) {
		return nil, errors.New("omsk JSON: expected bounded nonempty UTF-8 artifact")
	}
	if err := strictjson.RejectDuplicateObjectMembers(raw); err != nil {
		return nil, errors.New("omsk JSON: invalid JSON or duplicate object member")
	}
	var source artifact
	if err := exactObject(raw, &source, "city", "hijriYear", "months", "schedule"); err != nil {
		return nil, errors.New("omsk JSON: unsupported root schema")
	}
	if source.City != SourceCity || !validYearBanner(source.HijriYear) {
		return nil, errors.New("omsk JSON: invalid city or Hijri display metadata")
	}
	if len(source.Months) < 1 || len(source.Months) > 24 || len(source.Schedule) < 1 || len(source.Schedule) > 732 {
		return nil, errors.New("omsk JSON: expected 1 through 24 nonempty monthly calendars")
	}
	monthCounts := make(map[string]int, len(source.Months))
	for _, key := range sortedKeys(source.Months) {
		first, err := canonicalDate(key + "-01")
		if err != nil || len(key) != 7 {
			return nil, errors.New("omsk JSON: invalid month key")
		}
		var metadata monthMetadata
		if err := exactObject(source.Months[key], &metadata, "pdf", "image", "webp", "w", "h", "hijri", "updated"); err != nil {
			return nil, fmt.Errorf("omsk JSON month %s: unsupported metadata schema", key)
		}
		prefix := "files/raspisanie-namazov-" + key
		_, updatedErr := canonicalDate(metadata.Updated)
		if metadata.PDF != prefix+".pdf" || metadata.Image != prefix+".jpg" || metadata.WebP != prefix+".webp" ||
			metadata.Width < 1 || metadata.Width > 10000 || metadata.Height < 1 || metadata.Height > 10000 ||
			!validMonthText(metadata.Hijri, 160) || updatedErr != nil {
			return nil, fmt.Errorf("omsk JSON month %s: invalid file, dimensions or calendar metadata", key)
		}
		monthCounts[key] = first.AddDate(0, 1, -1).Day()
	}
	daysByDate := make(map[string]domain.CandidatePrayerDay, len(source.Schedule))
	for _, key := range sortedKeys(source.Schedule) {
		date, err := canonicalDate(key)
		if err != nil {
			return nil, errors.New("omsk JSON: invalid schedule date")
		}
		month := date.Format("2006-01")
		if _, exists := monthCounts[month]; !exists {
			return nil, fmt.Errorf("omsk JSON date %s: undeclared month", key)
		}
		var row prayerRow
		if err := exactObject(source.Schedule[key], &row, "suhur", "fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha", "hijri"); err != nil {
			return nil, fmt.Errorf("omsk JSON date %s: unsupported row schema", key)
		}
		for _, clock := range []string{row.Suhur, row.Fajr, row.Sunrise, row.Dhuhr, row.Asr, row.Maghrib, row.Isha} {
			if !validClock(clock) {
				return nil, fmt.Errorf("omsk JSON date %s: invalid canonical clock", key)
			}
		}
		if !(row.Suhur <= row.Fajr && row.Fajr < row.Sunrise && row.Sunrise < row.Dhuhr &&
			row.Dhuhr < row.Asr && row.Asr < row.Maghrib && row.Maghrib < row.Isha) {
			return nil, fmt.Errorf("omsk JSON date %s: invalid time order or unsupported day crossing", key)
		}
		hijriDay, hijriMonth, err := explicitHijri(row.Hijri)
		if err != nil {
			return nil, fmt.Errorf("omsk JSON date %s: invalid explicit Hijri day or month", key)
		}
		daysByDate[key] = domain.CandidatePrayerDay{
			PrayerDay: domain.PrayerDay{Date: key, Fajr: row.Fajr, Sunrise: row.Sunrise, Dhuhr: row.Dhuhr,
				Asr: row.Asr, Maghrib: row.Maghrib, Isha: row.Isha},
			HijriDay: hijriDay, HijriMonth: hijriMonth,
		}
		monthCounts[month]--
	}
	for _, key := range sortedKeys(source.Months) {
		if monthCounts[key] != 0 {
			return nil, fmt.Errorf("omsk JSON month %s: incomplete Gregorian month", key)
		}
	}
	days := make([]domain.CandidatePrayerDay, 0, int(last.Sub(start)/(24*time.Hour))+1)
	for date := start; !date.After(last); date = date.AddDate(0, 0, 1) {
		day, exists := daysByDate[date.Format(time.DateOnly)]
		if !exists {
			return nil, errors.New("omsk JSON: gap in requested coverage")
		}
		days = append(days, day)
	}
	return days, nil
}

// encoding/json alone accepts case-insensitive struct field names. Check exact
// member sets before typed decoding so case drift and missing/null objects fail.
func exactObject(raw []byte, value any, keys ...string) error {
	var members map[string]json.RawMessage
	if err := json.Unmarshal(raw, &members); err != nil || members == nil || len(members) != len(keys) {
		return errors.New("invalid object member set")
	}
	for _, key := range keys {
		member, exists := members[key]
		if !exists || bytes.Equal(bytes.TrimSpace(member), []byte("null")) {
			return errors.New("missing or null object member")
		}
	}
	return json.Unmarshal(raw, value)
}

func sortedKeys(values map[string]json.RawMessage) []string {
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	return keys
}

func canonicalDate(value string) (time.Time, error) {
	date, err := time.Parse(time.DateOnly, value)
	if err != nil || date.Format(time.DateOnly) != value || date.Year() < 2000 || date.Year() > 2100 {
		return time.Time{}, errors.New("invalid Gregorian date")
	}
	return date, nil
}

func validClock(value string) bool {
	if len(value) != 5 || value[2] != ':' {
		return false
	}
	for _, index := range []int{0, 1, 3, 4} {
		if value[index] < '0' || value[index] > '9' {
			return false
		}
	}
	return value[:2] <= "23" && value[3:] <= "59"
}

func validYearBanner(value string) bool {
	if len(value) < 4 || value[4:] != " г.х." {
		return false
	}
	year, err := strconv.Atoi(value[:4])
	return err == nil && year >= 1000 && year <= 1999 && strconv.Itoa(year) == value[:4]
}

func validMonthText(value string, maxRunes int) bool {
	if value == "" || value != strings.TrimSpace(value) || strings.Contains(value, "  ") || utf8.RuneCountInString(value) > maxRunes {
		return false
	}
	hasLetter := false
	for _, char := range value {
		if unicode.IsLetter(char) {
			hasLetter = true
			continue
		}
		if char != ' ' && char != '-' && char != '–' && char != '\'' && char != '’' {
			return false
		}
	}
	return hasLetter
}

func explicitHijri(value string) (int, string, error) {
	parts := strings.SplitN(value, " ", 2)
	if len(parts) != 2 {
		return 0, "", errors.New("missing Hijri fields")
	}
	day, err := strconv.Atoi(parts[0])
	if err != nil || day < 1 || day > 30 || strconv.Itoa(day) != parts[0] || !validMonthText(parts[1], 64) {
		return 0, "", errors.New("invalid Hijri fields")
	}
	return day, parts[1], nil
}
