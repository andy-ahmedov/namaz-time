package domain

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/canonicaljson"
)

func validateQualifiedSnapshot(result *ValidationErrors, snapshot Snapshot) {
	add := func(code, message string) {
		result.Items = append(result.Items, ValidationError{"source.qualification", code, message})
	}
	q := snapshot.Source.Qualification
	if snapshot.SchemaVersion != "2.0" {
		if q != nil {
			add("qualification_requires_v2", "public qualification cannot be interpreted as legacy approval")
		}
		return
	}
	if q == nil {
		add("qualification_required", "v2 snapshots require public first-party qualification")
		return
	}
	if snapshot.Source.Approval != (Approval{}) {
		add("conflicting_admission", "public qualification and legacy approval are separate branches")
	}
	if len(snapshot.IqamahRules) != 0 || len(snapshot.IqamahOverrides) != 0 || len(snapshot.JumuahSessions) != 0 {
		add("local_prayer_policy_not_qualified", "public onset data does not establish mosque iqamah or Jumuah")
	}
	scopeHash := sha256.Sum256([]byte(q.Scope.ID))
	if snapshot.Mosque.ID != "public-scope-"+hex.EncodeToString(scopeHash[:16]) || snapshot.Mosque.CountryCode != "RU" || snapshot.Mosque.Timezone != q.Timezone ||
		snapshot.Source.SourceID != q.SourceID || snapshot.Source.Kind != q.Kind || snapshot.Source.CanonicalURL != q.CanonicalURL ||
		snapshot.Source.AuthorityName != q.Authority.Name || snapshot.Source.AuthorityBranch != q.Authority.Branch || snapshot.Source.GeographicScope != q.Scope.Description ||
		snapshot.Source.RawSHA256 != q.Artifact.SHA256 || snapshot.Source.RetrievedAt != q.Artifact.CapturedAt || snapshot.Source.ParserVersion != q.ParserVersion ||
		snapshot.Coverage != q.Coverage || snapshot.Source.EffectiveFrom != q.Coverage.From || snapshot.Source.EffectiveTo != q.Coverage.To || snapshot.Source.CalculationProfile != "" {
		add("qualification_binding_mismatch", "source, scope, artifact, timezone or coverage differs from qualified evidence")
	}
	if q.TermsAssessment == "public_transport_attribution_required" && snapshot.Source.Attribution == "" {
		add("qualification_attribution_missing", "qualified source requires attribution")
	}
	generatedAt, generatedOK := qualificationTimestamp(snapshot.GeneratedAt)
	qualifiedAt, qualifiedOK := qualificationTimestamp(q.QualifiedAt)
	location, zoneErr := time.LoadLocation(q.Timezone)
	if !generatedOK || !qualifiedOK || zoneErr != nil || generatedAt.Before(qualifiedAt) || generatedAt.In(location).Format(time.DateOnly) > q.FreshThrough {
		add("qualification_not_current", "snapshot generation must follow qualification and precede qualified freshness expiry")
	}
	fingerprint, err := PrayerDaysSHA256(snapshot.PrayerDays)
	if err != nil || fingerprint != q.OnsetSHA256 || len(snapshot.PrayerDays) != q.ValidatedDays {
		add("qualified_onset_mismatch", "all materialized onset rows must match the qualified fingerprint")
	}
	daysByDate := make(map[string]PrayerDay, len(snapshot.PrayerDays))
	for _, day := range snapshot.PrayerDays {
		daysByDate[day.Date] = day
		previous := ""
		for _, value := range []string{day.Fajr, day.Sunrise, day.Dhuhr, day.Asr, day.Maghrib, day.Isha} {
			if !localTimePattern.MatchString(value) || (previous != "" && value <= previous) {
				add("qualified_onset_order", "public onset rows must have canonical same-day order")
				return
			}
			previous = value
		}
	}
	for _, comparison := range q.Comparisons {
		day, exists := daysByDate[comparison.Day.Date]
		if !exists || day.Fajr != comparison.Day.Fajr || day.Sunrise != comparison.Day.Sunrise || day.Dhuhr != comparison.Day.Dhuhr ||
			day.Asr != comparison.Day.Asr || day.Maghrib != comparison.Day.Maghrib || day.Isha != comparison.Day.Isha {
			add("qualified_comparison_mismatch", "independently read source comparisons must match materialized onset rows")
			break
		}
	}
}

// Wire checks avoid silently dropping present empty/default fields before
// qualification hashing. The fingerprint covers the exact canonical JSON
// object/array, not just a lossy decode into Go value fields.
func validateQualifiedWireShape(root map[string]any) error {
	source, sourceOK := root["source"].(map[string]any)
	if !sourceOK {
		return nil // Ordinary decoding/validation reports the missing source.
	}
	_, hasQualification := source["qualification"]
	_, hasApproval := source["approval"]
	if root["schema_version"] != "2.0" {
		if hasQualification {
			return fmt.Errorf("source.qualification requires snapshot schema_version 2.0")
		}
		return nil
	}
	q, proofOK := source["qualification"].(map[string]any)
	if !proofOK || hasApproval {
		return fmt.Errorf("v2 snapshot requires only the public qualification admission branch")
	}
	encoded, err := json.Marshal(q)
	if err != nil {
		return fmt.Errorf("encode qualification proof: %w", err)
	}
	canonical, err := canonicaljson.WithoutRootMembers(encoded, "qualification_id", "sha256")
	if err != nil {
		return err
	}
	hash := sha256.Sum256(canonical)
	if q["sha256"] != hex.EncodeToString(hash[:]) {
		return fmt.Errorf("source.qualification fingerprint does not match wire proof")
	}
	days, err := json.Marshal(root["prayer_days"])
	if err != nil {
		return err
	}
	canonicalDays, err := canonicaljson.Encode(days)
	if err != nil {
		return err
	}
	onsetHash := sha256.Sum256(canonicalDays)
	if q["onset_sha256"] != hex.EncodeToString(onsetHash[:]) {
		return fmt.Errorf("source.qualification onset fingerprint does not match wire rows")
	}
	return nil
}
