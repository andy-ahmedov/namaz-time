package strictjson_test

import (
	"testing"

	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

func TestRejectDuplicateObjectMembersAtAnyDepth(t *testing.T) {
	t.Parallel()

	for _, document := range []string{
		`{"key":1,"key":2}`,
		`{"outer":{"key":1,"key":2}}`,
		`[{"key":1},{"key":2,"key":3}]`,
	} {
		if err := strictjson.RejectDuplicateObjectMembers([]byte(document)); err == nil {
			t.Fatalf("duplicate JSON accepted: %s", document)
		}
	}
	if err := strictjson.RejectDuplicateObjectMembers([]byte(`{"key":1,"outer":{"key":2},"items":[true,null,"x"]}`)); err != nil {
		t.Fatalf("valid JSON rejected: %v", err)
	}
}
