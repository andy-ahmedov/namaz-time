package publication

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

const (
	DhuhrAdhanFromOnset        = "dhuhr_onset"
	DhuhrAdhanFromCongregation = "dhuhr_congregation"
)

// MosquePrayerPolicy contains mosque-local decisions that must never be
// inferred from a regional source. Its fingerprint is bound by approval.
type MosquePrayerPolicy struct {
	SchemaVersion               string                 `json:"schema_version"`
	PolicyID                    string                 `json:"policy_id"`
	MosqueID                    string                 `json:"mosque_id"`
	ValidFrom                   string                 `json:"valid_from"`
	ValidTo                     string                 `json:"valid_to"`
	DhuhrAdhanSource            string                 `json:"dhuhr_adhan_source"`
	DhuhrReplacedByJumuahFriday bool                   `json:"dhuhr_replaced_by_jumuah_friday"`
	IqamahRules                 []domain.IqamahRule    `json:"iqamah_rules"`
	JumuahSessions              []domain.JumuahSession `json:"jumuah_sessions"`
	RamadanExceptions           string                 `json:"ramadan_exceptions"`
	HolidayExceptions           string                 `json:"holiday_exceptions"`
	CorrectionOwner             string                 `json:"correction_owner"`
}

func MosquePrayerPolicySHA256(policy MosquePrayerPolicy) (string, error) {
	if err := validateMosquePrayerPolicy(policy); err != nil {
		return "", err
	}
	data, err := json.Marshal(policy)
	if err != nil {
		return "", err
	}
	hash := sha256.Sum256(data)
	return hex.EncodeToString(hash[:]), nil
}

func validateMosquePrayerPolicyBinding(request PublishRequest) error {
	if request.MosquePrayerPolicy == nil {
		if request.Approval.PrayerPolicySHA256 != "" {
			return newError("publish snapshot", "prayer_policy_binding_mismatch", errors.New("approval binds a missing mosque prayer policy"))
		}
		return nil
	}
	policy := *request.MosquePrayerPolicy
	if policy.MosqueID != request.Candidate.Mosque.ID || policy.ValidFrom != request.Candidate.Coverage.From || policy.ValidTo != request.Candidate.Coverage.To {
		return newError("publish snapshot", "prayer_policy_invalid", errors.New("mosque prayer policy scope does not match candidate"))
	}
	hash, err := MosquePrayerPolicySHA256(policy)
	if err != nil {
		return newError("publish snapshot", "prayer_policy_invalid", err)
	}
	if request.Approval.PrayerPolicySHA256 != hash {
		return newError("publish snapshot", "prayer_policy_binding_mismatch", errors.New("approval does not bind the exact mosque prayer policy"))
	}
	return nil
}

