// Command publisher prepares and finalizes snapshot publication without ever
// accepting production private key material. Signing happens in an isolated
// KMS/HSM operator boundary using the emitted canonical request.
package main

import (
	"bytes"
	"encoding/json"
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
	"github.com/andy-ahmedov/namaz-time/internal/trust"
)

const (
	componentName = "publisher"
	maxInputBytes = 8 * 1024 * 1024
)

func main() {
	if err := run(os.Args[1:], os.Stderr); err != nil {
		fmt.Fprintf(os.Stderr, "%s: %v\n", componentName, err)
		os.Exit(1)
	}
}

func run(args []string, stderr io.Writer) error {
	if len(args) == 0 {
		return errors.New("command is required: assemble, prepare, finalize, verify or verify-trust")
	}
	switch args[0] {
	case "assemble":
		return runAssemble(args[1:], stderr)
	case "prepare":
		return runPrepare(args[1:], stderr)
	case "finalize":
		return runFinalize(args[1:], stderr)
	case "verify":
		return runVerify(args[1:], stderr)
	case "verify-trust":
		return runVerifyTrust(args[1:], stderr)
	default:
		return fmt.Errorf("unsupported command %q", args[0])
	}
}

func runVerifyTrust(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("publisher verify-trust", flag.ContinueOnError)
	flags.SetOutput(stderr)
	currentPath := flags.String("current", "", "current public trust bundle JSON")
	previousPath := flags.String("previous", "", "directly preceding trust bundle JSON")
	testPath := flags.String("test-bundle", "", "optional test environment bundle")
	stagingPath := flags.String("staging-bundle", "", "optional staging environment bundle")
	productionPath := flags.String("production-bundle", "", "optional production environment bundle")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *currentPath == "" || flags.NArg() != 0 {
		return errors.New("-current is required")
	}
	current, err := readTrustPolicy(*currentPath)
	if err != nil {
		return err
	}
	if current.Revision() > 1 {
		if *previousPath == "" {
			return errors.New("non-genesis current bundle requires -previous")
		}
		previous, readErr := readTrustPolicy(*previousPath)
		if readErr != nil {
			return readErr
		}
		if transitionErr := trust.ValidateTransition(previous, current); transitionErr != nil {
			return fmt.Errorf("validate trust transition: %w", transitionErr)
		}
	} else if *previousPath != "" {
		return errors.New("genesis revision must not have -previous")
	}
	policies := []*trust.Policy{current}
	seen := map[string]bool{current.Environment(): true}
	for _, item := range []struct {
		path        string
		environment string
	}{{*testPath, "test"}, {*stagingPath, "staging"}, {*productionPath, "production"}} {
		if item.path == "" {
			continue
		}
		policy, readErr := readTrustPolicy(item.path)
		if readErr != nil {
			return readErr
		}
		if policy.Environment() != item.environment || seen[item.environment] {
			return fmt.Errorf("%s bundle is duplicated or has the wrong environment", item.environment)
		}
		seen[item.environment] = true
		policies = append(policies, policy)
	}
	if err := trust.ValidateEnvironmentSeparation(policies...); err != nil {
		return fmt.Errorf("validate environment separation: %w", err)
	}
	return nil
}

type inspectionInput struct {
	Previous  *domain.CandidateSchedule `json:"previous_candidate,omitempty"`
	Candidate domain.CandidateSchedule  `json:"candidate"`
	Diff      publication.DiffReport    `json:"diff"`
}

type publicationLedgerHead struct {
	SchemaVersion string `json:"schema_version"`
	Environment   string `json:"environment"`
	ReceiptSHA256 string `json:"receipt_sha256"`
}

