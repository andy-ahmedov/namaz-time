// Package cdum normalizes the specifically researched CDUM city HTML calendar.
// Retrieval, source qualification and publication are separate responsibilities.
package cdum

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/stricthtml"
	"golang.org/x/net/html"
)

const ParserVersion = "cdum-city-html/v1"

const (
	maxArtifactBytes = 4 * 1024 * 1024
	maxArtifactRunes = 2 * 1024 * 1024
	maxDocumentNodes = 100000
	maxDocumentDepth = 64
	maxFieldRunes    = 128
)

var (
	monthNames = [...]string{"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"}
	weekdays   = [...]string{"Вс.", "Пн.", "Вт.", "Ср.", "Чт.", "Пт.", "Сб."}
	columns    = [...]string{"Дата", "Фаджр", "Восход", "Зухр", "Аср", "Магриб", "Иша"}
	dayPattern = regexp.MustCompile(`^([1-9]|[12][0-9]|3[01]) (Вс|Пн|Вт|Ср|Чт|Пт|Сб)\.$`)
)

// ParseHTML accepts only explicitly researched CDUM city/path bindings. It does
// not derive geography from legacy filenames. Coverage must name contiguous
// complete months within one Gregorian year. The entire annual schema and every
// date/clock are checked;
// same-day ordering is enforced for every requested day. Unsupported midnight
// times outside the requested range are not returned, repaired or interpolated.
//
// This pure parser establishes no ownership, timezone, qualification, iqamah or
// publication decision. Callers retain/hash the original response separately.
func ParseHTML(raw []byte, locality string, coverage domain.DateRange) ([]domain.CandidatePrayerDay, error) {
	path := ""
	switch locality {
	case "Москва":
		path = "/time-namaz/Moskva/index.php"
	case "Санкт-Петербург":
		path = "/time-namaz/Spb.php"
	case "Казань":
		path = "/time-namaz/Kazan/Kazan.php"
	case "Астрахань":
		path = "/time-namaz/astrakhan/Astrakhan.php"
	case "Ростов-на-Дону":
		path = "/time-namaz/Rostov-na-Dony/Rostov.php"
	case "Киров":
		path = "/time-namaz/Киров/Kirov.php"
	case "Екатеринбург":
		path = "/time-namaz/ekb/Ekb2015.php"
	case "Челябинск":
		path = "/time-namaz/Chelyabinsk/Chelyabinsk2015.php"
	case "Ижевск":
		path = "/time-namaz/izhevsk/Izhevsk2015.php"
	case "Йошкар-Ола":
		path = "/time-namaz/yoshkar-ola/Yoshkar-Ola2015.php"
	case "Курган":
		path = "/time-namaz/Kurgan/Kurgan.php"
	case "Пенза":
		path = "/time-namaz/Пенза/Penza2015.php"
	case "Пермь":
		path = "/time-namaz/Пермь/Perm.php"
	case "Самара":
		path = "/time-namaz/samara/Samara2015.php"
	case "Ульяновск":
		path = "/time-namaz/Ulyanovsk/Ulyanovsk2015.php"
	case "Чебоксары":
		path = "/time-namaz/cheboksary/Cheboksary.php"
	case "Салехард":
		path = "/time-namaz/Surgut/Surgut2015.php"
	case "Хабаровск":
		path = "/time-namaz/khabarovsk/Habarovsk2015.php"
	default:
		return nil, errors.New("cdum HTML: unsupported locality")
	}
	from, to, err := parseCoverage(coverage)
	if err != nil {
		return nil, err
	}
	if len(raw) == 0 || len(raw) > maxArtifactBytes || !utf8.Valid(raw) || utf8.RuneCount(raw) > maxArtifactRunes || bytes.IndexByte(raw, 0) >= 0 {
		return nil, errors.New("cdum HTML: expected bounded nonempty UTF-8 artifact")
	}
	if err := validateComments(raw); err != nil {
		return nil, err
	}
	parseCopy, err := annotateRawAttributeErrors(raw)
	if err != nil {
		return nil, err
	}
	doc, err := html.Parse(bytes.NewReader(parseCopy))
	if err != nil {
		return nil, fmt.Errorf("cdum HTML: decode document: %w", err)
	}
	block, err := findCalendar(doc, path, locality, from.Year())
	if err != nil {
		return nil, err
	}
	for ancestor := block; ancestor != nil; ancestor = ancestor.Parent {
		if ancestor.Type == html.ElementNode {
			if err := staticAttributes(ancestor); err != nil {
				return nil, fmt.Errorf("cdum HTML: calendar is not a visible static subtree: %w", err)
			}
		}
	}
	children, err := elementChildren(block)
	if err != nil {
		return nil, err
	}
	if len(children) == 0 || children[0].Data != "h1" || attribute(children[0], "id") != "pagetitle" {
		return nil, errors.New("cdum HTML: missing first locality heading")
	}
	heading, err := inlineText(children[0])
	if err != nil || heading != locality {
		return nil, errors.New("cdum HTML: locality heading mismatch")
	}
	var days []domain.CandidatePrayerDay
	nextMonth, pendingMonth := 1, 0
	for _, child := range children[1:] {
		switch child.Data {
		case "p":
			text, err := inlineText(child)
			if err != nil {
				return nil, err
			}
			if text == "" {
				continue
			}
			if nextMonth > 12 || pendingMonth != 0 || text != monthNames[nextMonth-1] {
				return nil, errors.New("cdum HTML: unexpected, duplicate or out-of-order month heading/marker")
			}
			pendingMonth = nextMonth
		case "table":
			if pendingMonth == 0 {
				return nil, errors.New("cdum HTML: table without unique month heading")
			}
			monthDays, err := parseMonth(child, from.Year(), time.Month(pendingMonth), from, to)
			if err != nil {
				return nil, err
			}
			days = append(days, monthDays...)
			pendingMonth = 0
			nextMonth++
		default:
			return nil, fmt.Errorf("cdum HTML: unexpected calendar element %s", child.Data)
		}
	}
	if nextMonth != 13 || pendingMonth != 0 || len(days) != int(to.Sub(from)/(24*time.Hour))+1 {
		return nil, errors.New("cdum HTML: missing month or incomplete requested coverage")
	}
	return days, nil
}

