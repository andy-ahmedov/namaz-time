package sochi

import (
	"encoding/xml"
	"errors"
	"math"
	"strconv"
)

const compatibilityNS = "http://schemas.openxmlformats.org/markup-compatibility/2006"

// The workbook includes a bounded, known set of non-data Excel metadata.
// Values such as window position and print formatting are never interpreted as
// timetable policy. Unknown containers still fail closed, including alternate
// content: only the editor's inert absolute-path metadata is tolerated, never
// dereferenced or exposed by this parser.
func rootMetadataAttrs(node *xmlNode, ignorable string) bool {
	for name, value := range node.attrs {
		if name.Space == "xmlns" || (name.Space == "" && name.Local == "xmlns") {
			continue
		}
		if name != (xml.Name{Space: compatibilityNS, Local: "Ignorable"}) || value != ignorable {
			return false
		}
	}
	return true
}

func validateAlternateContent(node *xmlNode) error {
	if !node.is(compatibilityNS, "AlternateContent") || !node.onlyAttrs() || !node.container() || len(node.children) != 1 {
		return errors.New("unsupported workbook alternate content")
	}
	choice := node.children[0]
	if !choice.is(compatibilityNS, "Choice") || !choice.onlyAttrs("Requires") || choice.attr("Requires") != "x15" || !choice.container() || len(choice.children) != 1 {
		return errors.New("unsupported workbook compatibility choice")
	}
	absPath := choice.children[0]
	if !absPath.is("http://schemas.microsoft.com/office/spreadsheetml/2010/11/ac", "absPath") || !absPath.onlyAttrs("url") ||
		absPath.attr("url") == "" || !absPath.container() || len(absPath.children) != 0 {
		return errors.New("unsupported workbook editor metadata")
	}
	return nil
}

func validateFormatting(node *xmlNode) error {
	type rule struct {
		attributes []string
		child      string
		repeat     bool
	}
	rules := map[string]rule{
		"fileVersion":   {attributes: []string{"appName", "lastEdited", "lowestEdited", "rupBuild"}},
		"bookViews":     {child: "workbookView"},
		"workbookView":  {attributes: []string{"xWindow", "yWindow", "windowWidth", "windowHeight"}},
		"calcPr":        {attributes: []string{"calcId", "refMode"}},
		"sheetPr":       {child: "pageSetUpPr"},
		"pageSetUpPr":   {attributes: []string{"fitToPage"}},
		"sheetViews":    {child: "sheetView"},
		"sheetView":     {attributes: []string{"showGridLines", "tabSelected", "workbookViewId"}, child: "selection"},
		"selection":     {attributes: []string{"activeCell", "sqref"}},
		"sheetFormatPr": {attributes: []string{"defaultColWidth", "defaultRowHeight", "customHeight"}},
		"cols":          {child: "col", repeat: true},
		"col":           {attributes: []string{"min", "max", "width", "style", "customWidth", "hidden"}},
		"pageMargins":   {attributes: []string{"left", "right", "top", "bottom", "header", "footer"}},
		"pageSetup":     {attributes: []string{"orientation"}},
		"headerFooter":  {child: "oddFooter"},
		"oddFooter":     {},
	}
	schema, ok := rules[node.name.Local]
	if !ok || node.name.Space != spreadsheetNS || !node.onlyAttrs(schema.attributes...) || (node.name.Local != "oddFooter" && !node.container()) {
		return errors.New("unsupported Excel formatting schema")
	}
	if (schema.child == "" && len(node.children) != 0) || (!schema.repeat && len(node.children) > 1) || len(node.children) > 16 {
		return errors.New("unexpected Excel formatting child count")
	}
	previousColumn := 0
	for _, child := range node.children {
		if !child.is(spreadsheetNS, schema.child) {
			return errors.New("unknown Excel formatting child")
		}
		if err := validateFormatting(child); err != nil {
			return err
		}
		if schema.child == "col" {
			minimum, minErr := canonicalInteger(child.attr("min"), 16384)
			maximum, maxErr := canonicalInteger(child.attr("max"), 16384)
			if minErr != nil || maxErr != nil || minimum <= previousColumn || maximum < minimum {
				return errors.New("overlapping or invalid column definitions")
			}
			previousColumn = maximum
		}
	}
	if node.name.Local == "col" && node.attr("hidden") != "" && node.attr("hidden") != "0" && node.attr("hidden") != "false" {
		return errors.New("hidden worksheet column")
	}
	for _, key := range []string{"width", "defaultColWidth", "defaultRowHeight"} {
		if value := node.attr(key); value != "" {
			number, err := strconv.ParseFloat(value, 64)
			if err != nil || math.IsNaN(number) || math.IsInf(number, 0) || number <= 0 {
				return errors.New("invalid or hidden worksheet dimensions")
			}
		}
	}
	return nil
}
