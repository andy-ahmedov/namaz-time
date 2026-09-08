package omsk_test

import (
	"encoding/json"
	"fmt"
	"reflect"
	"sort"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/omsk"
)

// Deliberately synthetic values, not a copy of any published prayer timetable.
func syntheticMonths(t *testing.T, months ...string) string {
	t.Helper()
	metadata := map[string]any{}
	schedule := map[string]any{}
	for _, month := range months {
		start, err := time.Parse("2006-01", month)
		if err != nil {
			t.Fatal(err)
		}
		metadata[month] = map[string]any{
			"pdf":   "files/raspisanie-namazov-" + month + ".pdf",
			"image": "files/raspisanie-namazov-" + month + ".jpg",
			"webp":  "files/raspisanie-namazov-" + month + ".webp",
			"w":     1200, "h": 1700, "hijri": "Мухаррам – Сафар", "updated": "2028-01-01",
		}
		for day := start; day.Month() == start.Month(); day = day.AddDate(0, 0, 1) {
			schedule[day.Format(time.DateOnly)] = map[string]any{
				"suhur": "03:37", "fajr": "04:07", "sunrise": "06:17", "dhuhr": "12:27",
				"asr": "16:37", "maghrib": "18:47", "isha": "20:57", "hijri": "12 Мухаррам",
			}
		}
	}
	raw, err := json.Marshal(map[string]any{"city": "Омск", "hijriYear": "1448 г.х.", "months": metadata, "schedule": schedule})
	if err != nil {
		t.Fatal(err)
	}
	return string(raw)
}

func TestParseJSONPreservesExplicitFieldsAndSelectedCoverage(t *testing.T) {
	t.Parallel()
	raw := []byte(syntheticMonths(t, "2028-02", "2028-03"))
	coverage := domain.DateRange{From: "2028-02-28", To: "2028-03-02"}
	days, err := omsk.ParseJSON(raw, coverage)
	if err != nil {
		t.Fatal(err)
	}
	want := domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{
		Date: "2028-02-28", Fajr: "04:07", Sunrise: "06:17", Dhuhr: "12:27", Asr: "16:37", Maghrib: "18:47", Isha: "20:57",
	}, HijriDay: 12, HijriMonth: "Мухаррам"}
	if len(days) != 4 || !reflect.DeepEqual(days[0], want) || days[1].Date != "2028-02-29" || days[3].Date != coverage.To {
		t.Fatalf("unexpected normalized coverage or semantics: %#v", days)
	}
	for _, day := range days {
		if day.HijriYear != 0 || day.DhuhrCongregation != "" || day.RecommendedFajr != "" || day.Zenith != "" {
			t.Fatalf("invented year, iqamah or unpublished field: %#v", day)
		}
	}
	again, err := omsk.ParseJSON(raw, coverage)
	if err != nil || !reflect.DeepEqual(days, again) {
		t.Fatalf("nondeterministic output: %v", err)
	}
	if omsk.SourceCity != "Омск" || omsk.SourceTimezone != "Asia/Omsk" || omsk.ParserVersion == "" {
		t.Fatal("missing pinned source metadata")
	}
}

func TestParseJSONYearRolloverAndExplicitHijriWithoutYearInference(t *testing.T) {
	t.Parallel()
	raw := syntheticMonths(t, "2027-12", "2028-01")
	raw = strings.Replace(raw, `"hijri":"12 Мухаррам"`, `"hijri":"30 Зуль-хиджа"`, 1)
	days, err := omsk.ParseJSON([]byte(raw), domain.DateRange{From: "2027-12-31", To: "2028-01-01"})
	if err != nil || len(days) != 2 || days[0].Date != "2027-12-31" || days[1].Date != "2028-01-01" {
		t.Fatalf("rollover: %#v / %v", days, err)
	}
	if days[0].HijriYear != 0 || days[1].HijriYear != 0 {
		t.Fatal("global display year assigned to a row")
	}
	first, err := omsk.ParseJSON([]byte(raw), domain.DateRange{From: "2027-12-01", To: "2027-12-01"})
	if err != nil || first[0].HijriDay != 30 || first[0].HijriMonth != "Зуль-хиджа" {
		t.Fatalf("explicit Hijri fields lost: %#v / %v", first, err)
	}
}

