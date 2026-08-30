// Package geography imports and searches licensed geographic catalog data.
// Geography never selects a prayer authority or prayer policy.
package geography

import (
	"archive/zip"
	"bufio"
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/strictjson"
)

const (
	manifestSchemaVersion = "geonames-source-manifest/v1"
	catalogSchemaVersion  = "namaztime-city-catalog/v1"
	maximumExtractedBytes = 256 * 1024 * 1024
)

var (
	ErrArtifactMismatch = errors.New("geographic source artifact mismatch")
	ErrSchemaDrift      = errors.New("geographic source schema drift")
	ErrInvalidCatalog   = errors.New("invalid geographic catalog")
)

type ArtifactManifest struct {
	Role          string `json:"role"`
	URL           string `json:"url"`
	CacheFile     string `json:"cache_file"`
	ArchiveMember string `json:"archive_member,omitempty"`
	ByteLength    int64  `json:"byte_length"`
	SHA256        string `json:"sha256"`
}

type SourceManifest struct {
	SchemaVersion  string             `json:"schema_version"`
	SourceID       string             `json:"source_id"`
	SourceRevision string             `json:"source_revision"`
	CountryCode    string             `json:"country_code"`
	License        string             `json:"license"`
	LicenseURL     string             `json:"license_url"`
	Attribution    string             `json:"attribution"`
	Artifacts      []ArtifactManifest `json:"artifacts"`
}

type RegionMapping struct {
	SourceAdminCode    string   `json:"source_admin_code"`
	LegacyAdminCodes   []string `json:"legacy_admin_codes,omitempty"`
	ID                 string   `json:"id"`
	FederalSubjectCode string   `json:"federal_subject_code"`
	NameRU             string   `json:"name_ru"`
}

type ExcludedAdminCode struct {
	Code   string `json:"code"`
	Reason string `json:"reason"`
}

type RegionMappingFile struct {
	SchemaVersion      string              `json:"schema_version"`
	SourceID           string              `json:"source_id"`
	CountryCode        string              `json:"country_code"`
	Regions            []RegionMapping     `json:"regions"`
	ExcludedAdminCodes []ExcludedAdminCode `json:"excluded_admin_codes,omitempty"`
}

type ImportRequest struct {
	Manifest           SourceManifest
	Artifacts          map[string][]byte
	RegionMappings     []RegionMapping
	ExcludedAdminCodes []ExcludedAdminCode
}

type Revision struct {
	ID                    string `json:"id"`
	ContentSHA256         string `json:"content_sha256"`
	SourcePopulatedPlaces int    `json:"source_populated_places"`
	ImportedCities        int    `json:"imported_cities"`
	MissingRussianNames   int    `json:"missing_russian_names"`
	ExcludedAdminPlaces   int    `json:"excluded_admin_places"`
}

type Catalog struct {
	SchemaVersion string          `json:"schema_version"`
	Revision      Revision        `json:"revision"`
	Source        SourceManifest  `json:"source"`
	Regions       []domain.Region `json:"regions"`
	Cities        []domain.City   `json:"cities"`
}

type SearchResult struct {
	City   domain.City   `json:"city"`
	Region domain.Region `json:"region"`
}

type Diff struct {
	FromRevisionID string   `json:"from_revision_id"`
	ToRevisionID   string   `json:"to_revision_id"`
	AddedCityIDs   []string `json:"added_city_ids"`
	RemovedCityIDs []string `json:"removed_city_ids"`
	ChangedCityIDs []string `json:"changed_city_ids"`
	SHA256         string   `json:"sha256"`
}

type alternateName struct {
	language  string
	name      string
	preferred bool
}

var supportedPopulatedPlaceCodes = map[string]struct{}{
	"PPL": {}, "PPLA": {}, "PPLA2": {}, "PPLA3": {}, "PPLA4": {},
	"PPLC": {}, "PPLF": {}, "PPLG": {}, "PPLL": {}, "PPLR": {},
	"PPLS": {}, "PPLX": {},
}

