package publication_test

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/controlled"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
	"github.com/andy-ahmedov/namaz-time/internal/qualification"
	jsonschema "github.com/santhosh-tekuri/jsonschema/v6"
)

func TestQualifiedPublicProductionPublicationNeedsNoHumanApproval(t *testing.T) {
	request := qualifiedPublishRequest(t)
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	policy := productionPolicy(t, request.SigningKeyID, publicKey)
	signer := &protectedSigner{keyID: request.SigningKeyID, privateKey: privateKey}
	result, receipt, err := publication.PublishWithSigner(context.Background(), request, signer, policy, publication.AuditMetadata{
		SignerIdentity: "isolated://synthetic-production-test", PublishedAt: request.GeneratedAt.Add(time.Minute), ChainGenesisReason: "synthetic qualification protocol test",
	})
	if err != nil {
		t.Fatal(err)
	}
	if !signer.called || result.Snapshot.SchemaVersion != "2.0" || result.Snapshot.Source.Qualification == nil || result.Snapshot.Source.Qualification.ID != request.Qualification.ID {
		t.Fatal("public source did not produce an authenticated v2 artifact")
	}
	if len(result.Snapshot.IqamahRules) != 0 || len(result.Snapshot.JumuahSessions) != 0 || result.Snapshot.Source.Approval != (domain.Approval{}) {
		t.Fatal("regional onset publication invented a mosque approval or local prayer policy")
	}
	prepared, err := publication.PrepareSigning(request, policy)
	if err != nil {
		t.Fatal(err)
	}
	assertPublicationSchema(t, "publication-signing-request.schema.json", prepared)
	assertPublicationSchema(t, "publication-audit-receipt.schema.json", receipt)
	for _, value := range []any{request, prepared, receipt, result.Snapshot} {
		encoded, err := json.Marshal(value)
		if err != nil {
			t.Fatal(err)
		}
		for _, fabricated := range []string{`"approved_by"`, `"approver_identity"`, `"approval_id"`, `"approval"`, `"approved_at"`} {
			if bytes.Contains(encoded, []byte(fabricated)) {
				t.Fatalf("public publication fabricated %s", fabricated)
			}
		}
	}
	if err := publication.VerifyPublicationEvidence(result.JSON, receipt, policy, nil); err != nil {
		t.Fatalf("qualified publication admission failed: %v", err)
	}
	// Opt-in cross-language fixture export: only synthetic signed bytes and the
	// ephemeral PUBLIC test key leave this process. Never export a private key.
	if output := os.Getenv("NAMAZTIME_QUALIFIED_INTEROP_OUTPUT"); output != "" {
		manifest, err := json.MarshalIndent(map[string]string{
			"fixture_kind": "synthetic_protocol_test_only", "key_id": request.SigningKeyID,
			"public_key_base64": base64.StdEncoding.EncodeToString(publicKey),
			"generated_at":      request.GeneratedAt.Format(time.RFC3339),
		}, "", "  ")
		if err != nil {
			t.Fatal(err)
		}
		for name, contents := range map[string][]byte{"qualified-snapshot.json": result.JSON, "public-test-key.json": manifest} {
			file, err := os.OpenFile(filepath.Join(output, name), os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
			if err != nil {
				t.Fatal(err)
			}
			_, writeErr := file.Write(contents)
			closeErr := file.Close()
			if writeErr != nil || closeErr != nil {
				t.Fatalf("write synthetic interop fixture: %v; close: %v", writeErr, closeErr)
			}
		}
	}
	if _, err := publication.Publish(request, privateKey); !publication.IsErrorCode(err, "production_signer_required") {
		t.Fatalf("qualification bypassed protected production signer: %v", err)
	}
	tampered := append([]byte(nil), result.JSON...)
	tampered = bytes.Replace(tampered, []byte(`"fajr": "03:00"`), []byte(`"fajr": "03:01"`), 1)
	if bytes.Equal(tampered, result.JSON) || publication.VerifyWithTrust(tampered, policy) == nil {
		t.Fatal("qualified signed rows did not reject tampering")
	}
}

func TestQualifiedPublicationFreshnessUsesActualSigningAndPublicationDates(t *testing.T) {
	request := qualifiedPublishRequest(t)
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	policy := productionPolicy(t, request.SigningKeyID, publicKey)
	for _, lateSigning := range []bool{false, true} {
		signer := &protectedSigner{keyID: request.SigningKeyID, privateKey: privateKey}
		audit := publication.AuditMetadata{SignerIdentity: "isolated://synthetic-late-publisher", SignedAt: request.GeneratedAt.Add(time.Minute), PublishedAt: request.GeneratedAt.AddDate(0, 0, 2), ChainGenesisReason: "synthetic expiry test"}
		if lateSigning {
			audit.SignedAt = audit.PublishedAt
		}
		_, _, err := publication.PublishWithSigner(t.Context(), request, signer, policy, audit)
		if err == nil || signer.called {
			t.Fatalf("expired qualification reached protected signer (late signing %v): %v", lateSigning, err)
		}
	}
	prepared, err := publication.PrepareSigning(request, policy)
	if err != nil {
		t.Fatal(err)
	}
	canonical, err := base64.StdEncoding.DecodeString(prepared.CanonicalPayloadBase64)
	if err != nil {
		t.Fatal(err)
	}
	late := request.GeneratedAt.AddDate(0, 0, 2)
	response := publication.SigningResponse{SchemaVersion: "1.0", RequestID: prepared.RequestID, SigningKeyID: prepared.SigningKeyID, SignatureEd25519Base64: base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, canonical)), SignerIdentity: "isolated://synthetic-late-publisher", SignedAt: late.Format(time.RFC3339), PublishedAt: late.Format(time.RFC3339), ChainGenesisReason: "synthetic expiry test"}
	attestation, requestHash, err := publication.BuildAttestationPayload(prepared, response)
	if err != nil {
		t.Fatal(err)
	}
	response.SigningRequestSHA256 = requestHash
	response.AttestationEd25519Base64 = base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, attestation))
	if _, _, err := publication.FinalizeSigning(request, prepared, response, policy, publication.AuditMetadata{PublishedAt: late, ChainGenesisReason: response.ChainGenesisReason}); err == nil {
		t.Fatal("delayed correctly signed response was finalized after qualification expiry")
	}
	// Reconstruct a cryptographically valid historical receipt for the same
	// snapshot, with a late attestation. Authenticity alone is not admission.
	result, receipt, err := publication.PublishWithSigner(t.Context(), request, &protectedSigner{keyID: request.SigningKeyID, privateKey: privateKey}, policy, publication.AuditMetadata{SignerIdentity: response.SignerIdentity, PublishedAt: request.GeneratedAt.Add(time.Minute), ChainGenesisReason: response.ChainGenesisReason})
	if err != nil {
		t.Fatal(err)
	}
	receipt.SignedAt, receipt.PublishedAt = response.SignedAt, response.PublishedAt
	receipt.AttestationSignature = response.AttestationEd25519Base64
	receipt.SigningRequestSHA256 = response.SigningRequestSHA256
	receipt = rehashReceipt(t, receipt)
	if err := publication.VerifyAuditReceiptHead(receipt, policy); err != nil {
		t.Fatalf("expiry test attestation is not authentic: %v", err)
	}
	if err := publication.VerifyPublicationAdmission(result.JSON, receipt, policy); err == nil {
		t.Fatal("correctly re-signed stale publication was admitted")
	}
}

