// Command approver creates a mosque approver key outside Git and signs exact,
// hash-bound approval receipts. It never signs a prayer snapshot.
package main

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/approval"
	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/publication"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

const maxInputBytes = 8 * 1024 * 1024

type inspectionInput struct {
	Previous  *domain.CandidateSchedule `json:"previous_candidate,omitempty"`
	Candidate domain.CandidateSchedule  `json:"candidate"`
	Diff      publication.DiffReport    `json:"diff"`
}

func main() {
	if err := run(os.Args[1:], os.Stderr); err != nil {
		fmt.Fprintf(os.Stderr, "approver: %v\n", err)
		os.Exit(1)
	}
}

func run(args []string, stderr io.Writer) error {
	if len(args) == 0 {
		return errors.New("command is required: keygen, sign or verify")
	}
	switch args[0] {
	case "keygen":
		return runKeygen(args[1:], stderr)
	case "sign":
		return runSign(args[1:], stderr)
	case "verify":
		return runVerify(args[1:], stderr)
	default:
		return fmt.Errorf("unsupported command %q", args[0])
	}
}

func runKeygen(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("approver keygen", flag.ContinueOnError)
	flags.SetOutput(stderr)
	identity := flags.String("identity", "", "stable approver identity")
	keyID := flags.String("key-id", "", "stable approval key ID")
	generatedAtText := flags.String("generated-at", "", "canonical UTC whole-second time")
	privatePath := flags.String("private-key-out", "", "new private PKCS#8 PEM path outside Git")
	trustPath := flags.String("trust-bundle-out", "", "new public approval trust bundle path")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *identity == "" || *keyID == "" || *generatedAtText == "" || *privatePath == "" || *trustPath == "" || flags.NArg() != 0 {
		return errors.New("identity, key-id, generated-at, private-key-out and trust-bundle-out are required")
	}
	if filepath.Clean(*privatePath) == filepath.Clean(*trustPath) {
		return errors.New("private key and public trust bundle paths must differ")
	}
	generatedAt, err := canonicalTime(*generatedAtText)
	if err != nil {
		return err
	}
	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return fmt.Errorf("generate approver key: %w", err)
	}
	privateDER, err := x509.MarshalPKCS8PrivateKey(privateKey)
	if err != nil {
		return fmt.Errorf("encode approver private key: %w", err)
	}
	privatePEM := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: privateDER})
	bundle := approval.TrustBundle{
		SchemaVersion: "1.0", Revision: 1, Environment: "production",
		GeneratedAt: generatedAt.Format(time.RFC3339),
		Keys: []approval.KeyEntry{{
			KeyID: *keyID, Algorithm: "ed25519", ApproverIdentity: *identity,
			PublicKeyEd25519Base64: encodeBase64(publicKey), Status: approval.StatusActive,
			NotBefore: generatedAt.Format(time.RFC3339),
		}},
	}
	trustJSON, err := json.MarshalIndent(bundle, "", "  ")
	if err != nil {
		return fmt.Errorf("encode approval trust bundle: %w", err)
	}
	trustJSON = append(trustJSON, '\n')
	if _, err := approval.DecodeTrustBundle(trustJSON); err != nil {
		return fmt.Errorf("validate generated approval trust bundle: %w", err)
	}
	// Write public material first. A crash can leave an unusable public file,
	// but can never overwrite or expose a previously existing private key.
	if err := writeExclusive(*trustPath, trustJSON, 0o600); err != nil {
		return fmt.Errorf("write approval trust bundle: %w", err)
	}
	if err := writeExclusive(*privatePath, privatePEM, 0o600); err != nil {
		return fmt.Errorf("write approver private key (public bundle was created but is not usable without this key): %w", err)
	}
	return nil
}