func validateMosquePrayerPolicy(policy MosquePrayerPolicy) error {
	if policy.SchemaVersion != "1.0" || policy.PolicyID == "" || len(policy.PolicyID) > 128 || strings.TrimSpace(policy.PolicyID) != policy.PolicyID || policy.MosqueID == "" || policy.ValidFrom == "" || policy.ValidTo == "" {
		return errors.New("mosque prayer policy metadata is invalid")
	}
	from, fromErr := time.Parse(time.DateOnly, policy.ValidFrom)
	to, toErr := time.Parse(time.DateOnly, policy.ValidTo)
	if fromErr != nil || toErr != nil || to.Before(from) {
		return errors.New("mosque prayer policy coverage is invalid")
	}
	if policy.DhuhrAdhanSource != DhuhrAdhanFromOnset && policy.DhuhrAdhanSource != DhuhrAdhanFromCongregation {
		return errors.New("mosque prayer policy Dhuhr source is invalid")
	}
	if policy.RamadanExceptions != "" && policy.RamadanExceptions != "none" {
		return errors.New("unsupported Ramadan exception policy")
	}
	if policy.HolidayExceptions != "" && policy.HolidayExceptions != "none" {
		return errors.New("unsupported holiday exception policy")
	}
	if policy.CorrectionOwner != "" && policy.CorrectionOwner != "admin" {
		return errors.New("unsupported correction owner")
	}
	if policy.DhuhrReplacedByJumuahFriday != (len(policy.JumuahSessions) > 0) {
		return errors.New("friday Dhuhr replacement must match Jumuah sessions")
	}
	if len(policy.IqamahRules) > 512 || len(policy.JumuahSessions) > 32 {
		return errors.New("mosque prayer policy contains too many rules")
	}
	ids := make(map[string]struct{}, len(policy.IqamahRules)+len(policy.JumuahSessions))
	for _, rule := range policy.IqamahRules {
		if !validPolicyText(rule.ID, 128) || !validPrayer(rule.Prayer) || rule.Priority < 0 || rule.Priority > 100000 || len(rule.Weekdays) == 0 {
			return errors.New("mosque prayer policy iqamah rule metadata is invalid")
		}
		if _, exists := ids[rule.ID]; exists {
			return errors.New("mosque prayer policy child IDs must be unique")
		}
		ids[rule.ID] = struct{}{}
		ruleFrom, ruleFromErr := time.Parse(time.DateOnly, rule.ValidFrom)
		ruleTo, ruleToErr := time.Parse(time.DateOnly, rule.ValidTo)
		if ruleFromErr != nil || ruleToErr != nil || ruleTo.Before(ruleFrom) || ruleFrom.Before(from) || ruleTo.After(to) {
			return errors.New("mosque prayer policy iqamah rule range is invalid")
		}
		weekdays := map[int]struct{}{}
		for _, weekday := range rule.Weekdays {
			if weekday < 1 || weekday > 7 {
				return errors.New("mosque prayer policy iqamah weekday is invalid")
			}
			if _, exists := weekdays[weekday]; exists {
				return errors.New("mosque prayer policy iqamah weekdays must be unique")
			}
			weekdays[weekday] = struct{}{}
		}
		validValue := false
		switch rule.Value.Mode {
		case "offset_after_adhan":
			validValue = rule.Value.OffsetMinutes != nil &&
				*rule.Value.OffsetMinutes >= 0 && *rule.Value.OffsetMinutes <= 240 &&
				rule.Value.FixedTime == ""
		case "fixed_time":
			validValue = rule.Value.OffsetMinutes == nil && validClock(rule.Value.FixedTime)
		}
		if !validValue || utf8.RuneCountInString(rule.Reason) > 1000 {
			return errors.New("mosque prayer policy iqamah value is invalid")
		}
	}
	for _, session := range policy.JumuahSessions {
		if !validPolicyText(session.ID, 128) || !validPolicyText(session.Label, 120) || !validClock(session.SalahTime) || (session.KhutbahTime != "" && !validClock(session.KhutbahTime)) {
			return errors.New("mosque prayer policy Jumuah session is invalid")
		}
		if _, exists := ids[session.ID]; exists {
			return errors.New("mosque prayer policy child IDs must be unique")
		}
		ids[session.ID] = struct{}{}
		sessionFrom, sessionFromErr := time.Parse(time.DateOnly, session.ValidFrom)
		sessionTo, sessionToErr := time.Parse(time.DateOnly, session.ValidTo)
		if sessionFromErr != nil || sessionToErr != nil || sessionTo.Before(sessionFrom) || sessionFrom.Before(from) || sessionTo.After(to) {
			return errors.New("mosque prayer policy Jumuah range is invalid")
		}
	}
	return nil
}

func validPolicyText(value string, maximum int) bool {
	return value != "" && strings.TrimSpace(value) == value && utf8.RuneCountInString(value) <= maximum
}

func validPrayer(value string) bool {
	return value == "fajr" || value == "dhuhr" || value == "asr" || value == "maghrib" || value == "isha"
}

func validClock(value string) bool {
	parsed, err := time.Parse("15:04", value)
	return err == nil && parsed.Format("15:04") == value
}
