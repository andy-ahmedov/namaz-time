package saratov_test

import (
	"fmt"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/saratov"
)

var september = domain.DateRange{From: "2026-09-01", To: "2026-09-30"}

// These are synthetic clocks, not the publisher's real schedule. Only the
// observed markup, headings and separate mosque-performance notices are used.
func syntheticHTML() string {
	var rows strings.Builder
	weekdays := [...]string{"Воскрес", "Понедел", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"}
	for day := 1; day <= 30; day++ {
		date := time.Date(2026, time.September, day, 0, 0, 0, 0, time.UTC)
		fmt.Fprintf(&rows, `<tr><td>%d</td><td>%s</td><td>%d</td><td>4:00</td><td>6.00</td><td>12.00</td><td>16.00</td><td>18.00</td><td>20.00</td></tr>`, day, weekdays[date.Weekday()], (day+18)%30+1)
	}
	return `<!DOCTYPE html><html lang="en"><head><title> » Расписание намазов на сентябрь</title><link rel="canonical" href="https://dumso.ru/raspisanie"><link rel="alternate" type="application/json" href="https://dumso.ru/wp-json/wp/v2/pages/2348"></head><body><div id="wrap"><div id="menu"><h1>«Духовное управление мусульман Саратовской области»</h1></div><div id="extras"><center><a href="/raspisanie" title="Расписание намазов в г. Саратове">на месяц</a></center><div id="calendar_wrap" class="calendar_wrap"><table id="wp-calendar" class="wp-calendar-table"><caption>Сентябрь 2026</caption></table></div></div><div id="contentwide"><div class="post"><h2>Расписание намазов на сентябрь</h2><p align="center"><strong>«Поистине молитва для верующих предписана в определенное время»</strong><br>(Коран: сура 4, аят 103)</p><div class="print_btn"><a href="https://dumso.ru/print/namaz-time.html" title="Версия для печати">Версия для печати</a></div><table class="namaz_time"><tbody><tr><td><strong>сентябрь</strong></td><td>день<br>недели</td><td><strong>Раби аль-авваль/</strong><br><strong>Раби аль-ахир</strong></td><td><strong>Фажр</strong><br>утрен.<br>намаз</td><td><strong>Восход</strong><br><strong>солнца</strong></td><td><strong>Зухр</strong><br>уля<br>намаз</td><td><strong>Аср</strong><br>икенде<br>намаз</td><td><strong>Магриб</strong><br>ахшам</td><td><strong>Ийша</strong><br>ясых</td></tr>` + rows.String() + `</tbody></table><p>&nbsp;</p><ul><li>Азан на зухр намаз в соборной мечети 13:15</li><li>Фажр намаз в мечети через 45 минут после наступления времени.</li></ul></div></div></div></body></html>`
}

func TestParseSyntheticSeptember(t *testing.T) {
	got, err := saratov.ParseHTML([]byte(syntheticHTML()), september)
	if err != nil || len(got) != 30 {
		t.Fatalf("complete synthetic month: count=%d err=%v", len(got), err)
	}
	for i, day := range got {
		want := domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{
			Date: fmt.Sprintf("2026-09-%02d", i+1), Fajr: "04:00", Sunrise: "06:00",
			Dhuhr: "12:00", Asr: "16:00", Maghrib: "18:00", Isha: "20:00",
		}}
		if !reflect.DeepEqual(day, want) {
			t.Fatalf("day %d differs or inferred mosque fields added: got=%+v want=%+v", i+1, day, want)
		}
	}
	if saratov.ParserVersion != "saratov-city-html/v1" {
		t.Fatal("unexpected parser version")
	}
}

func TestParseExplicitSubsetAfterValidatingWholeMonth(t *testing.T) {
	rangeOnly := domain.DateRange{From: "2026-09-08", To: "2026-09-09"}
	got, err := saratov.ParseHTML([]byte(syntheticHTML()), rangeOnly)
	if err != nil || len(got) != 2 || got[0].Date != rangeOnly.From || got[1].Date != rangeOnly.To {
		t.Fatalf("explicit subset: days=%+v err=%v", got, err)
	}
	bad := strings.Replace(syntheticHTML(), `<td>1</td><td>Вторник</td>`, `<td>1</td><td>Среда</td>`, 1)
	if got, err := saratov.ParseHTML([]byte(bad), rangeOnly); err == nil || got != nil {
		t.Fatal("invalid day outside selected range must invalidate the source month")
	}
}

