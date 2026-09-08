//go:build ignore

// Offline research harness. It calls the production parser without retrieval,
// qualification, signing, publication or emitting substantial prayer rows.
package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/providers/dumrt"
)

type result struct {
	ParserVersion    string `json:"parser_version"`
	RawSHA256        string `json:"raw_sha256"`
	Status           string `json:"status"`
	Error            string `json:"error,omitempty"`
	Days             int    `json:"days"`
	From             string `json:"from,omitempty"`
	To               string `json:"to,omitempty"`
	NormalizedSHA256 string `json:"normalized_sha256,omitempty"`
	FieldsSHA256     string `json:"fields_sha256,omitempty"`
	AnnualStatus     string `json:"annual_status"`
	AnnualError      string `json:"annual_error,omitempty"`
}

func digest(raw []byte) string {
	value := sha256.Sum256(raw)
	return hex.EncodeToString(value[:])
}

func run() error {
	directory := flag.String("csv-dir", "", "explicit retained artifact directory outside Git")
	flag.Parse()
	if *directory == "" || flag.NArg() != 0 {
		return fmt.Errorf("explicit csv-dir and stdin filename list are required")
	}
	var filenames []string
	decoder := json.NewDecoder(io.LimitReader(os.Stdin, 16*1024))
	if err := decoder.Decode(&filenames); err != nil {
		return err
	}
	if len(filenames) < 1 || len(filenames) > 44 {
		return fmt.Errorf("expected one to 44 filenames")
	}
	results := make(map[string]result, len(filenames))
	for _, filename := range filenames {
		if filepath.Base(filename) != filename || !strings.HasPrefix(filename, "dumrt-") || !strings.HasSuffix(filename, ".csv") {
			return fmt.Errorf("unexpected retained CSV filename")
		}
		if _, exists := results[filename]; exists {
			return fmt.Errorf("duplicate retained CSV filename")
		}
		raw, err := os.ReadFile(filepath.Join(*directory, filename))
		if err != nil {
			return err
		}
		item := result{ParserVersion: dumrt.ParserVersion, RawSHA256: digest(raw), Status: "fail", AnnualStatus: "fail"}
		days, err := dumrt.ParseCSVRange(raw, domain.DateRange{From: "2026-09-01", To: "2026-09-30"})
		if err != nil {
			item.Error = err.Error()
		} else {
			item.Status, item.Days = "pass", len(days)
			item.From, item.To = days[0].Date, days[len(days)-1].Date
			normalized, err := json.Marshal(days)
			if err != nil {
				return err
			}
			item.NormalizedSHA256 = digest(normalized)
			fields := make([][]string, 0, len(days))
			for _, day := range days {
				fields = append(fields, []string{day.Date, day.Fajr, day.RecommendedFajr, day.Sunrise, day.Zenith, day.Dhuhr, day.Asr, day.Maghrib, day.Isha})
			}
			encoded, err := json.Marshal(fields)
			if err != nil {
				return err
			}
			item.FieldsSHA256 = digest(encoded)
		}
		if _, err := dumrt.ParseAnnualCSV(raw, 2026); err != nil {
			item.AnnualError = err.Error()
		} else {
			item.AnnualStatus = "pass"
		}
		results[filename] = item
	}
	return json.NewEncoder(os.Stdout).Encode(results)
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
