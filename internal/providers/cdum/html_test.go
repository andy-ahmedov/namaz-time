package cdum_test

import (
	"fmt"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/cdum"
)

func TestParseHTMLAnnualSyntheticCalendar(t *testing.T) {
	for _, year := range []int{2026, 2028} {
		t.Run(fmt.Sprint(year), func(t *testing.T) {
			raw := syntheticCalendar(year, "Москва", nil)
			coverage := domain.DateRange{From: fmt.Sprintf("%d-01-01", year), To: fmt.Sprintf("%d-12-31", year)}
			got, err := cdum.ParseHTML([]byte(raw), "Москва", coverage)
			if err != nil {
				t.Fatal(err)
			}
			wantCount := 365
			if year == 2028 {
				wantCount = 366
			}
			if len(got) != wantCount {
				t.Fatalf("days = %d, want %d", len(got), wantCount)
			}
			want := domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{
				Date: coverage.From, Fajr: "05:01", Sunrise: "07:02", Dhuhr: "12:03", Asr: "15:04", Maghrib: "18:05", Isha: "20:06",
			}}
			if !reflect.DeepEqual(got[0], want) || got[len(got)-1].Date != coverage.To {
				t.Fatalf("unexpected date/field mapping: first=%+v last=%+v", got[0], got[len(got)-1])
			}
			again, err := cdum.ParseHTML([]byte(raw), "Москва", coverage)
			if err != nil || !reflect.DeepEqual(got, again) {
				t.Fatal("normalization is not deterministic")
			}
		})
	}
}

func TestParseHTMLResearchedCityBindingsAreExact(t *testing.T) {
	coverage := domain.DateRange{From: "2026-01-01", To: "2026-12-31"}
	for _, tc := range []struct{ locality, path string }{
		{"Астрахань", "/time-namaz/astrakhan/Astrakhan.php"},
		{"Ростов-на-Дону", "/time-namaz/Rostov-na-Dony/Rostov.php"},
		{"Казань", "/time-namaz/Kazan/Kazan.php"},
		{"Киров", "/time-namaz/Киров/Kirov.php"},
		{"Екатеринбург", "/time-namaz/ekb/Ekb2015.php"},
		{"Челябинск", "/time-namaz/Chelyabinsk/Chelyabinsk2015.php"},
		{"Ижевск", "/time-namaz/izhevsk/Izhevsk2015.php"},
		{"Йошкар-Ола", "/time-namaz/yoshkar-ola/Yoshkar-Ola2015.php"},
		{"Курган", "/time-namaz/Kurgan/Kurgan.php"},
		{"Пенза", "/time-namaz/Пенза/Penza2015.php"},
		{"Пермь", "/time-namaz/Пермь/Perm.php"},
		{"Самара", "/time-namaz/samara/Samara2015.php"},
		{"Ульяновск", "/time-namaz/Ulyanovsk/Ulyanovsk2015.php"},
		{"Чебоксары", "/time-namaz/cheboksary/Cheboksary.php"},
		{"Салехард", "/time-namaz/Surgut/Surgut2015.php"},
		{"Хабаровск", "/time-namaz/khabarovsk/Habarovsk2015.php"},
	} {
		t.Run(tc.locality, func(t *testing.T) {
			raw := strings.ReplaceAll(syntheticCalendar(2026, "Москва", nil), "Москва", tc.locality)
			raw = strings.ReplaceAll(raw, "/time-namaz/Moskva/index.php", tc.path)
			got, err := cdum.ParseHTML([]byte(raw), tc.locality, coverage)
			if err != nil || len(got) != 365 {
				t.Fatalf("researched city calendar: days=%d error=%v", len(got), err)
			}
			wrongPath := strings.ReplaceAll(raw, tc.path, "/time-namaz/Moskva/index.php")
			if got, err := cdum.ParseHTML([]byte(wrongPath), tc.locality, coverage); err == nil || got != nil {
				t.Fatal("city identity inferred despite conflicting timetable path")
			}
		})
	}
	raw := syntheticCalendar(2026, "Москва", nil)
	for _, locality := range []string{"Казань", "Татарстан", "Республика Татарстан", "Сургут", "Оренбург", "Уфа"} {
		if got, err := cdum.ParseHTML([]byte(raw), locality, coverage); err == nil || got != nil {
			t.Fatalf("Moscow calendar must not establish scope %q", locality)
		}
	}
}