func StableCityID(sourceID, countryCode, sourceRecordID string) string {
	digest := sha256.Sum256([]byte(sourceID + "\x00" + countryCode + "\x00" + sourceRecordID))
	return "city-" + hex.EncodeToString(digest[:16])
}

func Import(request ImportRequest) (Catalog, error) {
	manifest, artifactBytes, err := validateAndReadArtifacts(request.Manifest, request.Artifacts)
	if err != nil {
		return Catalog{}, err
	}
	regions, regionByAdmin, excludedAdminCodes, err := validateRegionMappings(request.RegionMappings, request.ExcludedAdminCodes, artifactBytes["admin1_codes"], manifest.CountryCode)
	if err != nil {
		return Catalog{}, err
	}
	alternateNames, err := parseAlternateNames(artifactBytes["alternate_names"])
	if err != nil {
		return Catalog{}, err
	}
	cities, sourceCount, missingRussianNames, excludedAdminPlaces, err := parseGazetteer(artifactBytes["gazetteer"], manifest, regionByAdmin, excludedAdminCodes, alternateNames)
	if err != nil {
		return Catalog{}, err
	}
	sort.Slice(cities, func(i, j int) bool { return cities[i].ID < cities[j].ID })
	content := struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{Regions: regions, Cities: cities}
	contentBytes, err := json.Marshal(content)
	if err != nil {
		return Catalog{}, fmt.Errorf("%w: encode canonical content: %v", ErrInvalidCatalog, err)
	}
	digest := sha256.Sum256(contentBytes)
	digestText := hex.EncodeToString(digest[:])
	return Catalog{
		SchemaVersion: catalogSchemaVersion,
		Revision: Revision{
			ID:                    "catalog-" + manifest.SourceID + "-" + strings.ToLower(manifest.CountryCode) + "-" + manifest.SourceRevision + "-" + digestText[:16],
			ContentSHA256:         digestText,
			SourcePopulatedPlaces: sourceCount,
			ImportedCities:        len(cities),
			MissingRussianNames:   missingRussianNames,
			ExcludedAdminPlaces:   excludedAdminPlaces,
		},
		Source:  manifest,
		Regions: regions,
		Cities:  cities,
	}, nil
}

func validateAndReadArtifacts(manifest SourceManifest, artifacts map[string][]byte) (SourceManifest, map[string][]byte, error) {
	if manifest.SchemaVersion != manifestSchemaVersion || manifest.SourceID != "geonames" || manifest.CountryCode != "RU" ||
		manifest.License != "CC BY 4.0" || manifest.LicenseURL != "https://creativecommons.org/licenses/by/4.0/" || strings.TrimSpace(manifest.Attribution) == "" {
		return SourceManifest{}, nil, fmt.Errorf("%w: unsupported source manifest identity", ErrSchemaDrift)
	}
	if parsed, err := time.Parse("2006-01-02", manifest.SourceRevision); err != nil || parsed.Format("2006-01-02") != manifest.SourceRevision {
		return SourceManifest{}, nil, fmt.Errorf("%w: invalid source revision", ErrSchemaDrift)
	}
	if len(manifest.Artifacts) != 3 || len(artifacts) != 3 {
		return SourceManifest{}, nil, fmt.Errorf("%w: require exactly three source artifacts", ErrSchemaDrift)
	}
	manifest.Artifacts = append([]ArtifactManifest(nil), manifest.Artifacts...)
	sort.Slice(manifest.Artifacts, func(i, j int) bool { return manifest.Artifacts[i].Role < manifest.Artifacts[j].Role })
	expectedRoles := []string{"admin1_codes", "alternate_names", "gazetteer"}
	extracted := make(map[string][]byte, len(expectedRoles))
	for index, role := range expectedRoles {
		item := manifest.Artifacts[index]
		if item.Role != role || item.URL == "" || !safeCacheFile(item.CacheFile) || item.ByteLength <= 0 || len(item.SHA256) != sha256.Size*2 {
			return SourceManifest{}, nil, fmt.Errorf("%w: invalid %s manifest", ErrSchemaDrift, role)
		}
		if _, err := hex.DecodeString(item.SHA256); err != nil || strings.ToLower(item.SHA256) != item.SHA256 {
			return SourceManifest{}, nil, fmt.Errorf("%w: invalid %s digest", ErrSchemaDrift, role)
		}
		data, ok := artifacts[role]
		if !ok || int64(len(data)) != item.ByteLength {
			return SourceManifest{}, nil, fmt.Errorf("%w: %s byte length", ErrArtifactMismatch, role)
		}
		digest := sha256.Sum256(data)
		if hex.EncodeToString(digest[:]) != item.SHA256 {
			return SourceManifest{}, nil, fmt.Errorf("%w: %s SHA-256", ErrArtifactMismatch, role)
		}
		if item.ArchiveMember == "" {
			extracted[role] = append([]byte(nil), data...)
			continue
		}
		member, err := readZIPMember(data, item.ArchiveMember)
		if err != nil {
			return SourceManifest{}, nil, fmt.Errorf("%w: %s: %v", ErrSchemaDrift, role, err)
		}
		extracted[role] = member
	}
	return manifest, extracted, nil
}

