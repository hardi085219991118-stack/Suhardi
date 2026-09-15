package com.example.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.fire.FireDataAgeCalculator
import com.example.core.fire.FireDataRecord
import com.example.core.location.DeviceLocation
import com.example.core.map.CoordinateValidator
import com.example.core.map.HotspotNavigationHelper
import com.example.core.share.FireHotspotShareHelper
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusVerified
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialog / Panel Detail Titik Panas NASA FIRMS.
 *
 * ZERO-DUMMY MANDATE:
 * - Menampilkan informasi yang BENAR-BENAR berasal dari record NASA FIRMS.
 * - Tidak mengisi angka dummy (confidence=100, frp=0, brightness=0, scan=0, track=0).
 * - Field yang tidak tersedia ditampilkan sebagai "Tidak tersedia" atau "--".
 * - Navigasi ke lokasi titik panas hanya aktif jika koordinat valid.
 * - Jarak dan arah (bearing) dihitung dari GPS riil pengguna. Jika GPS tidak tersedia,
 *   ditampilkan "Belum tersedia" (tidak membuat-buat koordinat dummy).
 */
@Composable
fun HotspotDetailDialog(
  record: FireDataRecord,
  userLocation: DeviceLocation? = null,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val isCoordValid = remember(record.latitude, record.longitude) {
    HotspotNavigationHelper.validateCoordinates(record.latitude, record.longitude)
  }

  val isUserGpsValid = remember(userLocation) {
    userLocation != null && CoordinateValidator.isValid(userLocation.latitude, userLocation.longitude)
  }

  val distanceKm = remember(record, userLocation, isCoordValid, isUserGpsValid) {
    if (isCoordValid && isUserGpsValid && userLocation != null) {
      HotspotNavigationHelper.calculateDistanceKm(
        userLocation.latitude,
        userLocation.longitude,
        record.latitude,
        record.longitude
      )
    } else null
  }

  val bearingDegrees = remember(record, userLocation, isCoordValid, isUserGpsValid) {
    if (isCoordValid && isUserGpsValid && userLocation != null) {
      HotspotNavigationHelper.calculateBearing(
        userLocation.latitude,
        userLocation.longitude,
        record.latitude,
        record.longitude
      )
    } else null
  }

  val formattedAcqDate = remember(record.acqDate) {
    formatDisplayDate(record.acqDate)
  }

  val formattedAcqTime = remember(record.acqTime) {
    formatDisplayTime(record.acqTime)
  }

  val satelliteName = remember(record.satellite, record.instrument) {
    formatSatelliteName(record.satellite, record.instrument)
  }

  val ageString = remember(record.acquisitionTimestampMillis) {
    FireDataAgeCalculator.formatAgeDetail(record.acquisitionTimestampMillis)
  }

  val coordString = remember(record.latitude, record.longitude) {
    String.format(Locale.US, "%.6f°, %.6f°", record.latitude, record.longitude)
  }

  val brightnessString = remember(record.brightTi4, record.brightTi5) {
    formatBrightness(record.brightTi4, record.brightTi5)
  }

  val dayNightString = remember(record.dayNight) {
    formatDayNight(record.dayNight)
  }

  val scanTrackString = remember(record.scan, record.track) {
    formatScanTrack(record.scan, record.track)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("hotspot_detail_dialog"),
    icon = {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(Color(0xFFFF5722).copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.LocalFireDepartment,
          contentDescription = null,
          tint = Color(0xFFFF5722),
          modifier = Modifier.size(28.dp)
        )
      }
    },
    title = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = "DETAIL TITIK PANAS",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          modifier = Modifier.testTag("fire_marker_detail_title")
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isCoordValid) StatusVerified.copy(alpha = 0.2f) else StatusBlocked.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Text(
            text = if (isCoordValid) "DATA RESMI NASA FIRMS" else "KOORDINAT TIDAK VALID",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = if (isCoordValid) StatusVerified else StatusBlocked
            )
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Kartu Koordinat & Tombol Salin
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Koordinat Titik Panas",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = coordString,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                fontFamily = FontFamily.Monospace,
                color = if (isCoordValid) MaterialTheme.colorScheme.onSurface else StatusBlocked,
                modifier = Modifier.testTag("hotspot_coordinates_text")
              )
            }
            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Koordinat Hotspot", coordString)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Koordinat disalin: $coordString", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier
                .size(36.dp)
                .testTag("copy_coordinates_button")
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Salin Koordinat",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }

        // Rincian Sensor & Satelit
        DetailSectionCard(title = "INFORMASI SATELIT & WAKTU") {
          HotspotDetailRow("Satelit", satelliteName, "hotspot_satellite_text")
          HotspotDetailRow("Instrumen", record.instrument.ifBlank { "Tidak tersedia" }, "hotspot_instrument_text")
          HotspotDetailRow("Tanggal Akuisisi", formattedAcqDate, "hotspot_acq_date_text")
          HotspotDetailRow("Waktu Akuisisi", formattedAcqTime, "hotspot_acq_time_text")
          HotspotDetailRow("Usia Data", ageString, "hotspot_data_age_text")
        }

        // Rincian Deteksi Api
        DetailSectionCard(title = "KARAKTERISTIK API") {
          HotspotDetailRow(
            label = "Tingkat Keyakinan (Confidence)",
            value = record.confidence ?: "Tidak tersedia",
            tag = "hotspot_confidence_text"
          )
          HotspotDetailRow(
            label = "Daya Radiasi (FRP)",
            value = if (record.frp != null) "${record.frp} MW" else "Tidak tersedia",
            tag = "hotspot_frp_text"
          )
          HotspotDetailRow(
            label = "Suhu Kecerahan (Brightness)",
            value = brightnessString,
            tag = "hotspot_brightness_text"
          )
          HotspotDetailRow(
            label = "Waktu Lintasan (Day/Night)",
            value = dayNightString,
            tag = "hotspot_daynight_text"
          )
          HotspotDetailRow(
            label = "Resolusi Pixel (Scan × Track)",
            value = scanTrackString,
            tag = "hotspot_scantrack_text"
          )
        }

        // Rincian Relatif Lokasi Pengguna
        DetailSectionCard(title = "RELASI DENGAN POSISI SAYA") {
          val distanceDisplay = HotspotNavigationHelper.formatDistance(distanceKm)
          val bearingDisplay = HotspotNavigationHelper.formatBearing(bearingDegrees)

          HotspotDetailRow(
            label = "Jarak dari Lokasi Saya",
            value = distanceDisplay,
            tag = "hotspot_user_distance_text",
            highlight = distanceKm != null
          )
          HotspotDetailRow(
            label = "Arah (Bearing)",
            value = bearingDisplay,
            tag = "hotspot_user_bearing_text",
            highlight = bearingDegrees != null
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Tombol Navigasi ke Lokasi (UTAMA)
        Button(
          onClick = {
            if (isCoordValid) {
              HotspotNavigationHelper.openNavigation(context, record.latitude, record.longitude)
            } else {
              Toast.makeText(context, "Lokasi titik panas tidak valid.", Toast.LENGTH_SHORT).show()
            }
          },
          enabled = isCoordValid,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("navigate_to_hotspot_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1976D2),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("📍 Navigasi ke Lokasi", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (!isCoordValid) {
          Text(
            text = "Lokasi titik panas tidak valid. Navigasi dinonaktifkan.",
            style = MaterialTheme.typography.labelSmall,
            color = StatusBlocked,
            modifier = Modifier.align(Alignment.CenterHorizontally)
          )
        }

        // Tombol Bagikan Titik Panas
        OutlinedButton(
          onClick = {
            FireHotspotShareHelper.shareHotspot(context, record, distanceKm)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("share_fire_hotspot_button"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("📤 Bagikan Titik Panas", fontWeight = FontWeight.SemiBold)
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("dismiss_fire_detail_button")
      ) {
        Text(
          text = "Tutup",
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  )
}

@Composable
private fun DetailSectionCard(
  title: String,
  content: @Composable () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = Color(0xFFFF7043)
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
      content()
    }
  }
}

@Composable
fun HotspotDetailRow(
  label: String,
  value: String,
  tag: String? = null,
  highlight: Boolean = false
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val textModifier = if (tag != null) Modifier.testTag(tag) else Modifier
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(
        fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold
      ),
      fontFamily = FontFamily.Monospace,
      color = if (highlight) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurface,
      modifier = textModifier
    )
  }
}

private fun formatDisplayDate(acqDate: String): String {
  if (acqDate.isBlank()) return "Tidak tersedia"
  return try {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(acqDate.trim())
    if (input != null) {
      SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID")).format(input)
    } else {
      acqDate
    }
  } catch (_: Exception) {
    acqDate
  }
}

private fun formatDisplayTime(acqTime: String): String {
  val clean = acqTime.trim()
  if (clean.isBlank()) return "Tidak tersedia"
  return if (clean.length == 4 && clean.all { it.isDigit() }) {
    "${clean.substring(0, 2)}:${clean.substring(2, 4)} UTC"
  } else if (clean.contains(":")) {
    "$clean UTC"
  } else {
    "$clean UTC"
  }
}

private fun formatSatelliteName(satellite: String, instrument: String): String {
  val sat = satellite.trim()
  return when {
    sat.contains("21") -> "NOAA-21"
    sat.contains("20") -> "NOAA-20"
    sat.contains("SNPP", ignoreCase = true) || sat.contains("Suomi", ignoreCase = true) || sat == "N" -> "Suomi-NPP"
    sat.contains("Aqua", ignoreCase = true) -> "Aqua"
    sat.contains("Terra", ignoreCase = true) -> "Terra"
    sat.isNotBlank() -> sat
    instrument.isNotBlank() -> instrument
    else -> "Tidak tersedia"
  }
}

private fun formatBrightness(brightTi4: Double?, brightTi5: Double?): String {
  return when {
    brightTi4 != null && brightTi5 != null -> String.format(Locale.US, "%.1f K (Ch4) / %.1f K (Ch5)", brightTi4, brightTi5)
    brightTi4 != null -> String.format(Locale.US, "%.1f K", brightTi4)
    brightTi5 != null -> String.format(Locale.US, "%.1f K", brightTi5)
    else -> "Tidak tersedia"
  }
}

private fun formatDayNight(dayNight: String?): String {
  return when (dayNight?.trim()?.uppercase()) {
    "D" -> "Siang (Day)"
    "N" -> "Malam (Night)"
    null, "" -> "Tidak tersedia"
    else -> dayNight
  }
}

private fun formatScanTrack(scan: Double?, track: Double?): String {
  return when {
    scan != null && track != null -> String.format(Locale.US, "%.2f × %.2f km", scan, track)
    scan != null -> String.format(Locale.US, "Scan: %.2f km", scan)
    track != null -> String.format(Locale.US, "Track: %.2f km", track)
    else -> "Tidak tersedia"
  }
}
