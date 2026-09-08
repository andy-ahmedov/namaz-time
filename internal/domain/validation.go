package domain

import (
	"crypto/ed25519"
	"encoding/base64"
	"fmt"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"
	_ "time/tzdata"
)

var (
	localDatePattern = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}$`)
	localTimePattern = regexp.MustCompile(`^(?:[01]\d|2[0-3]):[0-5]\d$`)
	sha256Pattern    = regexp.MustCompile(`^[a-f0-9]{64}$`)
	countryPattern   = regexp.MustCompile(`^[A-Z]{2}$`)
	rfc3339Pattern   = regexp.MustCompile(`^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(?:([Zz])|([+-])(\d{2}):(\d{2}))$`)
)

const localDateLayout = "2006-01-02"

const (
	maxSnapshotPrayerDays      = 400
	maxSnapshotIqamahRules     = 512
	maxSnapshotIqamahOverrides = 2000
	maxSnapshotJumuahSessions  = 64
	maxSnapshotCampaigns       = 128
	maxSnapshotPrayerDayFlags  = 32
)

// ValidationError is a stable machine-readable domain validation failure.
type ValidationError struct {
	Path    string
	Code    string
	Message string
}

func (e ValidationError) Error() string {
	return fmt.Sprintf("%s: %s: %s", e.Path, e.Code, e.Message)
}

// ValidationErrors preserves deterministic validation order.
type ValidationErrors struct {
	Items []ValidationError
}

func (e *ValidationErrors) Error() string {
	parts := make([]string, 0, len(e.Items))
	for _, item := range e.Items {
		parts = append(parts, item.Error())
	}
	return strings.Join(parts, "; ")
}

// Validate checks the rules that JSON Schema cannot express, as well as the
// provenance and integrity fields required before a snapshot can be staged.
func (s Snapshot) Validate() error {
	var result ValidationErrors
	add := func(path, code, message string) {
		result.Items = append(result.Items, ValidationError{Path: path, Code: code, Message: message})
	}

	validateRequired(&result, "schema_version", s.SchemaVersion)
	if s.SchemaVersion != "" && s.SchemaVersion != "1.0" && s.SchemaVersion != "2.0" {
		add("schema_version", "unsupported_value", "must be 1.0 or 2.0")
	}
	validateText(&result, "snapshot_id", s.SnapshotID, 8, 128)
	validateRequired(&result, "data_classification", string(s.DataClassification))
	if s.DataClassification != "" && s.DataClassification != "production" && s.DataClassification != "synthetic" {
		add("data_classification", "unsupported_value", "must be production or synthetic")
	}
	validateRFC3339(&result, "generated_at", s.GeneratedAt)

	validateText(&result, "mosque.id", s.Mosque.ID, 1, 128)
	validateText(&result, "mosque.name", s.Mosque.Name, 1, 240)
	if s.Mosque.CountryCode != "" && !countryPattern.MatchString(s.Mosque.CountryCode) {
		add("mosque.country_code", "invalid_country_code", "must be two uppercase ASCII letters")
	}
	validateMaxLength(&result, "mosque.region", s.Mosque.Region, 240)
	validateMaxLength(&result, "mosque.locality", s.Mosque.Locality, 240)
	if s.Mosque.Timezone == "" {
		add("mosque.timezone", "required", "must not be empty")
	} else if length := len([]rune(s.Mosque.Timezone)); length < 3 || length > 64 {
		add("mosque.timezone", "invalid_length", "must contain 3 through 64 Unicode code points")
	} else if s.Mosque.Timezone == "Local" {
		add("mosque.timezone", "invalid_timezone", "must not depend on the runtime local timezone")
	} else if _, err := time.LoadLocation(s.Mosque.Timezone); err != nil {
		add("mosque.timezone", "invalid_timezone", "must be a loadable IANA timezone ID")
	}

	validateSource(&result, s.Source)
	validateQualifiedSnapshot(&result, s)
	coverageFrom, coverageFromOK := validateDate(&result, "coverage.from", s.Coverage.From)
	coverageTo, coverageToOK := validateDate(&result, "coverage.to", s.Coverage.To)
	if coverageFromOK && coverageToOK && coverageTo.Before(coverageFrom) {
		add("coverage", "invalid_range", "to must not be before from")
	}
	validateCoverageWithinSource(&result, s.Source, coverageFrom, coverageTo, coverageFromOK && coverageToOK)
	validatePrayerDays(&result, s.PrayerDays, coverageFrom, coverageTo, coverageFromOK && coverageToOK)
	validateIqamahRules(&result, s.IqamahRules)
	validateIqamahOverrides(&result, s.IqamahOverrides)
	validateIqamahApplications(&result, s)
	validateJumuahSessions(&result, s.JumuahSessions)
	validateCampaigns(&result, s.Campaigns)
	if s.Theme != nil {
		validateTheme(&result, *s.Theme)
	}
	validateIntegrity(&result, s.Integrity)

	if len(result.Items) > 0 {
		return &result
	}
	return nil
}

func validateIqamahRules(result *ValidationErrors, rules []IqamahRule) {
	if len(rules) > maxSnapshotIqamahRules {
		result.Items = append(result.Items, ValidationError{"iqamah_rules", "too_many_items", "must contain at most 512 rules"})
		return
	}
	for index, rule := range rules {
		path := fmt.Sprintf("iqamah_rules[%d]", index)
		validateText(result, path+".id", rule.ID, 1, 128)
		validatePrayer(result, path+".prayer", rule.Prayer)
		from, fromOK := validateDate(result, path+".valid_from", rule.ValidFrom)
		to, toOK := validateDate(result, path+".valid_to", rule.ValidTo)
		if fromOK && toOK && to.Before(from) {
			result.Items = append(result.Items, ValidationError{path, "invalid_range", "valid_to must not be before valid_from"})
		}
		if len(rule.Weekdays) == 0 {
			result.Items = append(result.Items, ValidationError{path + ".weekdays", "required", "must contain at least one weekday"})
		}
		seen := make(map[int]struct{}, len(rule.Weekdays))
		for _, weekday := range rule.Weekdays {
			if weekday < 1 || weekday > 7 {
				result.Items = append(result.Items, ValidationError{path + ".weekdays", "invalid_weekdays", "weekdays must be unique integers from 1 through 7"})
				break
			}
			if _, duplicate := seen[weekday]; duplicate {
				result.Items = append(result.Items, ValidationError{path + ".weekdays", "invalid_weekdays", "weekdays must be unique integers from 1 through 7"})
				break
			}
			seen[weekday] = struct{}{}
		}
		if rule.Priority < 0 || rule.Priority > 100000 {
			result.Items = append(result.Items, ValidationError{path + ".priority", "out_of_range", "must be from 0 through 100000"})
		}
		validateIqamahValue(result, path+".value", rule.Value)
		validateMaxLength(result, path+".reason", rule.Reason, 1000)
	}
}

func validateIqamahOverrides(result *ValidationErrors, overrides []IqamahOverride) {
	if len(overrides) > maxSnapshotIqamahOverrides {
		result.Items = append(result.Items, ValidationError{"iqamah_date_overrides", "too_many_items", "must contain at most 2000 overrides"})
		return
	}
	for index, override := range overrides {
		path := fmt.Sprintf("iqamah_date_overrides[%d]", index)
		validateDate(result, path+".date", override.Date)
		validatePrayer(result, path+".prayer", override.Prayer)
		validateIqamahValue(result, path+".value", override.Value)
		validateMaxLength(result, path+".reason", override.Reason, 1000)
	}
}

func validateIqamahValue(result *ValidationErrors, path string, value IqamahValue) {
	switch value.Mode {
	case "fixed_time":
		if value.OffsetMinutes != nil {
			result.Items = append(result.Items, ValidationError{path, "conflicting_value", "fixed_time cannot contain offset_minutes"})
		}
		validateTime(result, path+".fixed_time", value.FixedTime, true)
	case "offset_after_adhan":
		if value.FixedTime != "" {
			result.Items = append(result.Items, ValidationError{path, "conflicting_value", "offset mode cannot contain fixed_time"})
		}
		if value.OffsetMinutes == nil {
			result.Items = append(result.Items, ValidationError{path + ".offset_minutes", "required", "must be present"})
		} else if *value.OffsetMinutes < 0 || *value.OffsetMinutes > 240 {
			result.Items = append(result.Items, ValidationError{path + ".offset_minutes", "out_of_range", "must be from 0 through 240"})
		}
	default:
		result.Items = append(result.Items, ValidationError{path + ".mode", "unsupported_value", "must be fixed_time or offset_after_adhan"})
	}
}

// ValidateIqamahApplications materializes the effective iqamah value for every
// covered prayer day without requiring a signed integrity envelope. Publication
// uses this before invoking a signer; Snapshot.Validate repeats it when staging
// signed snapshots so producer and consumer fail closed on the same semantics.
func (s Snapshot) ValidateIqamahApplications() error {
	var result ValidationErrors
	validateIqamahApplications(&result, s)
	if len(result.Items) > 0 {
		return &result
	}
	return nil
}

func validateIqamahApplications(result *ValidationErrors, snapshot Snapshot) {
	seenOverrides := make(map[string]int, len(snapshot.IqamahOverrides))
	for index, override := range snapshot.IqamahOverrides {
		if override.Date == "" || override.Prayer == "" {
			continue
		}
		key := override.Date + "\x00" + override.Prayer
		if previous, duplicate := seenOverrides[key]; duplicate {
			result.Items = append(result.Items, ValidationError{
				Path:    fmt.Sprintf("iqamah_date_overrides[%d]", index),
				Code:    "duplicate_application",
				Message: fmt.Sprintf("duplicates iqamah_date_overrides[%d] for %s %s", previous, override.Date, override.Prayer),
			})
			continue
		}
		seenOverrides[key] = index
	}

	prayers := []string{"fajr", "dhuhr", "asr", "maghrib", "isha"}
	for dayIndex, day := range snapshot.PrayerDays {
		date, err := time.Parse(localDateLayout, day.Date)
		if err != nil || !localDatePattern.MatchString(day.Date) {
			continue
		}
		weekday := int(date.Weekday())
		if weekday == 0 {
			weekday = 7
		}
		for _, prayer := range prayers {
			adhan, ok := prayerClockMinutes(day, prayer)
			if !ok {
				continue
			}

			selectedValue := IqamahValue{}
			selectedPath := ""
			overrideCount := 0
			for overrideIndex, override := range snapshot.IqamahOverrides {
				if override.Date == day.Date && override.Prayer == prayer {
					overrideCount++
					selectedValue = override.Value
					selectedPath = fmt.Sprintf("iqamah_date_overrides[%d].value", overrideIndex)
				}
			}
			if overrideCount > 1 {
				continue
			}

			if overrideCount == 0 {
				highestPriority := -1
				winnerIndexes := make([]int, 0, 1)
				for ruleIndex, rule := range snapshot.IqamahRules {
					if rule.Prayer != prayer || !ruleMatchesDate(rule, date, weekday) {
						continue
					}
					if rule.Priority > highestPriority {
						highestPriority = rule.Priority
						winnerIndexes = []int{ruleIndex}
					} else if rule.Priority == highestPriority {
						winnerIndexes = append(winnerIndexes, ruleIndex)
					}
				}
				if len(winnerIndexes) > 1 {
					result.Items = append(result.Items, ValidationError{
						Path:    fmt.Sprintf("prayer_days[%d].%s", dayIndex, prayer),
						Code:    "ambiguous_iqamah_rule",
						Message: fmt.Sprintf("multiple highest-priority iqamah rules apply on %s", day.Date),
					})
					continue
				}
				if len(winnerIndexes) == 0 {
					continue
				}
				winnerIndex := winnerIndexes[0]
				selectedValue = snapshot.IqamahRules[winnerIndex].Value
				selectedPath = fmt.Sprintf("iqamah_rules[%d].value", winnerIndex)
			}

			validateMaterializedIqamahValue(
				result,
				selectedPath,
				day.Date,
				prayer,
				adhan,
				selectedValue,
			)
		}
	}
}

func ruleMatchesDate(rule IqamahRule, date time.Time, weekday int) bool {
	from, fromErr := time.Parse(localDateLayout, rule.ValidFrom)
	to, toErr := time.Parse(localDateLayout, rule.ValidTo)
	if fromErr != nil || toErr != nil || date.Before(from) || date.After(to) {
		return false
	}
	for _, candidate := range rule.Weekdays {
		if candidate == weekday {
			return true
		}
	}
	return false
}

func prayerClockMinutes(day PrayerDay, prayer string) (int, bool) {
	value := ""
	switch prayer {
	case "fajr":
		value = day.Fajr
	case "dhuhr":
		value = day.Dhuhr
	case "asr":
		value = day.Asr
	case "maghrib":
		value = day.Maghrib
	case "isha":
		value = day.Isha
	default:
		return 0, false
	}
	return clockMinutes(value)
}

func clockMinutes(value string) (int, bool) {
	if !localTimePattern.MatchString(value) {
		return 0, false
	}
	hour, hourErr := strconv.Atoi(value[:2])
	minute, minuteErr := strconv.Atoi(value[3:])
	if hourErr != nil || minuteErr != nil {
		return 0, false
	}
	return hour*60 + minute, true
}

func validateMaterializedIqamahValue(
	result *ValidationErrors,
	path string,
	date string,
	prayer string,
	adhan int,
	value IqamahValue,
) {
	switch value.Mode {
	case "fixed_time":
		fixed, ok := clockMinutes(value.FixedTime)
		if !ok || value.OffsetMinutes != nil {
			return
		}
		if fixed < adhan {
			result.Items = append(result.Items, ValidationError{
				Path:    path + ".fixed_time",
				Code:    "before_adhan",
				Message: fmt.Sprintf("effective iqamah for %s on %s must not be before adhan", prayer, date),
			})
		}
	case "offset_after_adhan":
		if value.OffsetMinutes == nil || value.FixedTime != "" || *value.OffsetMinutes < 0 || *value.OffsetMinutes > 240 {
			return
		}
		if adhan+(*value.OffsetMinutes) >= 24*60 {
			result.Items = append(result.Items, ValidationError{
				Path:    path + ".offset_minutes",
				Code:    "crosses_local_date",
				Message: fmt.Sprintf("effective iqamah for %s on %s must remain on the same local date", prayer, date),
			})
		}
	}
}

func validateJumuahSessions(result *ValidationErrors, sessions []JumuahSession) {
	if len(sessions) > maxSnapshotJumuahSessions {
		result.Items = append(result.Items, ValidationError{"jumuah_sessions", "too_many_items", "must contain at most 64 sessions"})
		return
	}
	for index, session := range sessions {
		path := fmt.Sprintf("jumuah_sessions[%d]", index)
		validateText(result, path+".id", session.ID, 1, 128)
		validateText(result, path+".label", session.Label, 1, 120)
		validateTime(result, path+".khutbah_time", session.KhutbahTime, false)
		validateTime(result, path+".salah_time", session.SalahTime, true)
		from, fromOK := validateDate(result, path+".valid_from", session.ValidFrom)
		to, toOK := validateDate(result, path+".valid_to", session.ValidTo)
		if fromOK && toOK && to.Before(from) {
			result.Items = append(result.Items, ValidationError{path, "invalid_range", "valid_to must not be before valid_from"})
		}
	}
}

func validateCampaigns(result *ValidationErrors, campaigns []Campaign) {
	if len(campaigns) > maxSnapshotCampaigns {
		result.Items = append(result.Items, ValidationError{"campaigns", "too_many_items", "must contain at most 128 campaigns"})
		return
	}
	for index, campaign := range campaigns {
		path := fmt.Sprintf("campaigns[%d]", index)
		validateText(result, path+".id", campaign.ID, 1, 128)
		if !stringInSet(campaign.Kind, "donation", "website", "telegram", "schedule", "contacts", "custom") {
			result.Items = append(result.Items, ValidationError{path + ".kind", "unsupported_value", "unsupported campaign kind"})
		}
		parsedURL, err := url.ParseRequestURI(campaign.URL)
		if err != nil || parsedURL.Scheme != "https" || parsedURL.Host == "" || parsedURL.User != nil || len([]rune(campaign.URL)) > 2048 {
			result.Items = append(result.Items, ValidationError{path + ".url", "invalid_https_url", "must be an absolute HTTPS URL"})
		}
		validateText(result, path+".title", campaign.Title, 1, 160)
		validateMaxLength(result, path+".subtitle", campaign.Subtitle, 500)
		validateRFC3339(result, path+".starts_at", campaign.StartsAt)
		validateRFC3339(result, path+".ends_at", campaign.EndsAt)
		startsAt, startsOK := parseRFC3339DateTime(campaign.StartsAt)
		endsAt, endsOK := parseRFC3339DateTime(campaign.EndsAt)
		if startsOK && endsOK && endsAt.compare(startsAt) <= 0 {
			result.Items = append(result.Items, ValidationError{path, "invalid_range", "ends_at must be after starts_at"})
		}
		if !stringInSet(campaign.Placement, "always", "with_prayer_times", "rotation") {
			result.Items = append(result.Items, ValidationError{path + ".placement", "unsupported_value", "unsupported campaign placement"})
		}
	}
}

func validateTheme(result *ValidationErrors, theme Theme) {
	validateText(result, "theme.theme_id", theme.ThemeID, 1, 128)
	if theme.OverlayOpacity < 0 || theme.OverlayOpacity > 1 {
		result.Items = append(result.Items, ValidationError{"theme.overlay_opacity", "out_of_range", "must be from 0 through 1"})
	}
	if theme.LandscapeAsset != nil {
		validateAsset(result, "theme.landscape_asset", *theme.LandscapeAsset)
	}
	if theme.PortraitAsset != nil {
		validateAsset(result, "theme.portrait_asset", *theme.PortraitAsset)
	}
}

func validateAsset(result *ValidationErrors, path string, asset AssetReference) {
	validateText(result, path+".asset_id", asset.AssetID, 1, 128)
	validateSHA256(result, path+".sha256", asset.SHA256)
	if !stringInSet(asset.MediaType, "image/jpeg", "image/png", "image/webp") {
		result.Items = append(result.Items, ValidationError{path + ".media_type", "unsupported_value", "unsupported image media type"})
	}
	if asset.ByteLength < 1 || asset.ByteLength > 20971520 {
		result.Items = append(result.Items, ValidationError{path + ".byte_length", "out_of_range", "must be from 1 through 20971520"})
	}
	if asset.Width < 320 || asset.Width > 7680 {
		result.Items = append(result.Items, ValidationError{path + ".width", "out_of_range", "must be from 320 through 7680"})
	}
	if asset.Height < 180 || asset.Height > 7680 {
		result.Items = append(result.Items, ValidationError{path + ".height", "out_of_range", "must be from 180 through 7680"})
	}
}

func validatePrayer(result *ValidationErrors, path, prayer string) {
	if !stringInSet(prayer, "fajr", "dhuhr", "asr", "maghrib", "isha") {
		result.Items = append(result.Items, ValidationError{path, "unsupported_value", "must be an allowed prayer"})
	}
}

func validateText(result *ValidationErrors, path, value string, minimum, maximum int) {
	validateRequired(result, path, value)
	length := len([]rune(value))
	if length < minimum || length > maximum {
		result.Items = append(result.Items, ValidationError{path, "invalid_length", fmt.Sprintf("must contain %d through %d Unicode code points", minimum, maximum)})
	}
}

func validateMaxLength(result *ValidationErrors, path, value string, maximum int) {
	if len([]rune(value)) > maximum {
		result.Items = append(result.Items, ValidationError{path, "invalid_length", fmt.Sprintf("must contain at most %d Unicode code points", maximum)})
	}
}

func stringInSet(value string, allowed ...string) bool {
	for _, candidate := range allowed {
		if value == candidate {
			return true
		}
	}
	return false
}

func validateCoverageWithinSource(result *ValidationErrors, source SourceMetadata, coverageFrom, coverageTo time.Time, coverageOK bool) {
	if !coverageOK {
		return
	}
	sourceFrom, fromErr := time.Parse(localDateLayout, source.EffectiveFrom)
	sourceTo, toErr := time.Parse(localDateLayout, source.EffectiveTo)
	if fromErr != nil || toErr != nil {
		return
	}
	if coverageFrom.Before(sourceFrom) || coverageTo.After(sourceTo) {
		result.Items = append(result.Items, ValidationError{
			Path:    "coverage",
			Code:    "outside_source_effective_range",
			Message: "must be contained by source effective range",
		})
	}
}

func validateSource(result *ValidationErrors, source SourceMetadata) {
	validateText(result, "source.source_id", source.SourceID, 1, 128)
	if source.Kind == "" {
		result.Items = append(result.Items, ValidationError{"source.kind", "required", "must not be empty"})
	} else if !source.Kind.valid() {
		result.Items = append(result.Items, ValidationError{"source.kind", "unsupported_value", "must be an allowed provider kind"})
	}
	if source.Kind == ProviderKindCalculationProfile && strings.TrimSpace(source.CalculationProfile) == "" {
		result.Items = append(result.Items, ValidationError{"source.calculation_profile", "required_for_kind", "must identify the frozen calculation profile"})
	}
	validateText(result, "source.authority_name", source.AuthorityName, 1, 240)
	validateMaxLength(result, "source.authority_branch", source.AuthorityBranch, 240)
	validateText(result, "source.geographic_scope", source.GeographicScope, 1, 1000)
	if source.CanonicalURL != "" {
		parsed, err := url.ParseRequestURI(source.CanonicalURL)
		if err != nil || parsed.Scheme == "" {
			result.Items = append(result.Items, ValidationError{"source.canonical_url", "invalid_uri", "must be an absolute URI"})
		}
	}
	validateRFC3339(result, "source.retrieved_at", source.RetrievedAt)
	sourceFrom, sourceFromOK := validateDate(result, "source.effective_from", source.EffectiveFrom)
	sourceTo, sourceToOK := validateDate(result, "source.effective_to", source.EffectiveTo)
	if sourceFromOK && sourceToOK && sourceTo.Before(sourceFrom) {
		result.Items = append(result.Items, ValidationError{"source", "invalid_range", "effective_to must not be before effective_from"})
	}
	validateSHA256(result, "source.raw_sha256", source.RawSHA256)
	validateText(result, "source.parser_version", source.ParserVersion, 1, 128)
	if source.CalculationProfile != "" {
		validateText(result, "source.calculation_profile", source.CalculationProfile, 1, 240)
	}
	validateMaxLength(result, "source.license_reference", source.LicenseReference, 1000)
	validateMaxLength(result, "source.attribution", source.Attribution, 1000)
	if source.Qualification != nil {
		if err := source.Qualification.Validate(); err != nil {
			result.Items = append(result.Items, ValidationError{"source.qualification", "invalid_qualification", err.Error()})
		}
		return // The versioned qualification branch never invents an approval.
	}
	validateRequired(result, "source.approval.status", source.Approval.Status)
	if source.Approval.Status != "" && source.Approval.Status != "approved" {
		result.Items = append(result.Items, ValidationError{"source.approval.status", "unsupported_value", "must be approved"})
	}
	validateText(result, "source.approval.approval_id", source.Approval.ID, 1, 128)
	validateText(result, "source.approval.approved_by", source.Approval.ApprovedBy, 1, 240)
	validateRFC3339(result, "source.approval.approved_at", source.Approval.ApprovedAt)
	validateText(result, "source.approval.approval_scope", source.Approval.Scope, 1, 1000)
	validateMaxLength(result, "source.approval.note", source.Approval.Note, 2000)
}

func validatePrayerDays(result *ValidationErrors, days []PrayerDay, coverageFrom, coverageTo time.Time, coverageOK bool) {
	if len(days) == 0 {
		result.Items = append(result.Items, ValidationError{"prayer_days", "required", "must contain at least one day"})
		return
	}
	if len(days) > maxSnapshotPrayerDays {
		result.Items = append(result.Items, ValidationError{"prayer_days", "too_many_items", "must contain at most 400 days"})
		return
	}

	seen := make(map[string]struct{}, len(days))
	parsedDates := make([]time.Time, len(days))
	validDates := make([]bool, len(days))
	for index, day := range days {
		datePath := fmt.Sprintf("prayer_days[%d].date", index)
		parsedDates[index], validDates[index] = validateDate(result, datePath, day.Date)
		if _, duplicate := seen[day.Date]; duplicate && day.Date != "" {
			result.Items = append(result.Items, ValidationError{datePath, "duplicate_date", "date must be unique"})
		} else {
			seen[day.Date] = struct{}{}
		}
		validatePrayerTimes(result, index, day)
		if len(day.Flags) > maxSnapshotPrayerDayFlags {
			result.Items = append(result.Items, ValidationError{fmt.Sprintf("prayer_days[%d].flags", index), "too_many_items", "must contain at most 32 flags"})
			continue
		}
		seenFlags := make(map[string]struct{}, len(day.Flags))
		for flagIndex, flag := range day.Flags {
			path := fmt.Sprintf("prayer_days[%d].flags[%d]", index, flagIndex)
			validateMaxLength(result, path, flag, 128)
			if _, duplicate := seenFlags[flag]; duplicate {
				result.Items = append(result.Items, ValidationError{fmt.Sprintf("prayer_days[%d].flags", index), "duplicate_value", "flags must be unique"})
				break
			}
			seenFlags[flag] = struct{}{}
		}
	}

	if coverageOK {
		for index := range days {
			if !validDates[index] {
				continue
			}
			expected := coverageFrom.AddDate(0, 0, index)
			if !parsedDates[index].Equal(expected) {
				result.Items = append(result.Items, ValidationError{
					Path:    fmt.Sprintf("prayer_days[%d].date", index),
					Code:    "date_gap",
					Message: fmt.Sprintf("must be %s for continuous coverage", expected.Format(localDateLayout)),
				})
			}
		}
		lastIndex := len(days) - 1
		if validDates[lastIndex] && !parsedDates[lastIndex].Equal(coverageTo) {
			result.Items = append(result.Items, ValidationError{"prayer_days", "coverage_mismatch", "must contain exactly one row for every coverage date"})
		}
	}
}

func validatePrayerTimes(result *ValidationErrors, index int, day PrayerDay) {
	required := []struct {
		name  string
		value string
	}{
		{"fajr", day.Fajr},
		{"sunrise", day.Sunrise},
		{"dhuhr", day.Dhuhr},
		{"asr", day.Asr},
		{"maghrib", day.Maghrib},
		{"isha", day.Isha},
	}
	for _, prayer := range required {
		validateTime(result, fmt.Sprintf("prayer_days[%d].%s", index, prayer.name), prayer.value, true)
	}
	optional := []struct {
		name  string
		value string
	}{
		{"duha", day.Duha},
		{"middle_of_night", day.MiddleOfNight},
		{"last_third_of_night", day.LastThirdOfNight},
	}
	for _, prayer := range optional {
		validateTime(result, fmt.Sprintf("prayer_days[%d].%s", index, prayer.name), prayer.value, false)
	}
}

func validateIntegrity(result *ValidationErrors, integrity IntegrityMetadata) {
	validateSHA256(result, "integrity.canonical_sha256", integrity.CanonicalSHA256)
	validateText(result, "integrity.signing_key_id", integrity.SigningKeyID, 1, 128)
	if integrity.SignatureEd25519Base64 == "" {
		result.Items = append(result.Items, ValidationError{"integrity.signature_ed25519_base64", "required", "must not be empty"})
		return
	}
	signature, err := base64.StdEncoding.DecodeString(integrity.SignatureEd25519Base64)
	if err != nil || len(signature) != ed25519.SignatureSize {
		result.Items = append(result.Items, ValidationError{"integrity.signature_ed25519_base64", "invalid_signature_encoding", "must encode a 64-byte Ed25519 signature"})
	}
}

func validateRequired(result *ValidationErrors, path, value string) {
	if strings.TrimSpace(value) == "" {
		result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
	}
}

func validateRFC3339(result *ValidationErrors, path, value string) {
	if strings.TrimSpace(value) == "" {
		result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
		return
	}
	if !isRFC3339DateTime(value) {
		result.Items = append(result.Items, ValidationError{path, "invalid_datetime", "must be RFC 3339"})
	}
}

func isRFC3339DateTime(value string) bool {
	_, ok := parseRFC3339DateTime(value)
	return ok
}

type parsedRFC3339 struct {
	epochSecond      int64
	leapSecond       bool
	fractionalDigits string
}

func (value parsedRFC3339) compare(other parsedRFC3339) int {
	if value.epochSecond < other.epochSecond {
		return -1
	}
	if value.epochSecond > other.epochSecond {
		return 1
	}
	if value.leapSecond != other.leapSecond {
		if value.leapSecond {
			return 1
		}
		return -1
	}
	width := len(value.fractionalDigits)
	if len(other.fractionalDigits) > width {
		width = len(other.fractionalDigits)
	}
	left := value.fractionalDigits + strings.Repeat("0", width-len(value.fractionalDigits))
	right := other.fractionalDigits + strings.Repeat("0", width-len(other.fractionalDigits))
	return strings.Compare(left, right)
}

func parseRFC3339DateTime(value string) (parsedRFC3339, bool) {
	match := rfc3339Pattern.FindStringSubmatch(value)
	if match == nil {
		return parsedRFC3339{}, false
	}
	component := func(index int) int {
		parsed, _ := strconv.Atoi(match[index])
		return parsed
	}
	year, month, day := component(1), component(2), component(3)
	hour, minute, second := component(4), component(5), component(6)
	if hour > 23 || minute > 59 || second > 60 {
		return parsedRFC3339{}, false
	}
	offsetHours, offsetMinutes := component(10), component(11)
	if offsetHours > 23 || offsetMinutes > 59 {
		return parsedRFC3339{}, false
	}
	normalizedSecond := second
	if normalizedSecond == 60 {
		normalizedSecond = 59
	}
	local := time.Date(year, time.Month(month), day, hour, minute, normalizedSecond, 0, time.UTC)
	if local.Year() != year || int(local.Month()) != month || local.Day() != day ||
		local.Hour() != hour || local.Minute() != minute || local.Second() != normalizedSecond {
		return parsedRFC3339{}, false
	}
	offsetSeconds := int64(offsetHours*3600 + offsetMinutes*60)
	if match[9] == "-" {
		offsetSeconds = -offsetSeconds
	}
	baseEpochSecond := local.Unix() - offsetSeconds
	if second == 60 {
		utc := time.Unix(baseEpochSecond, 0).UTC()
		if utc.Hour() != 23 || utc.Minute() != 59 {
			return parsedRFC3339{}, false
		}
	}
	return parsedRFC3339{
		epochSecond:      baseEpochSecond,
		leapSecond:       second == 60,
		fractionalDigits: strings.TrimRight(match[7], "0"),
	}, true
}

func validateDate(result *ValidationErrors, path, value string) (time.Time, bool) {
	if strings.TrimSpace(value) == "" {
		result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
		return time.Time{}, false
	}
	parsed, err := time.Parse(localDateLayout, value)
	if err != nil || !localDatePattern.MatchString(value) {
		result.Items = append(result.Items, ValidationError{path, "invalid_date", "must be YYYY-MM-DD"})
		return time.Time{}, false
	}
	return parsed, true
}

func validateTime(result *ValidationErrors, path, value string, required bool) {
	if value == "" {
		if required {
			result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
		}
		return
	}
	if !localTimePattern.MatchString(value) {
		result.Items = append(result.Items, ValidationError{path, "invalid_time", "must be HH:MM from 00:00 through 23:59"})
	}
}

func validateSHA256(result *ValidationErrors, path, value string) {
	if value == "" {
		result.Items = append(result.Items, ValidationError{path, "required", "must not be empty"})
		return
	}
	if !sha256Pattern.MatchString(value) {
		result.Items = append(result.Items, ValidationError{path, "invalid_sha256", "must be 64 lowercase hexadecimal characters"})
	}
}

func (kind ProviderKind) valid() bool {
	switch kind {
	case ProviderKindOfficialAPI,
		ProviderKindOfficialFile,
		ProviderKindOfficialHTML,
		ProviderKindMosqueCalendar,
		ProviderKindCalculationProfile,
		ProviderKindManualImport:
		return true
	default:
		return false
	}
}
