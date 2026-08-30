#!/usr/bin/env python3
"""Compare an external 1Muslim stored timetable with NamazTime fixtures.

The APK and extracted database remain external research inputs. This tool emits
only city metadata and independently generated aggregate comparison statistics;
it never exports the source timetable rows.
"""

from __future__ import annotations

import argparse
import calendar
import csv
import hashlib
import json
import sqlite3
from collections import Counter, defaultdict
from datetime import date, timedelta
from pathlib import Path
from typing import Iterable, Mapping


PRAYERS = ("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
DATABASE_COLUMNS = {
    "fajr": "Time1",
    "sunrise": "Sunrise",
    "dhuhr": "Time2",
    "asr": "Time3",
    "maghrib": "Time4",
    "isha": "Time5",
}
BUCKETS = ("exact", "plus_or_minus_1", "plus_or_minus_2_to_5", "over_5")


def minute_of_day(value: str) -> int:
    try:
        parts = value.split(":")
        if len(parts) != 2:
            raise ValueError
        hour, minute = (int(part) for part in parts)
    except (AttributeError, TypeError, ValueError) as error:
        raise ValueError(f"invalid prayer time {value!r}") from error
    if not 0 <= hour <= 23 or not 0 <= minute <= 59:
        raise ValueError(f"invalid prayer time {value!r}")
    return hour * 60 + minute


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_stored_timetable(database_path: Path, city_id: int, year: int):
    database_path = database_path.resolve()
    if not database_path.is_file():
        raise ValueError(f"database does not exist: {database_path}")

    connection = sqlite3.connect(database_path.as_uri() + "?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        city = connection.execute(
            "SELECT Id, Latitude, Longitude, TimezoneName FROM Cities WHERE Id = ?",
            (city_id,),
        ).fetchone()
        if city is None:
            raise ValueError(f"city ID {city_id} is absent")
        rows = connection.execute(
            """
            SELECT Month, Day, Time1, Sunrise, Time2, Time3, Time4, Time5
            FROM PrayerDays
            WHERE CityId = ?
            ORDER BY Month, Day
            """,
            (city_id,),
        ).fetchall()
    finally:
        connection.close()

    if len(rows) != 366:
        raise ValueError(f"city ID {city_id} has {len(rows)} rows, expected 366")

    schedule = {}
    seen_month_days = set()
    for row in rows:
        month_day = (row["Month"], row["Day"])
        if month_day in seen_month_days:
            raise ValueError(f"duplicate month/day row: {month_day}")
        seen_month_days.add(month_day)
        try:
            resolved_date = date(year, *month_day)
        except ValueError:
            if month_day == (2, 29) and not calendar.isleap(year):
                continue
            raise
        values = {prayer: row[column] for prayer, column in DATABASE_COLUMNS.items()}
        for value in values.values():
            minute_of_day(value)
        schedule[resolved_date.isoformat()] = values

    expected_dates = 366 if calendar.isleap(year) else 365
    if len(schedule) != expected_dates:
        raise ValueError(f"projected schedule has {len(schedule)} dates, expected {expected_dates}")

    metadata = {
        "database_sha256": sha256_file(database_path),
        "city_id": city["Id"],
        "latitude": city["Latitude"],
        "longitude": city["Longitude"],
        "timezone": city["TimezoneName"],
        "template_rows": len(rows),
        "projected_year": year,
    }
    return metadata, schedule


def load_csv(reference_path: Path, year: int):
    with reference_path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    return _reference_rows(rows, year, str(reference_path))


def load_snapshot(reference_path: Path, year: int):
    document = json.loads(reference_path.read_text(encoding="utf-8"))
    rows = document.get("prayer_days")
    if not isinstance(rows, list):
        raise ValueError(f"{reference_path} has no prayer_days array")
    return _reference_rows(rows, year, str(reference_path))


def _reference_rows(rows: Iterable[Mapping[str, str]], year: int, source: str):
    schedule = {}
    for row in rows:
        day = row.get("date")
        try:
            parsed = date.fromisoformat(day)
        except (TypeError, ValueError) as error:
            raise ValueError(f"invalid date {day!r} in {source}") from error
        if parsed.year != year:
            continue
        if day in schedule:
            raise ValueError(f"duplicate reference date {day}")
        values = {}
        for prayer in PRAYERS:
            value = row.get(prayer)
            minute_of_day(value)
            values[prayer] = value
        schedule[day] = values
    if not schedule:
        raise ValueError(f"{source} has no prayer rows for {year}")
    return schedule


def _bucket(delta: int) -> str:
    absolute = abs(delta)
    if absolute == 0:
        return "exact"
    if absolute == 1:
        return "plus_or_minus_1"
    if absolute <= 5:
        return "plus_or_minus_2_to_5"
    return "over_5"


def _counter(counter: Counter):
    return {bucket: counter.get(bucket, 0) for bucket in BUCKETS}


def _ranges(days: Iterable[str]):
    ordered = sorted(date.fromisoformat(day) for day in days)
    if not ordered:
        return []
    result = []
    start = previous = ordered[0]
    for current in ordered[1:]:
        if current == previous + timedelta(days=1):
            previous = current
            continue
        result.append({"from": start.isoformat(), "to": previous.isoformat()})
        start = previous = current
    result.append({"from": start.isoformat(), "to": previous.isoformat()})
    return result


def compare_schedules(app_schedule, reference_schedule):
    app_dates = set(app_schedule)
    reference_dates = set(reference_schedule)
    if app_dates != reference_dates:
        missing = sorted(app_dates - reference_dates)
        extra = sorted(reference_dates - app_dates)
        raise ValueError(
            f"date sets differ: absent from reference={missing[:5]}, absent from app={extra[:5]}"
        )

    fields = []
    by_prayer = {prayer: Counter() for prayer in PRAYERS}
    monthly_nonzero = defaultdict(Counter)
    material_dates = defaultdict(list)
    exact_days = 0
    within_one_days = 0

    for day in sorted(app_dates):
        day_deltas = []
        for prayer in PRAYERS:
            delta = minute_of_day(app_schedule[day][prayer]) - minute_of_day(reference_schedule[day][prayer])
            category = _bucket(delta)
            fields.append((day, prayer, delta, category))
            by_prayer[prayer][category] += 1
            day_deltas.append(delta)
            if delta:
                monthly_nonzero[day[:7]][prayer] += 1
            if abs(delta) > 5:
                material_dates[(prayer, delta)].append(day)
        exact_days += all(delta == 0 for delta in day_deltas)
        within_one_days += all(abs(delta) <= 1 for delta in day_deltas)

    total = len(fields)
    buckets = Counter(field[3] for field in fields)
    maximum = max(abs(field[2]) for field in fields)
    maximum_locations = [
        {"date": day, "prayer": prayer, "delta_minutes": delta}
        for day, prayer, delta, _ in fields
        if abs(delta) == maximum
    ]
    material_runs = [
        {"prayer": prayer, "delta_minutes": delta, "ranges": _ranges(days)}
        for (prayer, delta), days in material_dates.items()
    ]
    material_runs.sort(key=lambda item: (PRAYERS.index(item["prayer"]), item["delta_minutes"]))

    return {
        "dates": len(app_dates),
        "fields": total,
        "field_buckets": _counter(buckets),
        "field_percentages": {bucket: round(100 * buckets.get(bucket, 0) / total, 2) for bucket in BUCKETS},
        "all_prayers_exact_days": exact_days,
        "all_prayers_within_1_minute_days": within_one_days,
        "per_prayer": {prayer: _counter(by_prayer[prayer]) for prayer in PRAYERS},
        "nonzero_fields_by_month": {
            month: {prayer: counts.get(prayer, 0) for prayer in PRAYERS}
            for month, counts in sorted(monthly_nonzero.items())
        },
        "maximum_absolute_delta_minutes": maximum,
        "maximum_locations": maximum_locations,
        "material_runs": material_runs,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, required=True, help="external extracted timetable SQLite")
    parser.add_argument("--city-id", type=int, default=1187)
    parser.add_argument("--year", type=int, default=2026)
    references = parser.add_mutually_exclusive_group(required=True)
    references.add_argument("--reference-csv", type=Path)
    references.add_argument("--reference-snapshot", type=Path)
    parser.add_argument("--reference-name", default="reference")
    args = parser.parse_args()

    metadata, app_schedule = load_stored_timetable(args.database, args.city_id, args.year)
    if args.reference_csv:
        reference_schedule = load_csv(args.reference_csv, args.year)
    else:
        reference_schedule = load_snapshot(args.reference_snapshot, args.year)

    document = {
        "evidence": "CONFIRMED_STATIC",
        "reference": args.reference_name,
        "source": metadata,
        "comparison": compare_schedules(app_schedule, reference_schedule),
        "sanitization": {
            "raw_competitor_rows_emitted": False,
            "decompiled_code_used": False,
            "aggregate_statistics_only": True,
        },
    }
    print(json.dumps(document, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
