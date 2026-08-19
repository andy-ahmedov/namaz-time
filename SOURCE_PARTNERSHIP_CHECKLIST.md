# SOURCE_PARTNERSHIP_CHECKLIST.md

Use this before implementing a production adapter for a mosque, DUM or other authority.

## Authority and scope

- [ ] Legal/organizational name recorded.
- [ ] Named contact and role recorded.
- [ ] Exact covered territory/localities recorded.
- [ ] Whether schedule is adhan only or includes mosque performance/iqamah clarified.
- [ ] Madhab and special regional rules clarified.
- [ ] Ramadan, high-latitude and exceptional-day policy documented.
- [ ] Who has final correction authority named.

## Data access

- [ ] Preferred machine-readable format requested: JSON/CSV/XLSX/API.
- [ ] If only PDF/HTML exists, source acknowledges intended automated use.
- [ ] Stable canonical URL or delivery channel recorded.
- [ ] Update cadence and publication deadline recorded.
- [ ] Historical/next-year availability recorded.
- [ ] Timezone and date format confirmed.
- [ ] Example full year obtained.
- [ ] Correction/emergency channel defined.

## Permission and attribution

- [ ] Written permission/license to ingest, cache and redistribute to mosque TVs.
- [ ] Required attribution wording/logo constraints recorded.
- [ ] Whether raw files may be retained recorded.
- [ ] Whether derived normalized data may be published recorded.
- [ ] Rate limits/terms/robots policy reviewed.
- [ ] Contact procedure for source changes or revocation recorded.

## Technical validation

- [ ] Raw fixture hashed and stored according to permission.
- [ ] Parser contract documented.
- [ ] Full-year coverage validated, including leap year.
- [ ] At least 30 representative days manually compared.
- [ ] Seasonal transitions and Ramadan reviewed.
- [ ] Differences against previous/alternate source explained.
- [ ] Parser schema-drift alert tested.
- [ ] Stale threshold agreed.

## Approval and publication

- [ ] Mosque/authority approver account created.
- [ ] First candidate diff reviewed.
- [ ] Approval binds to raw hash/parser version.
- [ ] Source label shown to operator approved.
- [ ] Fallback policy explicitly approved or set to none.
- [ ] Rollback contact and procedure tested.
- [ ] Renewal/review date scheduled.

## Questions to send in the first contact

1. Do you maintain an official annual/monthly timetable for this locality?
2. Is there a JSON/CSV/XLSX/API feed, even if it is not public?
3. May we cache and show it in an Android TV application used by mosques?
4. What attribution is required?
5. How and how often are corrections published?
6. Which exact localities and coordinates does the schedule cover?
7. Which values are adhan start times, and which are congregation/iqamah times?
8. Who can formally approve the integration and future changes?