func TestParseJSONFailsClosedOnSchemaDatesAndMetadata(t *testing.T) {
	t.Parallel()
	valid := syntheticMonths(t, "2028-02")
	coverage := domain.DateRange{From: "2028-02-01", To: "2028-02-29"}
	mutate := func(change func(map[string]any)) string {
		var value map[string]any
		if err := json.Unmarshal([]byte(valid), &value); err != nil {
			t.Fatal(err)
		}
		change(value)
		out, err := json.Marshal(value)
		if err != nil {
			t.Fatal(err)
		}
		return string(out)
	}
	cases := map[string]string{
		"empty": "", "empty_object": `{}`, "null": `null`, "array": `[]`, "trailing_value": valid + ` {}`,
		"invalid_utf8": valid + string([]byte{0xff}), "oversize": strings.Repeat(" ", 1024*1024+1),
		"duplicate_root":       strings.Replace(valid, `"city":"Омск"`, `"city":"Омск","city":"Омск"`, 1),
		"escaped_duplicate":    strings.Replace(valid, `"city":"Омск"`, `"city":"Омск","\u0063ity":"Омск"`, 1),
		"duplicate_row_member": strings.Replace(valid, `"fajr":"04:07"`, `"fajr":"04:07","fajr":"04:07"`, 1),
		"duplicate_month":      strings.Replace(valid, `"2028-02":`, `"2028-02":{},"2028-02":`, 1),
		"duplicate_date":       strings.Replace(valid, `"2028-02-01":`, `"2028-02-01":{},"2028-02-01":`, 1),
		"case_drift":           strings.Replace(valid, `"city"`, `"City"`, 1),
		"wrong_city":           strings.Replace(valid, "Омск", "Томск", 1),
		"wrong_city_type":      strings.Replace(valid, `"city":"Омск"`, `"city":123`, 1),
		"unknown_timezone":     mutate(func(x map[string]any) { x["timezone"] = "Asia/Omsk" }),
		"missing_banner":       mutate(func(x map[string]any) { delete(x, "hijriYear") }),
		"invalid_banner":       strings.Replace(valid, "1448 г.х.", "1448/1449", 1),
		"null_schedule":        mutate(func(x map[string]any) { x["schedule"] = nil }),
		"null_months":          mutate(func(x map[string]any) { x["months"] = nil }),
		"gap":                  mutate(func(x map[string]any) { delete(x["schedule"].(map[string]any), "2028-02-15") }),
		"null_row":             mutate(func(x map[string]any) { x["schedule"].(map[string]any)["2028-02-01"] = nil }),
		"row_unknown_field": mutate(func(x map[string]any) {
			x["schedule"].(map[string]any)["2028-02-01"].(map[string]any)["iqamah"] = "13:00"
		}),
		"row_missing_field":     mutate(func(x map[string]any) { delete(x["schedule"].(map[string]any)["2028-02-01"].(map[string]any), "suhur") }),
		"bad_date":              strings.Replace(valid, `"2028-02-29":`, `"2028-02-30":`, 1),
		"noncanonical_date":     strings.Replace(valid, `"2028-02-01":`, `"2028-2-01":`, 1),
		"undeclared_month":      strings.Replace(valid, `"2028-02-01":`, `"2028-03-01":`, 1),
		"metadata_unknown":      mutate(func(x map[string]any) { x["months"].(map[string]any)["2028-02"].(map[string]any)["source"] = "other" }),
		"null_metadata":         mutate(func(x map[string]any) { x["months"].(map[string]any)["2028-02"] = nil }),
		"bad_month_key":         strings.Replace(valid, `"2028-02":`, `"2028-13":`, 1),
		"external_file":         strings.Replace(valid, `files/raspisanie-namazov-2028-02.pdf`, `https://other.invalid/calendar.pdf`, 1),
		"wrong_month_file":      strings.Replace(valid, `files/raspisanie-namazov-2028-02.jpg`, `files/raspisanie-namazov-2028-03.jpg`, 1),
		"missing_file":          mutate(func(x map[string]any) { delete(x["months"].(map[string]any)["2028-02"].(map[string]any), "pdf") }),
		"zero_dimensions":       strings.Replace(valid, `"w":1200`, `"w":0`, 1),
		"fractional_dimensions": strings.Replace(valid, `"w":1200`, `"w":1.5`, 1),
		"invalid_updated":       strings.Replace(valid, `"updated":"2028-01-01"`, `"updated":"2028-02-30"`, 1),
		"missing_hijri":         strings.Replace(valid, `"hijri":"12 Мухаррам"`, `"hijri":""`, 1),
		"hijri_day_zero":        strings.Replace(valid, `12 Мухаррам`, `0 Мухаррам`, 1),
		"hijri_day_31":          strings.Replace(valid, `12 Мухаррам`, `31 Мухаррам`, 1),
		"hijri_control":         strings.Replace(valid, `12 Мухаррам`, `12 Му\nхаррам`, 1),
		"hijri_inferred_year":   strings.Replace(valid, `12 Мухаррам`, `12 Мухаррам 1448`, 1),
	}
	for name, raw := range cases {
		t.Run(name, func(t *testing.T) {
			days, err := omsk.ParseJSON([]byte(raw), coverage)
			if err == nil || days != nil {
				t.Fatalf("accepted invalid artifact or leaked partial output: %#v / %v", days, err)
			}
		})
	}
}

