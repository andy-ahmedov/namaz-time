package registry

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

var (
	ErrRevisionUnavailable       = errors.New("registry revision unavailable")
	ErrRevisionConflict          = errors.New("registry revision conflict")
	ErrRevisionInvalid           = errors.New("invalid registry revision")
	ErrRevisionNotExecutable     = errors.New("registry revision is not executable")
	ErrVerifiedReferenceMissing  = errors.New("verified registry reference missing")
	ErrVerifiedReferenceMismatch = errors.New("verified registry reference mismatch")
)

const RegistrySchemaVersion = 1

type RevisionRecord struct {
	ID                string    `json:"id"`
	SchemaVersion     int       `json:"schema_version"`
	ParentRevisionID  string    `json:"parent_revision_id,omitempty"`
	CatalogRevisionID string    `json:"catalog_revision_id"`
	ContentSHA256     string    `json:"content_sha256,omitempty"`
	CreatedAt         time.Time `json:"created_at"`
	CreatedBy         string    `json:"created_by"`
	Reason            string    `json:"reason"`
}

type VerifiedApproval struct {
	ID             string
	MosqueID       string
	EvidenceSHA256 string
	VerifiedAt     time.Time
}

type VerifiedSnapshot struct {
	ID            string
	MosqueID      string
	Timezone      string
	Effective     domain.DateRange
	PayloadSHA256 string
	SigningKeyID  string
	VerifiedAt    time.Time
}

type ActivationRecord struct {
	RevisionID         string
	PreviousRevisionID string
	ActorID            string
	Reason             string
	ActivatedAt        time.Time
	Rollback           bool
	Approvals          []VerifiedApproval
	Snapshots          []VerifiedSnapshot
}

type RevisionStore interface {
	Stage(context.Context, RevisionRecord, Dataset) error
	Load(context.Context, string) (RevisionRecord, Dataset, error)
	Activate(context.Context, ActivationRecord) error
	Active(context.Context) (RevisionRecord, Dataset, error)
}

type ApprovalReferenceVerifier interface {
	VerifyApproval(context.Context, string) (VerifiedApproval, error)
}

type SnapshotReferenceVerifier interface {
	VerifySnapshot(context.Context, string) (VerifiedSnapshot, error)
}

type PersistentServiceConfig struct {
	Store            RevisionStore
	ApprovalVerifier ApprovalReferenceVerifier
	SnapshotVerifier SnapshotReferenceVerifier
	Now              func() time.Time
}

type PersistentService struct {
	store            RevisionStore
	approvalVerifier ApprovalReferenceVerifier
	snapshotVerifier SnapshotReferenceVerifier
	now              func() time.Time
}

func NewPersistentService(config PersistentServiceConfig) (*PersistentService, error) {
	if config.Store == nil || config.ApprovalVerifier == nil || config.SnapshotVerifier == nil || config.Now == nil {
		return nil, errors.New("create persistent registry service: store, verifiers and clock are required")
	}
	return &PersistentService{
		store: config.Store, approvalVerifier: config.ApprovalVerifier,
		snapshotVerifier: config.SnapshotVerifier, now: config.Now,
	}, nil
}

