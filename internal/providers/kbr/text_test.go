package kbr_test

import (
	"bytes"
	"fmt"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/kbr"
)

var annualCoverage = domain.DateRange{From: "2026-01-01", To: "2026-12-31"}

const fixtureAuthority = "ДУХОВНОЕ УПРАВЛЕНИЕ МУСУЛЬМАН КАБАРДИ НО- БАЛК АРСКО Й РЕСПУБЛИКИ\nСайт ДУМ КБР: www. dumkbr. ru\n"

// These repeated artificial times are not a retained official timetable.
// Only the public document's labels/layout are represented by the fixture.
const fixtureClocks = "06:10 07:40 12:10 15:40 18:10 20:40"

var fixtureMonths = []string{"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"}
var fixtureWeekdays = []string{"Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"}

func syntheticAnnual() []byte {
	var out strings.Builder
	for index, month := range fixtureMonths {
		if index > 0 {
			out.WriteString("\f")
		}
		out.WriteString(fixtureAuthority)
		fmt.Fprintf(&out, "ГРАФИК НАМАЗОВ НА %s 2026 г. ПО КБР\n", strings.ToUpper(month))
		header := month + " Фаджр Утренний Шурук Восход Зухр Обеденный Аср Предвечерний Магриб Вечерний Иша Ночной\n"
		out.WriteString(header)
		for day := time.Date(2026, time.Month(index+1), 1, 0, 0, 0, 0, time.UTC); int(day.Month()) == index+1; day = day.AddDate(0, 0, 1) {
			fmt.Fprintf(&out, "%d %s %s\n", day.Day(), fixtureWeekdays[day.Weekday()], fixtureClocks)
		}
		out.WriteString(header)
	}
	return []byte(out.String())
}

func TestParseTextSyntheticAnnual(t *testing.T) {
	raw := syntheticAnnual()
	before := append([]byte(nil), raw...)
	days, err := kbr.ParseText(raw, annualCoverage)
	if err != nil {
		t.Fatal(err)
	}
	if len(days) != 365 || days[0].Date != "2026-01-01" || days[364].Date != "2026-12-31" {
		t.Fatalf("incomplete annual coverage: %d rows", len(days))
	}
	want := domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{
		Date: "2026-09-08", Fajr: "06:10", Sunrise: "07:40", Dhuhr: "12:10",
		Asr: "15:40", Maghrib: "18:10", Isha: "20:40",
	}}
	if !reflect.DeepEqual(days[250], want) {
		t.Fatalf("golden row: got %+v, want %+v", days[250], want)
	}
	if !bytes.Equal(raw, before) {
		t.Fatal("parser mutated the captured text")
	}
	again, err := kbr.ParseText(raw, annualCoverage)
	if err != nil || !reflect.DeepEqual(again, days) {
		t.Fatal("normalization is not deterministic")
	}
}

func TestParseTextValidatesAnnualBeforeSelectingRange(t *testing.T) {
	raw := syntheticAnnual()
	coverage := domain.DateRange{From: "2026-09-01", To: "2026-09-30"}
	days, err := kbr.ParseText(raw, coverage)
	if err != nil {
		t.Fatal(err)
	}
	if len(days) != 30 || days[0].Date != coverage.From || days[29].Date != coverage.To {
		t.Fatal("requested subset was not preserved exactly")
	}
	brokenJanuary := bytes.Replace(raw, []byte("1 Чт "+fixtureClocks), []byte("1 Пн "+fixtureClocks), 1)
	if got, err := kbr.ParseText(brokenJanuary, coverage); err == nil || got != nil {
		t.Fatal("invalid rows outside requested September coverage were ignored")
	}
}

func TestParseTextPreservesExtractedWhitespaceOnly(t *testing.T) {
	raw := strings.Join(strings.Fields(string(syntheticAnnual())), " \r\n\t")
	days, err := kbr.ParseText([]byte(raw), annualCoverage)
	if err != nil || len(days) != 365 {
		t.Fatalf("PDF line wrapping must not change token semantics: %v", err)
	}
}

func TestParseTextFailsClosed(t *testing.T) {
	valid := syntheticAnnual()
	replace := func(old, replacement string) []byte { return bytes.Replace(valid, []byte(old), []byte(replacement), 1) }
	tests := []struct {
		name string
		raw  []byte
	}{
		{"empty", nil},
		{"oversized", bytes.Repeat([]byte(" "), 2*1024*1024)},
		{"invalid UTF-8", append([]byte{0xff}, valid...)},
		{"unverified authority", replace("ДУХОВНОЕ", "ДРУГОЕ")},
		{"unverified publisher URL", replace("dumkbr.", "example.")},
		{"wrong year", replace("2026 г.", "2025 г.")},
		{"wrong scope", replace("ПО КБР", "ПО КЧР")},
		{"missing scope", replace("ПО КБР", "")},
		{"wrong month", replace("НА ЯНВАРЬ", "НА ФЕВРАЛЬ")},
		{"wrong month label", replace("Январь Фаджр", "Февраль Фаджр")},
		{"renamed prayer", replace("Шурук Восход", "Духа Восход")},
		{"swapped columns", replace("Фаджр Утренний Шурук Восход", "Шурук Восход Фаджр Утренний")},
		{"missing row", replace("2 Пт "+fixtureClocks+"\n", "")},
		{"duplicate row", replace("2 Пт "+fixtureClocks+"\n", "1 Чт "+fixtureClocks+"\n")},
		{"wrong weekday", replace("1 Чт ", "1 Пн ")},
		{"noncanonical day", replace("1 Чт ", "01 Чт ")},
		{"unsupported leap day", replace("28 Сб "+fixtureClocks+"\nФевраль", "28 Сб "+fixtureClocks+"\n29 Вс "+fixtureClocks+"\nФевраль")},
		{"missing time", replace("1 Чт "+fixtureClocks, "1 Чт 06:10 07:40 12:10 15:40 18:10")},
		{"extra time", replace("1 Чт "+fixtureClocks, "1 Чт "+fixtureClocks+" 21:10")},
		{"unpadded hour", replace("06:10", "6:10")},
		{"incomplete minutes", replace("06:10", "06:1")},
		{"seconds not published", replace("06:10", "06:10:00")},
		{"invalid hour", replace("06:10", "24:10")},
		{"invalid minute", replace("06:10", "06:60")},
		{"non-digit clock", replace("06:10", "0a:10")},
		{"unknown footnote marker", replace("06:10", "06:10*")},
		{"equal consecutive times", replace("07:40", "06:10")},
		{"reversed time order", replace("07:40", "05:40")},
		{"cross-midnight Isha", replace("20:40", "00:40")},
		{"unknown trailing note", append(append([]byte(nil), valid...), []byte("Примечание: Иша уточняется")...)},
		{"truncated annual source", valid[:len(valid)/2]},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := kbr.ParseText(tt.raw, annualCoverage)
			if err == nil || got != nil {
				t.Fatalf("expected error and no partial rows; got %d rows, %v", len(got), err)
			}
		})
	}
}

func TestParseTextRejectsInvalidCoverage(t *testing.T) {
	for _, coverage := range []domain.DateRange{
		{}, {From: "2026-9-01", To: "2026-09-30"},
		{From: "2025-12-31", To: "2026-01-01"}, {From: "2026-12-31", To: "2027-01-01"},
		{From: "2026-02-29", To: "2026-03-01"}, {From: "2026-09-30", To: "2026-09-01"},
	} {
		if got, err := kbr.ParseText(syntheticAnnual(), coverage); err == nil || got != nil {
			t.Fatalf("accepted invalid coverage %+v", coverage)
		}
	}
}