func runAssemble(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("publisher assemble", flag.ContinueOnError)
	flags.SetOutput(stderr)
	inspectionPath := flags.String("inspection", "", "strict ingestor inspection JSON")
	approvalPath := flags.String("approval", "", "strict approval decision JSON")
	approvalReceiptPath := flags.String("approval-receipt", "", "signed production approval receipt JSON")
	approvalTrustPath := flags.String("approval-trust-bundle", "", "pinned public approval trust bundle JSON")
	previousApprovalTrustPath := flags.String("previous-approval-trust-bundle", "", "direct predecessor for approval trust revision greater than one")
	prayerPolicyPath := flags.String("prayer-policy", "", "approved mosque prayer policy JSON")
	snapshotID := flags.String("snapshot-id", "", "immutable snapshot ID")
	generatedAtText := flags.String("generated-at", "", "canonical UTC RFC3339 generation time")
	keyID := flags.String("signing-key-id", "", "active trust-bundle signing key ID")
	outputPath := flags.String("out", "", "new publication request JSON path")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *inspectionPath == "" || *snapshotID == "" || *generatedAtText == "" || *keyID == "" || *outputPath == "" || flags.NArg() != 0 {
		return errors.New("inspection, snapshot ID, generated-at, signing-key-id and out are required")
	}
	generatedAt, err := time.Parse(time.RFC3339, *generatedAtText)
	if err != nil || generatedAt.UTC().Format(time.RFC3339) != *generatedAtText {
		return errors.New("generated-at must be canonical UTC RFC3339")
	}
	var inspection inspectionInput
	if err := readStrictJSON(*inspectionPath, &inspection); err != nil {
		return fmt.Errorf("read inspection: %w", err)
	}
	var decision domain.ApprovalDecision
	var evidence *publication.ApprovalEvidence
	var prayerPolicy *publication.MosquePrayerPolicy
	if inspection.Candidate.DataClassification == domain.DataClassificationProduction {
		if *approvalPath != "" || *approvalReceiptPath == "" || *approvalTrustPath == "" || *prayerPolicyPath == "" {
			return errors.New("production assemble requires signed approval-receipt, approval-trust-bundle and prayer-policy; unsigned -approval is forbidden")
		}
		var policy publication.MosquePrayerPolicy
		if err := readStrictJSON(*prayerPolicyPath, &policy); err != nil {
			return fmt.Errorf("read prayer policy: %w", err)
		}
		policySHA256, err := publication.MosquePrayerPolicySHA256(policy)
		if err != nil {
			return fmt.Errorf("validate prayer policy: %w", err)
		}
		trustBytes, err := readRegularFile(*approvalTrustPath)
		if err != nil {
			return fmt.Errorf("read approval trust bundle: %w", err)
		}
		var previousTrustBytes []byte
		if *previousApprovalTrustPath != "" {
			previousTrustBytes, err = readRegularFile(*previousApprovalTrustPath)
			if err != nil {
				return fmt.Errorf("read previous approval trust bundle: %w", err)
			}
		}
		approvalPolicy, err := approval.DecodeTrustBundleChain(trustBytes, previousTrustBytes)
		if err != nil {
			return err
		}
		if approvalPolicy.Environment() != "production" {
			return errors.New("production approval requires a production approval trust bundle")
		}
		receiptBytes, err := readRegularFile(*approvalReceiptPath)
		if err != nil {
			return fmt.Errorf("read approval receipt: %w", err)
		}
		verified, verifiedEvidence, err := approval.Verify(receiptBytes, approvalPolicy, policySHA256)
		if err != nil {
			return fmt.Errorf("verify approval receipt: %w", err)
		}
		if err := validateApprovalAgainstInspection(verified, inspection, policySHA256); err != nil {
			return err
		}
		decision = verified
		evidence = &publication.ApprovalEvidence{
			ReceiptSHA256: verifiedEvidence.ReceiptSHA256, TrustRevision: verifiedEvidence.TrustRevision,
			TrustBundleSHA256: verifiedEvidence.TrustBundleSHA256, ApprovalKeyID: verifiedEvidence.KeyID,
		}
		prayerPolicy = &policy
	} else {
		if *approvalPath == "" || *approvalReceiptPath != "" || *approvalTrustPath != "" || *previousApprovalTrustPath != "" || *prayerPolicyPath != "" {
			return errors.New("synthetic assemble requires exactly -approval and no production approval inputs")
		}
		if err := readStrictJSON(*approvalPath, &decision); err != nil {
			return fmt.Errorf("read approval: %w", err)
		}
	}
	request := publication.PublishRequest{
		Previous: inspection.Previous, Candidate: inspection.Candidate, Diff: inspection.Diff, Approval: decision,
		ApprovalEvidence: evidence, MosquePrayerPolicy: prayerPolicy,
		SnapshotID: *snapshotID, GeneratedAt: generatedAt, SigningKeyID: *keyID,
	}
	data, err := json.MarshalIndent(request, "", "  ")
	if err != nil {
		return fmt.Errorf("encode publication request: %w", err)
	}
	return writeExclusive(*outputPath, append(data, '\n'))
}