func runSign(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("approver sign", flag.ContinueOnError)
	flags.SetOutput(stderr)
	privatePath := flags.String("private-key", "", "approver private PKCS#8 PEM")
	keyID := flags.String("key-id", "", "approval key ID")
	trustPath := flags.String("trust-bundle", "", "pinned public approval trust bundle")
	previousTrustPath := flags.String("previous-trust-bundle", "", "direct predecessor for approval trust revision greater than one")
	decisionPath := flags.String("decision", "", "exact approval decision JSON")
	inspectionPath := flags.String("inspection", "", "ingestor inspection JSON")
	policyPath := flags.String("prayer-policy", "", "mosque prayer policy JSON")
	outputPath := flags.String("out", "", "new signed approval receipt JSON")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *privatePath == "" || *keyID == "" || *trustPath == "" || *decisionPath == "" || *inspectionPath == "" || *policyPath == "" || *outputPath == "" || flags.NArg() != 0 {
		return errors.New("private-key, key-id, trust-bundle, decision, inspection, prayer-policy and out are required")
	}
	decision, inspection, policy, policyHash, err := readApprovalInputs(*decisionPath, *inspectionPath, *policyPath)
	if err != nil {
		return err
	}
	if err := validateDecision(decision, inspection, policy, policyHash); err != nil {
		return err
	}
	privateKey, err := readPrivateKey(*privatePath)
	if err != nil {
		return err
	}
	receipt, err := approval.Sign(decision, policyHash, *keyID, privateKey)
	if err != nil {
		return fmt.Errorf("sign approval receipt: %w", err)
	}
	data, err := approval.EncodeReceipt(receipt)
	if err != nil {
		return err
	}
	trustPolicy, err := readApprovalTrustPolicy(*trustPath, *previousTrustPath)
	if err != nil {
		return err
	}
	if verified, _, err := approval.Verify(data, trustPolicy, policyHash); err != nil {
		return fmt.Errorf("verify generated approval receipt: %w", err)
	} else if verified.Actor != decision.Actor || verified.ID != decision.ID {
		return errors.New("generated approval receipt identity does not match decision")
	}
	return writeExclusive(*outputPath, data, 0o600)
}

func runVerify(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("approver verify", flag.ContinueOnError)
	flags.SetOutput(stderr)
	receiptPath := flags.String("receipt", "", "signed approval receipt JSON")
	trustPath := flags.String("trust-bundle", "", "public approval trust bundle JSON")
	previousTrustPath := flags.String("previous-trust-bundle", "", "direct predecessor for approval trust revision greater than one")
	inspectionPath := flags.String("inspection", "", "ingestor inspection JSON")
	policyPath := flags.String("prayer-policy", "", "mosque prayer policy JSON")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *receiptPath == "" || *trustPath == "" || *inspectionPath == "" || *policyPath == "" || flags.NArg() != 0 {
		return errors.New("receipt, trust-bundle, inspection and prayer-policy are required")
	}
	var inspection inspectionInput
	var policy publication.MosquePrayerPolicy
	if err := readStrictJSON(*inspectionPath, &inspection); err != nil {
		return fmt.Errorf("read inspection: %w", err)
	}
	if err := readStrictJSON(*policyPath, &policy); err != nil {
		return fmt.Errorf("read prayer policy: %w", err)
	}
	policyHash, err := publication.MosquePrayerPolicySHA256(policy)
	if err != nil {
		return fmt.Errorf("validate prayer policy: %w", err)
	}
	trustPolicy, err := readApprovalTrustPolicy(*trustPath, *previousTrustPath)
	if err != nil {
		return err
	}
	receiptBytes, err := readRegularFile(*receiptPath)
	if err != nil {
		return fmt.Errorf("read approval receipt: %w", err)
	}
	decision, _, err := approval.Verify(receiptBytes, trustPolicy, policyHash)
	if err != nil {
		return err
	}
	return validateDecision(decision, inspection, policy, policyHash)
}

func readApprovalInputs(decisionPath, inspectionPath, policyPath string) (domain.ApprovalDecision, inspectionInput, publication.MosquePrayerPolicy, string, error) {
	var decision domain.ApprovalDecision
	var inspection inspectionInput
	var policy publication.MosquePrayerPolicy
	if err := readStrictJSON(decisionPath, &decision); err != nil {
		return decision, inspection, policy, "", fmt.Errorf("read approval decision: %w", err)
	}
	if err := readStrictJSON(inspectionPath, &inspection); err != nil {
		return decision, inspection, policy, "", fmt.Errorf("read inspection: %w", err)
	}
	if err := readStrictJSON(policyPath, &policy); err != nil {
		return decision, inspection, policy, "", fmt.Errorf("read prayer policy: %w", err)
	}
	policyHash, err := publication.MosquePrayerPolicySHA256(policy)
	if err != nil {
		return decision, inspection, policy, "", fmt.Errorf("validate prayer policy: %w", err)
	}
	return decision, inspection, policy, policyHash, nil
}

