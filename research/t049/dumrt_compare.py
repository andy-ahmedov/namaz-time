"""Offline DUM RT comparison. Emits metadata, not bulk rows or qualification.

Run from the repository root; retained artifacts must remain outside Git.
The Go subprocess exercises the public provider API; Python independently
extracts the workbook and compares every published 2026 field.
"""

import argparse
import csv
import datetime as dt
import hashlib
from html.parser import HTMLParser
import io
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urljoin, urlsplit, urlunsplit
import xml.etree.ElementTree as ET
import zipfile

YEAR = 2026
FROM, TO = "2026-09-01", "2026-09-30"
PAGE_URL = "https://dumrt.ru/ru/help-info/prayertime/"
XLSX_URL = "https://dumrt.ru/netcat_files/multifile/2649/vremena_namazov_RT_2026_0.xlsx"
FIELDS = ["suhur_end", "morning_performed_in_mosques", "sunrise", "zenith", "dhuhr", "asr", "maghrib", "isha"]
HEADERS_RU = ["Населенный пункт", "Дата", "Завершение сухура", "Совершается в мечетях", "Восход солнца", "Зенит", "Зухр", "Аср", "Магриб", "Иша"]
NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def digest_rows(rows):
    values = [[date, *rows[date]] for date in sorted(rows)]
    return sha(json.dumps(values, ensure_ascii=False, separators=(",", ":")).encode())


def parse_selector(text):
    class Selector(HTMLParser):
        def __init__(self):
            super().__init__()
            self.items, self.current = [], None

        def handle_starttag(self, tag, attrs):
            attrs = dict(attrs)
            if tag == "option" and "prayer_city_select" in attrs.get("class", "").split():
                if self.current is not None or "data-url" not in attrs:
                    raise ValueError("malformed selector option")
                self.current = [attrs["data-url"], ""]

        def handle_data(self, data):
            if self.current is not None:
                self.current[1] += data

        def handle_endtag(self, tag):
            if tag == "option" and self.current is not None:
                self.items.append(self.current)
                self.current = None

    parser = Selector()
    parser.feed(text)
    result = []
    for url, label in parser.items:
        parsed = urlsplit(urljoin(PAGE_URL, url))
        if parsed.scheme != "https" or parsed.netloc != "dumrt.ru" or parsed.fragment or not re.fullmatch(r"/netcat_files/391/638/[A-Za-z0-9]+\.csv", parsed.path):
            raise ValueError("unexpected selector transport")
        item = {"label": label.strip(), "filename": parsed.path.rsplit("/", 1)[1],
                "actual_url": urlunsplit(parsed), "canonical_url": urlunsplit(parsed._replace(query=""))}
        if not item["label"] or any(old["label"] == item["label"] or old["filename"] == item["filename"] for old in result):
            raise ValueError("duplicate or empty selector identity")
        result.append(item)
    if parser.current is not None or not result:
        raise ValueError("empty or unclosed selector")
    return result


def clock(value):
    if not re.fullmatch(r"[0-9]{1,2}:[0-9]{2}", value):
        raise ValueError("unknown clock shape or marker")
    hour, minute = map(int, value.split(":"))
    if hour > 23 or minute > 59:
        raise ValueError("invalid wall-clock range")
    return f"{hour:02}:{minute:02}"


def parse_csv(raw, year):
    if not raw or len(raw) > 1024 * 1024:
        raise ValueError("CSV outside bounded size")
    text = raw.decode("utf-8").replace("\r\n", "\n").removesuffix("\n")
    if "\r" in text or "\n\n" in text or text.startswith("\n") or text.endswith("\n"):
        raise ValueError("blank row or unsupported line ending")
    result, previous = {}, None
    for index, values in enumerate(csv.reader(io.StringIO(text), delimiter=";", strict=True), 1):
        if index > 732 or len(values) not in (9, 10):
            raise ValueError("unknown CSV columns")
        date = dt.datetime.strptime(values[0], "%d.%m.%Y").date()
        if date.strftime("%d.%m.%Y") != values[0] or date.year not in (year - 1, year):
            raise ValueError("noncanonical or unexpected CSV date")
        if previous is not None and date <= previous:
            raise ValueError("duplicate or out-of-order CSV date")
        if len(values) == 10 and date.year != 2025:
            raise ValueError("unknown CSV extra column")
        previous = date
        times = tuple(clock(value) for value in values[1:])
        if date.year == year:
            result[date.isoformat()] = times[:8]
    return result