func (service *PersistentService) Stage(ctx context.Context, record RevisionRecord, dataset Dataset) error {
	if service == nil || service.store == nil {
		return ErrRevisionInvalid
	}
	now := service.now().UTC()
	if !validAuditText(record.ID, 160) || record.SchemaVersion != RegistrySchemaVersion || !validAuditText(record.CatalogRevisionID, 200) || !validAuditText(record.CreatedBy, 160) || !validAuditText(record.Reason, 1000) ||
		(record.ParentRevisionID != "" && !validAuditText(record.ParentRevisionID, 160)) || now.IsZero() {
		return ErrRevisionInvalid
	}
	if _, err := New(dataset); err != nil {
		return fmt.Errorf("%w: %v", ErrRevisionInvalid, err)
	}
	contentSHA256, err := DatasetSHA256(dataset)
	if err != nil {
		return fmt.Errorf("%w: %v", ErrRevisionInvalid, err)
	}
	if record.ContentSHA256 != "" && record.ContentSHA256 != contentSHA256 {
		return fmt.Errorf("%w: declared content SHA-256 differs", ErrRevisionInvalid)
	}
	record.ContentSHA256 = contentSHA256
	if record.CreatedAt.IsZero() {
		record.CreatedAt = now
	} else if !isCanonicalUTC(record.CreatedAt) || record.CreatedAt.After(now) {
		return fmt.Errorf("%w: created time is invalid", ErrRevisionInvalid)
	}
	if err := service.store.Stage(ctx, record, dataset); err != nil {
		return fmt.Errorf("stage registry revision %q: %w", record.ID, err)
	}
	return nil
}

func (service *PersistentService) Activate(ctx context.Context, revisionID, actorID, reason string) error {
	return service.activate(ctx, revisionID, actorID, reason, false)
}

func (service *PersistentService) Rollback(ctx context.Context, revisionID, actorID, reason string) error {
	return service.activate(ctx, revisionID, actorID, reason, true)
}

func (service *PersistentService) activate(ctx context.Context, revisionID, actorID, reason string, rollback bool) error {
	if service == nil || service.store == nil || !validAuditText(revisionID, 160) || !validAuditText(actorID, 160) || !validAuditText(reason, 1000) {
		return ErrRevisionInvalid
	}
	record, dataset, err := service.store.Load(ctx, revisionID)
	if err != nil {
		return fmt.Errorf("load registry revision %q: %w", revisionID, err)
	}
	if record.ID != revisionID {
		return ErrRevisionInvalid
	}
	approvals, snapshots, err := service.verifyExecutable(ctx, dataset)
	if err != nil {
		return err
	}
	previousID := ""
	if previous, _, activeErr := service.store.Active(ctx); activeErr == nil {
		previousID = previous.ID
	} else if !errors.Is(activeErr, ErrRevisionUnavailable) {
		return fmt.Errorf("read active registry revision: %w", activeErr)
	}
	activation := ActivationRecord{
		RevisionID: revisionID, PreviousRevisionID: previousID, ActorID: actorID,
		Reason: reason, ActivatedAt: service.now().UTC(), Rollback: rollback,
		Approvals: approvals, Snapshots: snapshots,
	}
	if err := service.store.Activate(ctx, activation); err != nil {
		return fmt.Errorf("activate registry revision %q: %w", revisionID, err)
	}
	return nil
}

