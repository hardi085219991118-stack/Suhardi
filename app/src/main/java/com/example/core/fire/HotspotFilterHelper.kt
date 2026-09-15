package com.example.core.fire

import com.example.core.location.DeviceLocation
import com.example.core.share.FireHotspotShareHelper
import com.example.ui.dashboard.HotspotFilterCriteria

/**
 * Logika filter titik panas satelit sesuai audit teknis:
 * 1. Filter Waktu: HotspotFilterCriteria.maxAgeHours (6 jam, 12 jam, 24 jam, semua data)
 *    Berdasarkan record.acquisitionTimestampMillis dan waktu sekarang.
 * 2. Filter Satelit: Normalisasi N21 -> NOAA-21, N20 -> NOAA-20, SNPP -> Suomi-NPP, MODIS -> Terra/Aqua.
 *    Data mentah NASA FIRMS tidak dimodifikasi.
 */
object HotspotFilterHelper {

  /**
   * Normalisasi nama satelit untuk pencarian/filtering tanpa mengubah data record NASA mentah:
   * N21 / NOAA-21 / 21 -> NOAA-21
   * N20 / NOAA-20 / 20 -> NOAA-20
   * SNPP / Suomi-NPP / NPP / N -> Suomi-NPP
   * Terra / T -> Terra
   * Aqua / A -> Aqua
   */
  fun normalizeSatelliteName(rawSat: String): String {
    val clean = rawSat.trim()
    return when {
      clean.equals("N21", ignoreCase = true) ||
        clean.equals("NOAA-21", ignoreCase = true) ||
        clean.equals("NOAA21", ignoreCase = true) ||
        clean == "21" -> "NOAA-21"

      clean.equals("N20", ignoreCase = true) ||
        clean.equals("NOAA-20", ignoreCase = true) ||
        clean.equals("NOAA20", ignoreCase = true) ||
        clean == "20" -> "NOAA-20"

      clean.equals("SNPP", ignoreCase = true) ||
        clean.equals("Suomi-NPP", ignoreCase = true) ||
        clean.equals("NPP", ignoreCase = true) ||
        clean.equals("N", ignoreCase = true) -> "Suomi-NPP"

      clean.equals("Terra", ignoreCase = true) ||
        clean.equals("T", ignoreCase = true) -> "Terra"

      clean.equals("Aqua", ignoreCase = true) ||
        clean.equals("A", ignoreCase = true) -> "Aqua"

      else -> clean
    }
  }

  fun matchesSatellite(recordSatellite: String, filterSatellite: String?): Boolean {
    if (filterSatellite.isNullOrBlank()) return true
    val normRecord = normalizeSatelliteName(recordSatellite)
    val normFilter = normalizeSatelliteName(filterSatellite)

    if (normFilter.equals("MODIS", ignoreCase = true)) {
      return normRecord.equals("Terra", ignoreCase = true) || normRecord.equals("Aqua", ignoreCase = true)
    }

    return normRecord.equals(normFilter, ignoreCase = true)
  }

  /**
   * Memfilter usia data satelit menggunakan acquisitionTimestampMillis dan waktu sekarang.
   * Dilarang keras menggunakan waktu fetch lokal sebagai waktu akuisisi satelit.
   */
  fun matchesAge(
    record: FireDataRecord,
    maxAgeHours: Int?,
    currentTimeMillis: Long = System.currentTimeMillis()
  ): Boolean {
    if (maxAgeHours == null) return true
    val acqTime = record.acquisitionTimestampMillis ?: return false
    val diffMillis = currentTimeMillis - acqTime
    if (diffMillis < 0) return true // Clock skew kecil diizinkan
    val elapsedHours = diffMillis / (1000.0 * 3600.0)
    return elapsedHours <= maxAgeHours.toDouble()
  }

  fun filterRecords(
    records: List<FireDataRecord>,
    criteria: HotspotFilterCriteria,
    deviceLocation: DeviceLocation? = null,
    currentTimeMillis: Long = System.currentTimeMillis()
  ): List<FireDataRecord> {
    val isGpsValid = deviceLocation != null &&
      com.example.core.map.CoordinateValidator.isValid(deviceLocation.latitude, deviceLocation.longitude) &&
      !(deviceLocation.latitude == 0.0 && deviceLocation.longitude == 0.0)

    return records.filter { record ->
      val matchesSat = matchesSatellite(record.satellite, criteria.satellite)
      val matchesAge = matchesAge(record, criteria.maxAgeHours, currentTimeMillis)
      val matchesDist = if (criteria.maxDistanceKm != null) {
        if (isGpsValid && deviceLocation != null) {
          val dist = FireHotspotShareHelper.calculateDistanceKm(
            deviceLocation.latitude,
            deviceLocation.longitude,
            record.latitude,
            record.longitude
          )
          dist <= criteria.maxDistanceKm
        } else {
          // B3: Jika GPS belum tersedia dan filter jarak aktif, jangan loloskan record seolah-olah filter tidak aktif
          false
        }
      } else {
        true
      }
      matchesSat && matchesAge && matchesDist
    }
  }
}
