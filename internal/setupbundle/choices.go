package setupbundle

import (
	"context"
	"fmt"
	"sort"
	"time"

	"github.com/andy-ahmedov/namaz-time/internal/domain"
	"github.com/andy-ahmedov/namaz-time/internal/registry"
)

func projectChoices(ctx context.Context, in inputs) (Choices, map[string][]byte, error) {
	result := Choices{SchemaVersion: ChoicesSchema, RegistryRevisionID: in.record.ID, Policies: []Policy{}, Bindings: []Binding{}}
	active, err := registry.New(in.dataset)
	if err != nil {
		return result, nil, err
	}
	scopes := map[string]domain.GeographicScope{}
	for _, s := range in.dataset.Scopes {
		scopes[s.ID] = s
	}
	tables := map[string]domain.TimeTable{}
	for _, v := range in.dataset.TimeTables {
		tables[v.ID] = v
	}
	policies := map[string]domain.PrayerPolicy{}
	for _, v := range in.dataset.Policies {
		policies[v.ID] = v
		if v.Kind != domain.PrayerPolicyTimeTable {
			return result, nil, fmt.Errorf("policy %q has no materialized exact timetable", v.ID)
		}
	}
	snapshots := map[string]domain.Snapshot{}
	snapshotRaw := map[string][]byte{}
	for _, a := range in.artifacts.Snapshots {
		snapshot, err := domain.DecodeSnapshot(a.Snapshot)
		if err != nil {
			return result, nil, err
		}
		if snapshot.DataClassification != domain.DataClassificationProduction {
			return result, nil, fmt.Errorf("normal setup cannot export synthetic snapshots")
		}
		snapshots[a.SnapshotID], snapshotRaw[a.SnapshotID] = snapshot, a.Snapshot
	}
	regions := map[string]domain.Region{}
	for _, r := range in.dataset.Regions {
		regions[r.ID] = r
	}
	reachablePolicies := map[string]Policy{}
	files := map[string][]byte{}
	for _, city := range in.dataset.Cities {
		if err := ctx.Err(); err != nil {
			return result, nil, err
		}
		if city.FallbackPolicyID != "" {
			return result, nil, fmt.Errorf("canonical city %q contains a forbidden fallback", city.ID)
		}
		var applicable []domain.PrayerPolicy
		contexts := map[string]bool{} // true = authenticated public context, false = legacy real mosque.
		publicContext := ""
		for _, p := range in.dataset.Policies {
			scope := scopes[p.GeographicScopeID]
			if scope.RegionID != city.RegionID || (scope.Kind == domain.GeographicScopeCity && scope.CityID != city.ID) {
				continue
			}
			if scope.Kind != domain.GeographicScopeCity && scope.Kind != domain.GeographicScopeRegion {
				return result, nil, fmt.Errorf("unsupported policy scope")
			}
			table := tables[p.TimeTableID]
			snapshot, exists := snapshots[table.PublishedSnapshotID]
			if !exists || snapshot.Source.SourceID != p.SourceID {
				return result, nil, fmt.Errorf("policy %q signed source identity differs", p.ID)
			}
			if p.QualificationID != "" {
				if snapshot.SchemaVersion != "2.0" || snapshot.Source.Qualification == nil {
					return result, nil, fmt.Errorf("public policy %q has no signed v2 qualification", p.ID)
				}
				if publicContext == "" {
					publicContext = table.MosqueID
				}
			} else {
				if scope.Kind != domain.GeographicScopeCity || len(p.MosqueIDs) != 1 || snapshot.SchemaVersion != "1.0" || snapshot.Mosque.ID != p.MosqueIDs[0] ||
					!legacyCityBinding(city, regions[city.RegionID], snapshot, snapshotRaw[snapshot.SnapshotID]) {
					return result, nil, fmt.Errorf("legacy policy %q lacks an exact canonical city and signed original mosque context", p.ID)
				}
				contexts[p.MosqueIDs[0]] = false
			}
			applicable = append(applicable, p)
		}
		if len(applicable) == 0 {
			continue
		}
		if publicContext != "" {
			if _, collision := contexts[publicContext]; collision {
				return result, nil, fmt.Errorf("public context collides with a legacy mosque")
			}
			contexts[publicContext] = true
		}
		boundaries := choiceBoundaries(applicable, in.dataset, tables)
		if len(boundaries) > 8192 {
			return result, nil, fmt.Errorf("choice eligibility boundary limit exceeded")
		}
		contextIDs := make([]string, 0, len(contexts))
		for id := range contexts {
			contextIDs = append(contextIDs, id)
		}
		sort.Strings(contextIDs)
		for index := 0; index+1 < len(boundaries); index++ {
			from, to := boundaries[index], adjacentDate(boundaries[index+1], -1)
			if from > to {
				continue
			}
			for _, contextID := range contextIDs {
				if err := ctx.Err(); err != nil {
					return result, nil, err
				}
				assessment, err := active.Assess(registry.ResolveRequest{CityID: city.ID, MosqueID: contextID, Date: from})
				if err != nil {
					return result, nil, err
				}
				choices, err := registry.ProjectCityScheduleChoices(registry.RevisionPolicyAssessment{Revision: in.record, State: registry.RevisionStateActive, Result: assessment})
				if err != nil {
					return result, nil, err
				}
				for _, choice := range choices.Choices {
					if !choice.Executable || (choice.Qualification != nil) != contexts[contextID] {
						continue
					}
					p := policies[choice.PolicyID]
					if choice.TimeTable == nil || choice.PolicyKind != domain.PrayerPolicyTimeTable {
						return result, nil, fmt.Errorf("projected choice lacks its admitted timetable")
					}
					snapshot := snapshots[choice.TimeTable.PublishedSnapshotID]
					if p.QualificationID == "" && snapshot.Mosque.ID != contextID {
						continue
					}
					raw := snapshotRaw[snapshot.SnapshotID]
					hash := sum(raw)
					path := "snapshots/" + hash + ".json"
					files[path] = raw
					reachablePolicies[p.ID] = Policy{PolicyID: p.ID, AuthorityLabel: choice.AuthorityLabel, Policy: p, Scope: choice.Scope, Authorities: choice.Authorities, Source: choice.Source, Qualification: choice.Qualification, TimeTable: *choice.TimeTable, SourceOverrides: choice.SourceOverrides, Snapshot: SnapshotReference{SnapshotID: snapshot.SnapshotID, Path: path, SHA256: hash, ByteLength: int64(len(raw)), DisplayContext: snapshot.Mosque}}
					label := choice.DisplayLabel
					if choice.Qualification == nil {
						label += " — " + snapshot.Mosque.Name
					}
					result.Bindings = append(result.Bindings, Binding{CityID: city.ID, ChoiceID: choice.ID, PolicyID: p.ID, DisplayLabel: label, Tier: choice.Tier, Effective: domain.DateRange{From: from, To: to}})
					if len(result.Bindings) > 1_000_000 {
						return result, nil, fmt.Errorf("choice binding count limit exceeded")
					}
				}
			}
		}
	}
	for _, p := range reachablePolicies {
		result.Policies = append(result.Policies, p)
	}
	sort.Slice(result.Policies, func(i, j int) bool { return result.Policies[i].PolicyID < result.Policies[j].PolicyID })
	sort.Slice(result.Bindings, func(i, j int) bool {
		a, b := result.Bindings[i], result.Bindings[j]
		if a.CityID != b.CityID {
			return a.CityID < b.CityID
		}
		if a.ChoiceID != b.ChoiceID {
			return a.ChoiceID < b.ChoiceID
		}
		return a.Effective.From < b.Effective.From
	})
	merged := make([]Binding, 0, len(result.Bindings))
	for _, b := range result.Bindings {
		if len(merged) > 0 {
			last := &merged[len(merged)-1]
			if last.CityID == b.CityID && last.ChoiceID == b.ChoiceID {
				if last.Effective.To >= b.Effective.From {
					return result, nil, fmt.Errorf("duplicate or overlapping projected choice intervals")
				}
				if last.PolicyID == b.PolicyID && last.DisplayLabel == b.DisplayLabel && last.Tier == b.Tier && adjacentDate(last.Effective.To, 1) == b.Effective.From {
					last.Effective.To = b.Effective.To
					continue
				}
			}
		}
		merged = append(merged, b)
	}
	result.Bindings = merged
	if len(result.Bindings) == 0 {
		return result, nil, fmt.Errorf("admitted registry has no executable local setup choices")
	}
	return result, files, nil
}