func readZIPMember(data []byte, wanted string) ([]byte, error) {
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return nil, err
	}
	var selected *zip.File
	for _, entry := range reader.File {
		if entry.Name == wanted {
			if selected != nil || !entry.Mode().IsRegular() || entry.UncompressedSize64 > maximumExtractedBytes {
				return nil, errors.New("archive member is duplicate, non-regular, or oversized")
			}
			selected = entry
		}
	}
	if selected == nil {
		return nil, errors.New("archive member is missing")
	}
	stream, err := selected.Open()
	if err != nil {
		return nil, err
	}
	defer stream.Close()
	return io.ReadAll(io.LimitReader(stream, maximumExtractedBytes+1))
}

func validateRegionMappings(mappings []RegionMapping, exclusions []ExcludedAdminCode, adminBytes []byte, countryCode string) ([]domain.Region, map[string]domain.Region, map[string]struct{}, error) {
	knownAdminCodes := make(map[string]struct{})
	if err := scanTSV(adminBytes, 4, func(fields []string) error {
		parts := strings.Split(fields[0], ".")
		if len(parts) == 2 && parts[0] == countryCode {
			knownAdminCodes[parts[1]] = struct{}{}
		}
		return nil
	}); err != nil {
		return nil, nil, nil, fmt.Errorf("%w: admin1 codes: %v", ErrSchemaDrift, err)
	}
	if len(mappings) == 0 {
		return nil, nil, nil, fmt.Errorf("%w: region mappings are empty", ErrInvalidCatalog)
	}
	regions := make([]domain.Region, 0, len(mappings))
	byAdmin := make(map[string]domain.Region, len(mappings))
	regionIDs := make(map[string]struct{}, len(mappings))
	subjectCodes := make(map[string]struct{}, len(mappings))
	for _, mapping := range mappings {
		if _, ok := knownAdminCodes[mapping.SourceAdminCode]; !ok || mapping.ID == "" || mapping.NameRU == "" || !strings.HasPrefix(mapping.FederalSubjectCode, "RU-") {
			return nil, nil, nil, fmt.Errorf("%w: invalid region mapping for admin1 %q", ErrInvalidCatalog, mapping.SourceAdminCode)
		}
		if _, duplicate := regionIDs[mapping.ID]; duplicate {
			return nil, nil, nil, fmt.Errorf("%w: duplicate region ID %q", ErrInvalidCatalog, mapping.ID)
		}
		if _, duplicate := subjectCodes[mapping.FederalSubjectCode]; duplicate {
			return nil, nil, nil, fmt.Errorf("%w: duplicate federal subject code %q", ErrInvalidCatalog, mapping.FederalSubjectCode)
		}
		region := domain.Region{ID: mapping.ID, Name: mapping.NameRU, CountryCode: countryCode, FederalSubjectCode: mapping.FederalSubjectCode}
		regionIDs[region.ID] = struct{}{}
		subjectCodes[region.FederalSubjectCode] = struct{}{}
		regions = append(regions, region)
		for _, code := range append([]string{mapping.SourceAdminCode}, mapping.LegacyAdminCodes...) {
			if code == "" {
				return nil, nil, nil, fmt.Errorf("%w: blank legacy admin code", ErrInvalidCatalog)
			}
			if _, duplicate := byAdmin[code]; duplicate {
				return nil, nil, nil, fmt.Errorf("%w: duplicate admin mapping %q", ErrInvalidCatalog, code)
			}
			byAdmin[code] = region
		}
	}
	excluded := make(map[string]struct{}, len(exclusions))
	for _, exclusion := range exclusions {
		if strings.TrimSpace(exclusion.Reason) == "" {
			return nil, nil, nil, fmt.Errorf("%w: excluded admin code %q lacks a reason", ErrInvalidCatalog, exclusion.Code)
		}
		if _, mapped := byAdmin[exclusion.Code]; mapped {
			return nil, nil, nil, fmt.Errorf("%w: admin code %q is both mapped and excluded", ErrInvalidCatalog, exclusion.Code)
		}
		if _, duplicate := excluded[exclusion.Code]; duplicate {
			return nil, nil, nil, fmt.Errorf("%w: duplicate excluded admin code %q", ErrInvalidCatalog, exclusion.Code)
		}
		excluded[exclusion.Code] = struct{}{}
	}
	sort.Slice(regions, func(i, j int) bool { return regions[i].FederalSubjectCode < regions[j].FederalSubjectCode })
	return regions, byAdmin, excluded, nil
}

