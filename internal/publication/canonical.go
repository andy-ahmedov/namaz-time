package publication

import "github.com/andy-ahmedov/namaz-time/internal/canonicaljson"

// canonicalPayload preserves the v1 sorted-key/signature bytes. The shared
// encoder also fingerprints the new qualification and materialized onset data.
func canonicalPayload(data []byte) ([]byte, error) {
	return canonicaljson.WithoutRootMembers(data, "integrity")
}
