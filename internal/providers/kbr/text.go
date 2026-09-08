// Package kbr parses the explicitly scoped DUM KBR 2026 annual calendar.
// Retrieval, PDF text extraction, qualification and publication are separate.
package kbr

import (
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
)

const ParserVersion = "kbr-annual-pdf-text/v1"

const maxTextBytes = 1024 * 1024

// The unusual word spacing is the observed PyMuPDF 1.28.2 text extraction,
// not an inferred or repaired authority name. A changed extraction layout
// must be investigated and versioned rather than silently broadened.
const authorityHeader = "ДУХОВНОЕ УПРАВЛЕНИЕ МУСУЛЬМАН КАБАРДИ НО- БАЛК АРСКО Й РЕСПУБЛИКИ Сайт ДУМ КБР: www. dumkbr. ru"

const prayerHeader = "Фаджр Утренний Шурук Восход Зухр Обеденный Аср Предвечерний Магриб Вечерний Иша Ночной"

var months = [...]string{"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"}
var weekdays = [...]string{"Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"}

// ParseText consumes unedited UTF-8 text extracted from the full 12-page
// DUM KBR 2026 PDF. It validates every month header, row and repeated footer
// before returning the explicitly requested range within that same year.
// Whitespace wrapping is insignificant; unknown tokens and footnotes are not.
// Success is normalization only: the caller must independently bind the raw
// PDF/text hashes, first-party ownership, scope, timezone and qualification.
func ParseText(rawExtractedText []byte, coverage domain.DateRange) ([]domain.CandidatePrayerDay, error) {
	start, startErr := time.Parse(time.DateOnly, coverage.From)
	end, endErr := time.Parse(time.DateOnly, coverage.To)
	if startErr != nil || endErr != nil || start.Format(time.DateOnly) != coverage.From ||
		end.Format(time.DateOnly) != coverage.To || start.Year() != 2026 || end.Year() != 2026 || end.Before(start) {
		return nil, errors.New("kbr PDF text: coverage must be a canonical range within 2026")
	}
	if len(rawExtractedText) == 0 || len(rawExtractedText) > maxTextBytes || !utf8.Valid(rawExtractedText) {
		return nil, errors.New("kbr PDF text: expected bounded nonempty UTF-8 data")
	}
	tokens := strings.Fields(string(rawExtractedText))
	cursor := 0
	annual := make([]domain.CandidatePrayerDay, 0, 365)
	for index, month := range months {
		monthNumber := index + 1
		if !consume(tokens, &cursor, authorityHeader) ||
			!consume(tokens, &cursor, "ГРАФИК НАМАЗОВ НА "+strings.ToUpper(month)+" 2026 г. ПО КБР") ||
			!consume(tokens, &cursor, month+" "+prayerHeader) {
			return nil, fmt.Errorf("kbr PDF text month %02d: unexpected authority, scope or column header", monthNumber)
		}
		for date := time.Date(2026, time.Month(monthNumber), 1, 0, 0, 0, 0, time.UTC); int(date.Month()) == monthNumber; date = date.AddDate(0, 0, 1) {
			if len(tokens)-cursor < 8 || tokens[cursor] != strconv.Itoa(date.Day()) || tokens[cursor+1] != weekdays[date.Weekday()] {
				return nil, fmt.Errorf("kbr PDF text date %s: missing, duplicate, out-of-order day or wrong weekday", date.Format(time.DateOnly))
			}
			clocks := tokens[cursor+2 : cursor+8]
			for column, clock := range clocks {
				if !validClock(clock) {
					return nil, fmt.Errorf("kbr PDF text date %s column %d: invalid HH:mm or unknown marker", date.Format(time.DateOnly), column+1)
				}
				if column > 0 && clocks[column-1] >= clock {
					return nil, fmt.Errorf("kbr PDF text date %s: invalid prayer order or unsupported midnight crossing", date.Format(time.DateOnly))
				}
			}
			annual = append(annual, domain.CandidatePrayerDay{PrayerDay: domain.PrayerDay{
				Date: date.Format(time.DateOnly), Fajr: clocks[0], Sunrise: clocks[1],
				Dhuhr: clocks[2], Asr: clocks[3], Maghrib: clocks[4], Isha: clocks[5],
			}})
			cursor += 8
		}
		if !consume(tokens, &cursor, month+" "+prayerHeader) {
			return nil, fmt.Errorf("kbr PDF text month %02d: unexpected repeated footer, extra day or unknown note", monthNumber)
		}
	}
	if cursor != len(tokens) || len(annual) != 365 {
		return nil, errors.New("kbr PDF text: unexpected trailing content or incomplete annual coverage")
	}
	days := make([]domain.CandidatePrayerDay, 0, int(end.Sub(start).Hours()/24)+1)
	for _, day := range annual {
		if day.Date >= coverage.From && day.Date <= coverage.To {
			days = append(days, day)
		}
	}
	return days, nil
}

func consume(tokens []string, cursor *int, label string) bool {
	expected := strings.Fields(label)
	if len(tokens)-*cursor < len(expected) {
		return false
	}
	for offset, token := range expected {
		if tokens[*cursor+offset] != token {
			return false
		}
	}
	*cursor += len(expected)
	return true
}

func validClock(clock string) bool {
	if len(clock) != 5 || clock[2] != ':' {
		return false
	}
	for index, digit := range clock {
		if index != 2 && (digit < '0' || digit > '9') {
			return false
		}
	}
	return clock[:2] <= "23" && clock[3:] <= "59"
}