func TestParseHTMLExplicitCoverageRejectsRequestedMidnightWithoutDroppingDays(t *testing.T) {
	raw := syntheticCalendar(2026, "Санкт-Петербург", func(date time.Time, cells []string) []string {
		if date.Format("2006-01-02") == "2026-04-22" {
			cells[6] = "0:09"
		}
		return cells
	})
	for _, coverage := range []domain.DateRange{
		{From: "2026-01-01", To: "2026-12-31"},
		{From: "2026-04-01", To: "2026-04-30"},
		{From: "2026-04-01", To: "2026-06-30"},
	} {
		got, err := cdum.ParseHTML([]byte(raw), "Санкт-Петербург", coverage)
		if err == nil || got != nil || !strings.Contains(err.Error(), "2026-04-22") {
			t.Fatalf("requested failure must return no partial days: got=%d err=%v", len(got), err)
		}
	}
	for _, tc := range []struct {
		coverage domain.DateRange
		count    int
	}{
		{domain.DateRange{From: "2026-09-01", To: "2026-09-30"}, 30},
		{domain.DateRange{From: "2026-10-01", To: "2026-12-31"}, 92},
	} {
		got, err := cdum.ParseHTML([]byte(raw), "Санкт-Петербург", tc.coverage)
		if err != nil || len(got) != tc.count || got[0].Date != tc.coverage.From || got[len(got)-1].Date != tc.coverage.To {
			t.Fatalf("explicit bounded coverage: count=%d err=%v", len(got), err)
		}
	}
}

func TestParseHTMLCommentsAndNoscriptCannotSupplyEvidence(t *testing.T) {
	valid := syntheticCalendar(2026, "Москва", nil)
	coverage := domain.DateRange{From: "2026-09-01", To: "2026-09-30"}
	got, err := cdum.ParseHTML([]byte("<!--"+valid+"--><noscript>"+valid+"</noscript>"+valid), "Москва", coverage)
	if err != nil || len(got) != 30 {
		t.Fatalf("nonvisible decoy data must not cause duplicate data: %v", err)
	}
	for _, hidden := range []string{"<!--" + valid + "-->", "<noscript>" + valid + "</noscript>", "<template>" + valid + "</template>"} {
		if got, err := cdum.ParseHTML([]byte(hidden), "Москва", coverage); err == nil || got != nil {
			t.Fatal("nonvisible calendar was accepted")
		}
	}
	commentedYear := strings.Replace(valid, "Москва 2026</a>", "<!--Москва 2026--></a>", 1)
	if _, err := cdum.ParseHTML([]byte(commentedYear), "Москва", coverage); err == nil {
		t.Fatal("commented year cannot establish effective year")
	}
}

func TestParseHTMLStandaloneOfficeBOMSeparators(t *testing.T) {
	valid := syntheticCalendar(2026, "Москва", nil)
	raw := strings.Replace(valid, "</div></body>", "\ufeff\n</div></body>", 1)
	got, err := cdum.ParseHTML([]byte(raw), "Москва", domain.DateRange{From: "2026-09-01", To: "2026-09-30"})
	if err != nil || len(got) != 30 {
		t.Fatalf("standalone observed Office BOM separator: %v", err)
	}
	insideClock := strings.Replace(valid, ">5:01<", ">5:\ufeff01<", 1)
	if _, err := cdum.ParseHTML([]byte(insideClock), "Москва", domain.DateRange{From: "2026-09-01", To: "2026-09-30"}); err == nil {
		t.Fatal("BOM inside a clock must not be erased")
	}
}

func TestParseHTMLRejectsRawDuplicateEvidenceAttributes(t *testing.T) {
	valid := syntheticCalendar(2026, "Москва", nil)
	coverage := domain.DateRange{From: "2026-09-01", To: "2026-09-30"}
	for _, tc := range []struct{ name, old, replacement string }{
		{"calendar ID", `id="text_block"`, `id="text_block" id="other"`},
		{"case-folded calendar ID", `id="text_block"`, `id="text_block" ID="other"`},
		{"scope href", `href="/time-namaz/Moskva/index.php"`, `href="/time-namaz/Moskva/index.php" href="/other-city"`},
		{"single-quoted scope href", `href="/time-namaz/Moskva/index.php"`, `href='/time-namaz/Moskva/index.php' href='/other-city'`},
		{"unquoted calendar ID", `id="text_block"`, `id=text_block id=other`},
		{"inline class", `class="MsoNormal"`, `class="MsoNormal" class="hidden"`},
		{"cell style", "<td><p>5:01", `<td style="margin:0cm" style="display:none"><p>5:01`},
		{"ancestor style", "<body>", `<body style="margin:0cm" style="display:none">`},
		{"reserved marker collision", `id="text_block"`, `id="text_block" data-namaztime-raw-attribute-error="0"`},
		{"case-folded reserved marker collision", `id="text_block"`, `id="text_block" DATA-NAMAZTIME-RAW-ATTRIBUTE-ERROR="0"`},
		{"unrelated reserved marker collision", "<nav>", `<nav><a href="/contacts" data-namaztime-raw-attribute-error="0">Contact</a>`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if !strings.Contains(valid, tc.old) {
				t.Fatal("regression mutation does not match fixture")
			}
			raw := strings.Replace(valid, tc.old, tc.replacement, 1)
			if got, err := cdum.ParseHTML([]byte(raw), "Москва", coverage); err == nil || got != nil {
				t.Fatal("raw duplicate attribute was discarded before evidence validation")
			}
		})
	}
}

