package manual

import (
	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/controlled"
)

func validateCandidate(config ParseConfig, candidate domain.CandidateSchedule) domain.CandidateValidationReport {
	return controlled.ValidateCandidate(controlled.ValidationConfig{
		SourceCountryCode: config.Source.GeographicScope.CountryCode,
		SourceTimezone:    config.Source.TimezonePolicy.IANATimezone,
		SourceStatus:      config.Source.Status,
	}, candidate)
}
