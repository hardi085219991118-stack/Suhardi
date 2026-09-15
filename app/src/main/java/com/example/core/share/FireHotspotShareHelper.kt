package com.example.core.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.core.fire.FireDataRecord
import com.example.core.map.CoordinateValidator
import java.util.Locale

/**
 * Helper untuk fitur Berbagi Titik Panas dan Hubungi Pembuat (Hardi Mantangai).
 * 100% Zero-Dummy & Evidence-Based.
 */
object FireHotspotShareHelper {

  const val CREATOR_NAME = "Hardi Mantangai"
  const val CREATOR_PHONE_DISPLAY = "085219991118"
  const val CREATOR_WHATSAPP_NUMBER = "6285219991118"

  /**
   * Menghitung jarak perkiraan garis lurus (haversine) antara dua koordinat GPS dalam kilometer.
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
   * Menghasilkan URL Google Maps pencarian koordinat hotspot aktual.
   * Mengembalikan null jika koordinat di luar batas validitas.
   */
  fun generateGoogleMapsUrl(latitude: Double, longitude: Double): String? {
    if (latitude.isNaN() || latitude.isInfinite() || longitude.isNaN() || longitude.isInfinite()) {
      return null
    }
    if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
      return null
    }
    return String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", latitude, longitude)
  }

  /**
   * Menghasilkan teks deskriptif titik panas resmi dari data aktual NASA FIRMS.
   * Menjamin tidak ada kebocoran API Key / MAP_KEY / Token internal.
   */
  fun generateShareText(record: FireDataRecord, distanceKm: Double? = null): String {
    val mapsUrl = generateGoogleMapsUrl(record.latitude, record.longitude) ?: "Koordinat tidak valid"
    val confidenceText = if (!record.confidence.isNullOrBlank()) record.confidence else "Tidak tersedia"
    val frpText = if (record.frp != null) "${record.frp} MW" else "Tidak tersedia"
    val distanceText = if (distanceKm != null && distanceKm >= 0) {
      String.format(Locale.US, "%.1f km", distanceKm)
    } else {
      "Tidak tersedia"
    }

    val acqTimeDisplay = if (record.acqDate.isNotBlank() && record.acqTime.isNotBlank()) {
      "${record.acqDate} ${record.acqTime} UTC"
    } else if (record.acqDate.isNotBlank()) {
      record.acqDate
    } else {
      "Tidak tersedia"
    }

    val satName = if (record.satellite.isNotBlank()) record.satellite else "Tidak tersedia"

    return buildString {
      appendLine("🔥 TITIK PANAS TERDETEKSI SATELIT")
      appendLine("Sumber: NASA FIRMS")
      appendLine()
      appendLine("📍 Koordinat:")
      appendLine(String.format(Locale.US, "Lintang: %.6f°", record.latitude))
      appendLine(String.format(Locale.US, "Bujur: %.6f°", record.longitude))
      appendLine()
      appendLine("🛰️ Satelit: $satName")
      appendLine("🕐 Waktu akuisisi: $acqTimeDisplay")
      appendLine("🎯 Tingkat keyakinan: $confidenceText")
      appendLine("🔥 Daya radiasi api: $frpText")
      appendLine("📏 Jarak dari lokasi saya: $distanceText")
      appendLine()
      appendLine("🗺️ Lihat di Google Maps:")
      appendLine(mapsUrl)
      appendLine()
      appendLine("Aplikasi: HARDI MANTANGAI FIRE NOW")
      append("Pemantauan Titik Panas Satelit Mandiri")
    }
  }

  /**
   * Membuka lokasi titik panas di aplikasi Google Maps atau fallback ke browser web.
   */
  fun openGoogleMaps(context: Context, latitude: Double, longitude: Double) {
    val mapsUrl = generateGoogleMapsUrl(latitude, longitude)
    if (mapsUrl == null) {
      Toast.makeText(context, "Lokasi titik panas tidak valid.", Toast.LENGTH_SHORT).show()
      return
    }

    try {
      // Coba buka dengan geo uri spesifik maps
      val geoUri = Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(Titik+Panas+NASA+FIRMS)", latitude, longitude, latitude, longitude))
      val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        setPackage("com.google.android.apps.maps")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      if (mapIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(mapIntent)
      } else {
        // Fallback ke browser web standar
        openBrowserMaps(context, mapsUrl)
      }
    } catch (e: Exception) {
      openBrowserMaps(context, mapsUrl)
    }
  }

  private fun openBrowserMaps(context: Context, mapsUrl: String) {
    try {
      val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(webIntent)
    } catch (e: Exception) {
      Toast.makeText(context, "Tidak dapat membuka peramban web: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  /**
   * Membuka Android Share Sheet (ACTION_SEND) untuk membagikan informasi hotspot.
   */
  fun shareHotspot(context: Context, record: FireDataRecord, distanceKm: Double? = null) {
    try {
      val shareText = generateShareText(record, distanceKm)
      val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_SUBJECT, "🔥 Titik Panas Terdeteksi NASA FIRMS")
        type = "text/plain"
      }
      val shareIntent = Intent.createChooser(sendIntent, "Bagikan Titik Panas").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(shareIntent)
    } catch (e: Exception) {
      Toast.makeText(context, "Gagal membuka menu berbagi: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  /**
   * Membuka chat WhatsApp langsung ke pembuat aplikasi (Hardi Mantangai - 085219991118).
   * Dengan fallback ramah jika WhatsApp tidak terpasang.
   */
  fun openWhatsAppCreator(context: Context, message: String = "Halo Hardi Mantangai, saya menggunakan aplikasi HARDI MANTANGAI FIRE NOW.") {
    val encodedMessage = Uri.encode(message)
    val url = "https://wa.me/$CREATOR_WHATSAPP_NUMBER?text=$encodedMessage"
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      Toast.makeText(
        context,
        "WhatsApp tidak tersedia. Silakan hubungi nomor: $CREATOR_PHONE_DISPLAY",
        Toast.LENGTH_LONG
      ).show()
    }
  }
}
