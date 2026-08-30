# GeoNames Russia catalog inputs

NamazTime uses the GeoNames Russia gazetteer as geographic catalog input under
Creative Commons Attribution 4.0. GeoNames data is geography only: it does not
assign, recommend, or approve any prayer authority, source, method, or policy.

The tracked source manifest pins the exact 2026-08-29 archive byte lengths and
SHA-256 values. The raw archives and generated catalog are intentionally not
tracked. Retain the exact downloaded inputs in controlled artifact storage if a
catalog revision is activated.

Download the three URLs from `source-manifest.json` into `geodata/cache/` using
their `cache_file` names, then run:

```bash
go run ./cmd/citycatalog \
  -manifest geodata/geonames/source-manifest.json \
  -region-map geodata/geonames/ru-region-map.json \
  -input-dir geodata/cache \
  -output geodata/generated/russia-cities-2026-08-29.json
```

For an update, use a new manifest/revision and preserve the prior catalog:

```bash
go run ./cmd/citycatalog \
  -manifest geodata/geonames/source-manifest.json \
  -region-map geodata/geonames/ru-region-map.json \
  -input-dir geodata/cache \
  -previous /retained/catalog.json \
  -output geodata/generated/russia-cities-next.json \
  -diff-output geodata/generated/russia-cities-diff.json
```

Attribution: GeoNames (https://www.geonames.org/)

License: https://creativecommons.org/licenses/by/4.0/
Official export/readme: https://download.geonames.org/export/dump/

The reviewed map covers the 83 current `RU.*` admin1 records in the pinned
GeoNames metadata. Four explicit legacy/blank source codes are excluded rather
than guessed. Jurisdictions that GeoNames does not encode under `RU` are outside
this revision and must not be silently supplemented from another source.
