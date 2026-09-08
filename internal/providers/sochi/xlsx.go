// Package sochi normalizes the community's exact public Sochi workbook.
// Retrieval, source qualification and publication are separate responsibilities.
package sochi

import (
	"encoding/xml"
	"errors"
	"fmt"
	"math"
	"strconv"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

const (
	ParserVersion  = "sochi-city-xlsx/v1"
	SourceCity     = "Сочи"
	SourceTimezone = "Europe/Moscow"
	SourceScope    = "г.Сочи, Краснодарский край"
)

var policyNotes = map[int]string{
	2: "Методика расчета", 4: "Северная Широта: 43,587", 5: "Восточная Долгота: 39,72", 6: "Высота: 0 метров",
	8: "Время аср: стандартное", 9: "Фаджр: 18°", 10: "Иша: 17°", 12: "Шурук: -5 минут", 13: "Зухр: +5 минут", 14: "Магриб: +5 минут",
}

var tableHeaders = []string{"Дата", "Фаджр", "Шурук", "Зухр", "Аср", "Магриб", "Иша"}

// ParseXLSX validates the complete Gregorian-year Sochi workbook before
// selecting the explicit within-year range. ZIP members are never extracted;
// relationships are allowlisted, never resolved over the network. Every date,
// six onset/sunrise fields and published policy-note cell must match the known
// schema, including days outside coverage. No calculation, adjustment, iqamah,
// Hijri date, source qualification or publication is introduced.
//
// The caller must bind SourceCity/SourceScope and SourceTimezone to verified
// authority/catalog evidence and retain the raw hash separately.
func ParseXLSX(raw []byte, coverage domain.DateRange) ([]domain.CandidatePrayerDay, error) {
	start, errFrom := time.Parse(time.DateOnly, coverage.From)
	last, errTo := time.Parse(time.DateOnly, coverage.To)
	if errFrom != nil || errTo != nil || start.Format(time.DateOnly) != coverage.From || last.Format(time.DateOnly) != coverage.To ||
		last.Before(start) || last.Year() != start.Year() || start.Year() < 2000 || start.Year() > 2100 {
		return nil, errors.New("sochi XLSX: explicit canonical within-year coverage is required")
	}
	parts, err := readPackage(raw)
	if err != nil {
		return nil, fmt.Errorf("sochi XLSX: %w", err)
	}
	if err := validateWorkbook(parts); err != nil {
		return nil, fmt.Errorf("sochi XLSX: %w", err)
	}
	shared, references, err := parseSharedStrings(parts["xl/sharedStrings.xml"])
	if err != nil {
		return nil, fmt.Errorf("sochi XLSX: %w", err)
	}
	days, err := parseSheet(parts["xl/worksheets/sheet1.xml"], shared, references, start, last)
	if err != nil {
		return nil, fmt.Errorf("sochi XLSX: %w", err)
	}
	return days, nil
}

func validateWorkbook(parts map[string]*xmlNode) error {
	root := parts["xl/workbook.xml"]
	if !root.is(spreadsheetNS, "workbook") || !rootMetadataAttrs(root, "x15") || !root.container() {
		return errors.New("unknown workbook root")
	}
	seen := make(map[string]bool)
	for _, child := range root.children {
		allowed := child.name.Space == spreadsheetNS && strings.Contains("|fileVersion|workbookPr|bookViews|sheets|calcPr|", "|"+child.name.Local+"|")
		allowed = allowed || child.is("http://schemas.openxmlformats.org/markup-compatibility/2006", "AlternateContent")
		if !allowed || seen[child.name.Local] {
			return errors.New("duplicate or unknown workbook element")
		}
		seen[child.name.Local] = true
		switch child.name.Local {
		case "AlternateContent":
			if err := validateAlternateContent(child); err != nil {
				return err
			}
		case "fileVersion", "bookViews", "calcPr":
			if err := validateFormatting(child); err != nil {
				return err
			}
		}
	}
	properties, err := singleChild(root, spreadsheetNS, "workbookPr")
	if err != nil || !properties.onlyAttrs("date1904") || len(properties.children) != 0 || !properties.container() ||
		(properties.attr("date1904") != "" && properties.attr("date1904") != "0" && properties.attr("date1904") != "false") {
		return errors.New("unsupported workbook properties or Excel date epoch")
	}
	sheets, err := singleChild(root, spreadsheetNS, "sheets")
	if err != nil || !sheets.onlyAttrs() || !sheets.container() || len(sheets.children) != 1 {
		return errors.New("expected exactly one worksheet")
	}
	sheet := sheets.children[0]
	if !sheet.is(spreadsheetNS, "sheet") || !sheet.container() || len(sheet.children) != 0 || sheet.attr("name") == "" ||
		sheet.attr("sheetId") != "1" || (sheet.attr("state") != "" && sheet.attr("state") != "visible") {
		return errors.New("invalid or hidden worksheet identity")
	}
	relationID := sheet.attrs[xml.Name{Space: officeRelNS, Local: "id"}]
	for attr := range sheet.attrs {
		if attr.Space == "xmlns" || (attr.Space == "" && attr.Local == "xmlns") || attr == (xml.Name{Space: officeRelNS, Local: "id"}) {
			continue
		}
		if attr.Space != "" || (attr.Local != "name" && attr.Local != "sheetId" && attr.Local != "state") {
			return errors.New("unknown worksheet identity attribute")
		}
	}
	relations, err := validateRelationships(parts["xl/_rels/workbook.xml.rels"], map[string]string{
		officeRelNS + "/worksheet": "worksheets/sheet1.xml", officeRelNS + "/sharedStrings": "sharedStrings.xml",
		officeRelNS + "/styles": "styles.xml", officeRelNS + "/theme": "theme/theme1.xml",
	})
	if err != nil || relations[relationID] != officeRelNS+"/worksheet" {
		return errors.New("workbook worksheet relationship mismatch")
	}
	return nil
}

func parseSharedStrings(root *xmlNode) ([]string, int, error) {
	if !root.is(spreadsheetNS, "sst") || !root.onlyAttrs("count", "uniqueCount") || !root.container() || len(root.children) < 1 || len(root.children) > 4096 {
		return nil, 0, errors.New("unknown shared-string table schema")
	}
	unique, err := canonicalInteger(root.attr("uniqueCount"), 4096)
	count, countErr := canonicalInteger(root.attr("count"), 10000)
	if err != nil || countErr != nil || unique != len(root.children) || count < unique {
		return nil, 0, errors.New("invalid shared-string counts")
	}
	allowed := map[string]bool{SourceScope: true}
	for _, header := range tableHeaders {
		allowed[header] = true
	}
	for _, note := range policyNotes {
		allowed[note] = true
	}
	values := make([]string, 0, unique)
	seenValues := make(map[string]bool, unique)
	for _, child := range root.children {
		if !child.is(spreadsheetNS, "si") || !child.onlyAttrs() || !child.container() || len(child.children) != 1 {
			return nil, 0, errors.New("unsupported shared-string entry")
		}
		text := child.children[0]
		if !text.is(spreadsheetNS, "t") || len(text.children) != 0 || len(text.text) > 160 {
			return nil, 0, errors.New("unsupported shared-string value")
		}
		for name, value := range text.attrs {
			if name != (xml.Name{Space: "http://www.w3.org/XML/1998/namespace", Local: "space"}) || value != "preserve" {
				return nil, 0, errors.New("unknown shared-string text attribute")
			}
		}
		if !allowed[text.text] && !validClock(text.text) {
			return nil, 0, errors.New("unknown shared string or noncanonical clock")
		}
		if seenValues[text.text] {
			return nil, 0, errors.New("duplicate shared string contradicts unique count")
		}
		seenValues[text.text] = true
		values = append(values, text.text)
	}
	return values, count, nil
}

func parseSheet(root *xmlNode, shared []string, references int, start, last time.Time) ([]domain.CandidatePrayerDay, error) {
	if !root.is(spreadsheetNS, "worksheet") || !rootMetadataAttrs(root, "x14ac") || !root.container() {
		return nil, errors.New("unknown worksheet root")
	}
	seen := make(map[string]bool)
	for _, child := range root.children {
		if child.name.Space != spreadsheetNS || seen[child.name.Local] || !strings.Contains("|sheetPr|dimension|sheetViews|sheetFormatPr|cols|sheetData|mergeCells|pageMargins|pageSetup|headerFooter|", "|"+child.name.Local+"|") {
			return nil, errors.New("duplicate or unsupported worksheet element")
		}
		seen[child.name.Local] = true
		switch child.name.Local {
		case "sheetPr", "sheetViews", "sheetFormatPr", "cols", "pageMargins", "pageSetup", "headerFooter":
			if err := validateFormatting(child); err != nil {
				return nil, err
			}
		}
	}
	first := time.Date(start.Year(), 1, 1, 0, 0, 0, 0, time.UTC)
	yearDays := int(first.AddDate(1, 0, 0).Sub(first) / (24 * time.Hour))
	dimension, dimensionErr := singleChild(root, spreadsheetNS, "dimension")
	if dimensionErr != nil || !dimension.onlyAttrs("ref") || !dimension.container() || len(dimension.children) != 0 || dimension.attr("ref") != fmt.Sprintf("A1:P%d", yearDays+2) {
		return nil, errors.New("unexpected worksheet dimension")
	}
	merges, mergeErr := singleChild(root, spreadsheetNS, "mergeCells")
	if mergeErr != nil || !merges.onlyAttrs("count") || merges.attr("count") != "1" || !merges.container() || len(merges.children) != 1 ||
		!merges.children[0].is(spreadsheetNS, "mergeCell") || !merges.children[0].onlyAttrs("ref") || merges.children[0].attr("ref") != "A1:I1" ||
		!merges.children[0].container() || len(merges.children[0].children) != 0 {
		return nil, errors.New("unsupported worksheet merge")
	}
	data, err := singleChild(root, spreadsheetNS, "sheetData")
	if err != nil || !data.onlyAttrs() || !data.container() || len(data.children) != yearDays+2 {
		return nil, errors.New("incomplete annual worksheet row set")
	}
	days := make([]domain.CandidatePrayerDay, 0, int(last.Sub(start)/(24*time.Hour))+1)
	referenceCount := 0
	epoch := time.Date(1899, 12, 30, 0, 0, 0, 0, time.UTC)
	for index, row := range data.children {
		rowNumber := index + 1
		if !row.is(spreadsheetNS, "row") || !row.onlyAttrs("r", "spans", "ht", "customHeight") || !row.container() || row.attr("r") != strconv.Itoa(rowNumber) || len(row.children) > 16 {
			return nil, fmt.Errorf("row %d: unsupported row identity or schema", rowNumber)
		}
		if value := row.attr("ht"); value != "" {
			height, err := strconv.ParseFloat(value, 64)
			if err != nil || math.IsNaN(height) || math.IsInf(height, 0) || height <= 0 {
				return nil, fmt.Errorf("row %d: invalid or hidden row height", rowNumber)
			}
		}
		values, count, err := parseRow(row, rowNumber, shared)
		if err != nil {
			return nil, fmt.Errorf("row %d: %w", rowNumber, err)
		}
		referenceCount += count
		if note, exists := policyNotes[rowNumber]; exists {
			if values[8] != note {
				return nil, fmt.Errorf("row %d: published policy note missing or changed", rowNumber)
			}
		} else if values[8] != "" {
			return nil, fmt.Errorf("row %d: unknown policy note", rowNumber)
		}
		if rowNumber == 1 {
			if values[0] != SourceScope {
				return nil, errors.New("workbook geographic scope mismatch")
			}
			for _, value := range values[1:] {
				if value != "" {
					return nil, errors.New("unknown title-row value")
				}
			}
			continue
		}
		if rowNumber == 2 {
			for column, expected := range tableHeaders {
				if values[column] != expected {
					return nil, errors.New("worksheet prayer header mismatch")
				}
			}
			continue
		}
		date := first.AddDate(0, 0, rowNumber-3)
		serial := strconv.FormatInt(int64(date.Sub(epoch)/(24*time.Hour)), 10)
		if values[0] != serial {
			return nil, fmt.Errorf("row %d: unexpected Excel date, duplicate or gap", rowNumber)
		}
		for column := 1; column <= 6; column++ {
			if !validClock(values[column]) || (column > 1 && values[column-1] >= values[column]) {
				return nil, fmt.Errorf("row %d: invalid clock or prayer order", rowNumber)
			}
		}
		if !date.Before(start) && !date.After(last) {
			days = append(days, domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{Date: date.Format(time.DateOnly),
				Fajr: values[1], Sunrise: values[2], Dhuhr: values[3], Asr: values[4], Maghrib: values[5], Isha: values[6]}})
		}
	}
	if referenceCount != references {
		return nil, errors.New("shared-string reference count mismatch")
	}
	return days, nil
}