func legacyCityBinding(city domain.City, region domain.Region, snapshot domain.Snapshot, raw []byte) bool {
	if snapshot.Mosque.CountryCode != city.CountryCode || snapshot.Mosque.Region != region.Name || snapshot.Mosque.Timezone != city.Timezone {
		return false
	}
	// The retained, already approved Ulyanovsk artifact includes a street address
	// in locality. This exact existing binding is not a name-prefix heuristic or
	// permission to assign another mosque/address to a nearby canonical city.
	return sum(raw) == "78233e7be3dd8ac9013ae8f44e8e2fdea587a3780b6dadabb97a290ed57ec50b" &&
		snapshot.Mosque.ID == "second-cathedral-mosque-ulyanovsk" &&
		snapshot.Mosque.Locality == "Ульяновск, ул. Дзержинского, 18А" &&
		city.ID == "city-4adcfc15932f3850d5dd5dbaa17e3a4c" && city.GeographicSourceID == "geonames:479123" &&
		city.Name == "Ульяновск" && region.ID == "ru-uly" && region.FederalSubjectCode == "RU-ULY"
}

// All conditions in Registry.Assess are constant inside these intervals. The
// registry, not this function, still decides source eligibility and precedence.
func choiceBoundaries(policies []domain.PrayerPolicy, dataset registry.Dataset, tables map[string]domain.TimeTable) []string {
	points := map[string]struct{}{}
	addRange := func(r domain.DateRange) { points[r.From] = struct{}{}; points[adjacentDate(r.To, 1)] = struct{}{} }
	sourceIDs := map[string]bool{}
	overrideIDs := map[string]bool{}
	for _, p := range policies {
		addRange(p.Effective)
		table := tables[p.TimeTableID]
		addRange(table.Effective)
		sourceIDs[p.SourceID] = true
		for _, id := range table.SourceOverrideIDs {
			overrideIDs[id] = true
		}
	}
	for _, o := range dataset.SourceOverrides {
		if overrideIDs[o.ID] {
			addRange(o.Effective)
			sourceIDs[o.BaseSourceID], sourceIDs[o.OverrideSourceID] = true, true
		}
	}
	for _, s := range dataset.Sources {
		if sourceIDs[s.ID] && s.FreshThrough != "" {
			points[adjacentDate(s.FreshThrough, 1)] = struct{}{}
		}
	}
	values := make([]string, 0, len(points))
	for value := range points {
		values = append(values, value)
	}
	sort.Strings(values)
	return values
}

func adjacentDate(date string, days int) string {
	parsed, _ := time.Parse(time.DateOnly, date)
	return parsed.AddDate(0, 0, days).Format(time.DateOnly)
}