func validateApprovalAgainstInspection(decision domain.ApprovalDecision, inspection inspectionInput, prayerPolicySHA256 string) error {
	candidate := inspection.Candidate
	recomputed, err := publication.Diff(inspection.Previous, candidate)
	if err != nil || recomputed.SHA256 != inspection.Diff.SHA256 {
		return errors.New("approval inspection diff is not reproducible")
	}
	normalized, err := domain.CandidateNormalizedSHA256(candidate)
	if err != nil || normalized != candidate.NormalizedSHA256 {
		return errors.New("approval candidate fingerprint is not reproducible")
	}
	if decision.CandidateID != candidate.ID || decision.RawSHA256 != candidate.Artifact.SHA256 || decision.TranscriptionSHA256 != candidate.TranscriptionSHA256 || decision.NormalizedSHA256 != candidate.NormalizedSHA256 || decision.DiffSHA256 != inspection.Diff.SHA256 || decision.ParserVersion != candidate.ParserVersion || decision.PrayerPolicySHA256 != prayerPolicySHA256 {
		return errors.New("signed approval does not bind the exact inspection and prayer policy")
	}
	acknowledged := make(map[string]struct{}, len(decision.AcknowledgedWarningCodes))
	for _, code := range decision.AcknowledgedWarningCodes {
		acknowledged[code] = struct{}{}
	}
	for _, warning := range candidate.Validation.Warnings {
		if _, ok := acknowledged[warning.Code]; !ok {
			return fmt.Errorf("signed approval does not acknowledge warning %q", warning.Code)
		}
	}
	return nil
}

func runVerify(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("publisher verify", flag.ContinueOnError)
	flags.SetOutput(stderr)
	snapshotPath := flags.String("snapshot", "", "signed snapshot JSON")
	receiptPath := flags.String("receipt", "", "publication receipt JSON")
	previousPath := flags.String("previous-receipt", "", "optional previous publication receipt JSON")
	trustPath := flags.String("trust-bundle", "", "public trust bundle JSON")
	previousTrustPath := flags.String("previous-trust-bundle", "", "direct predecessor for non-genesis trust revision")
	testTrustPath := flags.String("test-trust-bundle", "", "test bundle used to prove production key separation")
	stagingTrustPath := flags.String("staging-trust-bundle", "", "staging bundle used to prove production key separation")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *snapshotPath == "" || *receiptPath == "" || *trustPath == "" || flags.NArg() != 0 {
		return errors.New("-snapshot, -receipt and -trust-bundle are required")
	}
	snapshot, err := readRegularFile(*snapshotPath)
	if err != nil {
		return fmt.Errorf("read snapshot: %w", err)
	}
	var receipt publication.AuditReceipt
	if err := readStrictJSON(*receiptPath, &receipt); err != nil {
		return fmt.Errorf("read receipt: %w", err)
	}
	var previous *publication.AuditReceipt
	if *previousPath != "" {
		previous = &publication.AuditReceipt{}
		if err := readStrictJSON(*previousPath, previous); err != nil {
			return fmt.Errorf("read previous receipt: %w", err)
		}
	}
	policy, err := readTrustPolicyTransition(*trustPath, *previousTrustPath, *testTrustPath, *stagingTrustPath)
	if err != nil {
		return err
	}
	return publication.VerifyPublicationEvidence(snapshot, receipt, policy, previous)
}

