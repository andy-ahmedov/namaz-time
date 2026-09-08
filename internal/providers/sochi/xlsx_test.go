package sochi_test

import (
	"archive/zip"
	"bytes"
	"fmt"
	"hash/crc32"
	"io/fs"
	"reflect"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/sochi"
)

const spreadsheetNS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
const officeRelNS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
const packageRelNS = "http://schemas.openxmlformats.org/package/2006/relationships"

// Only independently authored synthetic XML/ZIP fixtures. Times deliberately
// differ from the real timetable; no real workbook bytes are embedded.
func syntheticParts(year int) map[string]string {
	stringsTable := []string{"г.Сочи, Краснодарский край", "Дата", "Фаджр", "Шурук", "Зухр", "Аср", "Магриб", "Иша",
		"Методика расчета", "Северная Широта: 43,587", "Восточная Долгота: 39,72", "Высота: 0 метров", "Время аср: стандартное",
		"Фаджр: 18°", "Иша: 17°", "Шурук: -5 минут", "Зухр: +5 минут", "Магриб: +5 минут",
		"04:01", "06:02", "12:03", "16:04", "18:05", "20:06"}
	var rows strings.Builder
	rows.WriteString(`<row r="1"><c r="A1" t="s"><v>0</v></c></row><row r="2">`)
	for index, column := range "ABCDEFG" {
		fmt.Fprintf(&rows, `<c r="%c2" t="s"><v>%d</v></c>`, column, index+1)
	}
	rows.WriteString(`<c r="I2" t="s"><v>8</v></c></row>`)
	metadata := map[int]int{4: 9, 5: 10, 6: 11, 8: 12, 9: 13, 10: 14, 12: 15, 13: 16, 14: 17}
	start, epoch := time.Date(year, 1, 1, 0, 0, 0, 0, time.UTC), time.Date(1899, 12, 30, 0, 0, 0, 0, time.UTC)
	count := 0
	for date := start; date.Year() == year; date = date.AddDate(0, 0, 1) {
		row := count + 3
		fmt.Fprintf(&rows, `<row r="%d"><c r="A%d"><v>%d</v></c>`, row, row, int(date.Sub(epoch)/(24*time.Hour)))
		for index, column := range "BCDEFG" {
			fmt.Fprintf(&rows, `<c r="%c%d" t="s"><v>%d</v></c>`, column, row, 18+index)
		}
		if value, ok := metadata[row]; ok {
			fmt.Fprintf(&rows, `<c r="I%d" t="s"><v>%d</v></c>`, row, value)
		}
		fmt.Fprintf(&rows, `<c r="P%d" s="0"/></row>`, row)
		count++
	}
	var shared strings.Builder
	fmt.Fprintf(&shared, `<sst xmlns="%s" count="%d" uniqueCount="%d">`, spreadsheetNS, count*6+18, len(stringsTable))
	for _, value := range stringsTable {
		fmt.Fprintf(&shared, `<si><t>%s</t></si>`, value)
	}
	shared.WriteString(`</sst>`)
	parts := map[string]string{
		"xl/worksheets/sheet1.xml":   fmt.Sprintf(`<worksheet xmlns="%s"><dimension ref="A1:P%d"/><sheetData>%s</sheetData><mergeCells count="1"><mergeCell ref="A1:I1"/></mergeCells></worksheet>`, spreadsheetNS, count+2, rows.String()),
		"xl/sharedStrings.xml":       shared.String(),
		"xl/workbook.xml":            fmt.Sprintf(`<workbook xmlns="%s" xmlns:r="%s"><workbookPr date1904="0"/><sheets><sheet name="Synthetic" sheetId="1" r:id="rId1"/></sheets></workbook>`, spreadsheetNS, officeRelNS),
		"xl/_rels/workbook.xml.rels": fmt.Sprintf(`<Relationships xmlns="%s"><Relationship Id="rId1" Type="%s/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="%s/sharedStrings" Target="sharedStrings.xml"/><Relationship Id="rId3" Type="%s/styles" Target="styles.xml"/><Relationship Id="rId4" Type="%s/theme" Target="theme/theme1.xml"/></Relationships>`, packageRelNS, officeRelNS, officeRelNS, officeRelNS, officeRelNS),
		"_rels/.rels":                fmt.Sprintf(`<Relationships xmlns="%s"><Relationship Id="rId1" Type="%s/officeDocument" Target="xl/workbook.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="%s/extended-properties" Target="docProps/app.xml"/></Relationships>`, packageRelNS, officeRelNS, officeRelNS),
		"xl/styles.xml":              fmt.Sprintf(`<styleSheet xmlns="%s"/>`, spreadsheetNS),
		"xl/theme/theme1.xml":        `<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Synthetic"/>`,
		"docProps/core.xml":          `<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"/>`,
		"docProps/app.xml":           `<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"/>`,
	}
	contentTypes := map[string]string{
		"/xl/workbook.xml":          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
		"/xl/worksheets/sheet1.xml": "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml",
		"/xl/sharedStrings.xml":     "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml",
		"/xl/styles.xml":            "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml",
		"/xl/theme/theme1.xml":      "application/vnd.openxmlformats-officedocument.theme+xml",
		"/docProps/core.xml":        "application/vnd.openxmlformats-package.core-properties+xml",
		"/docProps/app.xml":         "application/vnd.openxmlformats-officedocument.extended-properties+xml",
	}
	var types strings.Builder
	types.WriteString(`<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>`)
	for _, name := range sortedKeys(contentTypes) {
		fmt.Fprintf(&types, `<Override PartName="%s" ContentType="%s"/>`, name, contentTypes[name])
	}
	types.WriteString(`</Types>`)
	parts["[Content_Types].xml"] = types.String()
	return parts
}