func (service *PersistentService) verifyExecutable(ctx context.Context, dataset Dataset) ([]VerifiedApproval, []VerifiedSnapshot, error) {
	if _, err := New(dataset); err != nil {
		return nil, nil, fmt.Errorf("%w: %v", ErrRevisionNotExecutable, err)
	}
	if err := rejectSameTierAmbiguity(dataset); err != nil {
		return nil, nil, err
	}
	sources := make(map[string]domain.PrayerSource, len(dataset.Sources))
	for _, source := range dataset.Sources {
		sources[source.ID] = source
	}
	approvalByID := make(map[string]VerifiedApproval)
	verifyApproval := func(approvalID, mosqueID string) error {
		if existing, exists := approvalByID[approvalID]; exists {
			if existing.MosqueID != mosqueID {
				return fmt.Errorf("%w: approval %q mosque scope", ErrVerifiedReferenceMismatch, approvalID)
			}
			return nil
		}
		evidence, err := service.approvalVerifier.VerifyApproval(ctx, approvalID)
		if err != nil {
			return fmt.Errorf("verify approval %q: %w", approvalID, err)
		}
		if evidence.ID != approvalID || evidence.MosqueID != mosqueID || !validSHA256(evidence.EvidenceSHA256) ||
			evidence.VerifiedAt.IsZero() || !isCanonicalUTC(evidence.VerifiedAt) || evidence.VerifiedAt.After(service.now()) {
			return fmt.Errorf("%w: approval %q", ErrVerifiedReferenceMismatch, approvalID)
		}
		approvalByID[evidence.ID] = evidence
		return nil
	}
	for _, policy := range dataset.Policies {
		source, ok := sources[policy.SourceID]
		if !ok || source.Status != domain.PrayerSourceApproved || !validDate(source.FreshThrough) || source.FreshThrough < policy.Effective.To {
			return nil, nil, fmt.Errorf("%w: policy %q source is not approved/fresh for its range", ErrRevisionNotExecutable, policy.ID)
		}
		if len(policy.MosqueIDs) != 1 {
			return nil, nil, fmt.Errorf("%w: policy %q requires one explicit mosque binding", ErrRevisionNotExecutable, policy.ID)
		}
		if err := verifyApproval(policy.ApprovalID, policy.MosqueIDs[0]); err != nil {
			return nil, nil, err
		}
	}
	policyByTimetable := make(map[string]domain.PrayerPolicy)
	policyByProfile := make(map[string]domain.PrayerPolicy)
	for _, policy := range dataset.Policies {
		if policy.Kind == domain.PrayerPolicyTimeTable {
			policyByTimetable[policy.TimeTableID] = policy
		} else if policy.Kind == domain.PrayerPolicyCalculationProfile {
			policyByProfile[policy.CalculationProfileID] = policy
		}
	}
	for _, profile := range dataset.CalculationProfiles {
		policy, executable := policyByProfile[profile.ID]
		if !executable {
			continue
		}
		source := sources[profile.SourceID]
		if source.Status != domain.PrayerSourceApproved || !validDate(source.FreshThrough) || source.FreshThrough < profile.Effective.To {
			return nil, nil, fmt.Errorf("%w: calculation profile %q source is not approved/fresh", ErrRevisionNotExecutable, profile.ID)
		}
		if err := verifyApproval(profile.ApprovalID, policy.MosqueIDs[0]); err != nil {
			return nil, nil, err
		}
	}
	overrideByID := make(map[string]domain.SourceOverride, len(dataset.SourceOverrides))
	for _, override := range dataset.SourceOverrides {
		overrideByID[override.ID] = override
	}
	snapshotByID := make(map[string]VerifiedSnapshot)
	for _, timetable := range dataset.TimeTables {
		policy, executable := policyByTimetable[timetable.ID]
		if !executable {
			continue
		}
		for _, overrideID := range timetable.SourceOverrideIDs {
			override, exists := overrideByID[overrideID]
			baseSource, baseExists := sources[override.BaseSourceID]
			overrideSource, overrideExists := sources[override.OverrideSourceID]
			if !exists || !baseExists || !overrideExists ||
				baseSource.Status != domain.PrayerSourceApproved || overrideSource.Status != domain.PrayerSourceApproved ||
				!validDate(baseSource.FreshThrough) || !validDate(overrideSource.FreshThrough) ||
				baseSource.FreshThrough < override.Effective.To || overrideSource.FreshThrough < override.Effective.To {
				return nil, nil, fmt.Errorf("%w: timetable %q override %q is not approved/fresh", ErrRevisionNotExecutable, timetable.ID, overrideID)
			}
			if err := verifyApproval(override.ApprovalID, policy.MosqueIDs[0]); err != nil {
				return nil, nil, err
			}
		}
		if _, exists := snapshotByID[timetable.PublishedSnapshotID]; exists {
			continue
		}
		evidence, err := service.snapshotVerifier.VerifySnapshot(ctx, timetable.PublishedSnapshotID)
		if err != nil {
			return nil, nil, fmt.Errorf("verify snapshot %q: %w", timetable.PublishedSnapshotID, err)
		}
		if evidence.ID != timetable.PublishedSnapshotID || evidence.MosqueID != timetable.MosqueID || evidence.MosqueID != policy.MosqueIDs[0] ||
			evidence.Timezone != timetable.Timezone || evidence.Effective.From > timetable.Effective.From || evidence.Effective.To < timetable.Effective.To ||
			!validDateRange(evidence.Effective) || !validSHA256(evidence.PayloadSHA256) || evidence.SigningKeyID == "" || evidence.VerifiedAt.IsZero() ||
			!isCanonicalUTC(evidence.VerifiedAt) || evidence.VerifiedAt.After(service.now()) {
			return nil, nil, fmt.Errorf("%w: snapshot %q", ErrVerifiedReferenceMismatch, timetable.PublishedSnapshotID)
		}
		snapshotByID[evidence.ID] = evidence
	}
	approvals := make([]VerifiedApproval, 0, len(approvalByID))
	for _, item := range approvalByID {
		approvals = append(approvals, item)
	}
	sort.Slice(approvals, func(i, j int) bool { return approvals[i].ID < approvals[j].ID })
	snapshots := make([]VerifiedSnapshot, 0, len(snapshotByID))
	for _, item := range snapshotByID {
		snapshots = append(snapshots, item)
	}
	sort.Slice(snapshots, func(i, j int) bool { return snapshots[i].ID < snapshots[j].ID })
	return approvals, snapshots, nil
}