def diagnostic_current_rows(raw, year):
    # Research-only salvage for comparison, NEVER a provider candidate. Keep
    # the actual whole-artifact parser failure alongside all rejected rows.
    result, errors = {}, []
    for index, values in enumerate(csv.reader(io.StringIO(raw.decode("utf-8")), delimiter=";", strict=True), 1):
        line = io.StringIO()
        csv.writer(line, delimiter=";", lineterminator="\n").writerow(values)
        try:
            current = parse_csv(line.getvalue().encode(), year)
        except ValueError:
            errors.append({"row": index, "date_field": values[0] if values else None, "columns": len(values)})
            continue
        if set(result) & set(current):
            raise ValueError("diagnostic comparison refuses duplicate current-year dates")
        result.update(current)
    return result, errors


def parse_workbook(raw, year):
    if not raw or len(raw) > 5 * 1024 * 1024:
        raise ValueError("workbook outside bounded size")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or sum(item.file_size for item in archive.infolist()) > 32 * 1024 * 1024:
            raise ValueError("duplicate or oversized workbook members")
        workbook = ET.fromstring(archive.read("xl/workbook.xml"))
        properties = workbook.find("m:workbookPr", NS)
        if properties is not None and properties.get("date1904", "0") not in ("0", "false"):
            raise ValueError("unsupported Excel date epoch")
        strings = ["".join(item.itertext()) for item in ET.fromstring(archive.read("xl/sharedStrings.xml"))]
        rows = ET.fromstring(archive.read("xl/worksheets/sheet1.xml")).findall("m:sheetData/m:row", NS)
    result, headers = {}, []
    for index, row in enumerate(rows, 1):
        cells = list(row)
        if row.get("r") != str(index) or [cell.get("r") for cell in cells] != [f"{column}{index}" for column in "ABCDEFGHIJ"]:
            raise ValueError("workbook row/column schema drift")
        values = []
        for cell in cells:
            value = cell.find("m:v", NS)
            if value is None or value.text is None or cell.find("m:f", NS) is not None:
                raise ValueError("missing or formula workbook value")
            if cell.get("t") == "s":
                reference = int(value.text)
                if not 0 <= reference < len(strings):
                    raise ValueError("unknown shared string")
                values.append(strings[reference])
            elif cell.get("t") in (None, "n"):
                values.append(value.text)
            else:
                raise ValueError("unsupported workbook cell type")
        if index <= 2:
            headers.append([value.strip() for value in values])
            continue
        if cells[0].get("t") != "s" or cells[1].get("t") not in (None, "n") or any(cell.get("t") != "s" for cell in cells[2:]):
            raise ValueError("unexpected workbook locality/date/clock type")
        if not re.fullmatch(r"[0-9]+", values[1]):
            raise ValueError("noninteger Excel date")
        date = dt.date(1899, 12, 30) + dt.timedelta(days=int(values[1]))
        if date.year != year or not values[0].strip():
            raise ValueError("unexpected workbook year or locality")
        locality = result.setdefault(values[0], {})
        if date.isoformat() in locality:
            raise ValueError("duplicate workbook locality/date")
        locality[date.isoformat()] = tuple(clock(value) for value in values[2:])
    if len(headers) != 2 or headers[1] != HEADERS_RU or not result:
        raise ValueError("unknown workbook header or no rows")
    expected = {(dt.date(year, 1, 1) + dt.timedelta(days=index)).isoformat() for index in range((dt.date(year + 1, 1, 1) - dt.date(year, 1, 1)).days)}
    if any(set(values) != expected for values in result.values()):
        raise ValueError("workbook locality/year coverage gap")
    return result, {"shared_strings": len(strings), "sheet_rows": len(rows), "headers": headers, "localities": len(result)}


def compare_rows(actual, expected):
    common = sorted(set(actual) & set(expected))
    mismatches = [{"date": date, "field": FIELDS[index]} for date in common for index in range(8) if actual[date][index] != expected[date][index]]
    return {"common_dates": len(common), "compared_values": len(common) * 8,
            "mismatched_values": len(mismatches), "mismatch_locations": mismatches[:10],
            "missing_csv_dates": sorted(set(expected) - set(actual)),
            "unexpected_csv_dates": sorted(set(actual) - set(expected))}


