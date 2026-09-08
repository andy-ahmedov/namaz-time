// Package saratov normalizes the researched Saratov-city monthly HTML table.
// Retrieval, composite currentness evidence, qualification and publication are
// separate responsibilities; a successful parse never authorizes publication.
package saratov

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/stricthtml"
	"golang.org/x/net/html"
)

const ParserVersion = "saratov-city-html/v1"

const (
	maxArtifactBytes = 1024 * 1024
	maxNodes         = 20000
	maxDepth         = 64
	canonicalURL     = "https://dumso.ru/raspisanie"
	metadataURL      = "https://dumso.ru/wp-json/wp/v2/pages/2348"
	printURL         = "https://dumso.ru/print/namaz-time.html"
	publisher        = "«Духовное управление мусульман Саратовской области»"
	monthHeading     = "Расписание намазов на сентябрь"
	quotation        = "«Поистине молитва для верующих предписана в определенное время» (Коран: сура 4, аят 103)"
)

var columns = [...]string{
	"сентябрь", "день недели", "Раби аль-авваль/ Раби аль-ахир",
	"Фажр утрен. намаз", "Восход солнца", "Зухр уля намаз",
	"Аср икенде намаз", "Магриб ахшам", "Ийша ясых",
}

var weekdays = [...]string{"Воскрес", "Понедел", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"}

// ParseHTML validates the complete researched September 2026 source month,
// then selects the explicitly requested subrange. Other months/years require
// independently researched source metadata and a reviewed parser version.
//
// The visible WordPress calendar is context, NOT a printed timetable year.
// Qualification must additionally bind the separately captured page-2348 JSON
// modified date and identical rendered content, plus the printed city evidence.
// Callers must retain all raw hashes and establish exact catalog/timezone scope.
// Separate mosque-performance notices are validated, never turned into onsets,
// inferred iqamah or portable mosque policies.
func ParseHTML(raw []byte, coverage domain.DateRange) ([]domain.CandidatePrayerDay, error) {
	start, fromErr := time.Parse(time.DateOnly, coverage.From)
	end, toErr := time.Parse(time.DateOnly, coverage.To)
	if fromErr != nil || toErr != nil || start.Format(time.DateOnly) != coverage.From || end.Format(time.DateOnly) != coverage.To ||
		start.Year() != 2026 || end.Year() != 2026 || start.Month() != time.September || end.Month() != time.September || end.Before(start) {
		return nil, errors.New("saratov HTML: coverage must explicitly name a range within researched September 2026")
	}
	if len(raw) == 0 || len(raw) > maxArtifactBytes || !utf8.Valid(raw) || bytes.IndexByte(raw, 0) >= 0 {
		return nil, errors.New("saratov HTML: expected bounded nonempty UTF-8 artifact")
	}
	if err := validateTokenStructure(raw); err != nil {
		return nil, err
	}
	doc, err := html.Parse(bytes.NewReader(raw))
	if err != nil {
		return nil, fmt.Errorf("saratov HTML: decode document: %w", err)
	}
	table, err := findEvidence(doc)
	if err != nil {
		return nil, err
	}
	if err := validatePost(table); err != nil {
		return nil, err
	}
	month, err := parseMonth(table)
	if err != nil {
		return nil, err
	}
	days := make([]domain.CandidatePrayerDay, 0, end.Day()-start.Day()+1)
	for _, day := range month {
		if day.Date >= coverage.From && day.Date <= coverage.To {
			days = append(days, day)
		}
	}
	return days, nil
}

// The DOM parser repairs malformed HTML. Check the researched table's explicit
// token nesting first so missing closing cells/rows cannot be silently repaired.
func validateTokenStructure(raw []byte) error {
	tokens := html.NewTokenizer(bytes.NewReader(raw))
	var stack []string
	ignored := 0
	for {
		kind := tokens.Next()
		switch kind {
		case html.ErrorToken:
			if !errors.Is(tokens.Err(), io.EOF) {
				return fmt.Errorf("saratov HTML: tokenize artifact: %w", tokens.Err())
			}
			if len(stack) != 0 || ignored != 0 {
				return errors.New("saratov HTML: unclosed source markup")
			}
			return nil
		case html.CommentToken:
			comment := string(tokens.Raw())
			if len(comment) < 7 || !strings.HasPrefix(comment, "<!--") || !strings.HasSuffix(comment, "-->") || strings.Contains(comment[4:len(comment)-3], "<!--") {
				return errors.New("saratov HTML: malformed comment")
			}
		case html.DoctypeToken:
			if len(stack) > 0 {
				return errors.New("saratov HTML: unexpected declaration inside source table")
			}
		case html.StartTagToken, html.SelfClosingTagToken:
			rawTag := string(tokens.Raw())
			token := tokens.Token()
			if len(stack) == 0 && (token.Data == "template" || token.Data == "noscript") {
				ignored++
				continue
			}
			if ignored > 0 {
				continue
			}
			if err := stricthtml.ValidateStartTagAttributes(rawTag); err != nil {
				return fmt.Errorf("saratov HTML: raw start-tag attributes: %w", err)
			}
			if len(stack) == 0 {
				if token.Data == "table" && hasClass(token.Attr, "namaz_time") {
					if kind == html.SelfClosingTagToken {
						return errors.New("saratov HTML: self-closing source table")
					}
					stack = append(stack, "table")
				}
				continue
			}
			parent := stack[len(stack)-1]
			allowed := (token.Data == "tbody" && parent == "table") || (token.Data == "tr" && parent == "tbody") ||
				(token.Data == "td" && parent == "tr") || ((token.Data == "strong" || token.Data == "br") && (parent == "td" || parent == "strong"))
			if !allowed || (kind == html.SelfClosingTagToken && token.Data != "br") || len(stack) >= maxDepth {
				return errors.New("saratov HTML: unexpected or implicitly closed table markup")
			}
			if token.Data != "br" {
				stack = append(stack, token.Data)
			}
		case html.EndTagToken:
			token := tokens.Token()
			if ignored > 0 {
				if token.Data == "template" || token.Data == "noscript" {
					ignored--
				}
				continue
			}
			if len(stack) > 0 {
				if token.Data != stack[len(stack)-1] {
					return errors.New("saratov HTML: mismatched table closing element")
				}
				stack = stack[:len(stack)-1]
			}
		}
	}
}

func findEvidence(doc *html.Node) (*html.Node, error) {
	var tables []*html.Node
	var calendar, identity, scope, canonical, metadata int
	nodes := 0
	ids := map[string]bool{}
	var walk func(*html.Node, int) error
	walk = func(n *html.Node, depth int) error {
		nodes++
		if nodes > maxNodes || depth > maxDepth {
			return errors.New("saratov HTML: document structure exceeds limits")
		}
		if nonEvidence(n) {
			return nil
		}
		if n.Type == html.ElementNode {
			if id := attribute(n, "id"); evidenceID(id) {
				if ids[id] {
					return errors.New("saratov HTML: duplicate evidence-container ID")
				}
				ids[id] = true
			}
			if hasClass(n.Attr, "namaz_time") {
				if n.Data != "table" || attribute(n, "class") != "namaz_time" {
					return errors.New("saratov HTML: unexpected calendar class or element")
				}
				tables = append(tables, n)
			}
			if n.Data == "h1" {
				if err := visibleAncestors(n); err != nil {
					return err
				}
				if !textEquals(n, publisher) || attribute(n.Parent, "id") != "menu" {
					return errors.New("saratov HTML: publisher identity mismatch")
				}
				identity++
			}
			if n.Data == "a" && attribute(n, "href") == "/raspisanie" {
				if err := visibleAncestors(n); err != nil {
					return err
				}
				if attribute(n, "title") != "Расписание намазов в г. Саратове" || !textEquals(n, "на месяц") ||
					n.Parent.Data != "center" || attribute(n.Parent.Parent, "id") != "extras" {
					return errors.New("saratov HTML: city-specific monthly link mismatch")
				}
				scope++
			}
			if n.Data == "link" && attribute(n, "rel") == "canonical" {
				if err := safeAttributes(n); err != nil {
					return err
				}
				if attribute(n, "href") != canonicalURL || n.Parent.Data != "head" {
					return errors.New("saratov HTML: canonical URL mismatch")
				}
				canonical++
			}
			if n.Data == "link" && attribute(n, "rel") == "alternate" && attribute(n, "type") == "application/json" {
				if err := safeAttributes(n); err != nil {
					return err
				}
				if attribute(n, "href") != metadataURL || n.Parent.Data != "head" {
					return errors.New("saratov HTML: linked publication metadata mismatch")
				}
				metadata++
			}
			if attribute(n, "id") == "wp-calendar" {
				if err := visibleAncestors(n); err != nil {
					return err
				}
				if n.Data != "table" || attribute(n, "class") != "wp-calendar-table" || attribute(n.Parent, "id") != "calendar_wrap" || attribute(n.Parent.Parent, "id") != "extras" {
					return errors.New("saratov HTML: unexpected month/year context container")
				}
				captions := 0
				for c := n.FirstChild; c != nil; c = c.NextSibling {
					if c.Type == html.ElementNode && c.Data == "caption" {
						if !textEquals(c, "Сентябрь 2026") {
							return errors.New("saratov HTML: expected September 2026 context, not an inferred year")
						}
						captions++
					}
				}
				if captions != 1 {
					return errors.New("saratov HTML: missing or duplicate month/year caption")
				}
				calendar++
			}
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			if err := walk(c, depth+1); err != nil {
				return err
			}
		}
		return nil
	}
	if err := walk(doc, 0); err != nil {
		return nil, err
	}
	if len(tables) != 1 || calendar != 1 || identity != 1 || scope != 1 || canonical != 1 || metadata != 1 {
		return nil, errors.New("saratov HTML: requires unique source, city, context and timetable evidence")
	}
	return tables[0], nil
}

func validatePost(table *html.Node) error {
	if err := visibleAncestors(table); err != nil {
		return err
	}
	post := table.Parent
	if post == nil || post.Data != "div" || attribute(post, "class") != "post" || attribute(post.Parent, "id") != "contentwide" {
		return errors.New("saratov HTML: unexpected timetable container")
	}
	children, err := elementChildren(post)
	if err != nil || len(children) != 6 || children[0].Data != "h2" || !textEquals(children[0], monthHeading) ||
		children[1].Data != "p" || !textEquals(children[1], quotation) || children[2].Data != "div" ||
		attribute(children[2], "class") != "print_btn" || children[3] != table || children[4].Data != "p" || !textEquals(children[4], "") || children[5].Data != "ul" {
		return errors.New("saratov HTML: changed heading, source layout or unknown note")
	}
	printChildren, err := elementChildren(children[2])
	if err != nil || len(printChildren) != 1 || printChildren[0].Data != "a" || attribute(printChildren[0], "href") != printURL ||
		attribute(printChildren[0], "title") != "Версия для печати" || !textEquals(printChildren[0], "Версия для печати") {
		return errors.New("saratov HTML: print evidence link mismatch")
	}
	notes, err := elementChildren(children[5])
	if err != nil || len(notes) != 2 || notes[0].Data != "li" || notes[1].Data != "li" ||
		!textEquals(notes[0], "Азан на зухр намаз в соборной мечети 13:15") ||
		!textEquals(notes[1], "Фажр намаз в мечети через 45 минут после наступления времени.") {
		return errors.New("saratov HTML: changed mosque-performance notice or unknown footnote")
	}
	return nil
}

func parseMonth(table *html.Node) ([]domain.CandidatePrayerDay, error) {
	bodies, err := elementChildren(table)
	if err != nil || len(bodies) != 1 || bodies[0].Data != "tbody" {
		return nil, errors.New("saratov HTML: expected one explicit table body")
	}
	rows, err := elementChildren(bodies[0])
	if err != nil || len(rows) != 31 {
		return nil, errors.New("saratov HTML: incomplete or duplicate month rows")
	}
	month := make([]domain.CandidatePrayerDay, 0, 30)
	for i, row := range rows {
		if row.Data != "tr" {
			return nil, errors.New("saratov HTML: unexpected table row")
		}
		cells, err := elementChildren(row)
		if err != nil || len(cells) != len(columns) {
			return nil, errors.New("saratov HTML: expected exactly nine cells")
		}
		values := make([]string, len(cells))
		for j, cell := range cells {
			if cell.Data != "td" || len(cell.Attr) != 0 {
				return nil, errors.New("saratov HTML: unsupported or spanning cell")
			}
			values[j], err = inlineText(cell)
			if err != nil {
				return nil, err
			}
		}
		if i == 0 {
			for j, header := range columns {
				if values[j] != header {
					return nil, fmt.Errorf("saratov HTML: column %d header mismatch", j+1)
				}
			}
			continue
		}
		date := time.Date(2026, time.September, i, 0, 0, 0, 0, time.UTC)
		if values[0] != strconv.Itoa(i) || values[1] != weekdays[date.Weekday()] || values[2] != strconv.Itoa((i+18)%30+1) {
			return nil, fmt.Errorf("saratov HTML: %s missing, duplicate or incorrect date/weekday/Hijri sequence", date.Format(time.DateOnly))
		}
		for j := 3; j < len(values); j++ {
			values[j], err = normalizeClock(values[j])
			if err != nil {
				return nil, fmt.Errorf("saratov HTML: %s column %d invalid clock or unknown marker", date.Format(time.DateOnly), j+1)
			}
			if j > 3 && values[j] <= values[j-1] {
				return nil, fmt.Errorf("saratov HTML: %s invalid prayer order or unsupported midnight crossing", date.Format(time.DateOnly))
			}
		}
		month = append(month, domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{
			Date: date.Format(time.DateOnly), Fajr: values[3], Sunrise: values[4], Dhuhr: values[5], Asr: values[6], Maghrib: values[7], Isha: values[8],
		}})
	}
	return month, nil
}