func sortedKeys(values map[string]string) []string {
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	return keys
}

func archiveParts(t *testing.T, parts map[string]string, duplicate string) []byte {
	t.Helper()
	var raw bytes.Buffer
	writer := zip.NewWriter(&raw)
	keys := sortedKeys(parts)
	if duplicate != "" {
		keys = append(keys, duplicate)
	}
	for _, key := range keys {
		entry, err := writer.Create(key)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write([]byte(parts[key])); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return raw.Bytes()
}

func replaceCell(parts map[string]string, reference, replacement string) {
	pattern := regexp.MustCompile(`<c r="` + regexp.QuoteMeta(reference) + `"[^>]*>.*?</c>`)
	parts["xl/worksheets/sheet1.xml"] = pattern.ReplaceAllString(parts["xl/worksheets/sheet1.xml"], replacement)
}

func TestParseXLSXCompleteYearPreservesOnlySixPublishedFields(t *testing.T) {
	t.Parallel()
	for _, year := range []int{2024, 2026} {
		raw := archiveParts(t, syntheticParts(year), "")
		coverage := domain.DateRange{From: fmt.Sprintf("%d-01-01", year), To: fmt.Sprintf("%d-12-31", year)}
		days, err := sochi.ParseXLSX(raw, coverage)
		if err != nil {
			t.Fatal(err)
		}
		wantCount := 365
		if year == 2024 {
			wantCount = 366
		}
		if len(days) != wantCount || days[0].Date != coverage.From || days[len(days)-1].Date != coverage.To {
			t.Fatalf("wrong coverage: %#v", days)
		}
		want := domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{Date: coverage.From, Fajr: "04:01", Sunrise: "06:02", Dhuhr: "12:03", Asr: "16:04", Maghrib: "18:05", Isha: "20:06"}}
		if !reflect.DeepEqual(days[0], want) {
			t.Fatalf("field semantics = %#v", days[0])
		}
		for _, day := range days {
			if day.HijriDay != 0 || day.HijriMonth != "" || day.HijriYear != 0 || day.RecommendedFajr != "" || day.DhuhrCongregation != "" {
				t.Fatalf("invented metadata = %#v", day)
			}
		}
		again, err := sochi.ParseXLSX(raw, coverage)
		if err != nil || !reflect.DeepEqual(days, again) {
			t.Fatal("nondeterministic parser")
		}
	}
}