func runPrepare(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("publisher prepare", flag.ContinueOnError)
	flags.SetOutput(stderr)
	requestPath := flags.String("request", "", "strict publication request JSON")
	trustPath := flags.String("trust-bundle", "", "public trust bundle JSON")
	previousTrustPath := flags.String("previous-trust-bundle", "", "direct predecessor for non-genesis trust revision")
	testTrustPath := flags.String("test-trust-bundle", "", "test bundle used to prove production key separation")
	stagingTrustPath := flags.String("staging-trust-bundle", "", "staging bundle used to prove production key separation")
	outputPath := flags.String("out", "", "new signing request JSON path")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *requestPath == "" || *trustPath == "" || *outputPath == "" || flags.NArg() != 0 {
		return errors.New("-request, -trust-bundle and -out are required")
	}
	var request publication.PublishRequest
	if err := readStrictJSON(*requestPath, &request); err != nil {
		return fmt.Errorf("read publication request: %w", err)
	}
	policy, err := readTrustPolicyTransition(*trustPath, *previousTrustPath, *testTrustPath, *stagingTrustPath)
	if err != nil {
		return err
	}
	prepared, err := publication.PrepareSigning(request, policy)
	if err != nil {
		return err
	}
	data, err := publication.EncodeSigningRequest(prepared)
	if err != nil {
		return err
	}
	return writeExclusive(*outputPath, data)
}

func runFinalize(args []string, stderr io.Writer) error {
	flags := flag.NewFlagSet("publisher finalize", flag.ContinueOnError)
	flags.SetOutput(stderr)
	requestPath := flags.String("request", "", "strict publication request JSON")
	preparedPath := flags.String("signing-request", "", "prepared signing request JSON")
	responsePath := flags.String("signer-response", "", "isolated signer response JSON")
	trustPath := flags.String("trust-bundle", "", "public trust bundle JSON")
	previousTrustPath := flags.String("previous-trust-bundle", "", "direct predecessor for non-genesis trust revision")
	testTrustPath := flags.String("test-trust-bundle", "", "test bundle used to prove production key separation")
	stagingTrustPath := flags.String("staging-trust-bundle", "", "staging bundle used to prove production key separation")
	snapshotPath := flags.String("out-snapshot", "", "new signed snapshot JSON path")
	receiptPath := flags.String("out-receipt", "", "new publication receipt JSON path")
	publishedAtText := flags.String("published-at", "", "canonical UTC RFC3339 publication time")
	previousReceiptPath := flags.String("previous-receipt", "", "prior publication receipt JSON")
	chainGenesisReason := flags.String("chain-genesis-reason", "", "bounded reason for the one explicit chain genesis")
	ledgerHeadPath := flags.String("ledger-head", "", "durable publication ledger head JSON")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *requestPath == "" || *preparedPath == "" || *responsePath == "" || *trustPath == "" ||
		*snapshotPath == "" || *receiptPath == "" || *publishedAtText == "" || *ledgerHeadPath == "" || flags.NArg() != 0 {
		return errors.New("request, signing request/response, trust bundle, ledger head, both outputs and published-at are required")
	}
	if filepath.Clean(*snapshotPath) == filepath.Clean(*receiptPath) {
		return errors.New("snapshot and receipt outputs must differ")
	}
	publishedAt, err := time.Parse(time.RFC3339, *publishedAtText)
	if err != nil || publishedAt.UTC().Format(time.RFC3339) != *publishedAtText {
		return errors.New("published-at must be canonical UTC RFC3339")
	}
	var request publication.PublishRequest
	var prepared publication.SigningRequest
	var response publication.SigningResponse
	for _, input := range []struct {
		path  string
		value any
		name  string
	}{{*requestPath, &request, "publication request"}, {*preparedPath, &prepared, "signing request"}, {*responsePath, &response, "signer response"}} {
		if err := readStrictJSON(input.path, input.value); err != nil {
			return fmt.Errorf("read %s: %w", input.name, err)
		}
	}
	policy, err := readTrustPolicyTransition(*trustPath, *previousTrustPath, *testTrustPath, *stagingTrustPath)
	if err != nil {
		return err
	}
	if (*previousReceiptPath == "") == (*chainGenesisReason == "") {
		return errors.New("exactly one -previous-receipt or -chain-genesis-reason is required")
	}
	previousReceiptSHA256 := ""
	if *previousReceiptPath != "" {
		var previous publication.AuditReceipt
		if err := readStrictJSON(*previousReceiptPath, &previous); err != nil {
			return fmt.Errorf("read previous receipt: %w", err)
		}
		if err := publication.VerifyAuditReceiptHead(previous, policy); err != nil {
			return fmt.Errorf("verify previous receipt: %w", err)
		}
		previousReceiptSHA256 = previous.ReceiptSHA256
	}
	commitLedgerHead, releaseLedgerLock, err := acquirePublicationLedger(
		*ledgerHeadPath,
		policy.Environment(),
		previousReceiptSHA256,
		*chainGenesisReason,
	)
	if err != nil {
		return err
	}
	defer releaseLedgerLock()
	result, receipt, err := publication.FinalizeSigning(request, prepared, response, policy, publication.AuditMetadata{
		PublishedAt: publishedAt, PreviousReceiptSHA256: previousReceiptSHA256, ChainGenesisReason: *chainGenesisReason,
	})
	if err != nil {
		return err
	}
	receiptBytes, err := publication.EncodeAuditReceipt(receipt)
	if err != nil {
		return err
	}
	// Refuse both operations up front when either target exists. A failed second
	// create is then limited to an external race and never overwrites evidence.
	for _, path := range []string{*snapshotPath, *receiptPath} {
		if _, err := os.Lstat(path); err == nil {
			return fmt.Errorf("output %q already exists", path)
		} else if !errors.Is(err, os.ErrNotExist) {
			return fmt.Errorf("inspect output %q: %w", path, err)
		}
	}
	// Persist audit evidence first. A crash can leave a harmless orphan receipt,
	// never an apparently publishable snapshot without its receipt.
	if err := writeExclusive(*receiptPath, receiptBytes); err != nil {
		return err
	}
	if err := writeExclusive(*snapshotPath, result.JSON); err != nil {
		return fmt.Errorf("receipt created but snapshot creation failed; quarantine receipt %q: %w", *receiptPath, err)
	}
	if err := commitLedgerHead(receipt.ReceiptSHA256); err != nil {
		return fmt.Errorf("snapshot and receipt created but ledger head update failed; quarantine both outputs: %w", err)
	}
	return nil
}

