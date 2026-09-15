package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.location.LocationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLocationScreen(
  state: DashboardState,
  onBack: () -> Unit,
  onRefreshLocation: () -> Unit,
  onRequestPermission: () -> Unit,
  onOpenLocationSettings: () -> Unit = {},
  onOpenMap: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isLocationAvailable = state.locationStatus == LocationStatus.LOCATION_AVAILABLE && state.deviceLocation != null
  val location = state.deviceLocation

  val timeFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.forLanguageTag("id-ID")) }
  val formattedTime = remember(location?.timeMillis) {
    if (location != null && location.timeMillis > 0) {
      "${timeFormat.format(Date(location.timeMillis))} WIB"
    } else {
      "Tidak tersedia"
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("my_location_screen"),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Lokasi Saya",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("back_from_my_location_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(24.dp))

      // Big Circle Pin Indicator
      val iconTint = when (state.locationStatus) {
        LocationStatus.LOCATION_AVAILABLE -> Color(0xFF00C853)
        LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
      }
      val iconBg = when (state.locationStatus) {
        LocationStatus.LOCATION_AVAILABLE -> Color(0xFF00C853).copy(alpha = 0.15f)
        LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
      }
      val iconBorder = when (state.locationStatus) {
        LocationStatus.LOCATION_AVAILABLE -> Color(0xFF00C853).copy(alpha = 0.4f)
        LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
      }

      Box(
        modifier = Modifier
          .size(96.dp)
          .clip(CircleShape)
          .background(iconBg)
          .border(width = 2.dp, color = iconBorder, shape = CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isLocationAvailable) Icons.Default.LocationOn else Icons.Default.GpsFixed,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(52.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      val statusTitle = when (state.locationStatus) {
        LocationStatus.LOCATION_AVAILABLE -> "GPS aktif"
        LocationStatus.LOCATION_LOADING -> "Mencari sinyal GPS..."
        LocationStatus.LOCATION_PROVIDER_DISABLED -> "GPS nonaktif"
        LocationStatus.LOCATION_PERMISSION_REQUIRED, LocationStatus.LOCATION_PERMISSION_DENIED -> "Izin lokasi belum diberikan"
        else -> "Status Lokasi"
      }
      val statusSubtitle = when (state.locationStatus) {
        LocationStatus.LOCATION_AVAILABLE -> "Perangkat berhasil memperoleh lokasi secara akurat."
        LocationStatus.LOCATION_LOADING -> "Sedang mencari sinyal GPS dari sensor perangkat..."
        LocationStatus.LOCATION_PROVIDER_DISABLED -> "Layanan GPS perangkat nonaktif. Aktifkan GPS pada pengaturan perangkat."
        LocationStatus.LOCATION_PERMISSION_REQUIRED, LocationStatus.LOCATION_PERMISSION_DENIED -> "Aplikasi memerlukan izin akses GPS nyata untuk menentukan posisi Anda."
        else -> state.locationErrorMessage ?: "Aplikasi siap membaca GPS perangkat."
      }

      Text(
        text = statusTitle,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = statusSubtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
      )

      Spacer(modifier = Modifier.height(24.dp))

      if (location != null && state.locationStatus == LocationStatus.LOCATION_AVAILABLE) {
        // Info Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            LocationRowItem(
              label = "Lintang (Latitude)",
              value = String.format(Locale.US, "%.6f°", location.latitude)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            LocationRowItem(
              label = "Bujur (Longitude)",
              value = String.format(Locale.US, "%.6f°", location.longitude)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            LocationRowItem(
              label = "Akurasi",
              value = if (location.accuracyMeters != null) "± ${String.format(Locale.US, "%.1f", location.accuracyMeters)} meter" else "Tidak tersedia"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            LocationRowItem(
              label = "Waktu pembaruan",
              value = formattedTime
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            LocationRowItem(
              label = "Sumber lokasi",
              value = if (!location.provider.isNullOrBlank()) "GPS (${location.provider})" else "GPS (Perangkat fisik)"
            )
          }
        }
      } else if (state.locationStatus == LocationStatus.LOCATION_PROVIDER_DISABLED) {
        Button(
          onClick = onOpenLocationSettings,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("my_location_open_gps_settings_button"),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Default.GpsFixed, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Buka Pengaturan Lokasi", fontWeight = FontWeight.Bold)
        }
      } else {
        // Action to request permission
        Button(
          onClick = onRequestPermission,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("my_location_request_permission_button"),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Default.GpsFixed, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Minta Izin Lokasi", fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Button: Perbarui Lokasi (Blue button)
      Button(
        onClick = onRefreshLocation,
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("my_location_refresh_button"),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
        shape = RoundedCornerShape(14.dp)
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Perbarui Lokasi", fontSize = 16.sp, fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Button: Lihat di Peta (Orange button)
      Button(
        onClick = onOpenMap,
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("my_location_view_on_map_button"),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
        shape = RoundedCornerShape(14.dp)
      ) {
        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Lihat Posisi di Peta", fontSize = 16.sp, fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

@Composable
private fun LocationRowItem(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}
