package setupbundle

import (
	"bufio"
	"bytes"
	"context"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/geography"
)

const indexDDL = `PRAGMA page_size=4096;
PRAGMA encoding='UTF-8';
PRAGMA application_id=0x4e545342;
PRAGMA user_version=1;
PRAGMA journal_mode=DELETE;
PRAGMA synchronous=FULL;
PRAGMA foreign_keys=ON;
CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE regions(id TEXT PRIMARY KEY,federal_subject_code TEXT NOT NULL,name TEXT NOT NULL,country_code TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE cities(id TEXT PRIMARY KEY,name TEXT NOT NULL,region_id TEXT NOT NULL REFERENCES regions(id),settlement_type TEXT NOT NULL,timezone TEXT NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,geographic_source_id TEXT NOT NULL,geographic_revision TEXT NOT NULL,geographic_license TEXT NOT NULL,aliases_json TEXT NOT NULL,country_code TEXT NOT NULL,population INTEGER NOT NULL,geographic_source TEXT NOT NULL,source_modified_date TEXT NOT NULL,fallback_policy_id TEXT NOT NULL) WITHOUT ROWID;
CREATE INDEX cities_region_name ON cities(region_id,name,id);
CREATE TABLE search_names(normalized_name TEXT NOT NULL,city_id TEXT NOT NULL REFERENCES cities(id),PRIMARY KEY(normalized_name,city_id)) WITHOUT ROWID;
BEGIN;
`

// NormalizeSearch is the same exact-name/alias operation used by geography and
// registry. It deliberately does not fold ё, accents, NFC, or word prefixes.
func NormalizeSearch(value string) string {
	return strings.ToLower(strings.Join(strings.Fields(value), " "))
}

type indexData struct {
	info     CatalogInfo
	cities   []domain.City
	regions  []domain.Region
	names    []string // normalized name + NUL + canonical city ID (NUL input is forbidden).
	metadata map[string]string
}

func prepareIndex(catalog geography.Catalog) (indexData, error) {
	data := indexData{info: CatalogInfo{RevisionID: catalog.Revision.ID, ContentSHA256: catalog.Revision.ContentSHA256, RegionCount: len(catalog.Regions), CityCount: len(catalog.Cities), License: catalog.Source.License, LicenseURL: catalog.Source.LicenseURL, Attribution: catalog.Source.Attribution}, cities: append([]domain.City(nil), catalog.Cities...), regions: append([]domain.Region(nil), catalog.Regions...)}
	sort.Slice(data.cities, func(i, j int) bool { return data.cities[i].ID < data.cities[j].ID })
	sort.Slice(data.regions, func(i, j int) bool { return data.regions[i].ID < data.regions[j].ID })
	names := map[string]struct{}{}
	for _, city := range data.cities {
		if city.FallbackPolicyID != "" {
			return data, fmt.Errorf("catalog contains a forbidden fallback")
		}
		for _, value := range []string{city.ID, city.Name, city.RegionID, city.SettlementType, city.Timezone, city.GeographicSourceID, city.GeographicRevision, city.GeographicLicense, city.CountryCode, city.GeographicSource, city.SourceModifiedDate} {
			if !indexText(value) {
				return data, fmt.Errorf("catalog city text is invalid or exceeds index bounds")
			}
		}
		if NormalizeSearch(city.Name) == "" {
			return data, fmt.Errorf("catalog city has an empty searchable name")
		}
		names[NormalizeSearch(city.Name)+"\x00"+city.ID] = struct{}{}
		for _, alias := range city.Aliases {
			if !indexText(alias) {
				return data, fmt.Errorf("catalog alias is invalid or exceeds index bounds")
			}
			data.info.AliasCount++
			if normalized := NormalizeSearch(alias); normalized != "" {
				names[normalized+"\x00"+city.ID] = struct{}{}
			}
		}
		if data.info.AliasCount > 2_000_000 || len(names) > 3_000_000 {
			return data, fmt.Errorf("complete catalog index exceeds supported count bounds")
		}
	}
	for _, region := range data.regions {
		for _, value := range []string{region.ID, region.Name, region.CountryCode, region.FederalSubjectCode} {
			if !indexText(value) {
				return data, fmt.Errorf("catalog region text is invalid")
			}
		}
	}
	for name := range names {
		data.names = append(data.names, name)
	}
	sort.Strings(data.names)
	data.info.SearchNameCount = len(data.names)
	data.metadata = map[string]string{"schema_version": IndexSchema, "catalog_revision": catalog.Revision.ID, "content_sha256": catalog.Revision.ContentSHA256, "normalizer": NormalizerVersion, "region_count": strconv.Itoa(data.info.RegionCount), "city_count": strconv.Itoa(data.info.CityCount), "alias_count": strconv.Itoa(data.info.AliasCount), "search_name_count": strconv.Itoa(data.info.SearchNameCount)}
	return data, nil
}