func TestParseXLSXExplicitSeptemberStillValidatesUnselectedRows(t *testing.T) {
	coverage := domain.DateRange{From: "2026-09-01", To: "2026-09-30"}
	parts := syntheticParts(2026)
	days, err := sochi.ParseXLSX(archiveParts(t, parts, ""), coverage)
	if err != nil || len(days) != 30 || days[0].Date != coverage.From || days[29].Date != coverage.To {
		t.Fatalf("bounded range: %d %v", len(days), err)
	}
	replaceCell(parts, "B3", `<c r="B3" t="s"><v>23</v></c>`)
	if days, err := sochi.ParseXLSX(archiveParts(t, parts, ""), coverage); err == nil || days != nil {
		t.Fatal("bad January time hidden by September selection")
	}
}

func TestParseXLSXAllowsKnownNonDataExcelMetadata(t *testing.T) {
	parts := syntheticParts(2026)
	parts["xl/workbook.xml"] = strings.Replace(parts["xl/workbook.xml"], `<workbook xmlns=`, `<workbook xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" mc:Ignorable="x15" xmlns=`, 1)
	parts["xl/workbook.xml"] = strings.Replace(parts["xl/workbook.xml"], `<workbookPr`, `<fileVersion appName="xl" lastEdited="6" lowestEdited="6" rupBuild="123"/><mc:AlternateContent><mc:Choice Requires="x15"><x15:absPath xmlns:x15="http://schemas.microsoft.com/office/spreadsheetml/2010/11/ac" url="synthetic-never-resolved"/></mc:Choice></mc:AlternateContent><bookViews><workbookView xWindow="0" yWindow="0" windowWidth="100" windowHeight="100"/></bookViews><calcPr calcId="123" refMode="R1C1"/><workbookPr`, 1)
	parts["xl/worksheets/sheet1.xml"] = strings.Replace(parts["xl/worksheets/sheet1.xml"], `<worksheet xmlns=`, `<worksheet xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" mc:Ignorable="x14ac" xmlns=`, 1)
	parts["xl/worksheets/sheet1.xml"] = strings.Replace(parts["xl/worksheets/sheet1.xml"], `<dimension`, `<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr><sheetViews><sheetView showGridLines="0" tabSelected="1" workbookViewId="0"><selection activeCell="G3" sqref="G3"/></sheetView></sheetViews><sheetFormatPr defaultColWidth="8" defaultRowHeight="20" customHeight="1"/><cols><col min="1" max="16" width="8" style="0" customWidth="1"/></cols><pageMargins left="1" right="1" top="1" bottom="1" header="0.25" footer="0.25"/><pageSetup orientation="portrait"/><headerFooter><oddFooter>synthetic</oddFooter></headerFooter><dimension`, 1)
	days, err := sochi.ParseXLSX(archiveParts(t, parts, ""), domain.DateRange{From: "2026-09-08", To: "2026-09-08"})
	if err != nil || len(days) != 1 || days[0].Fajr != "04:01" {
		t.Fatalf("known formatting changed data: %v", err)
	}
}