def bind_exact(label, cities):
    candidates = [city for city in cities if city["region_id"] == "ru-ta" and (city["name"] == label or label in city.get("aliases", []))]
    keys = ("id", "name", "region_id", "settlement_type", "timezone", "geographic_source_id", "geographic_source", "latitude", "longitude")
    result = {"status": "no_exact_catalog_match", "canonical_city_ids": [], "match_basis": "UNKNOWN",
              "candidates": [{key: city[key] for key in keys if key in city} for city in sorted(candidates, key=lambda city: city["id"])]}
    if len(candidates) == 1:
        result.update(status="unambiguous_exact_catalog_match", canonical_city_ids=[candidates[0]["id"]],
                      match_basis="exact_catalog_name" if candidates[0]["name"] == label else "exact_existing_catalog_alias")
    elif candidates:
        result["status"] = "ambiguous_exact_catalog_matches"
    return result


def artifact(path, url):
    raw = path.read_bytes()
    return {"filename": path.name, "canonical_url": url, "sha256": sha(raw), "byte_length": len(raw)}


def response_metadata(path):
    # Only an allowlist of ordinary response metadata; never copy set-cookie.
    fields, statuses = {}, []
    for line in path.read_text().splitlines():
        if line.startswith("HTTP/"):
            statuses.append(int(line.split()[1]))
        elif ":" in line:
            key, value = line.split(":", 1)
            if key.lower() in ("date", "content-type", "last-modified", "etag", "content-length"):
                fields[key.lower().replace("-", "_")] = value.strip()
    return {"http_status": statuses[-1] if statuses else None, **fields}