func acquirePublicationLedger(path, environment, previousReceiptSHA256, genesisReason string) (func(string) error, func(), error) {
	if filepath.Base(path) == "." || environment == "" {
		return nil, nil, errors.New("publication ledger path/environment is invalid")
	}
	lockPath := path + ".lock"
	if err := os.Mkdir(lockPath, 0o700); err != nil {
		return nil, nil, fmt.Errorf("acquire publication ledger lock %q: %w", lockPath, err)
	}
	released := false
	release := func() {
		if !released {
			released = true
			_ = os.Remove(lockPath)
		}
	}
	fail := func(err error) (func(string) error, func(), error) {
		release()
		return nil, nil, err
	}
	if genesisReason != "" {
		if _, err := os.Lstat(path); err == nil {
			return fail(errors.New("publication ledger already has a genesis/head"))
		} else if !errors.Is(err, os.ErrNotExist) {
			return fail(fmt.Errorf("inspect publication ledger head: %w", err))
		}
	} else {
		var head publicationLedgerHead
		if err := readStrictJSON(path, &head); err != nil {
			return fail(fmt.Errorf("read publication ledger head: %w", err))
		}
		if head.SchemaVersion != "1.0" || head.Environment != environment || head.ReceiptSHA256 != previousReceiptSHA256 {
			return fail(errors.New("publication ledger head does not match the authenticated direct predecessor"))
		}
	}
	commit := func(receiptSHA256 string) error {
		head := publicationLedgerHead{SchemaVersion: "1.0", Environment: environment, ReceiptSHA256: receiptSHA256}
		data, err := json.MarshalIndent(head, "", "  ")
		if err != nil {
			return fmt.Errorf("encode publication ledger head: %w", err)
		}
		data = append(data, '\n')
		if genesisReason != "" {
			return writeExclusive(path, data)
		}
		temporaryPath := fmt.Sprintf("%s.next-%d", path, os.Getpid())
		if err := writeExclusive(temporaryPath, data); err != nil {
			return err
		}
		if err := os.Rename(temporaryPath, path); err != nil {
			_ = os.Remove(temporaryPath)
			return fmt.Errorf("replace publication ledger head: %w", err)
		}
		return syncParentDirectory(path)
	}
	return commit, release, nil
}