func buildIndex(ctx context.Context, path, executable string, catalog geography.Catalog) (CatalogInfo, error) {
	data, err := prepareIndex(catalog)
	if err != nil {
		return CatalogInfo{}, err
	}
	// Streaming SQL avoids another full serialized copy of the large catalog.
	reader, writer := io.Pipe()
	done := make(chan error, 1)
	go func() { err := writeIndexSQL(writer, data); _ = writer.CloseWithError(err); done <- err }()
	err = runSQLite(ctx, executable, path, false, reader, io.Discard)
	_ = reader.CloseWithError(err)
	writeErr := <-done
	if err != nil {
		return CatalogInfo{}, err
	}
	if writeErr != nil {
		return CatalogInfo{}, writeErr
	}
	info, err := os.Stat(path)
	if err != nil {
		return CatalogInfo{}, err
	}
	if info.Size() < 1 || info.Size() > MaximumDatabaseBytes {
		return CatalogInfo{}, fmt.Errorf("complete SQLite index exceeds byte bounds")
	}
	if err := verifyIndex(ctx, executable, path, data); err != nil {
		return CatalogInfo{}, err
	}
	return data.info, nil
}

func writeIndexSQL(output io.Writer, data indexData) error {
	w := bufio.NewWriterSize(output, 64*1024)
	if _, err := io.WriteString(w, indexDDL); err != nil {
		return err
	}
	keys := make([]string, 0, len(data.metadata))
	for k := range data.metadata {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for _, k := range keys {
		if _, err := fmt.Fprintf(w, "INSERT INTO metadata VALUES(%s,%s);\n", sqlText(k), sqlText(data.metadata[k])); err != nil {
			return err
		}
	}
	for _, r := range data.regions {
		if _, err := fmt.Fprintf(w, "INSERT INTO regions VALUES(%s,%s,%s,%s);\n", sqlText(r.ID), sqlText(r.FederalSubjectCode), sqlText(r.Name), sqlText(r.CountryCode)); err != nil {
			return err
		}
	}
	for _, c := range data.cities {
		aliases := c.Aliases
		if aliases == nil {
			aliases = []string{}
		}
		raw, err := json.Marshal(aliases)
		if err != nil {
			return err
		}
		_, err = fmt.Fprintf(w, "INSERT INTO cities VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s);\n", sqlText(c.ID), sqlText(c.Name), sqlText(c.RegionID), sqlText(c.SettlementType), sqlText(c.Timezone), strconv.FormatFloat(c.Latitude, 'g', -1, 64), strconv.FormatFloat(c.Longitude, 'g', -1, 64), sqlText(c.GeographicSourceID), sqlText(c.GeographicRevision), sqlText(c.GeographicLicense), sqlText(string(raw)), sqlText(c.CountryCode), c.Population, sqlText(c.GeographicSource), sqlText(c.SourceModifiedDate), sqlText(c.FallbackPolicyID))
		if err != nil {
			return err
		}
	}
	for _, key := range data.names {
		name, id, _ := strings.Cut(key, "\x00")
		if _, err := fmt.Fprintf(w, "INSERT INTO search_names VALUES(%s,%s);\n", sqlText(name), sqlText(id)); err != nil {
			return err
		}
	}
	if _, err := io.WriteString(w, "COMMIT;\nVACUUM;\n"); err != nil {
		return err
	}
	return w.Flush()
}

// Hex UTF-8 literals cannot execute catalog text as SQL or sqlite dot commands.
func sqlText(s string) string { return "CAST(X'" + hex.EncodeToString([]byte(s)) + "' AS TEXT)" }
func indexText(s string) bool {
	return len(s) <= 4096 && utf8.ValidString(s) && !strings.ContainsRune(s, 0)
}

func runSQLite(ctx context.Context, executable, path string, readOnly bool, input io.Reader, output io.Writer) error {
	if executable == "" {
		executable = "sqlite3"
	}
	ctx, cancel := context.WithTimeout(ctx, 2*time.Minute)
	defer cancel()
	args := []string{"-batch", "-bail", "-init", "/dev/null", "-json"}
	if readOnly {
		args = append(args, "-readonly")
	}
	args = append(args, path)
	cmd := exec.CommandContext(ctx, executable, args...)
	cmd.Stdin, cmd.Stdout = input, output
	var stderr limitedBuffer
	stderr.remaining = 2048
	cmd.Stderr = &stderr
	cmd.WaitDelay = time.Second
	if err := cmd.Run(); err != nil {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		return fmt.Errorf("SQLite command failed: %w (%s)", err, strings.TrimSpace(stderr.String()))
	}
	return nil
}

type limitedBuffer struct {
	bytes.Buffer
	remaining int
}

func (b *limitedBuffer) Write(p []byte) (int, error) {
	n := len(p)
	if len(p) > b.remaining {
		p = p[:b.remaining]
	}
	b.remaining -= len(p)
	_, _ = b.Buffer.Write(p)
	return n, nil
}

// verifyIndex independently reads every stored city field, alias and indexed
// name back through SQLite; the full canonical catalog must survive the roundtrip.
func verifyIndex(ctx context.Context, executable, path string, data indexData) error {
	query := `SELECT (SELECT application_id FROM pragma_application_id) AS application_id,(SELECT user_version FROM pragma_user_version) AS user_version,(SELECT encoding FROM pragma_encoding) AS encoding,(SELECT count(*) FROM pragma_foreign_key_check) AS foreign_key_errors,(SELECT integrity_check FROM pragma_integrity_check) AS integrity_check;`
	var checks []struct {
		ApplicationID    int    `json:"application_id"`
		Version          int    `json:"user_version"`
		Encoding         string `json:"encoding"`
		ForeignKeyErrors int    `json:"foreign_key_errors"`
		Integrity        string `json:"integrity_check"`
	}
	if err := sqliteSmallJSON(ctx, executable, path, query, &checks); err != nil {
		return err
	}
	if len(checks) != 1 || checks[0].ApplicationID != 0x4e545342 || checks[0].Version != 1 || checks[0].Encoding != "UTF-8" || checks[0].ForeignKeyErrors != 0 || checks[0].Integrity != "ok" {
		return fmt.Errorf("SQLite integrity/header check failed")
	}
	var schema []struct {
		Type string `json:"type"`
		Name string `json:"name"`
	}
	if err := sqliteSmallJSON(ctx, executable, path, "SELECT type,name FROM sqlite_schema ORDER BY type,name;", &schema); err != nil {
		return err
	}
	wanted := []string{"index:cities_region_name", "table:cities", "table:metadata", "table:regions", "table:search_names"}
	if len(schema) != len(wanted) {
		return fmt.Errorf("unexpected SQLite schema inventory")
	}
	for i, row := range schema {
		if row.Type+":"+row.Name != wanted[i] {
			return fmt.Errorf("unexpected SQLite object")
		}
	}
	var metadata []struct {
		Key   string `json:"key"`
		Value string `json:"value"`
	}
	if err := sqliteSmallJSON(ctx, executable, path, "SELECT key,value FROM metadata ORDER BY key;", &metadata); err != nil {
		return err
	}
	if len(metadata) != len(data.metadata) {
		return fmt.Errorf("SQLite metadata count differs")
	}
	for _, row := range metadata {
		if data.metadata[row.Key] != row.Value {
			return fmt.Errorf("SQLite metadata differs")
		}
	}
	var regions []domain.Region
	if err := sqliteSmallJSON(ctx, executable, path, "SELECT id,name,country_code,federal_subject_code FROM regions ORDER BY id;", &regions); err != nil {
		return err
	}
	if len(regions) != len(data.regions) {
		return fmt.Errorf("SQLite region count differs")
	}
	for i, r := range regions {
		if r != data.regions[i] {
			return fmt.Errorf("SQLite region identity differs")
		}
	}
	citiesQuery := `SELECT id,name,region_id,settlement_type,timezone,printf('%!.17g',latitude) AS latitude_text,printf('%!.17g',longitude) AS longitude_text,geographic_source_id,geographic_revision,geographic_license,aliases_json,country_code,population,geographic_source,source_modified_date,fallback_policy_id FROM cities ORDER BY id;`
	cityIndex, aliasCount := 0, 0
	err := sqliteRows(ctx, executable, path, citiesQuery, func(dec *json.Decoder) error {
		var row struct {
			domain.City
			LatitudeText  string `json:"latitude_text"`
			LongitudeText string `json:"longitude_text"`
			AliasesJSON   string `json:"aliases_json"`
		}
		if err := dec.Decode(&row); err != nil {
			return err
		}
		var err error
		row.Latitude, err = strconv.ParseFloat(row.LatitudeText, 64)
		if err != nil {
			return err
		}
		row.Longitude, err = strconv.ParseFloat(row.LongitudeText, 64)
		if err != nil {
			return err
		}
		if err := decodeStrict([]byte(row.AliasesJSON), &row.Aliases); err != nil {
			return err
		}
		if cityIndex >= len(data.cities) {
			return fmt.Errorf("extra SQLite city")
		}
		actual, _ := json.Marshal(row.City)
		expected, _ := json.Marshal(data.cities[cityIndex])
		if !bytes.Equal(actual, expected) {
			return fmt.Errorf("SQLite city %q did not roundtrip exactly", row.ID)
		}
		cityIndex++
		aliasCount += len(row.Aliases)
		return nil
	})
	if err != nil {
		return err
	}
	if cityIndex != len(data.cities) || aliasCount != data.info.AliasCount {
		return fmt.Errorf("SQLite city/alias count differs")
	}
	nameIndex := 0
	err = sqliteRows(ctx, executable, path, "SELECT normalized_name,city_id FROM search_names ORDER BY normalized_name,city_id;", func(dec *json.Decoder) error {
		var row struct {
			Name   string `json:"normalized_name"`
			CityID string `json:"city_id"`
		}
		if err := dec.Decode(&row); err != nil {
			return err
		}
		if nameIndex >= len(data.names) || row.Name+"\x00"+row.CityID != data.names[nameIndex] {
			return fmt.Errorf("SQLite search name differs from canonical normalization")
		}
		nameIndex++
		return nil
	})
	if err != nil {
		return err
	}
	if nameIndex != len(data.names) {
		return fmt.Errorf("SQLite search name count differs")
	}
	return nil
}

func sqliteSmallJSON(ctx context.Context, executable, path, query string, target any) error {
	var output boundedOutput
	output.limit = MaximumManifestBytes
	if err := runSQLite(ctx, executable, path, true, strings.NewReader(query), &output); err != nil {
		return err
	}
	return decodeStrict(output.Bytes(), target)
}

type boundedOutput struct {
	bytes.Buffer
	limit int
}

func (b *boundedOutput) Write(p []byte) (int, error) {
	if b.Len()+len(p) > b.limit {
		return 0, fmt.Errorf("SQLite output exceeds bounded result")
	}
	return b.Buffer.Write(p)
}

func sqliteRows(ctx context.Context, executable, path, query string, visit func(*json.Decoder) error) error {
	reader, writer := io.Pipe()
	done := make(chan error, 1)
	go func() {
		err := runSQLite(ctx, executable, path, true, strings.NewReader(query), writer)
		_ = writer.CloseWithError(err)
		done <- err
	}()
	decodeErr := func() error {
		dec := json.NewDecoder(io.LimitReader(reader, MaximumBundleBytes+1))
		dec.DisallowUnknownFields()
		first, err := dec.Token()
		if err != nil {
			return err
		}
		if first != json.Delim('[') {
			return fmt.Errorf("SQLite rows are not a JSON array")
		}
		for dec.More() {
			if err := visit(dec); err != nil {
				return err
			}
		}
		last, err := dec.Token()
		if err != nil || last != json.Delim(']') {
			return fmt.Errorf("SQLite result is incomplete")
		}
		if err := dec.Decode(&struct{}{}); err != io.EOF {
			return fmt.Errorf("trailing SQLite result")
		}
		return nil
	}()
	_ = reader.CloseWithError(decodeErr)
	runErr := <-done
	if decodeErr != nil {
		return decodeErr
	}
	return runErr
}