const rawAttributeErrorMarker = "data-namaztime-raw-attribute-error"

// HTML tokenization discards duplicate attributes. Preserve raw-tag errors in
// an internal DOM-input copy, then let the existing evidence traversal reject
// marked calendar nodes, their ancestors and city/year links. Unrelated page
// markup is not promoted to evidence or special-cased by text/URL. The original
// caller-owned artifact bytes and their provenance hash are never modified.
func annotateRawAttributeErrors(raw []byte) ([]byte, error) {
	tokenizer := html.NewTokenizer(bytes.NewReader(raw))
	var out bytes.Buffer
	out.Grow(len(raw))
	marker := " " + rawAttributeErrorMarker + `="1"`
	for {
		kind := tokenizer.Next()
		// Token() may normalize its buffer, so snapshot Raw before inspecting
		// parsed attributes. Quoted attribute-like text is not an attribute.
		rawToken := string(tokenizer.Raw())
		annotate := false
		switch kind {
		case html.ErrorToken:
			if !errors.Is(tokenizer.Err(), io.EOF) {
				return nil, fmt.Errorf("cdum HTML: tokenize raw attributes: %w", tokenizer.Err())
			}
		case html.StartTagToken, html.SelfClosingTagToken:
			for _, attr := range tokenizer.Token().Attr {
				if attr.Namespace == "" && attr.Key == rawAttributeErrorMarker {
					return nil, errors.New("cdum HTML: reserved raw-attribute marker is not accepted from source")
				}
			}
			annotate = stricthtml.ValidateStartTagAttributes(rawToken) != nil
		}
		growth := len(rawToken)
		if annotate {
			growth += len(marker)
		}
		// The original 4-MiB artifact limit remains unchanged. This additional
		// cap bounds only marker growth before the existing DOM node/depth
		// budgets are enforced; there is no unbounded reparsing or recovery.
		if out.Len()+growth > 2*maxArtifactBytes {
			return nil, errors.New("cdum HTML: internal parse copy exceeds limit")
		}
		if annotate {
			position := len(rawToken) - 1
			if kind == html.SelfClosingTagToken {
				position--
			}
			if position < 1 || rawToken[len(rawToken)-1] != '>' {
				return nil, errors.New("cdum HTML: cannot mark an incomplete source tag")
			}
			out.WriteString(rawToken[:position])
			out.WriteString(marker)
			out.WriteString(rawToken[position:])
		} else {
			out.WriteString(rawToken)
		}
		if kind == html.ErrorToken {
			return out.Bytes(), nil
		}
	}
}