func TestFailClosedCoverage(t *testing.T) {
	for _, r := range []domain.DateRange{
		{}, {From: "2026-09-1", To: "2026-09-30"},
		{From: "2026-09-30", To: "2026-09-01"},
		{From: "2026-09-01", To: "2026-09-31"},
		{From: "2025-09-01", To: "2025-09-30"},
		{From: "2026-08-31", To: "2026-09-30"},
		{From: "2026-09-01", To: "2026-10-01"},
		{From: "2026-02-01", To: "2026-02-28"},
	} {
		t.Run(r.From+"/"+r.To, func(t *testing.T) {
			got, err := saratov.ParseHTML([]byte(syntheticHTML()), r)
			if err == nil || got != nil {
				t.Fatalf("unsupported coverage accepted: %+v", r)
			}
		})
	}
}

func TestFailClosedArtifactMutations(t *testing.T) {
	base := syntheticHTML()
	first := `<tr><td>1</td><td>Вторник</td><td>20</td><td>4:00</td><td>6.00</td><td>12.00</td><td>16.00</td><td>18.00</td><td>20.00</td></tr>`
	for _, tc := range []struct{ name, old, replacement string }{
		{"wrong publisher", "«Духовное управление мусульман Саратовской области»", "Different publisher"},
		{"missing publisher", "<h1>", "<h3>"},
		{"wrong city", "Расписание намазов в г. Саратове", "Расписание намазов в г. Энгельсе"},
		{"missing scope", `title="Расписание намазов в г. Саратове"`, ""},
		{"different scope path", `href="/raspisanie"`, `href="/another"`},
		{"wrong canonical", `rel="canonical" href="https://dumso.ru/raspisanie"`, `rel="canonical" href="https://example.invalid/raspisanie"`},
		{"wrong metadata link", "wp/v2/pages/2348", "wp/v2/pages/9999"},
		{"missing year", "Сентябрь 2026", "Сентябрь"},
		{"wrong year", "Сентябрь 2026", "Сентябрь 2025"},
		{"wrong calendar month", "Сентябрь 2026", "Август 2026"},
		{"wrong post month", "<h2>Расписание намазов на сентябрь</h2>", "<h2>Расписание намазов на август</h2>"},
		{"wrong month header", "<strong>сентябрь</strong>", "<strong>август</strong>"},
		{"wrong prayer header", "<strong>Фажр</strong>", "<strong>Икамат</strong>"},
		{"wrong hijri header", "Раби аль-авваль/", "Рамадан/"},
		{"wrong weekday", "<td>1</td><td>Вторник</td>", "<td>1</td><td>Понедел</td>"},
		{"duplicate day", "<td>2</td><td>Среда</td>", "<td>1</td><td>Среда</td>"},
		{"zero padded day", "<td>1</td><td>Вторник</td>", "<td>01</td><td>Вторник</td>"},
		{"missing row", first, ""},
		{"duplicate row", first, first + first},
		{"missing cell", "<td>4:00</td>", ""},
		{"extra cell", "<td>4:00</td>", "<td>4:00</td><td>5:00</td>"},
		{"spanning cell", "<td>4:00</td>", `<td colspan="2">4:00</td>`},
		{"unknown marker", "<td>4:00</td>", "<td>4:00*</td>"},
		{"unknown inline", "<td>4:00</td>", "<td><span>4:00</span></td>"},
		{"hidden clock", "<td>4:00</td>", `<td hidden>4:00</td>`},
		{"hidden table", `class="namaz_time"`, `class="namaz_time" style="display:none"`},
		{"unknown table class", `class="namaz_time"`, `class="namaz_time new"`},
		{"duplicate attribute", `class="namaz_time"`, `class="namaz_time" class="namaz_time"`},
		{"duplicate case-folded attribute", `class="namaz_time"`, `class="namaz_time" CLASS="namaz_time"`},
		{"duplicate relevant ID", `<div id="extras">`, `<div id="extras"></div><div id="extras">`},
		{"class on unexpected element", "<h1>", `<h1 class="green">`},
		{"foreign namespace ancestor", `<div id="wrap">`, `<svg><foreignObject><div id="wrap">`},
		{"hidden ancestor", `id="contentwide"`, `id="contentwide" aria-hidden="true"`},
		{"hidden year", "<caption>", `<caption style="display:none">`},
		{"scripted scope", `title="Расписание намазов в г. Саратове"`, `title="Расписание намазов в г. Саратове" onclick="run()"`},
		{"duplicate caption", "<caption>Сентябрь 2026</caption>", "<caption>Сентябрь 2026</caption><caption>Сентябрь 2026</caption>"},
		{"duplicate scope", `<center>`, `<center><a href="/raspisanie" title="Расписание намазов в г. Саратове">на месяц</a>`},
		{"duplicate table", "</tbody></table>", `</tbody></table><table class="namaz_time"></table>`},
		{"unknown footnote", "</ul></div></div>", "<li>Use a different Fajr in summer</li></ul></div></div>"},
		{"changed performance rule", "через 45 минут", "через 30 минут"},
		{"performance removed", "<li>Фажр намаз в мечети через 45 минут после наступления времени.</li>", ""},
		{"unknown trailing text", "</tbody></table>", "</tbody></table>Additional time correction"},
		{"hijri invalid", "<td>20</td><td>4:00", "<td>32</td><td>4:00"},
		{"hijri discontinuity", "<td>21</td><td>4:00", "<td>25</td><td>4:00"},
		{"unclosed cell", "<td>4:00</td>", "<td>4:00"},
		{"unclosed row", "</tr></tbody>", "</tbody>"},
		{"unexpected tbody", "<tbody>", "<tbody><tbody>"},
		{"doctype inside table", "<tbody>", "<tbody><!DOCTYPE html>"},
		{"self-closing table", `<table class="namaz_time">`, `<table class="namaz_time"/>`},
		{"self-closing field", "<td>4:00</td>", "<td/>4:00</td>"},
		{"nested table", "<td>4:00</td>", "<td><table><tr><td>4:00</td></tr></table></td>"},
		{"unterminated comment", "</body>", "<!-- broken </body>"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if !strings.Contains(base, tc.old) {
				t.Fatal("test mutation does not target the synthetic fixture")
			}
			raw := strings.Replace(base, tc.old, tc.replacement, 1)
			got, err := saratov.ParseHTML([]byte(raw), september)
			if err == nil || got != nil {
				t.Fatal("changed source schema unexpectedly accepted")
			}
		})
	}
}

