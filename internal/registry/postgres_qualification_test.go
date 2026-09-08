package registry

import (
	"encoding/json"
	"errors"
	"reflect"
	"strings"
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

func TestStoredQualificationPreservesStrictWireAndNormalizedIdentity(t *testing.T) {
	q := qualifiedDataset(t).Qualifications[0]
	raw, err := json.Marshal(q)
	if err != nil {
		t.Fatal(err)
	}
	identity := storedQualificationIdentity{ID: q.ID, SHA256: q.SHA256, SourceID: q.SourceID, ScopeID: q.Scope.ID, AuthorityID: q.Authority.ID,
		CatalogRevision: q.CatalogRevision, Timezone: q.Timezone, PublicContextID: publicQualificationContextID(q.Scope.ID), Coverage: q.Coverage}
	decoded, err := decodeStoredQualification(raw, identity)
	if err != nil || decoded.ID != q.ID || decoded.SHA256 != q.SHA256 {
		t.Fatalf("strict stored proof: %v", err)
	}
	for name, mutate := range map[string]func([]byte, *storedQualificationIdentity) []byte{
		"unknown_field": func(raw []byte, _ *storedQualificationIdentity) []byte {
			return append([]byte(`{"unknown":true,`), raw[1:]...)
		},
		"present_empty_field": func(raw []byte, _ *storedQualificationIdentity) []byte {
			return append([]byte(`{"unknowns":[],`), raw[1:]...)
		},
		"duplicate_field": func(raw []byte, _ *storedQualificationIdentity) []byte {
			return append([]byte(`{"state":"qualified",`), raw[1:]...)
		},
		"trailing_value":  func(raw []byte, _ *storedQualificationIdentity) []byte { return append(raw, []byte(` {}`)...) },
		"invalid_utf8":    func(raw []byte, _ *storedQualificationIdentity) []byte { return append(raw, 0xff) },
		"oversize":        func(_ []byte, _ *storedQualificationIdentity) []byte { return []byte(strings.Repeat("x", 1024*1024+1)) },
		"wrong_id":        func(raw []byte, ref *storedQualificationIdentity) []byte { ref.ID += "other"; return raw },
		"wrong_hash":      func(raw []byte, ref *storedQualificationIdentity) []byte { ref.SHA256 = repeatHex("1"); return raw },
		"wrong_source":    func(raw []byte, ref *storedQualificationIdentity) []byte { ref.SourceID += "other"; return raw },
		"wrong_scope":     func(raw []byte, ref *storedQualificationIdentity) []byte { ref.ScopeID += "other"; return raw },
		"wrong_authority": func(raw []byte, ref *storedQualificationIdentity) []byte { ref.AuthorityID += "other"; return raw },
		"wrong_catalog":   func(raw []byte, ref *storedQualificationIdentity) []byte { ref.CatalogRevision += "other"; return raw },
		"wrong_timezone":  func(raw []byte, ref *storedQualificationIdentity) []byte { ref.Timezone = "Europe/Moscow"; return raw },
		"wrong_context":   func(raw []byte, ref *storedQualificationIdentity) []byte { ref.PublicContextID += "other"; return raw },
		"wrong_coverage":  func(raw []byte, ref *storedQualificationIdentity) []byte { ref.Coverage.To = "2026-08-31"; return raw },
	} {
		t.Run(name, func(t *testing.T) {
			reference := identity
			changed := mutate(append([]byte(nil), raw...), &reference)
			if value, err := decodeStoredQualification(changed, reference); !errors.Is(err, ErrRevisionInvalid) || !reflect.DeepEqual(value, domain.SourceQualification{}) {
				t.Fatalf("untrusted stored proof accepted: %v", err)
			}
		})
	}
}
