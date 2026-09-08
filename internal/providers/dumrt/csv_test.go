package dumrt_test

import (
	"fmt"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/dumrt"
)

// All prayer values in this file are deliberately synthetic. No authority
// timetable or competitor data is embedded in the test corpus.
func syntheticYear(year int, legacyQibla bool) string {
	var result strings.Builder
	for date := time.Date(year, 1, 1, 0, 0, 0, 0, time.UTC); date.Year() == year; date = date.AddDate(0, 0, 1) {
		fmt.Fprintf(&result, "%s;4:05;05:10;6:15;11:52;12:07;16:22;18:31;20:43", date.Format("02.01.2006"))
		if legacyQibla {
			result.WriteString(";12:34")
		}
		result.WriteString("\r\n")
	}
	return result.String()
}

func TestParseAnnualCSVPreservesPublishedColumnSemantics(t *testing.T) {
	t.Parallel()
	raw := []byte(syntheticYear(2025, true) + syntheticYear(2026, false))
	days, err := dumrt.ParseAnnualCSV(raw, 2026)
	if err != nil {
		t.Fatal(err)
	}
	if len(days) != 365 {
		t.Fatalf("days = %d, want 365", len(days))
	}
	want := domain.CandidatePrayerDay{
		PrayerDay:       domain.PrayerDay{Date: "2026-01-01", Fajr: "04:05", Sunrise: "06:15", Dhuhr: "12:07", Asr: "16:22", Maghrib: "18:31", Isha: "20:43"},
		RecommendedFajr: "05:10", Zenith: "11:52",
	}
	if !reflect.DeepEqual(days[0], want) || days[364].Date != "2026-12-31" {
		t.Fatalf("first=%#v last=%#v", days[0], days[364])
	}
	for _, day := range days {
		if day.DhuhrCongregation != "" || day.HijriDay != 0 || day.HijriYear != 0 || day.HijriMonth != "" {
			t.Fatalf("invented unpublished fields: %#v", day)
		}
	}
	again, err := dumrt.ParseAnnualCSV(raw, 2026)
	if err != nil || !reflect.DeepEqual(days, again) {
		t.Fatalf("non-deterministic output: %v", err)
	}
}

func TestParseAnnualCSVRetainsLeapDayAndAcceptsLF(t *testing.T) {
	t.Parallel()
	days, err := dumrt.ParseAnnualCSV([]byte(strings.ReplaceAll(syntheticYear(2024, false), "\r\n", "\n")), 2024)
	if err != nil {
		t.Fatal(err)
	}
	if len(days) != 366 || days[59].Date != "2024-02-29" {
		t.Fatalf("leap coverage = %d / %#v", len(days), days)
	}
}

func TestParseCSVRangeRequiresExplicitCompleteBoundedCoverage(t *testing.T) {
	t.Parallel()
	// An unrepresentable time elsewhere in an annual transport cannot be
	// silently repaired or included in a smaller qualified snapshot.
	raw := strings.Replace(syntheticYear(2026, false), "05.05.2026;4:05", "05.05.2026;23:56", 1)
	if _, err := dumrt.ParseAnnualCSV([]byte(raw), 2026); err == nil {
		t.Fatal("unrepresentable annual timetable accepted")
	}
	september := domain.DateRange{From: "2026-09-01", To: "2026-09-30"}
	days, err := dumrt.ParseCSVRange([]byte(raw), september)
	if err != nil {
		t.Fatal(err)
	}
	if len(days) != 30 || days[0].Date != september.From || days[29].Date != september.To {
		t.Fatalf("explicit partial coverage = %#v", days)
	}
	for _, invalid := range []domain.DateRange{
		{From: "2026-05-01", To: "2026-05-31"},
		{From: "2026-09-30", To: "2026-09-01"},
		{From: "2026-12-01", To: "2027-01-01"},
		{From: "2026-9-01", To: "2026-09-30"},
		{},
	} {
		if days, err := dumrt.ParseCSVRange([]byte(raw), invalid); err == nil || days != nil {
			t.Fatalf("invalid requested coverage %#v accepted", invalid)
		}
	}
}

func TestParseAnnualCSVFailsClosedWithoutPartialOutput(t *testing.T) {
	t.Parallel()
	valid := syntheticYear(2026, false)
	rows := strings.Split(strings.TrimSuffix(valid, "\r\n"), "\r\n")
	cases := map[string]string{
		"empty":                     "",
		"wrong_year":                syntheticYear(2025, true),
		"gap":                       strings.Join(append(append([]string{}, rows[:150]...), rows[151:]...), "\n"),
		"duplicate":                 valid + rows[364] + "\n",
		"out_of_order":              rows[1] + "\n" + rows[0] + "\n" + strings.Join(rows[2:], "\n"),
		"extra_column":              strings.Replace(valid, "20:43", "20:43;12:34", 1),
		"missing_column":            strings.Replace(valid, ";11:52", "", 1),
		"unknown_marker":            strings.Replace(valid, "4:05", "4:05*", 1),
		"invalid_clock":             strings.Replace(valid, "4:05", "24:05", 1),
		"invalid_minutes":           strings.Replace(valid, "4:05", "4:65", 1),
		"unpad_minutes":             strings.Replace(valid, "4:05", "4:5", 1),
		"invalid_date":              strings.Replace(valid, "01.01.2026", "31.02.2026", 1),
		"noncanonical_date":         strings.Replace(valid, "01.01.2026", "1.1.2026", 1),
		"unexpected_header":         "date;fajr;recommended_fajr;sunrise;zenith;dhuhr;asr;maghrib;isha\n" + valid,
		"blank_row":                 strings.Replace(valid, "\r\n", "\r\n\r\n", 1),
		"utf8":                      "\xff" + valid,
		"oversize":                  strings.Repeat(" ", 2*1024*1024),
		"fajr_after_sunrise":        strings.Replace(valid, "4:05", "7:05", 1),
		"recommended_before_fajr":   strings.Replace(valid, "05:10", "03:10", 1),
		"recommended_after_sunrise": strings.Replace(valid, "05:10", "07:10", 1),
		"zenith_after_dhuhr":        strings.Replace(valid, "11:52", "12:52", 1),
		"asr_before_dhuhr":          strings.Replace(valid, "16:22", "11:22", 1),
		"unmodelled_next_day_isha":  strings.Replace(valid, "20:43", "00:43", 1),
		"unexpected_old_year":       syntheticYear(2024, false) + valid,
		"future_year":               valid + syntheticYear(2027, false),
	}
	for name, input := range cases {
		t.Run(name, func(t *testing.T) {
			days, err := dumrt.ParseAnnualCSV([]byte(input), 2026)
			if err == nil || days != nil {
				t.Fatalf("drift returned days=%d, error=%v", len(days), err)
			}
		})
	}
	for _, year := range []int{0, 99, 1999, 2101} {
		if days, err := dumrt.ParseAnnualCSV([]byte(valid), year); err == nil || days != nil {
			t.Fatalf("unsupported year %d accepted", year)
		}
	}
}