func TestMetadataLinksMustBeDocumentMetadata(t *testing.T) {
	link := `<link rel="canonical" href="https://dumso.ru/raspisanie">`
	raw := strings.Replace(syntheticHTML(), link, "", 1)
	raw = strings.Replace(raw, "</body>", link+"</body>", 1)
	if got, err := saratov.ParseHTML([]byte(raw), september); err == nil || got != nil {
		t.Fatal("canonical URL decoy outside document head accepted")
	}
}

func TestRawAttributeValuesAreQuoteAware(t *testing.T) {
	raw := strings.Replace(syntheticHTML(), "</body>", `<aside><a title='href="not an attribute" href="still quoted"'>unrelated</a></aside></body>`, 1)
	if got, err := saratov.ParseHTML([]byte(raw), september); err != nil || len(got) != 30 {
		t.Fatalf("quoted attribute content misinterpreted: %v", err)
	}
}

func TestFailClosedClocksAndOrder(t *testing.T) {
	for _, bad := range []string{"24:00", "4:60", "4:0", "04:00:00", "4,00", "4：00", "４:00", "4 :00", "4:00*", "-1:00", "+4:00", "6.00", "7.00", "0:00 (+1)"} {
		t.Run(bad, func(t *testing.T) {
			raw := strings.Replace(syntheticHTML(), "<td>4:00</td>", "<td>"+bad+"</td>", 1)
			if got, err := saratov.ParseHTML([]byte(raw), september); err == nil || got != nil {
				t.Fatal("invalid clock or ordering accepted")
			}
		})
	}
	for _, pair := range [][2]string{{"12.00", "6.00"}, {"16.00", "12.00"}, {"18.00", "16.00"}, {"20.00", "00.00"}} {
		raw := strings.Replace(syntheticHTML(), "<td>"+pair[0]+"</td>", "<td>"+pair[1]+"</td>", 1)
		if got, err := saratov.ParseHTML([]byte(raw), september); err == nil || got != nil {
			t.Fatal("invalid later-prayer order accepted")
		}
	}
}

func TestIgnoreProperlyDelimitedNonEvidence(t *testing.T) {
	raw := strings.Replace(syntheticHTML(), "</body>", `<!-- <table class="namaz_time">not evidence</table> --><script>var decoy='<caption>Сентябрь 2025</caption>';</script><template><table class="namaz_time">not evidence</table></template></body>`, 1)
	if got, err := saratov.ParseHTML([]byte(raw), september); err != nil || len(got) != 30 {
		t.Fatalf("non-evidence must not replace or duplicate visible table: %v", err)
	}
}

func TestFailClosedInvalidEncodingAndLimits(t *testing.T) {
	for _, raw := range [][]byte{nil, {}, {0xff}, []byte(syntheticHTML() + "\x00"), []byte(strings.Repeat(" ", 2*1024*1024))} {
		if got, err := saratov.ParseHTML(raw, september); err == nil || got != nil {
			t.Fatal("invalid or oversized artifact accepted")
		}
	}
	deep := strings.Replace(syntheticHTML(), `<div class="post">`, strings.Repeat("<div>", 100)+`<div class="post">`, 1)
	if got, err := saratov.ParseHTML([]byte(deep), september); err == nil || got != nil {
		t.Fatal("excessive nesting accepted")
	}
}