func parseAlternateNames(data []byte) (map[string][]alternateName, error) {
	result := make(map[string][]alternateName)
	err := scanTSV(data, 10, func(fields []string) error {
		language := fields[2]
		if language != "ru" && language != "en" {
			return nil
		}
		if fields[3] == "" || fields[6] == "1" || fields[7] == "1" {
			return nil
		}
		result[fields[1]] = append(result[fields[1]], alternateName{language: language, name: fields[3], preferred: fields[4] == "1"})
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("%w: alternate names: %v", ErrSchemaDrift, err)
	}
	for id := range result {
		sort.Slice(result[id], func(i, j int) bool {
			left, right := result[id][i], result[id][j]
			if left.language != right.language {
				return left.language < right.language
			}
			if left.preferred != right.preferred {
				return left.preferred
			}
			return left.name < right.name
		})
	}
	return result, nil
}

func parseGazetteer(data []byte, manifest SourceManifest, regions map[string]domain.Region, excludedAdminCodes map[string]struct{}, names map[string][]alternateName) ([]domain.City, int, int, int, error) {
	var cities []domain.City
	seenIDs := make(map[string]struct{})
	sourceCount := 0
	missingRussianNames := 0
	excludedAdminPlaces := 0
	err := scanTSV(data, 19, func(fields []string) error {
		if fields[6] != "P" || fields[8] != manifest.CountryCode {
			return nil
		}
		if _, supported := supportedPopulatedPlaceCodes[fields[7]]; !supported {
			return nil
		}
		sourceCount++
		if _, duplicate := seenIDs[fields[0]]; duplicate {
			return fmt.Errorf("duplicate GeoNames ID %q", fields[0])
		}
		seenIDs[fields[0]] = struct{}{}
		region, ok := regions[fields[10]]
		if !ok {
			if _, excluded := excludedAdminCodes[fields[10]]; excluded {
				excludedAdminPlaces++
				return nil
			}
			return fmt.Errorf("unmapped admin1 code %q for GeoNames ID %s", fields[10], fields[0])
		}
		canonicalName, aliases := canonicalNames(fields[1], fields[2], names[fields[0]])
		if canonicalName == "" {
			missingRussianNames++
			return nil
		}
		latitude, latitudeErr := strconv.ParseFloat(fields[4], 64)
		longitude, longitudeErr := strconv.ParseFloat(fields[5], 64)
		population, populationErr := strconv.ParseInt(fields[14], 10, 64)
		if latitudeErr != nil || longitudeErr != nil || populationErr != nil || math.IsNaN(latitude) || math.IsNaN(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 || population < 0 {
			return fmt.Errorf("%w: invalid coordinates/population for GeoNames ID %s", ErrInvalidCatalog, fields[0])
		}
		if _, err := time.LoadLocation(fields[17]); err != nil || fields[17] == "UTC" || !strings.Contains(fields[17], "/") {
			return fmt.Errorf("%w: invalid IANA timezone %q for GeoNames ID %s", ErrInvalidCatalog, fields[17], fields[0])
		}
		if parsed, err := time.Parse("2006-01-02", fields[18]); err != nil || parsed.Format("2006-01-02") != fields[18] {
			return fmt.Errorf("%w: invalid modification date for GeoNames ID %s", ErrInvalidCatalog, fields[0])
		}
		cities = append(cities, domain.City{
			ID:                 StableCityID(manifest.SourceID, manifest.CountryCode, fields[0]),
			Name:               canonicalName,
			Aliases:            aliases,
			CountryCode:        manifest.CountryCode,
			RegionID:           region.ID,
			SettlementType:     fields[7],
			Latitude:           latitude,
			Longitude:          longitude,
			Timezone:           fields[17],
			Population:         population,
			GeographicSource:   "https://www.geonames.org/" + fields[0],
			GeographicSourceID: manifest.SourceID + ":" + fields[0],
			GeographicRevision: manifest.SourceRevision,
			GeographicLicense:  manifest.License,
			SourceModifiedDate: fields[18],
		})
		return nil
	})
	if err != nil {
		if errors.Is(err, ErrInvalidCatalog) {
			return nil, 0, 0, 0, err
		}
		return nil, 0, 0, 0, fmt.Errorf("%w: gazetteer: %v", ErrSchemaDrift, err)
	}
	return cities, sourceCount, missingRussianNames, excludedAdminPlaces, nil
}

func canonicalNames(sourceName, asciiName string, names []alternateName) (string, []string) {
	canonical := ""
	for _, name := range names {
		if name.language == "ru" && name.preferred {
			canonical = name.name
			break
		}
	}
	if canonical == "" {
		for _, name := range names {
			if name.language == "ru" {
				canonical = name.name
				break
			}
		}
	}
	if canonical == "" && containsCyrillic(sourceName) {
		canonical = sourceName
	}
	if canonical == "" {
		return "", nil
	}
	candidates := []string{sourceName, asciiName}
	for _, name := range names {
		candidates = append(candidates, name.name)
	}
	seen := map[string]struct{}{normalizeSearch(canonical): {}}
	aliases := make([]string, 0, len(candidates))
	for _, candidate := range candidates {
		candidate = strings.TrimSpace(candidate)
		normalized := normalizeSearch(candidate)
		if normalized == "" {
			continue
		}
		if _, duplicate := seen[normalized]; duplicate {
			continue
		}
		seen[normalized] = struct{}{}
		aliases = append(aliases, candidate)
	}
	sort.Slice(aliases, func(i, j int) bool { return normalizeSearch(aliases[i]) < normalizeSearch(aliases[j]) })
	return canonical, aliases
}

func containsCyrillic(value string) bool {
	for _, character := range value {
		if unicode.In(character, unicode.Cyrillic) {
			return true
		}
	}
	return false
}

func scanTSV(data []byte, columns int, visit func([]string) error) error {
	if !utf8.Valid(data) {
		return errors.New("input is not valid UTF-8")
	}
	scanner := bufio.NewScanner(bytes.NewReader(data))
	scanner.Buffer(make([]byte, 64*1024), 16*1024*1024)
	line := 0
	for scanner.Scan() {
		line++
		fields := strings.Split(scanner.Text(), "\t")
		if len(fields) != columns {
			return fmt.Errorf("line %d has %d columns, require %d", line, len(fields), columns)
		}
		if err := visit(fields); err != nil {
			return fmt.Errorf("line %d: %w", line, err)
		}
	}
	if err := scanner.Err(); err != nil {
		return err
	}
	return nil
}

func (catalog Catalog) Search(query string) []SearchResult {
	normalized := normalizeSearch(query)
	if normalized == "" {
		return nil
	}
	regions := make(map[string]domain.Region, len(catalog.Regions))
	for _, region := range catalog.Regions {
		regions[region.ID] = region
	}
	var results []SearchResult
	for _, city := range catalog.Cities {
		if normalizeSearch(city.Name) != normalized && !containsNormalized(city.Aliases, normalized) {
			continue
		}
		region, ok := regions[city.RegionID]
		if !ok {
			continue
		}
		results = append(results, SearchResult{City: cloneCity(city), Region: region})
	}
	sort.Slice(results, func(i, j int) bool {
		left, right := results[i], results[j]
		if left.Region.FederalSubjectCode != right.Region.FederalSubjectCode {
			return left.Region.FederalSubjectCode < right.Region.FederalSubjectCode
		}
		if left.City.Name != right.City.Name {
			return left.City.Name < right.City.Name
		}
		return left.City.ID < right.City.ID
	})
	return results
}

func (catalog Catalog) Unique(query string) (SearchResult, bool) {
	results := catalog.Search(query)
	if len(results) != 1 {
		return SearchResult{}, false
	}
	return results[0], true
}

func Compare(before, after Catalog) Diff {
	beforeCities := cityJSONByID(before.Cities)
	afterCities := cityJSONByID(after.Cities)
	diff := Diff{FromRevisionID: before.Revision.ID, ToRevisionID: after.Revision.ID}
	for id, beforeJSON := range beforeCities {
		afterJSON, exists := afterCities[id]
		if !exists {
			diff.RemovedCityIDs = append(diff.RemovedCityIDs, id)
		} else if !bytes.Equal(beforeJSON, afterJSON) {
			diff.ChangedCityIDs = append(diff.ChangedCityIDs, id)
		}
	}
	for id := range afterCities {
		if _, exists := beforeCities[id]; !exists {
			diff.AddedCityIDs = append(diff.AddedCityIDs, id)
		}
	}
	sort.Strings(diff.AddedCityIDs)
	sort.Strings(diff.RemovedCityIDs)
	sort.Strings(diff.ChangedCityIDs)
	hashInput := diff
	hashInput.SHA256 = ""
	encoded, _ := json.Marshal(hashInput)
	digest := sha256.Sum256(encoded)
	diff.SHA256 = hex.EncodeToString(digest[:])
	return diff
}

func cityJSONByID(cities []domain.City) map[string][]byte {
	result := make(map[string][]byte, len(cities))
	for _, city := range cities {
		encoded, _ := json.Marshal(city)
		result[city.ID] = encoded
	}
	return result
}

func Encode(catalog Catalog) ([]byte, error) {
	encoded, err := json.MarshalIndent(catalog, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("encode city catalog: %w", err)
	}
	return append(encoded, '\n'), nil
}

func DecodeManifest(data []byte) (SourceManifest, error) {
	var manifest SourceManifest
	if err := decodeStrict(data, &manifest); err != nil {
		return SourceManifest{}, fmt.Errorf("decode geographic source manifest: %w", err)
	}
	if _, _, err := validateAndReadArtifacts(manifest, placeholderArtifacts(manifest)); err == nil || !errors.Is(err, ErrArtifactMismatch) {
		return SourceManifest{}, fmt.Errorf("decode geographic source manifest: validation did not reach artifact boundary")
	}
	return manifest, nil
}

func DecodeRegionMappingFile(data []byte) (RegionMappingFile, error) {
	var mapping RegionMappingFile
	if err := decodeStrict(data, &mapping); err != nil {
		return RegionMappingFile{}, fmt.Errorf("decode GeoNames region mapping: %w", err)
	}
	if mapping.SchemaVersion != "geonames-ru-region-map/v1" || mapping.SourceID != "geonames" || mapping.CountryCode != "RU" || len(mapping.Regions) == 0 {
		return RegionMappingFile{}, fmt.Errorf("decode GeoNames region mapping: %w: invalid identity", ErrInvalidCatalog)
	}
	return mapping, nil
}

func DecodeCatalog(data []byte) (Catalog, error) {
	var catalog Catalog
	if err := decodeStrict(data, &catalog); err != nil {
		return Catalog{}, fmt.Errorf("decode city catalog: %w", err)
	}
	if err := validateEncodedCatalog(catalog); err != nil {
		return Catalog{}, err
	}
	return catalog, nil
}

func decodeStrict(data []byte, target any) error {
	if len(data) == 0 || !utf8.Valid(data) {
		return errors.New("JSON is empty or invalid UTF-8")
	}
	if err := strictjson.RejectDuplicateObjectMembers(data); err != nil {
		return err
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return err
	}
	var trailing any
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		if err == nil {
			return errors.New("unexpected trailing JSON")
		}
		return err
	}
	return nil
}

func placeholderArtifacts(manifest SourceManifest) map[string][]byte {
	artifacts := make(map[string][]byte, len(manifest.Artifacts))
	for _, item := range manifest.Artifacts {
		artifacts[item.Role] = nil
	}
	return artifacts
}

func validateEncodedCatalog(catalog Catalog) error {
	if catalog.SchemaVersion != catalogSchemaVersion || catalog.Revision.ID == "" || catalog.Revision.ContentSHA256 == "" {
		return fmt.Errorf("decode city catalog: %w: invalid revision identity", ErrInvalidCatalog)
	}
	regionByID := make(map[string]domain.Region, len(catalog.Regions))
	for _, region := range catalog.Regions {
		if region.ID == "" || region.Name == "" || region.CountryCode != "RU" || !strings.HasPrefix(region.FederalSubjectCode, "RU-") {
			return fmt.Errorf("decode city catalog: %w: invalid region", ErrInvalidCatalog)
		}
		if _, duplicate := regionByID[region.ID]; duplicate {
			return fmt.Errorf("decode city catalog: %w: duplicate region", ErrInvalidCatalog)
		}
		regionByID[region.ID] = region
	}
	cityIDs := make(map[string]struct{}, len(catalog.Cities))
	for _, city := range catalog.Cities {
		if city.ID == "" || city.Name == "" || city.CountryCode != "RU" || city.GeographicSourceID == "" || city.GeographicLicense == "" {
			return fmt.Errorf("decode city catalog: %w: invalid city", ErrInvalidCatalog)
		}
		if _, ok := regionByID[city.RegionID]; !ok {
			return fmt.Errorf("decode city catalog: %w: city region missing", ErrInvalidCatalog)
		}
		if _, duplicate := cityIDs[city.ID]; duplicate {
			return fmt.Errorf("decode city catalog: %w: duplicate city", ErrInvalidCatalog)
		}
		cityIDs[city.ID] = struct{}{}
		if _, err := time.LoadLocation(city.Timezone); err != nil {
			return fmt.Errorf("decode city catalog: %w: invalid timezone", ErrInvalidCatalog)
		}
	}
	content := struct {
		Regions []domain.Region `json:"regions"`
		Cities  []domain.City   `json:"cities"`
	}{Regions: catalog.Regions, Cities: catalog.Cities}
	contentBytes, _ := json.Marshal(content)
	digest := sha256.Sum256(contentBytes)
	if hex.EncodeToString(digest[:]) != catalog.Revision.ContentSHA256 || catalog.Revision.ImportedCities != len(catalog.Cities) {
		return fmt.Errorf("decode city catalog: %w: content hash/count mismatch", ErrInvalidCatalog)
	}
	return nil
}

func safeCacheFile(value string) bool {
	return value != "" && value == filepath.Base(value) && value != "." && value != ".." && !strings.ContainsAny(value, `/\\`)
}

func normalizeSearch(value string) string {
	return strings.ToLower(strings.Join(strings.Fields(value), " "))
}

func containsNormalized(values []string, wanted string) bool {
	for _, value := range values {
		if normalizeSearch(value) == wanted {
			return true
		}
	}
	return false
}

func cloneCity(city domain.City) domain.City {
	city.Aliases = append([]string(nil), city.Aliases...)
	return city
}