func normalizeClock(value string) (string, error) {
	if len(value) < 4 || len(value) > 5 || (len(value) == 5 && value[0] == '0') {
		return "", errors.New("unsupported clock width")
	}
	separator := len(value) - 3
	for i, c := range value {
		if i == separator {
			if c != '.' && c != ':' {
				return "", errors.New("unsupported clock separator")
			}
		} else if c < '0' || c > '9' {
			return "", errors.New("unsupported clock character")
		}
	}
	hour, _ := strconv.Atoi(value[:separator])
	minute, _ := strconv.Atoi(value[separator+1:])
	if hour > 23 || minute > 59 {
		return "", errors.New("clock outside civil day")
	}
	return fmt.Sprintf("%02d:%02d", hour, minute), nil
}

func attribute(n *html.Node, key string) string {
	if n != nil {
		for _, a := range n.Attr {
			if a.Namespace == "" && a.Key == key {
				return a.Val
			}
		}
	}
	return ""
}

func hasClass(attrs []html.Attribute, name string) bool {
	for _, a := range attrs {
		if a.Namespace == "" && a.Key == "class" {
			for _, c := range strings.Fields(a.Val) {
				if c == name {
					return true
				}
			}
		}
	}
	return false
}

func nonEvidence(n *html.Node) bool {
	return n.Type == html.CommentNode || (n.Type == html.ElementNode && (n.Data == "script" || n.Data == "style" || n.Data == "template" || n.Data == "noscript"))
}

