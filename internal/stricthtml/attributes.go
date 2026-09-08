// Package stricthtml contains narrow raw-markup checks used before HTML repair.
package stricthtml

import (
	"errors"
	"strings"
	"unicode/utf8"
)

// ErrDuplicateAttribute identifies an ambiguity discarded by HTML tokenization.
var ErrDuplicateAttribute = errors.New("duplicate raw HTML attribute")

// ValidateStartTagAttributes examines one complete raw start tag, before an
// HTML tokenizer deduplicates its attributes. It supports quoted, unquoted and
// boolean attributes and ASCII-case-insensitive names. Quoted > signs and text
// resembling additional attributes remain data. It does not recover malformed
// syntax, modify input, render CSS or determine which subtree is evidence.
// The caller must delimit tokens with an HTML tokenizer and bound its artifact;
// this linear check also caps any single raw tag at 4 MiB.
func ValidateStartTagAttributes(tag string) error {
	if len(tag) < 3 || len(tag) > 4*1024*1024 || !utf8.ValidString(tag) || strings.IndexByte(tag, 0) >= 0 || tag[0] != '<' || tag[len(tag)-1] != '>' || !((tag[1] >= 'a' && tag[1] <= 'z') || (tag[1] >= 'A' && tag[1] <= 'Z')) {
		return errors.New("stricthtml: expected bounded complete raw start tag")
	}
	i := 1
	space := func(c byte) bool { return c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\f' }
	for i < len(tag) && !space(tag[i]) && tag[i] != '>' && tag[i] != '/' {
		i++
	}
	seen := map[string]bool{}
	for i < len(tag) {
		for i < len(tag) && space(tag[i]) {
			i++
		}
		if i == len(tag)-1 && tag[i] == '>' || i == len(tag)-2 && tag[i:] == "/>" {
			return nil
		}
		start := i
		for i < len(tag) && !space(tag[i]) && tag[i] != '=' && tag[i] != '>' && tag[i] != '/' {
			if strings.ContainsRune("\"'`<", rune(tag[i])) {
				return errors.New("stricthtml: malformed raw attribute name")
			}
			i++
		}
		if start == i {
			return errors.New("stricthtml: malformed raw start tag")
		}
		key := strings.ToLower(tag[start:i])
		if seen[key] {
			return ErrDuplicateAttribute
		}
		seen[key] = true
		for i < len(tag) && space(tag[i]) {
			i++
		}
		if i >= len(tag) || tag[i] != '=' {
			continue
		}
		i++
		for i < len(tag) && space(tag[i]) {
			i++
		}
		if i >= len(tag) {
			return errors.New("stricthtml: missing raw attribute value")
		}
		if tag[i] == '\'' || tag[i] == '"' {
			quote := tag[i]
			i++
			for i < len(tag) && tag[i] != quote {
				i++
			}
			if i >= len(tag) {
				return errors.New("stricthtml: unterminated raw attribute value")
			}
			i++
		} else {
			start = i
			for i < len(tag) && !space(tag[i]) && tag[i] != '>' {
				if strings.ContainsRune("\"'`=<", rune(tag[i])) {
					return errors.New("stricthtml: malformed unquoted attribute value")
				}
				i++
			}
			if start == i {
				return errors.New("stricthtml: empty unquoted attribute value")
			}
		}
	}
	return errors.New("stricthtml: incomplete raw start tag")
}