func parseCoverage(coverage domain.DateRange) (time.Time, time.Time, error) {
	from, fromErr := time.Parse("2006-01-02", coverage.From)
	to, toErr := time.Parse("2006-01-02", coverage.To)
	if fromErr != nil || toErr != nil || from.Format("2006-01-02") != coverage.From || to.Format("2006-01-02") != coverage.To ||
		from.Year() < 2000 || from.Year() > 2100 || from.Year() != to.Year() || to.Before(from) ||
		from.Day() != 1 || to.AddDate(0, 0, 1).Day() != 1 {
		return time.Time{}, time.Time{}, errors.New("cdum HTML: coverage must explicitly name complete months within one supported year")
	}
	return from, to, nil
}

// A tokenizer handles quoted attributes and raw-text elements correctly. Reject
// unterminated/ambiguous comments before DOM repair; their contents are never
// evidence. Normal, properly delimited comments remain ignored.
func validateComments(raw []byte) error {
	tokenizer := html.NewTokenizer(bytes.NewReader(raw))
	for {
		switch tokenizer.Next() {
		case html.ErrorToken:
			if errors.Is(tokenizer.Err(), io.EOF) {
				return nil
			}
			return fmt.Errorf("cdum HTML: tokenize document: %w", tokenizer.Err())
		case html.CommentToken:
			comment := string(tokenizer.Raw())
			if len(comment) < 7 || !strings.HasPrefix(comment, "<!--") || !strings.HasSuffix(comment, "-->") || strings.Contains(comment[4:len(comment)-3], "<!--") {
				return errors.New("cdum HTML: unsupported comment syntax")
			}
		}
	}
}

func findCalendar(doc *html.Node, path, locality string, year int) (*html.Node, error) {
	var blocks []*html.Node
	links, nodes := 0, 0
	var walk func(*html.Node, int) error
	walk = func(node *html.Node, depth int) error {
		nodes++
		if depth > maxDocumentDepth || nodes > maxDocumentNodes {
			return errors.New("cdum HTML: document structure exceeds limits")
		}
		if ignoredNode(node) {
			return nil
		}
		if node.Type == html.ElementNode && attribute(node, "id") == "text_block" {
			if node.Data != "div" {
				return errors.New("cdum HTML: unexpected calendar container")
			}
			blocks = append(blocks, node)
		}
		if node.Type == html.ElementNode && node.Data == "a" && attribute(node, "href") == path {
			for ancestor := node; ancestor != nil; ancestor = ancestor.Parent {
				if ancestor.Type == html.ElementNode {
					if err := staticAttributes(ancestor); err != nil {
						return errors.New("cdum HTML: city-year navigation is not visible static evidence")
					}
				}
			}
			label, err := inlineText(node)
			if err != nil || label != fmt.Sprintf("%s %d", locality, year) {
				return errors.New("cdum HTML: explicit city-year navigation mismatch")
			}
			links++
		}
		for child := node.FirstChild; child != nil; child = child.NextSibling {
			if err := walk(child, depth+1); err != nil {
				return err
			}
		}
		return nil
	}
	if err := walk(doc, 0); err != nil {
		return nil, err
	}
	if links != 1 || len(blocks) != 1 {
		return nil, errors.New("cdum HTML: requires unique visible calendar and explicit city-year link")
	}
	return blocks[0], nil
}