func visibleAncestors(n *html.Node) error {
	for c := n; c != nil; c = c.Parent {
		if c.Type == html.ElementNode {
			if err := safeAttributes(c); err != nil {
				return err
			}
		}
	}
	return nil
}

// A deliberately small observed attribute allowlist, not a CSS interpreter.
// Unknown visibility classes/styles and executable attributes require review.
func safeAttributes(n *html.Node) error {
	if n.Namespace != "" {
		return errors.New("saratov HTML: foreign namespace in source evidence")
	}
	seen := map[string]bool{}
	for _, a := range n.Attr {
		key := a.Namespace + ":" + a.Key
		if seen[key] {
			return errors.New("saratov HTML: duplicate source attribute")
		}
		seen[key] = true
		allowed := false
		if a.Namespace == "" {
			switch a.Key {
			case "id":
				allowed = evidenceID(a.Val) && ((n.Data == "table" && a.Val == "wp-calendar") || (n.Data == "div" && a.Val != "wp-calendar"))
			case "class":
				allowed = (n.Data == "div" && (a.Val == "post" || a.Val == "print_btn" || a.Val == "calendar_wrap")) ||
					(n.Data == "table" && (a.Val == "namaz_time" || a.Val == "wp-calendar-table")) || (n.Data == "tr" && a.Val == "green")
			case "href", "title", "rel", "type":
				allowed = n.Data == "a" || n.Data == "link"
			case "align":
				allowed = n.Data == "p" && a.Val == "center"
			case "lang", "xml:lang":
				allowed = n.Data == "html" && a.Val == "en"
			case "xmlns":
				allowed = n.Data == "html" && a.Val == "http://www.w3.org/1999/xhtml"
			}
		}
		if !allowed {
			return fmt.Errorf("saratov HTML: hidden, executable or unresearched attribute %s on %s", key, n.Data)
		}
	}
	return nil
}

