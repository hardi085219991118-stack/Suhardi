package com.example.core.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

/**
 * Helper untuk navigasi ke lokasi titik panas NASA FIRMS.
 *
 * MANDAT ZERO-DUMMY & KEAMANAN:
 * - Tidak memerlukan API Key Google Maps atau kredensial eksternal.
 * - Koordinat tujuan adalah titik panas aktual (BUKAN lokasi pengguna, bukan default, bukan dummy).
 * - Koordinat divalidasi ketat (-90..90, -180..180, bukan NaN/Infinite).
 * - Jika invalid, intent navigasi DILARANG dibuat.
 * - Prioritas navigasi:
 *   1. Aplikasi Google Maps (jika terpasang)
 *   2. Aplikasi navigasi/peta lain melalui Intent geo
 *   3. Fallback peramban web
 *   4. Pesan ramah jika tidak ada aplikasi yang dapat menangani intent.
 * - Perhitungan jarak dan bearing geografis hanya jika GPS pengguna aktual valid.
 */
object HotspotNavigationHelper {

  sealed class NavigationResult {
    data class Success(val appType: String, val intent: Intent) : NavigationResult()
    object InvalidCoordinates : NavigationResult()
    data class NoAppAvailable(val message: String) : NavigationResult()
  }

  /**
   * Memvalidasi koordinat latitude dan longitude geografis.
   */
  fun validateCoordinates(latitude: Double, longitude: Double): Boolean {
    return CoordinateValidator.isValid(latitude, longitude)
  }

  /**
   * Membuat Intent navigasi khusus Google Maps (google.navigation:q=lat,lon).
   * Mengembalikan null jika koordinat tidak valid.
   */
  fun createGoogleMapsNavigationIntent(
    latitude: Double,
    longitude: Double,
    label: String = "Titik Panas NASA FIRMS"
  ): Intent? {
    if (!validateCoordinates(latitude, longitude)) return null
    val uri = Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f", latitude, longitude))
    return Intent(Intent.ACTION_VIEW, uri).apply {
      setPackage("com.google.android.apps.maps")
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
  }

  /**
   * Membuat Intent geo generik (geo:lat,lon?q=lat,lon(label)) yang dapat ditangani oleh aplikasi peta apa pun.
   * Mengembalikan null jika koordinat tidak valid.
   */
  fun createGeoIntent(
    latitude: Double,
    longitude: Double,
    label: String = "Titik Panas NASA FIRMS"
  ): Intent? {
    if (!validateCoordinates(latitude, longitude)) return null
    val encodedLabel = Uri.encode(label)
    val uri = Uri.parse(
      String.format(
        Locale.US,
        "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
        latitude,
        longitude,
        latitude,
        longitude,
        encodedLabel
      )
    )
    return Intent(Intent.ACTION_VIEW, uri).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
  }

  /**
   * Membuat Intent peramban web fallback jika tidak ada aplikasi peta khusus.
   * Mengembalikan null jika koordinat tidak valid.
   */
  fun createBrowserIntent(
    latitude: Double,
    longitude: Double,
    originLat: Double? = null,
    originLon: Double? = null
  ): Intent? {
    if (!validateCoordinates(latitude, longitude)) return null
    val url = if (originLat != null && originLon != null && validateCoordinates(originLat, originLon)) {
      String.format(
        Locale.US,
        "https://www.google.com/maps/dir/?api=1&origin=%.6f,%.6f&destination=%.6f,%.6f",
        originLat,
        originLon,
        latitude,
        longitude
      )
    } else {
      String.format(
        Locale.US,
        "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f",
        latitude,
        longitude
      )
    }
    return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
  }

  /**
   * Membuka navigasi ke koordinat titik panas dengan urutan prioritas:
   * 1. Google Maps
   * 2. Peta lain via geo intent
   * 3. Browser web
   * 4. Menampilkan pesan ramah jika gagal
   */
  fun openNavigation(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String = "Titik Panas NASA FIRMS",
    originLat: Double? = null,
    originLon: Double? = null
  ): NavigationResult {
    if (!validateCoordinates(latitude, longitude)) {
      Toast.makeText(context, "Koordinat titik panas tidak valid.", Toast.LENGTH_SHORT).show()
      return NavigationResult.InvalidCoordinates
    }

    val packageManager = context.packageManager

    // 1. Google Maps app
    val gmapsIntent = createGoogleMapsNavigationIntent(latitude, longitude, label)
    if (gmapsIntent != null) {
      val isPackageInstalled = try {
        packageManager.getPackageInfo("com.google.android.apps.maps", 0) != null
      } catch (_: Exception) {
        false
      }
      val canResolve = gmapsIntent.resolveActivity(packageManager) != null || isPackageInstalled
      if (canResolve) {
        try {
          context.startActivity(gmapsIntent)
          return NavigationResult.Success("Google Maps", gmapsIntent)
        } catch (_: Exception) {}
      }
    }

    // 2. Generic geo Intent
    val geoIntent = createGeoIntent(latitude, longitude, label)
    if (geoIntent != null) {
      if (geoIntent.resolveActivity(packageManager) != null) {
        try {
          context.startActivity(geoIntent)
          return NavigationResult.Success("Aplikasi Peta Eksternal", geoIntent)
        } catch (_: Exception) {}
      }
    }

    // 3. Fallback peramban web
    val browserIntent = createBrowserIntent(latitude, longitude, originLat, originLon)
    if (browserIntent != null) {
      if (browserIntent.resolveActivity(packageManager) != null) {
        try {
          context.startActivity(browserIntent)
          return NavigationResult.Success("Peramban Web", browserIntent)
        } catch (_: Exception) {}
      } else {
        try {
          context.startActivity(browserIntent)
          return NavigationResult.Success("Peramban Web", browserIntent)
        } catch (_: Exception) {}
      }
    }

    // 4. Tidak ada aplikasi yang menangani
    val message = "Perangkat tidak memiliki aplikasi atau peramban yang dapat digunakan untuk navigasi."
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    return NavigationResult.NoAppAvailable(message)
  }

  /**
   * Menghitung jarak perkiraan garis lurus (haversine) dalam kilometer.
   */
  fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val latDistance = Math.toRadians(lat2 - lat1)
    val lonDistance = Math.toRadians(lon2 - lon1)
    val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
  }

