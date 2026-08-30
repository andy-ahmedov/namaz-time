package registry

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/approval"
	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
	"github.com/andy-ahmedov/namaz-time/internal/trust"
)

type ApprovalArtifact struct {
	ApprovalID          string
	MosqueID            string
	PrayerPolicy        []byte
	Receipt             []byte
	TrustBundle         []byte
	PreviousTrustBundle []byte
}

type PublishedSnapshotArtifact struct {
	SnapshotID          string
	Snapshot            []byte
	Receipt             []byte
	TrustBundle         []byte
	PreviousTrustBundle []byte
	TestTrustBundle     []byte
	StagingTrustBundle  []byte
}

type ArtifactReferenceVerifierConfig struct {
	Approvals []ApprovalArtifact
	Snapshots []PublishedSnapshotArtifact
	Now       func() time.Time
}

type ArtifactReferenceVerifier struct {
	approvals map[string]ApprovalArtifact
	snapshots map[string]PublishedSnapshotArtifact
	now       func() time.Time
}

func NewArtifactReferenceVerifier(config ArtifactReferenceVerifierConfig) (*ArtifactReferenceVerifier, error) {
	if config.Now == nil {
		return nil, errors.New("create artifact verifier: clock is required")
	}
	verifier := &ArtifactReferenceVerifier{
		approvals: make(map[string]ApprovalArtifact, len(config.Approvals)),
		snapshots: make(map[string]PublishedSnapshotArtifact, len(config.Snapshots)), now: config.Now,
	}
	for _, artifact := range config.Approvals {
		if !validAuditText(artifact.ApprovalID, 200) || !validAuditText(artifact.MosqueID, 160) ||
			len(artifact.PrayerPolicy) == 0 || len(artifact.Receipt) == 0 || len(artifact.TrustBundle) == 0 {
			return nil, errors.New("create artifact verifier: approval artifact is incomplete")
		}
		if _, duplicate := verifier.approvals[artifact.ApprovalID]; duplicate {
			return nil, errors.New("create artifact verifier: duplicate approval ID")
		}
		artifact.PrayerPolicy = append([]byte(nil), artifact.PrayerPolicy...)
		artifact.Receipt = append([]byte(nil), artifact.Receipt...)
		artifact.TrustBundle = append([]byte(nil), artifact.TrustBundle...)
		artifact.PreviousTrustBundle = append([]byte(nil), artifact.PreviousTrustBundle...)
		verifier.approvals[artifact.ApprovalID] = artifact
	}
	for _, artifact := range config.Snapshots {
		if !validAuditText(artifact.SnapshotID, 200) || len(artifact.Snapshot) == 0 || len(artifact.Receipt) == 0 || len(artifact.TrustBundle) == 0 ||
			len(artifact.TestTrustBundle) == 0 || len(artifact.StagingTrustBundle) == 0 {
			return nil, errors.New("create artifact verifier: snapshot artifact is incomplete")
		}
		if _, duplicate := verifier.snapshots[artifact.SnapshotID]; duplicate {
			return nil, errors.New("create artifact verifier: duplicate snapshot ID")
		}
		artifact.Snapshot = append([]byte(nil), artifact.Snapshot...)
		artifact.Receipt = append([]byte(nil), artifact.Receipt...)
		artifact.TrustBundle = append([]byte(nil), artifact.TrustBundle...)
		artifact.PreviousTrustBundle = append([]byte(nil), artifact.PreviousTrustBundle...)
		artifact.TestTrustBundle = append([]byte(nil), artifact.TestTrustBundle...)
		artifact.StagingTrustBundle = append([]byte(nil), artifact.StagingTrustBundle...)
		verifier.snapshots[artifact.SnapshotID] = artifact
	}
	return verifier, nil
}

