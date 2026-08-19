package com.example.namaztime.tv.repository

import kotlinx.coroutines.flow.Flow

data class LocalPrayerDay(
    val localDate: String,
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
)

data class LocalPrayerSchedule(
    val snapshotId: String,
    val mosqueId: String,
    val mosqueName: String,
    val timezoneId: String,
    val sourceKind: String,
    val authorityName: String,
    val coverageFrom: String,
    val coverageTo: String,
    val days: List<LocalPrayerDay>,
)

interface PrayerScheduleRepository {
    fun observeActiveSchedule(): Flow<LocalPrayerSchedule?>
}