func TestParseHTMLRawAttributeChecksKeepNonEvidenceSeparate(t *testing.T) {
	valid := syntheticCalendar(2026, "Москва", nil)
	coverage := domain.DateRange{From: "2026-09-01", To: "2026-09-30"}
	for name, raw := range map[string]string{
		// This unrelated duplicate is present on the retained publisher pages.
		"unrelated contacts link":     strings.Replace(valid, "<nav>", `<nav><a class="nav_a" href="/contacts/contacts.php" class="root-item">Контакты</a>`, 1),
		"quoted attribute-like text":  strings.Replace(valid, "<td><p>5:01", `<td title='x > y; id="first" id="second" data-namaztime-raw-attribute-error="0"'><p>5:01`, 1),
		"nonvisible duplicate decoys": "<!--<div id='text_block' id='other'>decoy</div>--><script>var x='<a href=one href=two>';</script><template><div id='text_block' id='other'>decoy</div></template>" + valid,
	} {
		t.Run(name, func(t *testing.T) {
			before := []byte(raw)
			got, err := cdum.ParseHTML(before, "Москва", coverage)
			if err != nil || len(got) != 30 || string(before) != raw {
				t.Fatalf("non-evidence changed parsing or input bytes: days=%d err=%v", len(got), err)
			}
		})
	}
}

