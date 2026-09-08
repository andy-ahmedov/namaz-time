// Package canonicaljson implements NamazTime's existing sorted-key JSON
// contract. Object keys are ASCII; number spellings are preserved, not coerced.
package canonicaljson

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"sort"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

func Encode(data []byte) ([]byte, error) { return encode(data, nil) }

// WithoutRootMembers requires an object and every named member to be present.
// It removes only those root members; nested names remain part of the payload.
func WithoutRootMembers(data []byte, names ...string) ([]byte, error) {
	return encode(data, names)
}

func encode(data []byte, omit []string) ([]byte, error) {
	if !utf8.Valid(data) {
		return nil, fmt.Errorf("snapshot JSON is not valid UTF-8")
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return nil, fmt.Errorf("ambiguous JSON: %w", err)
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.UseNumber()
	var value any
	if err := decoder.Decode(&value); err != nil {
		return nil, fmt.Errorf("decode JSON: %w", err)
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return nil, fmt.Errorf("multiple JSON values")
	}
	if len(omit) != 0 {
		root, ok := value.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("JSON root must be an object")
		}
		for _, name := range omit {
			if _, exists := root[name]; !exists {
				return nil, fmt.Errorf("root member %s is required", name)
			}
			delete(root, name)
		}
	}
	var output bytes.Buffer
	if err := writeCanonical(&output, value); err != nil {
		return nil, err
	}
	return output.Bytes(), nil
}

func writeCanonical(output *bytes.Buffer, value any) error {
	switch typed := value.(type) {
	case map[string]any:
		keys := make([]string, 0, len(typed))
		for key := range typed {
			keys = append(keys, key)
		}
		sort.Strings(keys)
		output.WriteByte('{')
		for index, key := range keys {
			if index > 0 {
				output.WriteByte(',')
			}
			writeCanonicalString(output, key)
			output.WriteByte(':')
			if err := writeCanonical(output, typed[key]); err != nil {
				return err
			}
		}
		output.WriteByte('}')
	case []any:
		output.WriteByte('[')
		for index, item := range typed {
			if index > 0 {
				output.WriteByte(',')
			}
			if err := writeCanonical(output, item); err != nil {
				return err
			}
		}
		output.WriteByte(']')
	case string:
		writeCanonicalString(output, typed)
	case json.Number:
		output.WriteString(typed.String())
	case bool:
		if typed {
			output.WriteString("true")
		} else {
			output.WriteString("false")
		}
	case nil:
		output.WriteString("null")
	default:
		return fmt.Errorf("unsupported JSON value %T", value)
	}
	return nil
}

func writeCanonicalString(output *bytes.Buffer, value string) {
	const hexadecimal = "0123456789abcdef"
	output.WriteByte('"')
	for _, character := range value {
		switch character {
		case '"', '\\':
			output.WriteByte('\\')
			output.WriteRune(character)
		case '\b':
			output.WriteString(`\b`)
		case '\f':
			output.WriteString(`\f`)
		case '\n':
			output.WriteString(`\n`)
		case '\r':
			output.WriteString(`\r`)
		case '\t':
			output.WriteString(`\t`)
		default:
			if character < 0x20 {
				output.WriteString(`\u00`)
				output.WriteByte(hexadecimal[byte(character)>>4])
				output.WriteByte(hexadecimal[byte(character)&0x0f])
			} else {
				output.WriteRune(character)
			}
		}
	}
	output.WriteByte('"')
}