func parseMonth(table *html.Node, year int, month time.Month, from, to time.Time) ([]domain.CandidatePrayerDay, error) {
	body, err := elementChildren(table)
	if err != nil || len(body) != 1 || body[0].Data != "tbody" {
		return nil, errors.New("cdum HTML: unsupported monthly table body")
	}
	rows, err := elementChildren(body[0])
	lastDay := time.Date(year, month+1, 0, 0, 0, 0, 0, time.UTC).Day()
	if err != nil || len(rows) != lastDay+1 {
		return nil, fmt.Errorf("cdum HTML: %04d-%02d missing/duplicate day or header", year, month)
	}
	var days []domain.CandidatePrayerDay
	for rowIndex, row := range rows {
		if row.Data != "tr" {
			return nil, errors.New("cdum HTML: unexpected table row element")
		}
		cells, err := elementChildren(row)
		if err != nil || len(cells) != len(columns) {
			return nil, errors.New("cdum HTML: expected exactly seven cells per row")
		}
		values := make([]string, len(cells))
		for i, cell := range cells {
			if cell.Data != "td" || (attribute(cell, "colspan") != "" && attribute(cell, "colspan") != "1") || (attribute(cell, "rowspan") != "" && attribute(cell, "rowspan") != "1") {
				return nil, errors.New("cdum HTML: unknown or spanning cell")
			}
			paragraphs, err := elementChildren(cell)
			if err != nil || len(paragraphs) != 1 || paragraphs[0].Data != "p" {
				return nil, errors.New("cdum HTML: cell must contain exactly one source paragraph")
			}
			values[i], err = inlineText(paragraphs[0])
			if err != nil {
				return nil, err
			}
		}
		if rowIndex == 0 {
			for i, column := range columns {
				if values[i] != column {
					return nil, fmt.Errorf("cdum HTML: column%d header mismatch", i+1)
				}
			}
			continue
		}
		date := time.Date(year, month, rowIndex, 0, 0, 0, 0, time.UTC)
		if !dayPattern.MatchString(values[0]) || values[0] != fmt.Sprintf("%d %s", rowIndex, weekdays[date.Weekday()]) {
			return nil, fmt.Errorf("cdum HTML: %s invalid, missing, duplicate or out-of-order date/weekday", date.Format("2006-01-02"))
		}
		for i := 1; i < len(values); i++ {
			values[i], err = normalizeClock(values[i])
			if err != nil {
				return nil, fmt.Errorf("cdum HTML: %s column%d invalid clock or unknown marker", date.Format("2006-01-02"), i+1)
			}
		}
		if date.Before(from) || date.After(to) {
			continue
		}
		for i := 2; i < len(values); i++ {
			if values[i] <= values[i-1] {
				return nil, fmt.Errorf("cdum HTML: %s invalid time order or unsupported next-day time", date.Format("2006-01-02"))
			}
		}
		days = append(days, domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{
			Date: date.Format("2006-01-02"), Fajr: values[1], Sunrise: values[2], Dhuhr: values[3], Asr: values[4], Maghrib: values[5], Isha: values[6],
		}})
	}
	return days, nil
}

func normalizeClock(value string) (string, error) {
	if len(value) != 4 && len(value) != 5 {
		return "", errors.New("unexpected clock length")
	}
	for i, char := range value {
		if i == len(value)-3 {
			if char != ':' {
				return "", errors.New("unexpected clock separator")
			}
		} else if char < '0' || char > '9' {
			return "", errors.New("unexpected clock character")
		}
	}
	hour, _ := strconv.Atoi(value[:len(value)-3])
	minute, _ := strconv.Atoi(value[len(value)-2:])
	if hour > 23 || minute > 59 {
		return "", errors.New("clock outside civil day")
	}
	return fmt.Sprintf("%02d:%02d", hour, minute), nil
}

func ignoredNode(node *html.Node) bool {
	return node.Type == html.CommentNode || (node.Type == html.ElementNode && (node.Data == "noscript" || node.Data == "template" || node.Data == "script" || node.Data == "style"))
}

func attribute(node *html.Node, name string) string {
	for _, attr := range node.Attr {
		if attr.Key == name && attr.Namespace == "" {
			return attr.Val
		}
	}
	return ""
}

func staticAttributes(node *html.Node) error {
	seen := make(map[string]bool, len(node.Attr))
	for _, attr := range node.Attr {
		if attr.Namespace == "" && attr.Key == rawAttributeErrorMarker {
			return errors.New("ambiguous raw attributes in relevant source evidence")
		}
		key := attr.Namespace + ":" + attr.Key
		if seen[key] || strings.HasPrefix(attr.Key, "on") || attr.Key == "hidden" || (attr.Key == "aria-hidden" && attr.Val == "true") {
			return errors.New("duplicate, hidden or executable attribute")
		}
		seen[key] = true
		if attr.Key == "style" {
			if err := observedStyle(attr.Val); err != nil {
				return err
			}
		}
	}
	return nil
}

