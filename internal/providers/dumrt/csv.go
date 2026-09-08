// Package dumrt normalizes the public DUM RT locality CSV format. Retrieval,
// source qualification and signed publication are separate responsibilities.
package dumrt

import (
	"encoding/csv"
	"errors"
	"fmt"
	"io"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

const ParserVersion = "dumrt-locality-csv/v1"

// ParseAnnualCSV selects an explicitly requested complete Gregorian year. The
// public transport currently contains the preceding and current years; its
// historical 2025 rows have an additional Qibla-direction time, not a prayer.
// Unknown columns, missing days and unrepresentable midnight crossings fail
// closed. No interpolation, calculation, iqamah or approval is introduced.
func ParseAnnualCSV(raw []byte, year int) ([]domain.CandidatePrayerDay, error) {
	if year < 2000 || year > 2100 {
		return nil, errors.New("dumrt CSV: year must be between 2000 and 2100")
	}
	return ParseCSVRange(raw, domain.DateRange{From: fmt.Sprintf("%04d-01-01", year), To: fmt.Sprintf("%04d-12-31", year)})
}

// ParseCSVRange validates every input row's schema, but publishes no values
// outside the explicitly requested range. Semantic validation applies to all
// selected days without omissions. A qualification must bind this exact range;
// success here is not evidence that the rest of an annual transport is usable.
func ParseCSVRange(raw []byte, coverage domain.DateRange) ([]domain.CandidatePrayerDay, error) {
	start, startErr := time.Parse(time.DateOnly, coverage.From)
	last, lastErr := time.Parse(time.DateOnly, coverage.To)
	if startErr != nil || lastErr != nil || start.Format(time.DateOnly) != coverage.From || last.Format(time.DateOnly) != coverage.To ||
		last.Before(start) || start.Year() != last.Year() || start.Year() < 2000 || start.Year() > 2100 {
		return nil, errors.New("dumrt CSV: explicit coverage must be a canonical range within one supported year")
	}
	year := start.Year()
	if len(raw) == 0 || len(raw) > 1024*1024 || !utf8.Valid(raw) {
		return nil, errors.New("dumrt CSV: expected bounded nonempty UTF-8 data")
	}
	text := strings.TrimSuffix(strings.ReplaceAll(string(raw), "\r\n", "\n"), "\n")
	if strings.Contains(text, "\r") || strings.Contains(text, "\n\n") || strings.HasPrefix(text, "\n") || strings.HasSuffix(text, "\n") {
		return nil, errors.New("dumrt CSV: unexpected blank row or line ending")
	}
	reader := csv.NewReader(strings.NewReader(text))
	reader.Comma = ';'
	reader.FieldsPerRecord = -1
	end := last.AddDate(0, 0, 1)
	next := start
	var previous time.Time
	days := make([]domain.CandidatePrayerDay, 0, 366)
	for row := 1; ; row++ {
		record, err := reader.Read()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil || row > 732 || len(record) < 9 || len(record) > 10 {
			return nil, fmt.Errorf("dumrt CSV row %d: unsupported row schema", row)
		}
		date, err := time.Parse("02.01.2006", record[0])
		if err != nil || date.Format("02.01.2006") != record[0] || date.Year() < year-1 || date.Year() > year {
			return nil, fmt.Errorf("dumrt CSV row %d: invalid or unexpected date", row)
		}
		if !date.After(previous) {
			return nil, fmt.Errorf("dumrt CSV row %d: duplicate or out-of-order date", row)
		}
		previous = date
		if len(record) == 10 && date.Year() != 2025 {
			return nil, fmt.Errorf("dumrt CSV row %d: unknown extra column outside historical 2025 format", row)
		}
		clocks := make([]string, len(record)-1)
		for column := 1; column < len(record); column++ {
			clock, err := normalizeClock(record[column])
			if err != nil {
				return nil, fmt.Errorf("dumrt CSV row %d column %d: invalid clock or unknown marker", row, column+1)
			}
			clocks[column-1] = clock
		}
		if date.Before(start) || date.After(last) {
			continue
		}
		day := domain.CandidatePrayerDay{
			PrayerDay: domain.PrayerDay{
				Date: date.Format("2006-01-02"), Fajr: clocks[0], Sunrise: clocks[2],
				Dhuhr: clocks[4], Asr: clocks[5], Maghrib: clocks[6], Isha: clocks[7],
			},
			RecommendedFajr: clocks[1], Zenith: clocks[3],
		}
		if !(day.Fajr < day.Sunrise && day.Sunrise < day.Zenith && day.Zenith <= day.Dhuhr &&
			day.Dhuhr < day.Asr && day.Asr < day.Maghrib && day.Maghrib < day.Isha &&
			day.Fajr <= day.RecommendedFajr && day.RecommendedFajr < day.Sunrise) {
			return nil, fmt.Errorf("dumrt CSV row %d: invalid time order or unsupported next-day time", row)
		}
		if !date.Equal(next) {
			return nil, fmt.Errorf("dumrt CSV row %d: gap in requested coverage", row)
		}
		days = append(days, day)
		next = next.AddDate(0, 0, 1)
	}
	if !next.Equal(end) {
		return nil, errors.New("dumrt CSV: requested range is not fully covered")
	}
	return days, nil
}

func normalizeClock(value string) (string, error) {
	if (len(value) != 4 && len(value) != 5) || value[len(value)-3] != ':' {
		return "", errors.New("invalid time shape")
	}
	for index, char := range value {
		if index != len(value)-3 && (char < '0' || char > '9') {
			return "", errors.New("invalid time digit")
		}
	}
	hour, hourErr := strconv.Atoi(value[:len(value)-3])
	minute, minuteErr := strconv.Atoi(value[len(value)-2:])
	if hourErr != nil || minuteErr != nil || hour > 23 || minute > 59 {
		return "", errors.New("invalid time range")
	}
	return fmt.Sprintf("%02d:%02d", hour, minute), nil
}