func TestPublicationSchemaRevisionBoundsRemainExactInt64(t *testing.T) {
	for _, file := range []string{"publication-signing-request.schema.json", "publication-audit-receipt.schema.json"} {
		raw, err := os.ReadFile(filepath.Join("..", "..", "contracts", file))
		if err != nil {
			t.Fatal(err)
		}
		var document struct {
			Properties map[string]json.RawMessage `json:"properties"`
		}
		if err := json.Unmarshal(raw, &document); err != nil {
			t.Fatal(err)
		}
		for _, field := range []string{"trust_bundle_revision", "approval_trust_revision"} {
			schemaDoc, err := jsonschema.UnmarshalJSON(bytes.NewReader(document.Properties[field]))
			if err != nil {
				t.Fatal(err)
			}
			compiler := jsonschema.NewCompiler()
			if err := compiler.AddResource("https://example.invalid/bound", schemaDoc); err != nil {
				t.Fatal(err)
			}
			schema, err := compiler.Compile("https://example.invalid/bound")
			if err != nil {
				t.Fatal(err)
			}
			for _, value := range []string{"9223372036854775807", "9223372036854775808"} {
				number, err := jsonschema.UnmarshalJSON(strings.NewReader(value))
				if err != nil {
					t.Fatal(err)
				}
				accepted := schema.Validate(number) == nil
				if accepted != (value == "9223372036854775807") {
					t.Fatalf("%s %s incorrectly accepts=%v for %s", file, field, accepted, value)
				}
			}
		}
	}
}

