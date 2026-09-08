// Package setupbundle exports already authenticated registry choices for
// offline local setup. It cannot qualify a source, sign data, or provision a
// device. PUBLIC_LOCAL_SETUP_BUNDLE.md is the versioned disk contract.
package setupbundle

import (
	"context"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
)

const (
	ManifestSchema       = "namaztime-local-setup-bundle/v1"
	ChoicesSchema        = "namaztime-local-setup-choices/v1"
	IndexSchema          = "namaztime-local-city-index/v1"
	NormalizerVersion    = "go-simple-lower-unicode-space/v1"
	MaximumManifestBytes = 512 * 1024
	MaximumDatabaseBytes = 128 * 1024 * 1024
	MaximumChoicesBytes  = 16 * 1024 * 1024
	MaximumTrustBytes    = 256 * 1024
	MaximumSnapshotBytes = 5 * 1024 * 1024
	MaximumBundleBytes   = 256 * 1024 * 1024
	MaximumSnapshots     = 1024
	maximumInputBytes    = 256 * 1024 * 1024
	maximumMetadataBytes = 8 * 1024 * 1024
)

type PinnedInput struct{ Path, SHA256 string }

type Config struct {
	Catalog, Bindings, Artifacts PinnedInput
	ArtifactRoot                 string
	OutputDirectory              string
	ActorID                      string
	Reason                       string
	SQLiteExecutable             string
}

type File struct {
	Path       string `json:"path"`
	ByteLength int64  `json:"byte_length"`
	SHA256     string `json:"sha256"`
}

type Admission struct {
	Kind       string `json:"kind"`
	VerifiedAt string `json:"verified_at"`
	ActorID    string `json:"actor_id"`
	Reason     string `json:"reason"`
}

type CatalogInfo struct {
	RevisionID      string `json:"revision_id"`
	ContentSHA256   string `json:"content_sha256"`
	RegionCount     int    `json:"region_count"`
	CityCount       int    `json:"city_count"`
	AliasCount      int    `json:"alias_count"`
	SearchNameCount int    `json:"search_name_count"`
	License         string `json:"license"`
	LicenseURL      string `json:"license_url"`
	Attribution     string `json:"attribution"`
}

type Manifest struct {
	SchemaVersion        string                  `json:"schema_version"`
	BundleID             string                  `json:"bundle_id"`
	ManifestSHA256       string                  `json:"manifest_sha256"`
	CreatedAt            string                  `json:"created_at"`
	RegistryRevision     registry.RevisionRecord `json:"registry_revision"`
	RegistryState        string                  `json:"registry_state"`
	Admission            Admission               `json:"admission"`
	Catalog              CatalogInfo             `json:"catalog"`
	MinimumTrustRevision uint64                  `json:"minimum_trust_revision"`
	Files                []File                  `json:"files"`
}

type SnapshotReference struct {
	SnapshotID     string        `json:"snapshot_id"`
	Path           string        `json:"path"`
	SHA256         string        `json:"sha256"`
	ByteLength     int64         `json:"byte_length"`
	DisplayContext domain.Mosque `json:"display_context"`
}

type Policy struct {
	PolicyID        string                      `json:"policy_id"`
	AuthorityLabel  string                      `json:"authority_label"`
	Policy          domain.PrayerPolicy         `json:"policy"`
	Scope           domain.GeographicScope      `json:"scope"`
	Authorities     []domain.PrayerAuthority    `json:"authorities"`
	Source          domain.PrayerSource         `json:"source"`
	Qualification   *domain.SourceQualification `json:"qualification,omitempty"`
	TimeTable       domain.TimeTable            `json:"timetable"`
	SourceOverrides []domain.SourceOverride     `json:"source_overrides"`
	Snapshot        SnapshotReference           `json:"snapshot"`
}

type Binding struct {
	CityID       string                  `json:"city_id"`
	ChoiceID     string                  `json:"choice_id"`
	PolicyID     string                  `json:"policy_id"`
	DisplayLabel string                  `json:"display_label"`
	Tier         registry.ResolutionTier `json:"tier"`
	Effective    domain.DateRange        `json:"effective"`
}

type Choices struct {
	SchemaVersion      string    `json:"schema_version"`
	RegistryRevisionID string    `json:"registry_revision_id"`
	Policies           []Policy  `json:"policies"`
	Bindings           []Binding `json:"bindings"`
}

// These are fixed existing Android/pilot anchors, not configurable trust roots.
var productionAnchors = trustAnchors{
	Revision:   3,
	Production: "2fc9b7a34cbba4bf57ba5242aec6b782d41877ff6863006e83a3d40bc34eb085",
	Previous:   "55d58bef5426876b8f47be721409d211644cf5bcbad24ddfdc7903ee5549c4a7",
	Test:       "82c7e7e943f5796ab689265a2d24862fc1f869f5ea574f7a940d2adaf74c1f77",
	Staging:    "db4d936d6894d6ff60bfa422c12a886bfc0f553b47a7e1201fa9e2560b25d0bd",
}

type trustAnchors struct {
	Revision                            uint64
	Production, Previous, Test, Staging string
}

// Export admits and exports at the actual current time. Neither an alternate
// trust root nor a historical clock is available through this production API.
func Export(ctx context.Context, config Config) (Manifest, error) {
	return exportAt(ctx, config, time.Now().UTC().Truncate(time.Second), productionAnchors)
}