func parseRow(row *xmlNode, number int, shared []string) ([16]string, int, error) {
	var values [16]string
	count, previous := 0, -1
	for _, cell := range row.children {
		reference := cell.attr("r")
		if !cell.is(spreadsheetNS, "c") || !cell.onlyAttrs("r", "s", "t") || !cell.container() || len(reference) < 2 || reference[0] < 'A' || reference[0] > 'P' || reference[1:] != strconv.Itoa(number) {
			return values, 0, errors.New("invalid cell identity or attributes")
		}
		column := int(reference[0] - 'A')
		if column <= previous {
			return values, 0, errors.New("duplicate or out-of-order cell")
		}
		previous = column
		if cell.attr("s") != "" {
			if _, err := canonicalInteger(cell.attr("s"), 4096); err != nil {
				return values, 0, errors.New("invalid cell style index")
			}
		}
		if len(cell.children) == 0 {
			if cell.attr("t") != "" {
				return values, 0, errors.New("typed cell lacks its value")
			}
			continue
		}
		if len(cell.children) != 1 || !cell.children[0].is(spreadsheetNS, "v") || !cell.children[0].scalar() {
			return values, 0, errors.New("formula, unknown or duplicate cell value")
		}
		if column >= 7 && column != 8 {
			return values, 0, errors.New("unexpected data outside known columns")
		}
		value := cell.children[0].text
		if number >= 3 && column == 0 {
			if cell.attr("t") != "" && cell.attr("t") != "n" {
				return values, 0, errors.New("date is not an Excel numeric serial")
			}
		} else {
			if cell.attr("t") != "s" {
				return values, 0, errors.New("expected a shared-string cell")
			}
			index, err := canonicalInteger(value, len(shared)-1)
			if err != nil {
				return values, 0, errors.New("invalid shared-string reference")
			}
			value = shared[index]
			count++
		}
		values[column] = value
	}
	return values, count, nil
}

func canonicalInteger(value string, maximum int) (int, error) {
	number, err := strconv.Atoi(value)
	if err != nil || number < 0 || number > maximum || strconv.Itoa(number) != value {
		return 0, errors.New("invalid canonical integer")
	}
	return number, nil
}

func validClock(value string) bool {
	if len(value) != 5 || value[2] != ':' {
		return false
	}
	for index := range value {
		if index != 2 && (value[index] < '0' || value[index] > '9') {
			return false
		}
	}
	return value[:2] <= "23" && value[3:] <= "59"
}