func readApprovalTrustPolicy(currentPath, previousPath string) (*approval.Policy, error) {
	current, err := readRegularFile(currentPath)
	if err != nil {
		return nil, fmt.Errorf("read approval trust bundle: %w", err)
	}
	var previous []byte
	if previousPath != "" {
		previous, err = readRegularFile(previousPath)
		if err != nil {
			return nil, fmt.Errorf("read previous approval trust bundle: %w", err)
		}
	}
	policy, err := approval.DecodeTrustBundleChain(current, previous)
	if err != nil {
		return nil, fmt.Errorf("validate approval trust bundle: %w", err)
	}
	return policy, nil
}

func validateDecision(decision domain.ApprovalDecision, inspection inspectionInput, policy publication.MosquePrayerPolicy, policyHash string) error {
	candidate := inspection.Candidate
	if policy.MosqueID != candidate.Mosque.ID || policy.ValidFrom != candidate.Coverage.From || policy.ValidTo != candidate.Coverage.To {
		return errors.New("prayer policy scope does not match candidate")
	}
	recomputed, err := publication.Diff(inspection.Previous, candidate)
	if err != nil || recomputed.SHA256 != inspection.Diff.SHA256 {
		return errors.New("inspection diff is not reproducible")
	}
	normalized, err := domain.CandidateNormalizedSHA256(candidate)
	if err != nil || normalized != candidate.NormalizedSHA256 {
		return errors.New("candidate normalized hash is not reproducible")
	}
	if decision.Decision != domain.ApprovalApproved || decision.CandidateID != candidate.ID || decision.RawSHA256 != candidate.Artifact.SHA256 || decision.TranscriptionSHA256 != candidate.TranscriptionSHA256 || decision.NormalizedSHA256 != candidate.NormalizedSHA256 || decision.DiffSHA256 != inspection.Diff.SHA256 || decision.ParserVersion != candidate.ParserVersion || decision.PrayerPolicySHA256 != policyHash {
		return errors.New("approval decision does not bind the exact inspection and prayer policy")
	}
	acknowledged := make(map[string]struct{}, len(decision.AcknowledgedWarningCodes))
	for _, code := range decision.AcknowledgedWarningCodes {
		acknowledged[code] = struct{}{}
	}
	for _, warning := range candidate.Validation.Warnings {
		if _, ok := acknowledged[warning.Code]; !ok {
			return fmt.Errorf("approval does not acknowledge warning %q", warning.Code)
		}
	}
	return nil
}

func readPrivateKey(path string) (ed25519.PrivateKey, error) {
	data, err := readRegularFile(path)
	if err != nil {
		return nil, fmt.Errorf("read approver private key: %w", err)
	}
	block, rest := pem.Decode(data)
	if block == nil || block.Type != "PRIVATE KEY" || len(bytes.TrimSpace(rest)) != 0 {
		return nil, errors.New("approver private key must be one PKCS#8 PEM block")
	}
	parsed, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, errors.New("approver private key PKCS#8 is invalid")
	}
	key, ok := parsed.(ed25519.PrivateKey)
	if !ok || len(key) != ed25519.PrivateKeySize {
		return nil, errors.New("approver private key is not Ed25519")
	}
	return key, nil
}

func readStrictJSON(path string, target any) error {
	data, err := readRegularFile(path)
	if err != nil {
		return err
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return err
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return errors.New("JSON contains trailing data")
	}
	return nil
}

func readRegularFile(path string) ([]byte, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() || info.Size() <= 0 || info.Size() > maxInputBytes {
		return nil, errors.New("input must be a bounded non-empty regular file")
	}
	return os.ReadFile(path)
}

func writeExclusive(path string, data []byte, mode os.FileMode) (returnErr error) {
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, mode)
	if err != nil {
		return err
	}
	closed := false
	defer func() {
		if !closed {
			closeErr := file.Close()
			if returnErr == nil && closeErr != nil {
				returnErr = closeErr
			}
		}
	}()
	if _, err := file.Write(data); err != nil {
		return err
	}
	if err := file.Sync(); err != nil {
		return err
	}
	if err := file.Close(); err != nil {
		return err
	}
	closed = true
	directory, err := os.Open(filepath.Dir(path))
	if err != nil {
		return err
	}
	defer directory.Close()
	return directory.Sync()
}

func canonicalTime(value string) (time.Time, error) {
	parsed, err := time.Parse(time.RFC3339, value)
	if err != nil || parsed.UTC().Format(time.RFC3339) != value {
		return time.Time{}, errors.New("time must be canonical UTC whole-second RFC3339")
	}
	return parsed, nil
}

func encodeBase64(value []byte) string {
	return base64.StdEncoding.EncodeToString(value)
}
