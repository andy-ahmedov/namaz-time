import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from one_muslim_ulyanovsk_compare import (
    PRAYERS,
    compare_schedules,
    load_snapshot,
    load_stored_timetable,
    minute_of_day,
)


class OneMuslimUlyanovskCompareTest(unittest.TestCase):
    def test_minute_of_day_accepts_unpadded_hours_and_rejects_invalid_time(self):
        self.assertEqual(minute_of_day("2:07"), 127)
        self.assertEqual(minute_of_day("23:59"), 1439)
        for value in ("24:00", "10:60", "bad", ""):
            with self.subTest(value=value), self.assertRaises(ValueError):
                minute_of_day(value)

    def test_load_stored_timetable_projects_synthetic_template(self):
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "synthetic.sqlite"
            self._create_synthetic_database(database)

            metadata, non_leap = load_stored_timetable(database, city_id=7, year=2026)
            _, leap = load_stored_timetable(database, city_id=7, year=2028)

            self.assertEqual(metadata["city_id"], 7)
            self.assertEqual(metadata["timezone"], "Europe/Ulyanovsk")
            self.assertEqual(len(non_leap), 365)
            self.assertNotIn("2026-02-29", non_leap)
            self.assertEqual(len(leap), 366)
            self.assertIn("2028-02-29", leap)

    def test_compare_schedules_reports_field_buckets_days_and_material_runs(self):
        app = self._schedule(
            {
                "2026-01-01": ["06:00", "08:00", "12:00", "15:00", "17:00", "19:00"],
                "2026-01-02": ["06:01", "08:02", "12:05", "15:06", "17:00", "19:00"],
                "2026-01-03": ["06:00", "08:00", "12:10", "15:10", "17:00", "19:00"],
                "2026-01-04": ["06:00", "08:00", "12:10", "15:10", "17:00", "19:00"],
            }
        )
        reference = self._schedule(
            {
                "2026-01-01": ["06:00", "08:00", "12:00", "15:00", "17:00", "19:00"],
                "2026-01-02": ["06:00", "08:00", "12:00", "15:00", "17:00", "19:00"],
                "2026-01-03": ["06:00", "08:00", "12:00", "15:00", "17:00", "19:00"],
                "2026-01-04": ["06:00", "08:00", "12:00", "15:00", "17:00", "19:00"],
            }
        )

        result = compare_schedules(app, reference)

        self.assertEqual(result["dates"], 4)
        self.assertEqual(result["fields"], 24)
        self.assertEqual(
            result["field_buckets"],
            {"exact": 16, "plus_or_minus_1": 1, "plus_or_minus_2_to_5": 2, "over_5": 5},
        )
        self.assertEqual(result["all_prayers_exact_days"], 1)
        self.assertEqual(result["all_prayers_within_1_minute_days"], 1)
        self.assertEqual(result["maximum_absolute_delta_minutes"], 10)
        self.assertEqual(
            result["material_runs"],
            [
                {"prayer": "dhuhr", "delta_minutes": 10, "ranges": [{"from": "2026-01-03", "to": "2026-01-04"}]},
                {"prayer": "asr", "delta_minutes": 6, "ranges": [{"from": "2026-01-02", "to": "2026-01-02"}]},
                {"prayer": "asr", "delta_minutes": 10, "ranges": [{"from": "2026-01-03", "to": "2026-01-04"}]},
            ],
        )

    def test_snapshot_loader_accepts_only_requested_year_and_prayer_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            snapshot = Path(directory) / "snapshot.json"
            snapshot.write_text(
                json.dumps(
                    {
                        "prayer_days": [
                            {
                                "date": "2026-01-01",
                                "fajr": "06:00",
                                "sunrise": "08:00",
                                "dhuhr": "12:00",
                                "asr": "15:00",
                                "maghrib": "17:00",
                                "isha": "19:00",
                                "ignored": "synthetic",
                            }
                        ]
                    }
                ),
                encoding="utf-8",
            )
            self.assertEqual(load_snapshot(snapshot, 2026)["2026-01-01"]["fajr"], "06:00")
            with self.assertRaises(ValueError):
                load_snapshot(snapshot, 2027)

    @staticmethod
    def _schedule(rows):
        return {day: dict(zip(PRAYERS, values, strict=True)) for day, values in rows.items()}

    @staticmethod
    def _create_synthetic_database(path):
        connection = sqlite3.connect(path)
        connection.executescript(
            """
            CREATE TABLE Cities (
                Id INTEGER PRIMARY KEY,
                Latitude REAL NOT NULL,
                Longitude REAL NOT NULL,
                TimezoneName TEXT NOT NULL
            );
            CREATE TABLE PrayerDays (
                CityId INTEGER NOT NULL,
                Month INTEGER NOT NULL,
                Day INTEGER NOT NULL,
                Time1 TEXT NOT NULL,
                Sunrise TEXT NOT NULL,
                Time2 TEXT NOT NULL,
                Time3 TEXT NOT NULL,
                Time4 TEXT NOT NULL,
                Time5 TEXT NOT NULL
            );
            INSERT INTO Cities VALUES (7, 54.3, 48.3, 'Europe/Ulyanovsk');
            """
        )
        import datetime

        current = datetime.date(2028, 1, 1)
        for offset in range(366):
            day = current + datetime.timedelta(days=offset)
            connection.execute(
                "INSERT INTO PrayerDays VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (7, day.month, day.day, "06:00", "08:00", "12:00", "15:00", "17:00", "19:00"),
            )
        connection.commit()
        connection.close()


if __name__ == "__main__":
    unittest.main()