func TestParseXLSXFailsClosedForDataAndPackageDrift(t *testing.T) {
	t.Parallel()
	mutations := map[string]func(map[string]string){
		"wrong_city": func(p map[string]string) {
			p["xl/sharedStrings.xml"] = strings.ReplaceAll(p["xl/sharedStrings.xml"], "г.Сочи, Краснодарский край", "Another city")
		},
		"header_drift": func(p map[string]string) {
			p["xl/sharedStrings.xml"] = strings.ReplaceAll(p["xl/sharedStrings.xml"], ">Фаджр<", ">Unknown<")
		},
		"policy_drift": func(p map[string]string) {
			p["xl/sharedStrings.xml"] = strings.ReplaceAll(p["xl/sharedStrings.xml"], "Фаджр: 18°", "Фаджр: 19°")
		},
		"bad_clock": func(p map[string]string) {
			p["xl/sharedStrings.xml"] = strings.ReplaceAll(p["xl/sharedStrings.xml"], "04:01", "24:01")
		},
		"noncanonical_clock": func(p map[string]string) {
			p["xl/sharedStrings.xml"] = strings.ReplaceAll(p["xl/sharedStrings.xml"], "04:01", "4:01")
		},
		"unknown_marker": func(p map[string]string) {
			p["xl/sharedStrings.xml"] = strings.ReplaceAll(p["xl/sharedStrings.xml"], "04:01", "04:01*")
		},
		"duplicate_shared_string": func(p map[string]string) {
			p["xl/sharedStrings.xml"] = strings.Replace(p["xl/sharedStrings.xml"], `uniqueCount="24"`, `uniqueCount="25"`, 1)
			p["xl/sharedStrings.xml"] = strings.Replace(p["xl/sharedStrings.xml"], `</sst>`, `<si><t>04:01</t></si></sst>`, 1)
		},
		"wrong_shared_reference_count": func(p map[string]string) {
			p["xl/sharedStrings.xml"] = strings.Replace(p["xl/sharedStrings.xml"], `count="2208"`, `count="2209"`, 1)
		},
		"rich_string": func(p map[string]string) {
			p["xl/sharedStrings.xml"] = strings.Replace(p["xl/sharedStrings.xml"], `<t>04:01</t>`, `<r><t>04:01</t></r>`, 1)
		},
		"bad_reference":      func(p map[string]string) { replaceCell(p, "B3", `<c r="B3" t="s"><v>99999</v></c>`) },
		"negative_reference": func(p map[string]string) { replaceCell(p, "B3", `<c r="B3" t="s"><v>-1</v></c>`) },
		"duplicate_date":     func(p map[string]string) { replaceCell(p, "A4", `<c r="A4"><v>46023</v></c>`) },
		"fractional_date":    func(p map[string]string) { replaceCell(p, "A3", `<c r="A3"><v>46023.5</v></c>`) },
		"zero_padded_date":   func(p map[string]string) { replaceCell(p, "A3", `<c r="A3"><v>046023</v></c>`) },
		"string_date":        func(p map[string]string) { replaceCell(p, "A3", `<c r="A3" t="s"><v>18</v></c>`) },
		"missing_cell":       func(p map[string]string) { replaceCell(p, "B3", "") },
		"duplicate_cell": func(p map[string]string) {
			replaceCell(p, "B3", `<c r="B3" t="s"><v>18</v></c><c r="B3" t="s"><v>18</v></c>`)
		},
		"cell_wrong_row": func(p map[string]string) { replaceCell(p, "B3", `<c r="B4" t="s"><v>18</v></c>`) },
		"formula":        func(p map[string]string) { replaceCell(p, "B3", `<c r="B3" t="s"><f>18</f><v>18</v></c>`) },
		"unknown_value_column": func(p map[string]string) {
			replaceCell(p, "G3", `<c r="G3" t="s"><v>23</v></c><c r="H3" t="s"><v>18</v></c>`)
		},
		"duplicate_attribute": func(p map[string]string) { replaceCell(p, "B3", `<c r="B3" r="B3" t="s"><v>18</v></c>`) },
		"wrong_namespace":     func(p map[string]string) { replaceCell(p, "B3", `<c xmlns="urn:other" r="B3" t="s"><v>18</v></c>`) },
		"missing_row": func(p map[string]string) {
			p["xl/worksheets/sheet1.xml"] = regexp.MustCompile(`<row r="100">.*?</row>`).ReplaceAllString(p["xl/worksheets/sheet1.xml"], "")
		},
		"hidden_row": func(p map[string]string) {
			p["xl/worksheets/sheet1.xml"] = strings.Replace(p["xl/worksheets/sheet1.xml"], `<row r="3">`, `<row r="3" hidden="1">`, 1)
		},
		"zero_height_row": func(p map[string]string) {
			p["xl/worksheets/sheet1.xml"] = strings.Replace(p["xl/worksheets/sheet1.xml"], `<row r="3">`, `<row r="3" ht="0">`, 1)
		},
		"unknown_workbook_attribute": func(p map[string]string) {
			p["xl/workbook.xml"] = strings.Replace(p["xl/workbook.xml"], `<workbook xmlns=`, `<workbook unknown="1" xmlns=`, 1)
		},
		"unknown_workbook_view_child": func(p map[string]string) {
			p["xl/workbook.xml"] = strings.Replace(p["xl/workbook.xml"], `<workbookPr`, `<bookViews><unknown/></bookViews><workbookPr`, 1)
		},
		"unknown_column_definition": func(p map[string]string) {
			p["xl/worksheets/sheet1.xml"] = strings.Replace(p["xl/worksheets/sheet1.xml"], `<dimension`, `<cols><unknown/></cols><dimension`, 1)
		},
		"hidden_column": func(p map[string]string) {
			p["xl/worksheets/sheet1.xml"] = strings.Replace(p["xl/worksheets/sheet1.xml"], `<dimension`, `<cols><col min="2" max="2" width="8" hidden="1"/></cols><dimension`, 1)
		},
		"zero_height_default_rows": func(p map[string]string) {
			p["xl/worksheets/sheet1.xml"] = strings.Replace(p["xl/worksheets/sheet1.xml"], `<dimension`, `<sheetFormatPr zeroHeight="1"/><dimension`, 1)
		},
		"alternate_content_unknown": func(p map[string]string) {
			p["xl/workbook.xml"] = strings.Replace(p["xl/workbook.xml"], `<workbookPr`, `<mc:AlternateContent xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"><unknown/></mc:AlternateContent><workbookPr`, 1)
		},
		"new_sheet": func(p map[string]string) { p["xl/worksheets/sheet2.xml"] = p["xl/worksheets/sheet1.xml"] },
		"extra_workbook_sheet": func(p map[string]string) {
			p["xl/workbook.xml"] = strings.Replace(p["xl/workbook.xml"], `</sheets>`, `<sheet name="Other" sheetId="2" r:id="rId1"/></sheets>`, 1)
		},
		"wrong_epoch": func(p map[string]string) {
			p["xl/workbook.xml"] = strings.ReplaceAll(p["xl/workbook.xml"], `date1904="0"`, `date1904="1"`)
		},
		"external_relationship": func(p map[string]string) {
			p["xl/_rels/workbook.xml.rels"] = strings.Replace(p["xl/_rels/workbook.xml.rels"], `Target="worksheets/sheet1.xml"`, `Target="https://example.invalid/evil.xml" TargetMode="External"`, 1)
		},
		"traversal_relationship": func(p map[string]string) {
			p["xl/_rels/workbook.xml.rels"] = strings.Replace(p["xl/_rels/workbook.xml.rels"], `Target="worksheets/sheet1.xml"`, `Target="../worksheets/sheet1.xml"`, 1)
		},
		"archive_path_traversal": func(p map[string]string) { p["../outside.xml"] = `<x/>` },
		"unknown_archive_part":   func(p map[string]string) { p["xl/vbaProject.bin"] = "synthetic" },
		"missing_part":           func(p map[string]string) { delete(p, "xl/sharedStrings.xml") },
		"dtd": func(p map[string]string) {
			p["xl/worksheets/sheet1.xml"] = `<!DOCTYPE worksheet [<!ENTITY bad SYSTEM "file:///never-read">]>` + p["xl/worksheets/sheet1.xml"]
		},
		"invalid_utf8":            func(p map[string]string) { p["xl/worksheets/sheet1.xml"] += "\xff" },
		"multiple_roots":          func(p map[string]string) { p["xl/worksheets/sheet1.xml"] += `<worksheet/>` },
		"oversized_inflated_part": func(p map[string]string) { p["xl/theme/theme1.xml"] = `<x>` + strings.Repeat("a", 1024*1024) + `</x>` },
		"excessive_depth": func(p map[string]string) {
			p["xl/theme/theme1.xml"] = strings.Repeat(`<x>`, 80) + strings.Repeat(`</x>`, 80)
		},
		"excessive_nodes": func(p map[string]string) {
			p["xl/theme/theme1.xml"] = `<x>` + strings.Repeat(`<y/>`, 20000) + `</x>`
		},
		"oversized_attribute": func(p map[string]string) {
			p["xl/theme/theme1.xml"] = `<x a="` + strings.Repeat("a", 4097) + `"/>`
		},
		"processing_instruction": func(p map[string]string) {
			p["xl/worksheets/sheet1.xml"] = `<?external read="never"?>` + p["xl/worksheets/sheet1.xml"]
		},
		"total_inflated_size": func(p map[string]string) {
			for name := range p {
				p[name] = `<!--` + strings.Repeat("a", 450000) + `-->` + p[name]
			}
		},
	}
	for name, mutate := range mutations {
		t.Run(name, func(t *testing.T) {
			parts := syntheticParts(2026)
			mutate(parts)
			days, err := sochi.ParseXLSX(archiveParts(t, parts, ""), domain.DateRange{From: "2026-01-01", To: "2026-12-31"})
			if err == nil || days != nil {
				t.Fatalf("drift accepted: %d %v", len(days), err)
			}
		})
	}
	for name, raw := range map[string][]byte{"empty": nil, "invalid_zip": []byte("not zip"), "oversize": bytes.Repeat([]byte("a"), 2*1024*1024+1), "duplicate_part": archiveParts(t, syntheticParts(2026), "xl/worksheets/sheet1.xml")} {
		t.Run(name, func(t *testing.T) {
			if days, err := sochi.ParseXLSX(raw, domain.DateRange{From: "2026-09-01", To: "2026-09-30"}); err == nil || days != nil {
				t.Fatal("bad package accepted")
			}
		})
	}
}