func TestParseJSONDoesNotDependOnObjectKeyOrder(t *testing.T) {
	t.Parallel()
	raw := syntheticMonths(t, "2028-02")
	var root map[string]json.RawMessage
	if err := json.Unmarshal([]byte(raw), &root); err != nil {
		t.Fatal(err)
	}
	var schedule map[string]json.RawMessage
	if err := json.Unmarshal(root["schedule"], &schedule); err != nil {
		t.Fatal(err)
	}
	keys := make([]string, 0, len(schedule))
	for key := range schedule {
		keys = append(keys, key)
	}
	sort.Sort(sort.Reverse(sort.StringSlice(keys)))
	rows := make([]string, 0, len(keys))
	for _, key := range keys {
		rows = append(rows, fmt.Sprintf("%q:%s", key, schedule[key]))
	}
	reordered := fmt.Sprintf(`{"schedule":{%s},"months":%s,"hijriYear":%s,"city":%s}`, strings.Join(rows, ","), root["months"], root["hijriYear"], root["city"])
	coverage := domain.DateRange{From: "2028-02-01", To: "2028-02-29"}
	want, err := omsk.ParseJSON([]byte(raw), coverage)
	if err != nil {
		t.Fatal(err)
	}
	got, err := omsk.ParseJSON([]byte(reordered), coverage)
	if err != nil || !reflect.DeepEqual(got, want) {
		t.Fatalf("JSON member order affected normalization: %v", err)
	}
}

func TestParseJSONRejectsInvalidClocksAndAllArtifactRows(t *testing.T) {
	t.Parallel()
	valid := syntheticMonths(t, "2028-02", "2028-03")
	// The requested range is March; corrupt February must still fail closed.
	coverage := domain.DateRange{From: "2028-03-01", To: "2028-03-31"}
	for _, clock := range []string{"4:07", "24:07", "04:60", "04:07:00", " 04:07", "04:07*", "０４:０７", "06:17", "23:59"} {
		t.Run(clock, func(t *testing.T) {
			raw := strings.Replace(valid, `"fajr":"04:07"`, fmt.Sprintf(`"fajr":%q`, clock), 1)
			if days, err := omsk.ParseJSON([]byte(raw), coverage); err == nil || days != nil {
				t.Fatalf("bad clock accepted: %#v / %v", days, err)
			}
		})
	}
	for _, pair := range [][2]string{{`"suhur":"03:37"`, `"suhur":"04:08"`}, {`"isha":"20:57"`, `"isha":"00:57"`}, {`"asr":"16:37"`, `"asr":"12:27"`}} {
		if days, err := omsk.ParseJSON([]byte(strings.Replace(valid, pair[0], pair[1], 1)), coverage); err == nil || days != nil {
			t.Fatalf("time order accepted: %v", err)
		}
	}
	// An equal published suhur/Fajr boundary is not an instruction to replace Fajr.
	if _, err := omsk.ParseJSON([]byte(strings.ReplaceAll(valid, `"suhur":"03:37"`, `"suhur":"04:07"`)), coverage); err != nil {
		t.Fatal(err)
	}
}

func TestParseJSONRejectsInvalidOrMissingCoverage(t *testing.T) {
	t.Parallel()
	raw := []byte(syntheticMonths(t, "2028-02"))
	for _, coverage := range []domain.DateRange{
		{}, {From: "2028-02-29", To: "2028-02-01"}, {From: "2028-02-01", To: "2028-03-01"},
		{From: "2028-2-01", To: "2028-02-29"}, {From: "2027-02-29", To: "2028-02-29"},
		{From: "1999-02-01", To: "1999-02-28"}, {From: "2027-01-01", To: "2028-12-31"},
	} {
		if days, err := omsk.ParseJSON(raw, coverage); err == nil || days != nil {
			t.Fatalf("invalid coverage accepted: %#v / %v", coverage, err)
		}
	}
}
