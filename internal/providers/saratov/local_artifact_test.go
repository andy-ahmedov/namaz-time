package saratov_test

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"html"
	"os"
	"path/filepath"
	"reflect"
	"regexp"
	"strconv"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/saratov"
)

// Raw public artifacts are deliberately not committed or fetched by tests.
// This optional reproduction binds the three separately captured artifacts,
// explicit source-page modified date, city evidence and all 180 prayer fields.
// It is comparison evidence, not a production qualification implementation.
func TestRetainedPublicMonth(t *testing.T) {
	dir := os.Getenv("T049_SARATOV_ARTIFACT_DIR")
	if dir == "" {
		t.Skip("retained first-party captures not configured")
	}
	read := func(name, expected string) []byte {
		t.Helper()
		raw, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil {
			t.Fatal(err)
		}
		hash := sha256.Sum256(raw)
		if hex.EncodeToString(hash[:]) != expected {
			t.Fatalf("%s retained raw SHA-256 mismatch", name)
		}
		return raw
	}
	main := read("saratov-raspisanie.html", "85ee2d5a8813b47403af55aee92afd1bbcdc536aa3cfb36864548ce66fec6d0d")
	print := read("saratov-print.html", "4f94b2a5dfcab1e406c4d870732da65b021af27d90b9f26bcf26461a18933564")
	metadata := read("saratov-page2348.json", "b6c08e8d648189ac9988aa279f836852d18ad79522b94bbc82463dd3d315c3f5")
	var page struct {
		ID          int    `json:"id"`
		Status      string `json:"status"`
		Type        string `json:"type"`
		Slug        string `json:"slug"`
		Link        string `json:"link"`
		ModifiedGMT string `json:"modified_gmt"`
		Content     struct {
			Rendered string `json:"rendered"`
		} `json:"content"`
	}
	if err := json.Unmarshal(metadata, &page); err != nil {
		t.Fatal(err)
	}
	if page.ID != 2348 || page.Status != "publish" || page.Type != "page" || page.Slug != "raspisanie" ||
		page.Link != "https://dumso.ru/raspisanie" || page.ModifiedGMT != "2026-08-31T17:17:26" {
		t.Fatal("page-specific currentness metadata differs from independently captured evidence")
	}
	if !strings.Contains(string(print), "Расписание намазов на сентябрь в г. Саратове") {
		t.Fatal("explicit printable Saratov city heading missing")
	}
	mainCells := independentCells(t, string(main))
	printCells := independentCells(t, string(print))
	pageCells := independentCells(t, page.Content.Rendered)
	if !reflect.DeepEqual(mainCells, printCells) || !reflect.DeepEqual(mainCells, pageCells) {
		t.Fatal("metadata rendered content, main and print tables differ")
	}
	got, err := saratov.ParseHTML(main, september)
	if err != nil || len(got) != 30 {
		t.Fatalf("retained month: days=%d err=%v", len(got), err)
	}
	var matrix strings.Builder
	clock := regexp.MustCompile(`^([0-9]{1,2})[.:]([0-9]{2})$`)
	for i, day := range got {
		cells := printCells[i+1]
		if cells[0] != strconv.Itoa(i+1) {
			t.Fatalf("print day %d has unexpected date", i+1)
		}
		var times [6]string
		for j := range times {
			parts := clock.FindStringSubmatch(cells[j+3])
			if len(parts) != 3 {
				t.Fatalf("independent printed clock format at day %d field %d", i+1, j+1)
			}
			hour, _ := strconv.Atoi(parts[1])
			minute, _ := strconv.Atoi(parts[2])
			times[j] = fmt.Sprintf("%02d:%02d", hour, minute)
		}
		want := domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{
			Date: fmt.Sprintf("2026-09-%02d", i+1), Fajr: times[0], Sunrise: times[1],
			Dhuhr: times[2], Asr: times[3], Maghrib: times[4], Isha: times[5],
		}}
		if !reflect.DeepEqual(day, want) {
			t.Fatalf("day %d differs from independent print extraction", i+1)
		}
		fmt.Fprintf(&matrix, "%s|%s\n", want.Date, strings.Join(times[:], "|"))
	}
	hash := sha256.Sum256([]byte(matrix.String()))
	if hex.EncodeToString(hash[:]) != "275824c832aaaeb838189232769eeb2c185cdd3a5c15ba16d486c835e9163b3f" {
		t.Fatal("full matrix differs from independent Python/BeautifulSoup print comparison")
	}
	part, err := saratov.ParseHTML(main, domain.DateRange{From: "2026-09-08", To: "2026-09-30"})
	if err != nil || !reflect.DeepEqual(part, got[7:]) {
		t.Fatalf("retained bounded selection differs: %v", err)
	}
	// Undated standalone print content and undated API rendered HTML cannot
	// replace the main source context in the production parser.
	for _, raw := range [][]byte{print, []byte(page.Content.Rendered)} {
		if days, err := saratov.ParseHTML(raw, september); err == nil || days != nil {
			t.Fatal("standalone undated alternate unexpectedly accepted")
		}
	}
	t.Log("30 dates and all 180 prayer fields match independent print extraction; main/API/print cell equality and source-modified metadata verified")
}

// This comparison intentionally uses a separate small regex-based extractor
// on the hash-pinned, already inspected printable artifact. It is not a general
// HTML parser and is never used for production normalization.
func independentCells(t *testing.T, raw string) [][]string {
	t.Helper()
	tables := regexp.MustCompile(`(?s)<table class="namaz_time">(.*?)</table>`).FindAllStringSubmatch(raw, -1)
	if len(tables) != 1 {
		t.Fatal("independent comparison requires exactly one source table")
	}
	rows := regexp.MustCompile(`(?s)<tr(?: class="green")?>(.*?)</tr>`).FindAllStringSubmatch(tables[0][1], -1)
	if len(rows) != 31 {
		t.Fatalf("independent source row count = %d", len(rows))
	}
	td := regexp.MustCompile(`(?s)<td>(.*?)</td>`)
	tags := regexp.MustCompile(`<[^>]+>`)
	all := make([][]string, 0, len(rows))
	for _, row := range rows {
		cells := td.FindAllStringSubmatch(row[1], -1)
		if len(cells) != 9 {
			t.Fatalf("independent source cell count = %d", len(cells))
		}
		values := make([]string, len(cells))
		for i, cell := range cells {
			values[i] = strings.Join(strings.Fields(html.UnescapeString(tags.ReplaceAllString(cell[1], " "))), " ")
		}
		all = append(all, values)
	}
	return all
}