func TestQualifiedReceiptCannotAttestSigningBeforeSnapshotGeneration(t *testing.T) {
	request := qualifiedPublishRequest(t)
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	policy := productionPolicy(t, request.SigningKeyID, publicKey)
	prepared, err := publication.PrepareSigning(request, policy)
	if err != nil {
		t.Fatal(err)
	}
	result, receipt, err := publication.PublishWithSigner(t.Context(), request, &protectedSigner{keyID: request.SigningKeyID, privateKey: privateKey}, policy, publication.AuditMetadata{SignerIdentity: "isolated://synthetic-order-test", PublishedAt: request.GeneratedAt.Add(time.Minute), ChainGenesisReason: "synthetic temporal order test"})
	if err != nil {
		t.Fatal(err)
	}
	response := publication.SigningResponse{SchemaVersion: "1.0", RequestID: prepared.RequestID, SigningKeyID: prepared.SigningKeyID, SignatureEd25519Base64: result.Snapshot.Integrity.SignatureEd25519Base64, SignerIdentity: receipt.SignerIdentity, SignedAt: request.Qualification.QualifiedAt, PublishedAt: receipt.PublishedAt, ChainGenesisReason: receipt.ChainGenesisReason}
	attestation, requestHash, err := publication.BuildAttestationPayload(prepared, response)
	if err != nil {
		t.Fatal(err)
	}
	receipt.SignedAt = response.SignedAt
	receipt.SigningRequestSHA256 = requestHash
	receipt.AttestationSignature = base64.StdEncoding.EncodeToString(ed25519.Sign(privateKey, attestation))
	receipt = rehashReceipt(t, receipt)
	if err := publication.VerifyPublicationEvidence(result.JSON, receipt, policy, nil); err == nil {
		t.Fatal("authentic receipt claims signing before snapshot generation")
	}
}

func assertPublicationSchema(t *testing.T, filename string, value any) {
	t.Helper()
	rawSchema, err := os.ReadFile(filepath.Join("..", "..", "contracts", filename))
	if err != nil {
		t.Fatal(err)
	}
	schemaDocument, err := jsonschema.UnmarshalJSON(bytes.NewReader(rawSchema))
	if err != nil {
		t.Fatal(err)
	}
	compiler := jsonschema.NewCompiler()
	compiler.AssertFormat()
	url := "https://example.invalid/schemas/" + filename
	if err := compiler.AddResource(url, schemaDocument); err != nil {
		t.Fatal(err)
	}
	schema, err := compiler.Compile(url)
	if err != nil {
		t.Fatal(err)
	}
	encoded, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	document, err := jsonschema.UnmarshalJSON(bytes.NewReader(encoded))
	if err != nil {
		t.Fatal(err)
	}
	if err := schema.Validate(document); err != nil {
		t.Fatalf("%s: %v", filename, err)
	}
}

func TestQualifiedPublicationFailsClosedBeforeProtectedSigning(t *testing.T) {
	for _, tt := range []struct {
		name   string
		change func(*publication.PublishRequest)
	}{
		{"no qualification", func(r *publication.PublishRequest) { r.Qualification = nil }},
		{"human approval mixed into public branch", func(r *publication.PublishRequest) { r.Approval = approvalFor(r.Candidate, r.Diff) }},
		{"unbound private prayer policy", func(r *publication.PublishRequest) { r.MosquePrayerPolicy = &publication.MosquePrayerPolicy{} }},
		{"borrowed approval receipt", func(r *publication.PublishRequest) { r.ApprovalReceiptBase64 = "c29tZXRoaW5n" }},
		{"modified qualification", func(r *publication.PublishRequest) { r.Qualification.Scope.Description += "-expanded" }},
		{"modified onset", func(r *publication.PublishRequest) { r.Candidate.Days[0].Fajr = "03:01" }},
		{"modified diff", func(r *publication.PublishRequest) { r.Diff.Changes[0].After = "03:01" }},
		{"expired qualification", func(r *publication.PublishRequest) { r.GeneratedAt = r.GeneratedAt.AddDate(0, 0, 1) }},
	} {
		t.Run(tt.name, func(t *testing.T) {
			request := qualifiedPublishRequest(t)
			tt.change(&request)
			publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
			if err != nil {
				t.Fatal(err)
			}
			policy := productionPolicy(t, request.SigningKeyID, publicKey)
			signer := &protectedSigner{keyID: request.SigningKeyID, privateKey: privateKey}
			_, _, err = publication.PublishWithSigner(context.Background(), request, signer, policy, publication.AuditMetadata{SignerIdentity: "isolated://synthetic-production-test", PublishedAt: request.GeneratedAt.Add(time.Minute), ChainGenesisReason: "invalid proof test"})
			if err == nil || signer.called {
				t.Fatalf("invalid public qualification reached signer: %v", err)
			}
		})
	}
}