func isCanonicalUTC(value time.Time) bool {
	return value.Location() == time.UTC
}

func (service *PersistentService) ActiveRevision(ctx context.Context) (RevisionRecord, error) {
	if service == nil || service.store == nil {
		return RevisionRecord{}, ErrRevisionUnavailable
	}
	record, _, err := service.store.Active(ctx)
	return record, err
}

func (service *PersistentService) SearchCities(ctx context.Context, query string) ([]CitySearchResult, error) {
	if service == nil || service.store == nil {
		return nil, ErrRevisionUnavailable
	}
	searcher, ok := service.store.(interface {
		SearchActiveCities(context.Context, string) ([]CitySearchResult, error)
	})
	if !ok {
		return nil, ErrRevisionUnavailable
	}
	return searcher.SearchActiveCities(ctx, query)
}

func (service *PersistentService) Resolve(ctx context.Context, request ResolveRequest) (Resolution, error) {
	if service == nil || service.store == nil {
		return Resolution{}, ErrRevisionUnavailable
	}
	_, dataset, err := service.store.Active(ctx)
	if err != nil {
		return Resolution{}, err
	}
	active, err := New(dataset)
	if err != nil {
		return Resolution{}, fmt.Errorf("load executable registry: %w", err)
	}
	return active.Resolve(request)
}

func (service *PersistentService) AssessRevision(
	ctx context.Context,
	revisionID string,
	request ResolveRequest,
) (RevisionPolicyAssessment, error) {
	if service == nil || service.store == nil {
		return RevisionPolicyAssessment{}, ErrRevisionUnavailable
	}
	return assessRevision(ctx, service.store, revisionID, request)
}

func DatasetSHA256(dataset Dataset) (string, error) {
	canonical := cloneDataset(dataset)
	sortDataset(&canonical)
	encoded, err := json.Marshal(canonical)
	if err != nil {
		return "", err
	}
	digest := sha256.Sum256(encoded)
	return hex.EncodeToString(digest[:]), nil
}