func TestParseHTMLRejectsSchemaScopeAndDateDrift(t *testing.T) {
	valid := syntheticCalendar(2026, "Москва", nil)
	coverage := domain.DateRange{From: "2026-09-01", To: "2026-09-30"}
	cases := map[string]string{
		"wrong city":             strings.Replace(valid, `<h1 id="pagetitle">Москва</h1>`, `<h1 id="pagetitle">Тула</h1>`, 1),
		"wrong year":             strings.Replace(valid, "Москва 2026</a>", "Москва 2025</a>", 1),
		"year only in footer":    strings.Replace(valid, "Москва 2026</a>", "Москва</a>", 1) + "<footer>2026</footer>",
		"hidden year evidence":   strings.Replace(valid, "<nav>", "<nav hidden>", 1),
		"wrong source link":      strings.Replace(valid, "/time-namaz/Moskva/index.php", "/not-a-calendar", 1),
		"duplicate year":         strings.Replace(valid, "</nav>", `<a href="/time-namaz/Moskva/index.php">Москва 2025</a></nav>`, 1),
		"duplicate block":        valid + valid,
		"duplicate annual body":  strings.Replace(valid, "</div></body>", valid[strings.Index(valid, `<p class="MsoNormal">`):strings.Index(valid, "</div>")]+"</div></body>", 1),
		"wrong month":            strings.Replace(valid, ">Сентябрь<", ">Октябрь<", 1),
		"month marker":           strings.Replace(valid, ">Сентябрь<", ">Сентябрь*<", 1),
		"renamed column":         strings.Replace(valid, ">Восход<", ">Икамат<", 1),
		"wrong weekday":          strings.Replace(valid, ">1 Вт.<", ">1 Ср.<", 1),
		"missing row":            replaceRow(valid, "1 Вт.", ""),
		"duplicate row":          replaceRow(valid, "1 Вт.", "$ROW$ROW"),
		"missing field":          strings.Replace(valid, "<td><p>5:01<o:p></o:p></p></td>", "", 1),
		"extra field":            strings.Replace(valid, "</tr>", "<td>00:00</td></tr>", 1),
		"unknown marker":         strings.Replace(valid, ">5:01<", ">5:01*<", 1),
		"next day marker":        strings.Replace(valid, ">20:06<", ">20:06+1<", 1),
		"bad hour":               strings.Replace(valid, ">5:01<", ">24:01<", 1),
		"bad minute":             strings.Replace(valid, ">5:01<", ">5:60<", 1),
		"blank clock":            strings.Replace(valid, ">5:01<", ">&nbsp;<", 1),
		"unicode digit":          strings.Replace(valid, ">5:01<", ">５:01<", 1),
		"nonexistent date":       strings.Replace(valid, ">28 Сб.<", ">29 Вс.<", 1),
		"spanning cells":         strings.Replace(valid, "<td>", `<td colspan="2">`, 1),
		"unexpected footnote":    strings.Replace(valid, "</div></body>", "<p>*use another city</p></div></body>", 1),
		"hidden block":           strings.Replace(valid, `<div id="text_block">`, `<div id="text_block" hidden>`, 1),
		"hidden time":            strings.Replace(valid, "<td><p>5:01", `<td style="display: none"><p>5:01`, 1),
		"content hidden":         strings.Replace(valid, "<td><p>5:01", `<td style="content-visibility: hidden"><p>5:01`, 1),
		"offscreen time":         strings.Replace(valid, "<td><p>5:01", `<td style="position:absolute; left:-9999px"><p>5:01`, 1),
		"escaped hiding style":   strings.Replace(valid, "<td><p>5:01", `<td style="displ\61y:none"><p>5:01`, 1),
		"zero-sized time":        strings.Replace(valid, "<td><p>5:01", `<td style="font-size:0"><p>5:01`, 1),
		"white hidden time":      strings.Replace(valid, "<td><p>5:01", `<td style="color:white;background:white"><p>5:01`, 1),
		"unknown hiding class":   strings.Replace(valid, "<td><p>5:01", `<td class="hidden"><p>5:01`, 1),
		"script inside table":    strings.Replace(valid, "<td><p>5:01", "<td><script>5:01</script><p>5:01", 1),
		"unknown formatting":     strings.Replace(valid, "<td><p>5:01", "<td><svg></svg><p>5:01", 1),
		"split clock paragraphs": strings.Replace(valid, "<td><p>5:01", "<td><p>5:</p><p>01", 1),
		"oversize field":         strings.Replace(valid, ">5:01<", ">"+strings.Repeat("1", 130)+"<", 1),
		"truncated schedule":     valid[:len(valid)/2],
		"unterminated comment":   valid + "<!--",
		"short comment":          valid + "<!-->",
		"nested comment":         valid + "<!-- <!-- calendar -->",
	}
	for name, raw := range cases {
		t.Run(name, func(t *testing.T) {
			got, err := cdum.ParseHTML([]byte(raw), "Москва", coverage)
			if err == nil || got != nil {
				t.Fatalf("schema drift accepted; days=%d err=%v", len(got), err)
			}
		})
	}
}

func FuzzParseHTML(f *testing.F) {
	f.Add([]byte("<!-->"))
	f.Add([]byte("<noscript><div id='text_block'>fake</div></noscript>"))
	f.Add([]byte(syntheticCalendar(2026, "Москва", nil)))
	f.Fuzz(func(t *testing.T, raw []byte) {
		got, err := cdum.ParseHTML(raw, "Москва", domain.DateRange{From: "2026-09-01", To: "2026-09-30"})
		if err != nil {
			if got != nil {
				t.Fatal("error exposed partial data")
			}
			return
		}
		if len(got) != 30 || got[0].Date != "2026-09-01" || got[29].Date != "2026-09-30" {
			t.Fatal("successful parse escaped explicit coverage")
		}
	})
}

func TestParseHTMLTimeOrdering(t *testing.T) {
	for column := 1; column <= 6; column++ {
		raw := syntheticCalendar(2026, "Москва", func(date time.Time, cells []string) []string {
			if date.Format("2006-01-02") == "2026-09-08" {
				cells[column] = "12:03"
				if column == 3 {
					cells[column] = "07:02"
				}
			}
			return cells
		})
		if got, err := cdum.ParseHTML([]byte(raw), "Москва", domain.DateRange{From: "2026-09-01", To: "2026-09-30"}); err == nil || got != nil {
			t.Fatalf("invalid order at column%d accepted", column)
		}
	}
}