  /**
   * Menghitung bearing (arah) geografis dari posisi awal ke posisi tujuan dalam derajat [0..360).
   */
  fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val y = Math.sin(deltaLambda) * Math.cos(phi2)
    val x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda)
    val theta = Math.atan2(y, x)
    return (Math.toDegrees(theta) + 360.0) % 360.0
  }

  /**
   * Menentukan 8 arah mata angin dari nilai derajat bearing.
   */
  fun getCompassDirection(bearingDegrees: Double): String {
    val normalized = (bearingDegrees % 360.0 + 360.0) % 360.0
    return when {
      normalized >= 337.5 || normalized < 22.5 -> "Utara"
      normalized >= 22.5 && normalized < 67.5 -> "Timur Laut"
      normalized >= 67.5 && normalized < 112.5 -> "Timur"
      normalized >= 112.5 && normalized < 157.5 -> "Tenggara"
      normalized >= 157.5 && normalized < 202.5 -> "Selatan"
      normalized >= 202.5 && normalized < 247.5 -> "Barat Daya"
      normalized >= 247.5 && normalized < 292.5 -> "Barat"
      normalized >= 292.5 && normalized < 337.5 -> "Barat Laut"
      else -> "Utara"
    }
  }

  /**
   * Format teks arah/bearing: e.g. "124° (Tenggara)" atau "Belum tersedia" jika null.
   */
  fun formatBearing(bearingDegrees: Double?): String {
    if (bearingDegrees == null || bearingDegrees.isNaN() || bearingDegrees.isInfinite()) {
      return "Belum tersedia"
    }
    val deg = Math.round(bearingDegrees)
    val dir = getCompassDirection(bearingDegrees)
    return "$deg° ($dir)"
  }

  /**
   * Format teks jarak: e.g. "8.4 km" atau "Belum tersedia" jika null.
   */
  fun formatDistance(distanceKm: Double?): String {
    if (distanceKm == null || distanceKm.isNaN() || distanceKm.isInfinite() || distanceKm < 0) {
      return "Belum tersedia"
    }
    return String.format(Locale.US, "%.1f km", distanceKm)
  }
}