// This is deliberately not a CSS interpreter. Only declarations present in the
// researched static export are accepted; escapes, functions and new properties
// require a source-schema review instead of a visibility guess.
func observedStyle(value string) error {
	style := strings.ToLower(strings.Join(strings.Fields(value), ""))
	seen := make(map[string]bool)
	for _, declaration := range strings.Split(style, ";") {
		if declaration == "" {
			continue
		}
		property, value, found := strings.Cut(declaration, ":")
		if !found || seen[property] {
			return errors.New("unsupported or duplicate source style")
		}
		seen[property] = true
		allowed := false
		switch property {
		case "font-size":
			allowed = value == "12pt" || value == "14pt"
		case "font-family":
			allowed = value == `"timesnewroman",serif`
		case "text-align":
			allowed = value == "center" || value == "justify"
		case "margin":
			allowed = value == "0cm"
		case "margin-left":
			allowed = value == "20.8pt"
		case "width":
			width, err := strconv.ParseFloat(strings.TrimSuffix(value, "px"), 64)
			allowed = strings.HasSuffix(value, "px") && err == nil && width > 0 && width <= 1000
		case "background":
			allowed = value == "white" || value == "transparent" || value == "rgb(242,242,242)" || value == "rgb(234,241,221)"
		case "padding":
			allowed = value == "0cm" || value == "1.65pt16.35pt" || value == "10.75pt10.75pt9.65pt"
		case "border", "border-top", "border-bottom", "border-left", "border-right":
			allowed = value == "none" || value == "1ptsolidrgb(205,205,205)" || value == "1ptsolidrgb(221,221,221)"
		case "border-image":
			allowed = value == "initial"
		}
		if !allowed {
			return errors.New("unresearched or visibility-altering source style")
		}
	}
	return nil
}

func elementChildren(node *html.Node) ([]*html.Node, error) {
	if err := controlledAttributes(node); err != nil {
		return nil, fmt.Errorf("cdum HTML: nonstatic table markup: %w", err)
	}
	var children []*html.Node
	for child := node.FirstChild; child != nil; child = child.NextSibling {
		if child.Type == html.CommentNode || (child.Type == html.ElementNode && (child.Data == "noscript" || child.Data == "template")) {
			continue
		}
		if child.Type == html.TextNode && strings.TrimSpace(child.Data) == "" {
			continue
		}
		// Both researched Office exports have one standalone trailing BOM.
		// It is formatting only here; a BOM inside any field remains invalid.
		if node.Data == "div" && attribute(node, "id") == "text_block" && child.Type == html.TextNode && strings.TrimSpace(child.Data) == "\ufeff" && child.NextSibling == nil {
			continue
		}
		if child.Type != html.ElementNode {
			text := []rune(strings.Join(strings.Fields(child.Data), " "))
			if len(text) > 64 {
				text = text[:64]
			}
			return nil, fmt.Errorf("cdum HTML: unexpected text outside timetable fields in %s: %q", node.Data, string(text))
		}
		children = append(children, child)
	}
	return children, nil
}

func inlineText(node *html.Node) (string, error) {
	var out strings.Builder
	var walk func(*html.Node) error
	walk = func(current *html.Node) error {
		if current.Type == html.CommentNode || (current.Type == html.ElementNode && (current.Data == "noscript" || current.Data == "template")) {
			return nil
		}
		if current.Type == html.TextNode {
			out.WriteString(current.Data)
			if utf8.RuneCountInString(out.String()) > maxFieldRunes {
				return errors.New("cdum HTML: field exceeds codepoint limit")
			}
			return nil
		}
		if current.Type != html.ElementNode {
			return errors.New("cdum HTML: unexpected field node")
		}
		if current != node && current.Data != "p" && current.Data != "b" && current.Data != "span" && current.Data != "o:p" {
			return fmt.Errorf("cdum HTML: unknown inline field element %s", current.Data)
		}
		if err := controlledAttributes(current); err != nil {
			return fmt.Errorf("cdum HTML: nonstatic field: %w", err)
		}
		for child := current.FirstChild; child != nil; child = child.NextSibling {
			if err := walk(child); err != nil {
				return err
			}
		}
		return nil
	}
	if err := walk(node); err != nil {
		return "", err
	}
	return strings.Join(strings.Fields(out.String()), " "), nil
}

func controlledAttributes(node *html.Node) error {
	if err := staticAttributes(node); err != nil {
		return err
	}
	for _, class := range strings.Fields(attribute(node, "class")) {
		if class != "MsoNormal" && class != "MsoNormalTable" {
			return errors.New("unknown controlled-subtree class")
		}
	}
	return nil
}