func TestParseHTMLRejectsInvalidBoundsAndInput(t *testing.T) {
	valid := []byte(syntheticCalendar(2026, "Москва", nil))
	for _, coverage := range []domain.DateRange{
		{}, {From: "2026-9-01", To: "2026-09-30"},
		{From: "2026-09-02", To: "2026-09-30"}, {From: "2026-09-01", To: "2026-09-29"},
		{From: "2026-09-01", To: "2026-08-31"}, {From: "2026-12-01", To: "2027-01-31"},
		{From: "2025-09-01", To: "2025-09-30"}, {From: "2026-02-01", To: "2026-02-29"},
	} {
		if _, err := cdum.ParseHTML(valid, "Москва", coverage); err == nil {
			t.Fatalf("invalid coverage accepted: %+v", coverage)
		}
	}
	for _, locality := range []string{"", " Москва", "московская область", "Тула"} {
		if _, err := cdum.ParseHTML(valid, locality, domain.DateRange{From: "2026-09-01", To: "2026-09-30"}); err == nil {
			t.Fatalf("unresearched locality accepted: %q", locality)
		}
	}
	for _, raw := range [][]byte{nil, {0xff}, []byte(strings.Repeat("x", 4*1024*1024+1)), []byte(strings.Repeat("я", 2*1024*1024+1))} {
		if _, err := cdum.ParseHTML(raw, "Москва", domain.DateRange{From: "2026-09-01", To: "2026-09-30"}); err == nil {
			t.Fatal("unbounded or invalid input accepted")
		}
	}
	for name, raw := range map[string]string{
		"codepoint-only limit": string(valid) + strings.Repeat(" ", 2*1024*1024),
		"deep subtree":         strings.Replace(strings.Replace(string(valid), "<body>", "<body>"+strings.Repeat("<div>", 70), 1), "</body>", strings.Repeat("</div>", 70)+"</body>", 1),
		"node limit":           strings.Replace(string(valid), "<body>", "<body>"+strings.Repeat("<br>", 100001), 1),
		"null byte":            string(valid) + "\x00",
	} {
		t.Run(name, func(t *testing.T) {
			if _, err := cdum.ParseHTML([]byte(raw), "Москва", domain.DateRange{From: "2026-09-01", To: "2026-09-30"}); err == nil {
				t.Fatal("structural or codepoint bound was not enforced")
			}
		})
	}
}

// syntheticCalendar mirrors the observed markup schema, never actual prayer
// values. Its deliberately constant times are not real prayer times.
func syntheticCalendar(year int, locality string, mutate func(time.Time, []string) []string) string {
	months := []string{"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"}
	weekdays := []string{"Вс.", "Пн.", "Вт.", "Ср.", "Чт.", "Пт.", "Сб."}
	path := "/time-namaz/Moskva/index.php"
	if locality == "Санкт-Петербург" {
		path = "/time-namaz/Spb.php"
	}
	var out strings.Builder
	fmt.Fprintf(&out, `<!doctype html><html><body><nav><a href="%s">%s %d</a></nav><div id="text_block"><h1 id="pagetitle">%s</h1>`, path, locality, year, locality)
	for month := 1; month <= 12; month++ {
		fmt.Fprintf(&out, `<p class="MsoNormal"><b><span>%s<o:p></o:p></span></b></p><p>&nbsp;</p><table><tbody>`, months[month-1])
		writeSyntheticRow(&out, []string{"Дата", "Фаджр", "Восход", "Зухр", "Аср", "Магриб", "Иша"})
		for day := 1; day <= time.Date(year, time.Month(month)+1, 0, 0, 0, 0, 0, time.UTC).Day(); day++ {
			date := time.Date(year, time.Month(month), day, 0, 0, 0, 0, time.UTC)
			cells := []string{fmt.Sprintf("%d %s", day, weekdays[date.Weekday()]), "5:01", "7:02", "12:03", "15:04", "18:05", "20:06"}
			if mutate != nil {
				cells = mutate(date, cells)
			}
			writeSyntheticRow(&out, cells)
		}
		out.WriteString("</tbody></table>")
	}
	out.WriteString("</div></body></html>")
	return out.String()
}

func writeSyntheticRow(out *strings.Builder, cells []string) {
	out.WriteString("<tr>")
	for _, cell := range cells {
		fmt.Fprintf(out, "<td><p>%s<o:p></o:p></p></td>", cell)
	}
	out.WriteString("</tr>")
}

func replaceRow(raw, date, replacement string) string {
	position := strings.Index(raw, ">"+date+"<")
	start := strings.LastIndex(raw[:position], "<tr>")
	end := position + strings.Index(raw[position:], "</tr>") + len("</tr>")
	return raw[:start] + strings.ReplaceAll(replacement, "$ROW", raw[start:end]) + raw[end:]
}