def build_report(root):
    selector = parse_selector((root / "dumrt.html").read_text())
    if len(selector) != 44:
        raise ValueError("reviewed selector no longer contains exactly 44 options")
    workbook, workbook_shape = parse_workbook((root / "dumrt-2026.xlsx").read_bytes(), YEAR)
    if len(workbook) != 44:
        raise ValueError("reviewed workbook no longer contains exactly 44 localities")
    catalog_path = root / "russia-cities-2026-09-08.json"
    catalog = json.loads(catalog_path.read_bytes())
    if catalog["schema_version"] != "namaztime-city-catalog/v1" or not any(region["id"] == "ru-ta" and region["federal_subject_code"] == "RU-TA" for region in catalog["regions"]):
        raise ValueError("unexpected canonical catalog or subject mapping")
    filenames = ["dumrt-kazan.csv" if item["filename"] == "Kazan.csv" else "dumrt-" + item["filename"] for item in selector]
    helper = Path(__file__).with_name("dumrt_parse_check.go").resolve()
    process = subprocess.run(["go", "run", str(helper), "-csv-dir", str(root)], input=json.dumps(filenames), text=True, capture_output=True, check=True, cwd=Path(__file__).resolve().parents[2])
    parsed = json.loads(process.stdout)
    if set(parsed) != set(filenames):
        raise ValueError("provider checker omitted or added files")
    localities = []
    for item, filename in zip(selector, filenames):
        raw = (root / filename).read_bytes()
        schema_errors = []
        try:
            rows = parse_csv(raw, YEAR)
        except ValueError:
            rows, schema_errors = diagnostic_current_rows(raw, YEAR)
            if not schema_errors:
                raise ValueError("CSV failed cross-row validation; diagnostic comparison refused")
        full_content_matches = [label for label, values in workbook.items() if values == rows]
        # Content equality is evidence about artifacts, never an inferred name alias.
        workbook_label = item["label"] if item["label"] in workbook else None
        relation = "exact_label"
        if workbook_label is None and len(full_content_matches) == 1:
            workbook_label = full_content_matches[0]
            relation = "unique_full_content_match_identity_unresolved"
        if workbook_label is None:
            raise ValueError(f"cannot compare selector {item['label']} against workbook")
        comparison = compare_rows(rows, workbook[workbook_label])
        september = {date: values for date, values in workbook[workbook_label].items() if FROM <= date <= TO}
        parser_result = parsed[filename]
        if parser_result["raw_sha256"] != sha(raw):
            raise ValueError("raw file changed between independent checks")
        parser_result["independent_xlsx_fields_equal"] = parser_result["status"] == "pass" and parser_result["fields_sha256"] == digest_rows(september)
        binding = bind_exact(item["label"], catalog["cities"])
        headers = "dumrt-kazan.headers" if item["filename"] == "Kazan.csv" else filename + ".headers"
        gaps = []
        if not binding["canonical_city_ids"]:
            gaps.append("Exact canonical locality identity unresolved; no nearby/population/admin-class choice.")
        if relation != "exact_label":
            gaps.append("CSV and workbook contents agree, but HTML Алексеевск versus XLSX Алексеевское is not a verified geographic alias.")
        if comparison["missing_csv_dates"]:
            gaps.append("CSV annual coverage is incomplete; no workbook backfill was performed.")
        if schema_errors:
            gaps.append("Whole-artifact schema is invalid. Comparison covers only individually parseable rows and is diagnostic, never a candidate.")
        if parser_result["annual_status"] != "pass":
            gaps.append("Whole-year public-parser normalization fails; only the explicitly tested range is reported.")
        localities.append({**item, "retained_filename": filename, "evidence_label": "CONFIRMED_PUBLIC",
                           "raw_sha256": sha(raw), "byte_length": len(raw), "response_metadata": response_metadata(root / headers),
                           "source_year": YEAR, "csv_current_year_rows": len(rows), "scope": "named locality only; not all surrounding district or republic",
                           "csv_schema_errors": schema_errors,
                           "canonical_city_ids": binding["canonical_city_ids"], "catalog_binding": binding,
                           "timezone": "Europe/Moscow" if binding["canonical_city_ids"] else "UNKNOWN",
                           "workbook_label": workbook_label, "workbook_relation": relation,
                           "annual_csv_xlsx_comparison": comparison, "september_parser": parser_result,
                           "qualification_status": "not_assessed", "qualification_gaps": gaps})
    summary = {"selector_localities": len(localities), "workbook_localities": len(workbook),
               "csv_current_year_rows": sum(item["csv_current_year_rows"] for item in localities),
               "compared_values": sum(item["annual_csv_xlsx_comparison"]["compared_values"] for item in localities),
               "mismatched_values": sum(item["annual_csv_xlsx_comparison"]["mismatched_values"] for item in localities),
               "missing_csv_dates": sum(len(item["annual_csv_xlsx_comparison"]["missing_csv_dates"]) for item in localities),
               "unambiguous_canonical_bindings": sum(bool(item["canonical_city_ids"]) for item in localities),
               "unresolved_canonical_bindings": sum(not item["canonical_city_ids"] for item in localities),
               "september_parser_pass": sum(item["september_parser"]["status"] == "pass" for item in localities),
               "september_parser_xlsx_equal": sum(item["september_parser"]["independent_xlsx_fields_equal"] for item in localities),
               "annual_parser_pass": sum(item["september_parser"]["annual_status"] == "pass" for item in localities)}
    return {"schema_version": "t049-dumrt-localities/v1", "researched_at": "2026-09-08", "subject_code": "RU-TA",
            "qualification_status": "not_assessed", "coverage_tested": {"from": FROM, "to": TO},
            "comparison_fields_in_source_order": FIELDS,
            "selector_artifact": artifact(root / "dumrt.html", PAGE_URL),
            "workbook_artifact": {**artifact(root / "dumrt-2026.xlsx", XLSX_URL), **workbook_shape},
            "catalog": {"retained_filename": catalog_path.name, "file_sha256": sha(catalog_path.read_bytes()), "revision": catalog["revision"], "tatarstan_localities": sum(city["region_id"] == "ru-ta" for city in catalog["cities"])},
            "interpretation": "Source fields are compared literally. Parser execution is not qualification, endorsement or signed publication. No district-wide scope or unresolved alias is inferred.",
            "summary": summary, "localities": localities}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--check-report", type=Path)
    args = parser.parse_args()
    report = build_report(args.artifacts.resolve())
    if args.check_report:
        if json.loads(args.check_report.read_text()) != report:
            raise ValueError("retained report differs from reproduced evidence")
        print(json.dumps(report["summary"], ensure_ascii=False))
    else:
        print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"DUM RT comparison failed closed: {error}", file=sys.stderr)
        sys.exit(1)
