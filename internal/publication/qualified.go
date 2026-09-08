package publication

import (
	"errors"
	"fmt"
	"reflect"
	"strings"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
)

// QualificationReference is signer-attested provenance, distinct from human
// approval. The full independently hash-checked proof is inside the snapshot.
type QualificationReference struct {
	ID             string `json:"qualification_id"`
	SHA256         string `json:"sha256"`
	DecisionSystem string `json:"decision_system"`
	QualifiedAt    string `json:"qualified_at"`
	ScopeID        string `json:"scope_id"`
	OnsetSHA256    string `json:"onset_sha256"`
}

func qualificationReference(q *domain.SourceQualification) *QualificationReference {
	if q == nil {
		return nil
	}
	return &QualificationReference{ID: q.ID, SHA256: q.SHA256, DecisionSystem: q.DecisionSystem, QualifiedAt: q.QualifiedAt, ScopeID: q.Scope.ID, OnsetSHA256: q.OnsetSHA256}
}

func admissionID(approvalID string, q *QualificationReference) string {
	if q != nil {
		return q.ID
	}
	return approvalID
}

func validateQualifiedPublishRequest(request PublishRequest) error {
	if !reflect.DeepEqual(request.Approval, domain.ApprovalDecision{}) || request.ApprovalEvidence != nil || request.ApprovalReceiptBase64 != "" || request.ApprovalTrustBundleBase64 != "" || request.PreviousApprovalTrustBundleBase64 != "" {
		return newError("publish snapshot", "conflicting_admission", errors.New("public qualification cannot carry legacy human approval evidence"))
	}
	if request.MosquePrayerPolicy != nil {
		return newError("publish snapshot", "qualified_local_policy_forbidden", errors.New("public onset qualification does not authorize mosque iqamah or Jumuah"))
	}
	if err := qualification.VerifyCandidate(*request.Qualification, request.Candidate, request.Diff.SHA256, request.GeneratedAt); err != nil {
		return newError("publish snapshot", "qualification_invalid", err)
	}
	return validatePublicationContent(request)
}

func validateQualificationReference(q *QualificationReference) error {
	if q == nil || !validSHA256(q.SHA256) || q.SHA256 != strings.ToLower(q.SHA256) || q.ID != "qualification-"+q.SHA256[:32] ||
		q.DecisionSystem != domain.SourceQualificationDecisionSystem || len(q.ScopeID) < 1 || len(q.ScopeID) > 128 || strings.TrimSpace(q.ScopeID) != q.ScopeID ||
		!validSHA256(q.OnsetSHA256) || q.OnsetSHA256 != strings.ToLower(q.OnsetSHA256) {
		return errors.New("audit receipt qualification reference is invalid")
	}
	qualifiedAt, err := time.Parse(time.RFC3339, q.QualifiedAt)
	if err != nil || qualifiedAt.UTC().Format(time.RFC3339) != q.QualifiedAt {
		return errors.New("audit receipt qualification time is invalid")
	}
	return nil
}

func verifyQualificationReceiptBinding(receipt AuditReceipt, q *domain.SourceQualification) error {
	if !reflect.DeepEqual(receipt.Qualification, qualificationReference(q)) {
		return errors.New("audit receipt does not bind the snapshot qualification")
	}
	if q != nil && (receipt.CandidateID != q.CandidateID || receipt.RawSHA256 != q.Artifact.SHA256 ||
		receipt.TranscriptionSHA256 != q.TranscriptionSHA256 || receipt.NormalizedSHA256 != q.NormalizedSHA256 || receipt.DiffSHA256 != q.DiffSHA256 || receipt.ParserVersion != q.ParserVersion) {
		return fmt.Errorf("audit receipt does not bind qualified candidate provenance")
	}
	if q != nil {
		signedAt, signErr := time.Parse(time.RFC3339, receipt.SignedAt)
		publishedAt, publishErr := time.Parse(time.RFC3339, receipt.PublishedAt)
		if signErr != nil || publishErr != nil {
			return errors.New("qualified publication has invalid signing/publication timestamps")
		}
		return validateQualificationPublicationTimes(q, signedAt, publishedAt)
	}
	return nil
}

// Compare the attested operational instants, not wall-clock verification time:
// valid historical LKG data remains verifiable, while a stale prepared request
// cannot be signed or published later by retaining an old generated_at value.
func validateQualificationPublicationTimes(q *domain.SourceQualification, signedAt, publishedAt time.Time) error {
	if q == nil {
		return nil
	}
	location, zoneErr := time.LoadLocation(q.Timezone)
	qualifiedAt, timeErr := time.Parse(time.RFC3339, q.QualifiedAt)
	if zoneErr != nil || timeErr != nil || signedAt.IsZero() || publishedAt.IsZero() || signedAt.Before(qualifiedAt) || publishedAt.Before(signedAt) ||
		signedAt.In(location).Format(time.DateOnly) > q.FreshThrough || publishedAt.In(location).Format(time.DateOnly) > q.FreshThrough {
		return newError("publish qualified snapshot", "qualification_expired_at_publication", errors.New("qualification must be current at actual signing and publication times"))
	}
	return nil
}
