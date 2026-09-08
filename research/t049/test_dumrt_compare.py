"""Synthetic metadata/times only; no retained timetable corpus."""

import datetime as dt
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

import dumrt_compare as subject


def synthetic_workbook(mutate=None, epoch="0"):
    namespace = subject.NS["m"]
    strings = list(subject.HEADERS_RU) + ["Пример", "04:01", "05:02", "06:03", "11:04", "12:05", "16:06", "18:07", "20:08"]
    sheet = ET.Element("worksheet", xmlns=namespace)
    data = ET.SubElement(sheet, "sheetData")
    for index in (1, 2):
        row = ET.SubElement(data, "row", r=str(index))
        for column, reference in zip("ABCDEFGHIJ", range(10)):
            cell = ET.SubElement(row, "c", r=f"{column}{index}", t="s")
            ET.SubElement(cell, "v").text = str(reference)
    for offset in range(365):
        index = offset + 3
        row = ET.SubElement(data, "row", r=str(index))
        values = ["10", str((dt.date(2026, 1, 1) - dt.date(1899, 12, 30)).days + offset)] + [str(i) for i in range(11, 19)]
        for column, value in zip("ABCDEFGHIJ", values):
            attrs = {"r": f"{column}{index}"}
            if column != "B":
                attrs["t"] = "s"
            cell = ET.SubElement(row, "c", attrs)
            ET.SubElement(cell, "v").text = value
    if mutate:
        mutate(data, strings)
    shared = ET.Element("sst", xmlns=namespace)
    for value in strings:
        ET.SubElement(ET.SubElement(shared, "si"), "t").text = value
    raw = io.BytesIO()
    with zipfile.ZipFile(raw, "w") as archive:
        archive.writestr("xl/workbook.xml", f'<workbook xmlns="{namespace}"><workbookPr date1904="{epoch}"/></workbook>')
        archive.writestr("xl/sharedStrings.xml", ET.tostring(shared))
        archive.writestr("xl/worksheets/sheet1.xml", ET.tostring(sheet))
    return raw.getvalue()


