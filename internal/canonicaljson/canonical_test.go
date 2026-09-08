package canonicaljson

import "testing"

func TestCanonicalEncodingPreservesLegacyContract(t *testing.T) {
	got, err := WithoutRootMembers([]byte(`{"integrity":{},"z":{"integrity":7,"a":"<Уфа>&\u2028\n"},"a":[true,null,1.0,1e2,"\u0001"]}`), "integrity")
	want := "{\"a\":[true,null,1.0,1e2,\"\\u0001\"],\"z\":{\"a\":\"<Уфа>&\u2028\\n\",\"integrity\":7}}"
	if err != nil || string(got) != want {
		t.Fatalf("canonical bytes = %s, %v; want %s", got, err, want)
	}
	array, err := Encode([]byte(`[{"b":2,"a":1}]`))
	if err != nil || string(array) != `[{"a":1,"b":2}]` {
		t.Fatalf("canonical array = %s, %v", array, err)
	}
}

func TestCanonicalEncodingRejectsAmbiguousInput(t *testing.T) {
	for _, raw := range [][]byte{[]byte(`{"a":1,"a":2}`), []byte(`{"a":1} {}`), []byte(`{"a":`), {'"', 0xff, '"'}} {
		if _, err := Encode(raw); err == nil {
			t.Fatalf("accepted invalid JSON: %q", raw)
		}
	}
	for _, raw := range []string{`[]`, `{}`} {
		if _, err := WithoutRootMembers([]byte(raw), "integrity"); err == nil {
			t.Fatalf("accepted absent root envelope: %s", raw)
		}
	}
}