func TestParseXLSXRejectsInvalidOrUnavailableCoverage(t *testing.T) {
	raw := archiveParts(t, syntheticParts(2026), "")
	for _, coverage := range []domain.DateRange{{}, {From: "2026-9-01", To: "2026-09-30"}, {From: "2026-09-30", To: "2026-09-01"}, {From: "2026-02-29", To: "2026-03-01"}, {From: "2026-12-31", To: "2027-01-01"}, {From: "2025-01-01", To: "2025-12-31"}} {
		if days, err := sochi.ParseXLSX(raw, coverage); err == nil || days != nil {
			t.Fatalf("bad coverage %#v accepted", coverage)
		}
	}
	for _, year := range []int{1999, 2101} {
		coverage := domain.DateRange{From: strconv.Itoa(year) + "-01-01", To: strconv.Itoa(year) + "-01-01"}
		if days, err := sochi.ParseXLSX(raw, coverage); err == nil || days != nil {
			t.Fatalf("unsupported year %d accepted", year)
		}
	}
}

func TestParseXLSXRejectsUntrustedZIPMetadataAndIntegrity(t *testing.T) {
	for name, alter := range map[string]func(*zip.FileHeader){
		"encrypted":          func(header *zip.FileHeader) { header.Flags |= 1 },
		"symlink":            func(header *zip.FileHeader) { header.SetMode(fs.ModeSymlink | 0o777) },
		"unknown_method":     func(header *zip.FileHeader) { header.Method = 99 },
		"crc_mismatch":       func(header *zip.FileHeader) { header.CRC32++ },
		"understated_size":   func(header *zip.FileHeader) { header.UncompressedSize64-- },
		"overstated_size":    func(header *zip.FileHeader) { header.UncompressedSize64++ },
		"absolute_path":      func(header *zip.FileHeader) { header.Name = "/xl/sharedStrings.xml" },
		"normalized_path":    func(header *zip.FileHeader) { header.Name = "xl/../xl/sharedStrings.xml" },
		"backslash_path":     func(header *zip.FileHeader) { header.Name = `xl\sharedStrings.xml` },
		"zero_inflated_size": func(header *zip.FileHeader) { header.UncompressedSize64 = 0 },
	} {
		t.Run(name, func(t *testing.T) {
			parts := syntheticParts(2026)
			var raw bytes.Buffer
			writer := zip.NewWriter(&raw)
			for _, name := range sortedKeys(parts) {
				data := []byte(parts[name])
				header := &zip.FileHeader{Name: name, Method: zip.Store, CRC32: crc32.ChecksumIEEE(data),
					CompressedSize64: uint64(len(data)), UncompressedSize64: uint64(len(data))}
				if name == "xl/sharedStrings.xml" {
					alter(header)
				}
				entry, err := writer.CreateRaw(header)
				if err != nil {
					t.Fatal(err)
				}
				if _, err := entry.Write(data); err != nil {
					t.Fatal(err)
				}
			}
			if err := writer.Close(); err != nil {
				t.Fatal(err)
			}
			if days, err := sochi.ParseXLSX(raw.Bytes(), domain.DateRange{From: "2026-09-01", To: "2026-09-30"}); err == nil || days != nil {
				t.Fatal("untrusted ZIP metadata accepted")
			}
		})
	}
	parts := syntheticParts(2026)
	delete(parts, "xl/theme/theme1.xml")
	if days, err := sochi.ParseXLSX(archiveParts(t, parts, "xl/worksheets/sheet1.xml"), domain.DateRange{From: "2026-09-01", To: "2026-09-30"}); err == nil || days != nil {
		t.Fatal("duplicate member accepted despite correct total member count")
	}
}