func sortDataset(dataset *Dataset) {
	sort.Slice(dataset.Cities, func(i, j int) bool { return dataset.Cities[i].ID < dataset.Cities[j].ID })
	sort.Slice(dataset.Regions, func(i, j int) bool { return dataset.Regions[i].ID < dataset.Regions[j].ID })
	sort.Slice(dataset.Scopes, func(i, j int) bool { return dataset.Scopes[i].ID < dataset.Scopes[j].ID })
	sort.Slice(dataset.Authorities, func(i, j int) bool { return dataset.Authorities[i].ID < dataset.Authorities[j].ID })
	sort.Slice(dataset.Sources, func(i, j int) bool { return dataset.Sources[i].ID < dataset.Sources[j].ID })
	sort.Slice(dataset.Policies, func(i, j int) bool { return dataset.Policies[i].ID < dataset.Policies[j].ID })
	sort.Slice(dataset.CalculationProfiles, func(i, j int) bool { return dataset.CalculationProfiles[i].ID < dataset.CalculationProfiles[j].ID })
	sort.Slice(dataset.TimeTables, func(i, j int) bool { return dataset.TimeTables[i].ID < dataset.TimeTables[j].ID })
	sort.Slice(dataset.SourceOverrides, func(i, j int) bool { return dataset.SourceOverrides[i].ID < dataset.SourceOverrides[j].ID })
	for index := range dataset.Cities {
		sort.Strings(dataset.Cities[index].Aliases)
	}
	for index := range dataset.Sources {
		sort.Strings(dataset.Sources[index].AuthorityIDs)
	}
	for index := range dataset.Policies {
		sort.Strings(dataset.Policies[index].AuthorityIDs)
		sort.Strings(dataset.Policies[index].MosqueIDs)
	}
	for index := range dataset.TimeTables {
		sort.Strings(dataset.TimeTables[index].SourceOverrideIDs)
	}
	for index := range dataset.SourceOverrides {
		sort.Strings(dataset.SourceOverrides[index].AppliedFields)
	}
}

func rejectSameTierAmbiguity(dataset Dataset) error {
	scopeByID := make(map[string]domain.GeographicScope, len(dataset.Scopes))
	for _, scope := range dataset.Scopes {
		scopeByID[scope.ID] = scope
	}
	for leftIndex, left := range dataset.Policies {
		leftScope := scopeByID[left.GeographicScopeID]
		for rightIndex := leftIndex + 1; rightIndex < len(dataset.Policies); rightIndex++ {
			right := dataset.Policies[rightIndex]
			rightScope := scopeByID[right.GeographicScopeID]
			if policyTierKey(left, leftScope) != policyTierKey(right, rightScope) || !sameGeography(leftScope, rightScope) || !rangesOverlap(left.Effective, right.Effective) || !stringsOverlap(left.MosqueIDs, right.MosqueIDs) {
				continue
			}
			return fmt.Errorf("%w: policies %q and %q overlap at one tier", ErrPolicyAmbiguous, left.ID, right.ID)
		}
	}
	return nil
}

func policyTierKey(policy domain.PrayerPolicy, scope domain.GeographicScope) string {
	if scope.Kind == domain.GeographicScopeCity && policy.Kind == domain.PrayerPolicyTimeTable {
		return string(ResolutionExactCityTimetable)
	}
	if scope.Kind == domain.GeographicScopeRegion && policy.Kind == domain.PrayerPolicyTimeTable {
		return string(ResolutionRegionalTimetable)
	}
	if scope.Kind == domain.GeographicScopeRegion && policy.Kind == domain.PrayerPolicyCalculationProfile {
		return string(ResolutionRegionalCalculation)
	}
	return "unsupported"
}

func sameGeography(left, right domain.GeographicScope) bool {
	return left.Kind == right.Kind && left.CityID == right.CityID && left.RegionID == right.RegionID
}

func rangesOverlap(left, right domain.DateRange) bool {
	return left.From <= right.To && right.From <= left.To
}

func stringsOverlap(left, right []string) bool {
	for _, value := range left {
		for _, candidate := range right {
			if value == candidate {
				return true
			}
		}
	}
	return false
}

func validAuditText(value string, maximum int) bool {
	return value != "" && strings.TrimSpace(value) == value && len([]rune(value)) <= maximum && !strings.ContainsAny(value, "\x00\r\n")
}

func validSHA256(value string) bool {
	if len(value) != sha256.Size*2 || strings.ToLower(value) != value {
		return false
	}
	_, err := hex.DecodeString(value)
	return err == nil
}

func validDate(value string) bool {
	parsed, err := time.Parse("2006-01-02", value)
	return err == nil && parsed.Format("2006-01-02") == value
}
