package sochi

import (
	"bytes"
	"encoding/xml"
	"errors"
	"io"
	"strings"
	"unicode/utf8"
)

const (
	spreadsheetNS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
	officeRelNS   = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
	packageRelNS  = "http://schemas.openxmlformats.org/package/2006/relationships"
	contentTypeNS = "http://schemas.openxmlformats.org/package/2006/content-types"
)

type xmlNode struct {
	name     xml.Name
	attrs    map[xml.Name]string
	children []*xmlNode
	text     string
}

// A small bounded OOXML tree keeps duplicate attributes, directives, extra
// roots and namespace changes visible. encoding/xml never resolves external
// entities; directives and non-XML processing instructions are also rejected.
func parseXML(raw []byte) (*xmlNode, error) {
	if len(raw) == 0 || len(raw) > maxPartBytes || !utf8.Valid(raw) {
		return nil, errors.New("invalid XML encoding or size")
	}
	decoder := xml.NewDecoder(bytes.NewReader(raw))
	var root *xmlNode
	var stack []*xmlNode
	nodes, declaration := 0, false
	for {
		token, err := decoder.Token()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return nil, errors.New("invalid XML syntax")
		}
		switch item := token.(type) {
		case xml.StartElement:
			nodes++
			if len(stack) >= 32 || nodes > 20000 || len(item.Attr) > 64 {
				return nil, errors.New("XML complexity limit exceeded")
			}
			node := &xmlNode{name: item.Name, attrs: make(map[xml.Name]string, len(item.Attr))}
			for _, attr := range item.Attr {
				if _, exists := node.attrs[attr.Name]; exists {
					return nil, errors.New("duplicate XML attribute")
				}
				if len(attr.Value) > 4096 {
					return nil, errors.New("oversized XML attribute")
				}
				node.attrs[attr.Name] = attr.Value
			}
			if len(stack) == 0 {
				if root != nil {
					return nil, errors.New("multiple XML roots")
				}
				root = node
			} else {
				parent := stack[len(stack)-1]
				parent.children = append(parent.children, node)
			}
			stack = append(stack, node)
		case xml.EndElement:
			if len(stack) == 0 {
				return nil, errors.New("unexpected XML end element")
			}
			stack = stack[:len(stack)-1]
		case xml.CharData:
			if len(stack) == 0 {
				if strings.TrimSpace(string(item)) != "" {
					return nil, errors.New("text outside XML root")
				}
			} else {
				node := stack[len(stack)-1]
				if len(node.text)+len(item) > 4096 {
					return nil, errors.New("oversized XML text node")
				}
				node.text += string(item)
			}
		case xml.Directive:
			return nil, errors.New("XML directives are not supported")
		case xml.ProcInst:
			if item.Target != "xml" || declaration || root != nil {
				return nil, errors.New("unsupported XML processing instruction")
			}
			declaration = true
		}
	}
	if root == nil || len(stack) != 0 {
		return nil, errors.New("incomplete XML document")
	}
	return root, nil
}

func (node *xmlNode) is(namespace, local string) bool {
	return node != nil && node.name.Space == namespace && node.name.Local == local
}

func (node *xmlNode) attr(local string) string {
	return node.attrs[xml.Name{Local: local}]
}

func (node *xmlNode) onlyAttrs(names ...string) bool {
	for name := range node.attrs {
		if name.Space == "xmlns" || (name.Space == "" && name.Local == "xmlns") {
			continue
		}
		found := false
		for _, allowed := range names {
			if name.Space == "" && name.Local == allowed {
				found = true
			}
		}
		if !found {
			return false
		}
	}
	return true
}

func (node *xmlNode) container() bool { return strings.TrimSpace(node.text) == "" }

func (node *xmlNode) scalar() bool { return len(node.children) == 0 && node.onlyAttrs() }

func singleChild(node *xmlNode, namespace, local string) (*xmlNode, error) {
	var result *xmlNode
	for _, child := range node.children {
		if child.is(namespace, local) {
			if result != nil {
				return nil, errors.New("duplicate XML element")
			}
			result = child
		}
	}
	if result == nil {
		return nil, errors.New("required XML element missing")
	}
	return result, nil
}
