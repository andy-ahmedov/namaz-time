package sochi

import (
	"archive/zip"
	"bytes"
	"errors"
	"fmt"
	"io"
	"path"
	"strings"
)

const (
	maxArtifactBytes = 2 * 1024 * 1024
	maxPartBytes     = 1024 * 1024
	maxInflatedBytes = 4 * 1024 * 1024
)

var partTypes = map[string]string{
	"/xl/workbook.xml":          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
	"/xl/worksheets/sheet1.xml": "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml",
	"/xl/sharedStrings.xml":     "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml",
	"/xl/styles.xml":            "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml",
	"/xl/theme/theme1.xml":      "application/vnd.openxmlformats-officedocument.theme+xml",
	"/docProps/core.xml":        "application/vnd.openxmlformats-package.core-properties+xml",
	"/docProps/app.xml":         "application/vnd.openxmlformats-officedocument.extended-properties+xml",
}

func readPackage(raw []byte) (map[string]*xmlNode, error) {
	if len(raw) == 0 || len(raw) > maxArtifactBytes {
		return nil, errors.New("expected bounded nonempty ZIP artifact")
	}
	archive, err := zip.NewReader(bytes.NewReader(raw), int64(len(raw)))
	if err != nil || len(archive.File) != 10 {
		return nil, errors.New("invalid ZIP or unknown package member set")
	}
	parts := make(map[string]*xmlNode, 10)
	var total uint64
	for _, file := range archive.File {
		_, standardPart := partTypes["/"+file.Name]
		if (!standardPart && file.Name != "[Content_Types].xml" && file.Name != "_rels/.rels" && file.Name != "xl/_rels/workbook.xml.rels") ||
			path.Clean(file.Name) != file.Name || strings.ContainsAny(file.Name, `\:`) || strings.HasPrefix(file.Name, "/") ||
			!file.Mode().IsRegular() || file.Flags&1 != 0 || (file.Method != zip.Store && file.Method != zip.Deflate) {
			return nil, errors.New("unsupported ZIP member, path or compression")
		}
		if _, exists := parts[file.Name]; exists {
			return nil, errors.New("duplicate ZIP member")
		}
		if file.UncompressedSize64 == 0 || file.UncompressedSize64 > maxPartBytes {
			return nil, errors.New("ZIP member exceeds inflated size limit")
		}
		total += file.UncompressedSize64
		if total > maxInflatedBytes {
			return nil, errors.New("ZIP exceeds total inflated size limit")
		}
		reader, err := file.Open()
		if err != nil {
			return nil, errors.New("cannot open ZIP member")
		}
		content, readErr := io.ReadAll(io.LimitReader(reader, maxPartBytes+1))
		closeErr := reader.Close()
		if readErr != nil || closeErr != nil || len(content) > maxPartBytes || uint64(len(content)) != file.UncompressedSize64 {
			return nil, errors.New("ZIP member integrity or size mismatch")
		}
		node, err := parseXML(content)
		if err != nil {
			return nil, fmt.Errorf("part %s: %w", file.Name, err)
		}
		parts[file.Name] = node
	}
	for name := range partTypes {
		if parts[strings.TrimPrefix(name, "/")] == nil {
			return nil, errors.New("required ZIP member missing")
		}
	}
	if parts["[Content_Types].xml"] == nil || parts["_rels/.rels"] == nil || parts["xl/_rels/workbook.xml.rels"] == nil {
		return nil, errors.New("required package metadata missing")
	}
	if err := validateContentTypes(parts["[Content_Types].xml"]); err != nil {
		return nil, err
	}
	rootRelations := map[string]string{
		officeRelNS + "/officeDocument":            "xl/workbook.xml",
		officeRelNS + "/extended-properties":       "docProps/app.xml",
		packageRelNS + "/metadata/core-properties": "docProps/core.xml",
	}
	if _, err := validateRelationships(parts["_rels/.rels"], rootRelations); err != nil {
		return nil, err
	}
	for name, identity := range map[string][2]string{
		"xl/styles.xml":       {spreadsheetNS, "styleSheet"},
		"xl/theme/theme1.xml": {"http://schemas.openxmlformats.org/drawingml/2006/main", "theme"},
		"docProps/core.xml":   {"http://schemas.openxmlformats.org/package/2006/metadata/core-properties", "coreProperties"},
		"docProps/app.xml":    {"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties", "Properties"},
	} {
		if !parts[name].is(identity[0], identity[1]) {
			return nil, errors.New("unsupported ancillary XML root")
		}
	}
	return parts, nil
}

func validateContentTypes(root *xmlNode) error {
	if !root.is(contentTypeNS, "Types") || !root.container() || !root.onlyAttrs() || len(root.children) != 9 {
		return errors.New("unknown package content-type schema")
	}
	seen := make(map[string]bool)
	for _, child := range root.children {
		if !child.container() || len(child.children) != 0 {
			return errors.New("invalid content-type entry")
		}
		var key string
		switch {
		case child.is(contentTypeNS, "Default") && child.onlyAttrs("Extension", "ContentType"):
			key = child.attr("Extension")
			want := map[string]string{"xml": "application/xml", "rels": "application/vnd.openxmlformats-package.relationships+xml"}[key]
			if want == "" || child.attr("ContentType") != want {
				return errors.New("unsupported default content type")
			}
		case child.is(contentTypeNS, "Override") && child.onlyAttrs("PartName", "ContentType"):
			key = child.attr("PartName")
			if partTypes[key] == "" || child.attr("ContentType") != partTypes[key] {
				return errors.New("unsupported package part content type")
			}
		default:
			return errors.New("unknown content-type element")
		}
		if seen[key] {
			return errors.New("duplicate package content type")
		}
		seen[key] = true
	}
	return nil
}

func validateRelationships(root *xmlNode, expected map[string]string) (map[string]string, error) {
	if !root.is(packageRelNS, "Relationships") || !root.onlyAttrs() || !root.container() || len(root.children) != len(expected) {
		return nil, errors.New("unknown relationship schema")
	}
	ids, types := make(map[string]string), make(map[string]bool)
	for _, child := range root.children {
		id, kind, target := child.attr("Id"), child.attr("Type"), child.attr("Target")
		if !child.is(packageRelNS, "Relationship") || !child.onlyAttrs("Id", "Type", "Target", "TargetMode") || !child.container() || len(child.children) != 0 ||
			id == "" || len(id) > 64 || ids[id] != "" || types[kind] || expected[kind] == "" || target != expected[kind] ||
			(child.attr("TargetMode") != "" && child.attr("TargetMode") != "Internal") {
			return nil, errors.New("duplicate, external or unsupported relationship")
		}
		ids[id], types[kind] = kind, true
	}
	return ids, nil
}