func evidenceID(id string) bool {
	switch id {
	case "header", "wrap", "menu", "extras", "calendar_wrap", "wp-calendar", "contentwide":
		return true
	default:
		return false
	}
}

func elementChildren(n *html.Node) ([]*html.Node, error) {
	if err := safeAttributes(n); err != nil {
		return nil, err
	}
	var children []*html.Node
	for c := n.FirstChild; c != nil; c = c.NextSibling {
		if c.Type == html.CommentNode || (c.Type == html.TextNode && strings.TrimSpace(c.Data) == "") {
			continue
		}
		if c.Type != html.ElementNode {
			return nil, errors.New("saratov HTML: unexpected text outside source fields")
		}
		children = append(children, c)
	}
	return children, nil
}

func textEquals(n *html.Node, expected string) bool {
	value, err := inlineText(n)
	return err == nil && value == expected
}

func inlineText(n *html.Node) (string, error) {
	var b strings.Builder
	var walk func(*html.Node) error
	walk = func(c *html.Node) error {
		if c.Type == html.CommentNode {
			return nil
		}
		if c.Type == html.TextNode {
			b.WriteString(c.Data)
			if b.Len() > 1024 {
				return errors.New("saratov HTML: source field exceeds limit")
			}
			return nil
		}
		if c.Type != html.ElementNode || (c != n && c.Data != "strong" && c.Data != "br") {
			return errors.New("saratov HTML: unexpected inline source markup")
		}
		if err := safeAttributes(c); err != nil {
			return err
		}
		if c.Data == "br" {
			b.WriteByte(' ')
		}
		for child := c.FirstChild; child != nil; child = child.NextSibling {
			if err := walk(child); err != nil {
				return err
			}
		}
		return nil
	}
	if err := walk(n); err != nil {
		return "", err
	}
	return strings.Join(strings.Fields(b.String()), " "), nil
}