func (verifier *ArtifactReferenceVerifier) VerifyApproval(ctx context.Context, approvalID string) (VerifiedApproval, error) {
	if err := ctx.Err(); err != nil {
		return VerifiedApproval{}, err
	}
	artifact, exists := verifier.approvals[approvalID]
	if !exists {
		return VerifiedApproval{}, ErrVerifiedReferenceMissing
	}
	var prayerPolicy publication.MosquePrayerPolicy
	if err := decodeRegistryJSON(artifact.PrayerPolicy, &prayerPolicy); err != nil {
		return VerifiedApproval{}, fmt.Errorf("%w: decode mosque prayer policy: %v", ErrVerifiedReferenceMismatch, err)
	}
	if prayerPolicy.MosqueID != artifact.MosqueID {
		return VerifiedApproval{}, fmt.Errorf("%w: approval mosque policy", ErrVerifiedReferenceMismatch)
	}
	policySHA256, err := publication.MosquePrayerPolicySHA256(prayerPolicy)
	if err != nil {
		return VerifiedApproval{}, fmt.Errorf("%w: validate mosque prayer policy: %v", ErrVerifiedReferenceMismatch, err)
	}
	trustPolicy, err := approval.DecodeTrustBundleChain(artifact.TrustBundle, artifact.PreviousTrustBundle)
	if err != nil {
		return VerifiedApproval{}, fmt.Errorf("%w: validate approval trust: %v", ErrVerifiedReferenceMismatch, err)
	}
	decision, evidence, err := approval.Verify(artifact.Receipt, trustPolicy, policySHA256)
	if err != nil || decision.ID != approvalID {
		return VerifiedApproval{}, fmt.Errorf("%w: verify approval receipt: %v", ErrVerifiedReferenceMismatch, err)
	}
	return VerifiedApproval{
		ID: decision.ID, MosqueID: artifact.MosqueID, EvidenceSHA256: evidence.ReceiptSHA256,
		VerifiedAt: verifier.now().UTC(),
	}, nil
}

func (verifier *ArtifactReferenceVerifier) VerifySnapshot(ctx context.Context, snapshotID string) (VerifiedSnapshot, error) {
	if err := ctx.Err(); err != nil {
		return VerifiedSnapshot{}, err
	}
	artifact, exists := verifier.snapshots[snapshotID]
	if !exists {
		return VerifiedSnapshot{}, ErrVerifiedReferenceMissing
	}
	trustPolicy, err := decodePublicationTrustChain(artifact.TrustBundle, artifact.PreviousTrustBundle)
	if err != nil {
		return VerifiedSnapshot{}, fmt.Errorf("%w: validate publication trust: %v", ErrVerifiedReferenceMismatch, err)
	}
	testPolicy, err := trust.Decode(artifact.TestTrustBundle)
	if err != nil {
		return VerifiedSnapshot{}, fmt.Errorf("%w: validate test trust: %v", ErrVerifiedReferenceMismatch, err)
	}
	stagingPolicy, err := trust.Decode(artifact.StagingTrustBundle)
	if err != nil {
		return VerifiedSnapshot{}, fmt.Errorf("%w: validate staging trust: %v", ErrVerifiedReferenceMismatch, err)
	}
	if err := trust.ValidateEnvironmentSeparation(testPolicy, stagingPolicy, trustPolicy); err != nil {
		return VerifiedSnapshot{}, fmt.Errorf("%w: validate trust environment separation: %v", ErrVerifiedReferenceMismatch, err)
	}
	var receipt publication.AuditReceipt
	if err := decodeRegistryJSON(artifact.Receipt, &receipt); err != nil {
		return VerifiedSnapshot{}, fmt.Errorf("%w: decode publication receipt: %v", ErrVerifiedReferenceMismatch, err)
	}
	if err := publication.VerifyPublicationAdmission(artifact.Snapshot, receipt, trustPolicy); err != nil {
		return VerifiedSnapshot{}, fmt.Errorf("%w: verify publication admission: %v", ErrVerifiedReferenceMismatch, err)
	}
	snapshot, err := domain.DecodeSnapshot(artifact.Snapshot)
	if err != nil || snapshot.SnapshotID != snapshotID || receipt.SnapshotID != snapshotID {
		return VerifiedSnapshot{}, fmt.Errorf("%w: snapshot identity: %v", ErrVerifiedReferenceMismatch, err)
	}
	return VerifiedSnapshot{
		ID: snapshot.SnapshotID, MosqueID: snapshot.Mosque.ID, Timezone: snapshot.Mosque.Timezone,
		Effective: snapshot.Coverage, PayloadSHA256: sha256Hex(artifact.Snapshot),
		SigningKeyID: snapshot.Integrity.SigningKeyID, VerifiedAt: verifier.now().UTC(),
	}, nil
}

func decodePublicationTrustChain(currentBytes, previousBytes []byte) (*trust.Policy, error) {
	current, err := trust.Decode(currentBytes)
	if err != nil {
		return nil, err
	}
	if current.Revision() == 1 {
		if len(previousBytes) != 0 {
			return nil, errors.New("publication trust genesis cannot have a predecessor")
		}
		return current, nil
	}
	if len(previousBytes) == 0 {
		return nil, errors.New("publication trust predecessor is required")
	}
	previous, err := trust.Decode(previousBytes)
	if err != nil {
		return nil, err
	}
	if err := trust.ValidateTransition(previous, current); err != nil {
		return nil, err
	}
	return current, nil
}