func readTrustPolicy(path string) (*trust.Policy, error) {
	data, err := readRegularFile(path)
	if err != nil {
		return nil, fmt.Errorf("read trust bundle: %w", err)
	}
	policy, err := trust.Decode(data)
	if err != nil {
		return nil, fmt.Errorf("decode trust bundle: %w", err)
	}
	return policy, nil
}

func readTrustPolicyTransition(path, previousPath, testPath, stagingPath string) (*trust.Policy, error) {
	policy, err := readTrustPolicy(path)
	if err != nil {
		return nil, err
	}
	if policy.Revision() == 1 {
		if previousPath != "" {
			return nil, errors.New("genesis trust bundle must not have a predecessor")
		}
		return validateProductionEnvironmentSeparation(policy, testPath, stagingPath)
	}
	if previousPath == "" {
		return nil, errors.New("non-genesis trust bundle requires -previous-trust-bundle")
	}
	previous, err := readTrustPolicy(previousPath)
	if err != nil {
		return nil, err
	}
	if err := trust.ValidateTransition(previous, policy); err != nil {
		return nil, fmt.Errorf("validate trust bundle transition: %w", err)
	}
	return validateProductionEnvironmentSeparation(policy, testPath, stagingPath)
}

func validateProductionEnvironmentSeparation(policy *trust.Policy, testPath, stagingPath string) (*trust.Policy, error) {
	if policy.Environment() != "production" {
		if testPath != "" || stagingPath != "" {
			return nil, errors.New("environment comparison bundles are production-only")
		}
		return policy, nil
	}
	if testPath == "" || stagingPath == "" {
		return nil, errors.New("production trust requires -test-trust-bundle and -staging-trust-bundle")
	}
	testPolicy, err := readTrustPolicy(testPath)
	if err != nil {
		return nil, err
	}
	stagingPolicy, err := readTrustPolicy(stagingPath)
	if err != nil {
		return nil, err
	}
	if err := trust.ValidateEnvironmentSeparation(testPolicy, stagingPolicy, policy); err != nil {
		return nil, fmt.Errorf("validate environment separation: %w", err)
	}
	return policy, nil
}

func readStrictJSON(path string, destination any) error {
	data, err := readRegularFile(path)
	if err != nil {
		return err
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return err
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(destination); err != nil {
		return err
	}
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		if err == nil {
			return errors.New("multiple JSON values")
		}
		return err
	}
	return nil
}

func readRegularFile(path string) ([]byte, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return nil, err
	}
	if !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 || info.Size() <= 0 || info.Size() > maxInputBytes {
		return nil, errors.New("input must be a bounded regular non-symlink file")
	}
	return os.ReadFile(path)
}

func writeExclusive(path string, data []byte) error {
	if len(data) == 0 || filepath.Base(path) == "." {
		return errors.New("output path/data is invalid")
	}
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o644)
	if err != nil {
		return fmt.Errorf("create output %q: %w", path, err)
	}
	written := false
	defer func() {
		_ = file.Close()
		if !written {
			_ = os.Remove(path)
		}
	}()
	if _, err := file.Write(data); err != nil {
		return fmt.Errorf("write output %q: %w", path, err)
	}
	if err := file.Sync(); err != nil {
		return fmt.Errorf("sync output %q: %w", path, err)
	}
	if err := file.Close(); err != nil {
		return fmt.Errorf("close output %q: %w", path, err)
	}
	if err := syncParentDirectory(path); err != nil {
		return err
	}
	written = true
	return nil
}

func syncParentDirectory(path string) error {
	directory, err := os.Open(filepath.Dir(path))
	if err != nil {
		return fmt.Errorf("open output directory %q for sync: %w", filepath.Dir(path), err)
	}
	if err := directory.Sync(); err != nil {
		_ = directory.Close()
		return fmt.Errorf("sync output directory %q: %w", filepath.Dir(path), err)
	}
	if err := directory.Close(); err != nil {
		return fmt.Errorf("close output directory %q: %w", filepath.Dir(path), err)
	}
	return nil
}