class ComparisonTests(unittest.TestCase):
    def test_selector_preserves_case_and_explicit_url_without_invented_alias(self):
        html = '<option class="prayer_city_select" data-url="/netcat_files/391/638/TestTown.csv?t=080926">Example &amp; Town</option>'
        self.assertEqual(subject.parse_selector(html), [{
            "label": "Example & Town", "filename": "TestTown.csv",
            "actual_url": "https://dumrt.ru/netcat_files/391/638/TestTown.csv?t=080926",
            "canonical_url": "https://dumrt.ru/netcat_files/391/638/TestTown.csv",
        }])

    def test_selector_rejects_duplicate_or_non_first_party_transport(self):
        item = '<option class="prayer_city_select" data-url="/netcat_files/391/638/Test.csv">Example</option>'
        for text in (item + item, item.replace('/netcat_files/391/638/Test.csv', 'https://other.invalid/Test.csv')):
            with self.subTest(text=text), self.assertRaises(ValueError):
                subject.parse_selector(text)

    def test_csv_preserves_eight_columns_and_excludes_known_historical_qibla(self):
        raw = b'01.01.2025;4:01;05:02;6:03;11:04;12:05;16:06;18:07;20:08;12:34\r\n01.09.2026;4:01;05:02;6:03;11:04;12:05;16:06;18:07;20:08\r\n'
        self.assertEqual(subject.parse_csv(raw, 2026), {
            "2026-09-01": ("04:01", "05:02", "06:03", "11:04", "12:05", "16:06", "18:07", "20:08"),
        })

    def test_csv_rejects_duplicate_date_unknown_columns_markers_and_blank_rows(self):
        row = b'01.09.2026;4:01;05:02;6:03;11:04;12:05;16:06;18:07;20:08\n'
        for raw in (row + row, row.replace(b'20:08', b'20:08;12:34'),
                    row.replace(b'4:01', b'4:01*'), row + b'\n',
                    row.replace(b'4:01', b'24:01')):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                subject.parse_csv(raw, 2026)

    def test_diagnostic_comparison_records_bad_historical_row_without_repairing_it(self):
        good = b'02.01.2026;4:01;05:02;6:03;11:04;12:05;16:06;18:07;20:08\n'
        bad = b'31.12.2025;4:01;05:02;6:03;11:04;12:05;16:06;18:07;20:08;12:3401.01.2026;4:01;05:02;6:03;11:04;12:05;16:06;18:07;20:08\n'
        rows, errors = subject.diagnostic_current_rows(bad + good, 2026)
        self.assertEqual(sorted(rows), ["2026-01-02"])
        self.assertEqual(errors, [{"row": 1, "date_field": "31.12.2025", "columns": 18}])

    def test_comparison_reports_gap_and_column_difference_without_filling_either(self):
        times = ("04:01", "05:02", "06:03", "11:04", "12:05", "16:06", "18:07", "20:08")
        actual = {"2026-09-02": times, "2026-09-03": times}
        expected = {"2026-09-01": times, "2026-09-02": times[:-1] + ("20:09",)}
        result = subject.compare_rows(actual, expected)
        self.assertEqual(result.get("common_dates"), 1)
        self.assertEqual(result.get("compared_values"), 8)
        self.assertEqual(result.get("mismatched_values"), 1)
        self.assertEqual(result.get("missing_csv_dates"), ["2026-09-01"])
        self.assertEqual(result.get("unexpected_csv_dates"), ["2026-09-03"])

    def test_exact_binding_uses_existing_alias_and_region_only(self):
        city = {"id": "synthetic-id", "name": "Example Town", "aliases": ["Пример"],
                "region_id": "ru-ta", "timezone": "Europe/Moscow", "geographic_source_id": "geonames:synthetic"}
        other_region = dict(city, id="not-tatarstan", region_id="ru-xx")
        result = subject.bind_exact("Пример", [city, other_region])
        self.assertEqual(result.get("canonical_city_ids"), ["synthetic-id"])
        self.assertEqual(result.get("match_basis"), "exact_existing_catalog_alias")
        self.assertEqual(subject.bind_exact("пример", [city]).get("canonical_city_ids"), [])

    def test_ambiguous_label_never_chooses_population_or_admin_class(self):
        city = {"id": "synthetic-one", "name": "Пример", "aliases": [],
                "region_id": "ru-ta", "timezone": "Europe/Moscow"}
        alternate = dict(city, id="synthetic-two", population=100000, settlement_type="PPLA2")
        result = subject.bind_exact("Пример", [city, alternate])
        self.assertEqual(result.get("canonical_city_ids"), [])
        self.assertEqual(result.get("status"), "ambiguous_exact_catalog_matches")

    def test_workbook_independently_reads_excel_dates_and_shared_string_fields(self):
        rows, metadata = subject.parse_workbook(synthetic_workbook(), 2026)
        self.assertEqual(metadata["sheet_rows"], 367)
        self.assertEqual(len(rows["Пример"]), 365)
        self.assertEqual(rows["Пример"]["2026-09-08"], ("04:01", "05:02", "06:03", "11:04", "12:05", "16:06", "18:07", "20:08"))

    def test_workbook_fails_closed_for_gaps_duplicate_dates_formulas_and_header_drift(self):
        mutations = [
            lambda rows, strings: rows.remove(rows[-1]),
            lambda rows, strings: setattr(rows[3][1][0], "text", rows[2][1][0].text),
            lambda rows, strings: ET.SubElement(rows[2][2], "f"),
            lambda rows, strings: strings.__setitem__(2, "Unknown column"),
            lambda rows, strings: rows[2][2].set("r", "K3"),
        ]
        for mutate in mutations:
            with self.subTest(mutate=mutate), self.assertRaises(ValueError):
                subject.parse_workbook(synthetic_workbook(mutate), 2026)
        with self.assertRaises(ValueError):
            subject.parse_workbook(synthetic_workbook(epoch="1"), 2026)

    def test_workbook_preserves_unrepresentable_values_for_comparison_without_repair(self):
        rows, _ = subject.parse_workbook(synthetic_workbook(lambda rows, strings: strings.__setitem__(11, "23:54")), 2026)
        self.assertEqual(rows["Пример"]["2026-05-05"][0], "23:54")

    def test_public_go_checker_agrees_with_independent_field_digest(self):
        values = ("04:01", "05:02", "06:03", "11:04", "12:05", "16:06", "18:07", "20:08")
        rows = {(dt.date(2026, 9, 1) + dt.timedelta(days=index)).isoformat(): values for index in range(30)}
        raw = "".join(dt.date.fromisoformat(date).strftime("%d.%m.%Y") + ";" + ";".join(times) + "\n" for date, times in rows.items())
        with tempfile.TemporaryDirectory(prefix="namaz-dumrt-synthetic-") as directory:
            (Path(directory) / "dumrt-Synthetic.csv").write_text(raw)
            helper = Path(__file__).with_name("dumrt_parse_check.go").resolve()
            completed = subprocess.run(["go", "run", str(helper), "-csv-dir", directory],
                                       input='["dumrt-Synthetic.csv"]', text=True, capture_output=True, check=True,
                                       cwd=Path(__file__).resolve().parents[2])
        result = json.loads(completed.stdout)["dumrt-Synthetic.csv"]
        self.assertEqual(result["status"], "pass")
        self.assertEqual(result["days"], 30)
        self.assertEqual(result["fields_sha256"], subject.digest_rows(rows))
        self.assertEqual(result["raw_sha256"], subject.sha(raw.encode()))
        self.assertEqual(result["annual_status"], "fail")


if __name__ == "__main__":
    unittest.main()