func qualifiedPublishRequest(t *testing.T) publication.PublishRequest {
	t.Helper()
	c := candidate()
	c.DataClassification = domain.DataClassificationProduction // Synthetic fixture exercising production trust separation.
	c.Source.Kind, c.Source.PermissionStatus, c.Source.ApprovalRequired = domain.ProviderKindOfficialFile, "", false
	c.Source.CanonicalURL, c.Source.MinimumCoverageDays, c.Source.MaxDeltaMinutes = "https://authority.example/calendar", 1, 15
	raw := []byte("synthetic qualification protocol fixture")
	rawHash := sha256.Sum256(raw)
	c.Artifact.Filename, c.Artifact.ByteLength, c.Artifact.SHA256 = c.Source.CanonicalURL, int64(len(raw)), hex.EncodeToString(rawHash[:])
	c.TranscriptionSHA256 = c.Artifact.SHA256
	catalog := qualification.CatalogBinding{Revision: "catalog-test-v1", SourceRevision: "2025-01-01", Region: domain.Region{ID: "region-test", Name: "Synthetic region", CountryCode: "RU"}, Cities: []domain.City{{ID: "city-test", Name: "Synthetic city", CountryCode: "RU", RegionID: "region-test", Timezone: c.Mosque.Timezone, GeographicRevision: "2025-01-01"}}}
	review := qualification.EvidenceReview{Authority: domain.PrayerAuthority{ID: "authority-test", Name: c.Source.AuthorityName, Website: "https://authority.example", EvidenceLabel: "CONFIRMED_PUBLIC"}, Scope: domain.GeographicScope{ID: "scope-test", Kind: domain.GeographicScopeCity, CityID: "city-test", RegionID: "region-test", Description: c.Source.GeographicScope}, CatalogRevision: catalog.Revision, Timezone: c.Mosque.Timezone, FreshThrough: c.Coverage.To, Retrieval: domain.PublicSourceRetrieval{URL: c.Source.CanonicalURL, HTTPStatus: 200, ContentType: c.Artifact.ContentType}, TermsAssessment: "public_transport_no_restriction_observed"}
	var err error
	c.Mosque, err = qualification.PublicDisplayContext(review.Scope, catalog, review.Timezone)
	if err != nil {
		t.Fatal(err)
	}
	for _, purpose := range []string{"ownership", "scope", "currentness", "terms", "time_semantics", "value_comparison"} {
		review.Evidence = append(review.Evidence, domain.SourceEvidence{ID: "evidence-" + purpose, Purpose: purpose, Label: "CONFIRMED_PUBLIC", URL: "https://authority.example/" + purpose, RetrievedAt: c.Artifact.CapturedAt, SHA256: strings.Repeat("e", 64), Claim: "Synthetic evidence for " + purpose})
	}
	compared := c.Days[0].PrayerDay
	compared.Flags = nil
	review.Comparisons = []domain.SourceValueComparison{{EvidenceID: "evidence-value_comparison", Day: compared}}
	c.Validation = controlled.ValidatePublicCandidateData(controlled.ValidationConfig{SourceCountryCode: "RU", SourceTimezone: c.Mosque.Timezone}, c)
	if err := domain.FinalizeCandidateIdentity(&c); err != nil {
		t.Fatal(err)
	}
	diff, err := publication.Diff(nil, c)
	if err != nil {
		t.Fatal(err)
	}
	qualifiedAt := time.Date(2025, 1, 1, 0, 1, 0, 0, time.UTC)
	proof, err := qualification.Create(review, c, raw, diff.SHA256, catalog, qualifiedAt)
	if err != nil {
		t.Fatal(err)
	}
	return publication.PublishRequest{Candidate: c, Diff: diff, Qualification: &proof, SnapshotID: "qualified-synthetic-test-v1", GeneratedAt: qualifiedAt.Add(time.Minute), SigningKeyID: "synthetic-public-production-test-key"}
}
