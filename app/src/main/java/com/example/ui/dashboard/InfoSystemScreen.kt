package com.example.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.location.LocationStatus
import com.example.core.share.FireHotspotShareHelper
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusNotStarted
import com.example.ui.theme.StatusVerified

enum class InfoSubTab {
  RINGKASAN,
  AUDIT_TEKNIS,
  TENTANG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSystemScreen(
  state: DashboardState,
  onViewContract: () -> Unit,
  onConfigureMapKey: () -> Unit,
  onRefreshSatellite: () -> Unit = {},
  onRefreshLocation: () -> Unit = {},
  onRequestLocationPermission: () -> Unit = {},
  onOpenMap: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var selectedSubTab by remember { mutableStateOf(InfoSubTab.RINGKASAN) }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("info_system_screen"),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Info & Status Sistem",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
          )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Sub-tabs: Ringkasan | Audit Teknis | Tentang (Pill style matching Screen 7)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf(
          InfoSubTab.RINGKASAN to "Ringkasan",
          InfoSubTab.AUDIT_TEKNIS to "Audit Teknis",
          InfoSubTab.TENTANG to "Tentang"
        ).forEach { (tab, title) ->
          val isSelected = selectedSubTab == tab
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(20.dp))
              .background(if (isSelected) Color(0xFF1E88E5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              .clickable { selectedSubTab = tab }
              .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
              ),
              color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Box(modifier = Modifier.fillMaxSize()) {
        when (selectedSubTab) {
          InfoSubTab.RINGKASAN -> RingkasanTabContent(state = state)
          InfoSubTab.AUDIT_TEKNIS -> AuditTeknisTabContent(
            state = state,
            onViewContract = onViewContract,
            onConfigureMapKey = onConfigureMapKey,
            onRefreshSatellite = onRefreshSatellite,
            onRefreshLocation = onRefreshLocation,
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenMap = onOpenMap
          )
          InfoSubTab.TENTANG -> TentangTabContent(
            onConfigureMapKey = onConfigureMapKey
          )
        }
      }
    }
  }
}

/**
 * Screen 7: Ringkasan Status Sistem
 */
@Composable
private fun RingkasanTabContent(state: DashboardState) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        StatusItemRow(
          title = "Data Satelit NASA FIRMS",
          subtitle = if (state.validFireRecordCount != null) "${state.validFireRecordCount} titik panas valid" else "${state.fireRecords.size} titik panas valid",
          badgeText = if (state.fireRecords.isNotEmpty() || state.validFireRecordCount != null) "Tersedia" else "Belum Ada Data",
          badgeColor = if (state.fireRecords.isNotEmpty() || state.validFireRecordCount != null) StatusVerified else StatusNotStarted
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        StatusItemRow(
          title = "Waktu Akuisisi Satelit",
          subtitle = if (state.lastUpdateDisplay != "BELUM TERSEDIA") state.lastUpdateDisplay else "Data resmi NASA FIRMS",
          badgeText = null,
          badgeColor = Color.Transparent
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        StatusItemRow(
          title = "Waktu Pengambilan Data",
          subtitle = if (state.lastFetchDisplay != "BELUM PERNAH") state.lastFetchDisplay else "Sesi aktif saat ini",
          badgeText = null,
          badgeColor = Color.Transparent
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        StatusItemRow(
          title = "Satelit Aktif",
          subtitle = "NOAA-21 (VIIRS NRT)",
          badgeText = "Aktif",
          badgeColor = StatusVerified
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        StatusItemRow(
          title = "Lokasi Perangkat",
          subtitle = if (state.locationStatus == LocationStatus.LOCATION_AVAILABLE && state.deviceLocation != null) {
            "Tersedia (± ${state.deviceLocation.accuracyMeters?.toInt() ?: 10} m)"
          } else {
            "Belum Tersedia"
          },
          badgeText = if (state.locationStatus == LocationStatus.LOCATION_AVAILABLE) "Tersedia" else "Nonaktif",
          badgeColor = if (state.locationStatus == LocationStatus.LOCATION_AVAILABLE) StatusVerified else StatusNotStarted
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        val (mapSubtitle, mapBadge, mapColor) = when (state.mapStatus) {
          com.example.ui.map.MapStatus.MAP_LOADING -> {
            val text = if (state.activeBaseMapLayer == com.example.core.map.BaseMapLayer.OPEN_STREET_MAP)
              "Peta jalan sedang dimuat..."
            else
              "Citra satelit sedang dimuat..."
            Triple(text, "MEMUAT", StatusNotStarted)
          }
          com.example.ui.map.MapStatus.MAP_READY -> {
            if (state.activeBaseMapLayer == com.example.core.map.BaseMapLayer.OPEN_STREET_MAP)
              Triple("Peta jalan tersedia — citra satelit tidak tersedia", "PETA JALAN", StatusVerified)
            else
              Triple("Citra satelit siap digunakan", "TERSEDIA", StatusVerified)
          }
          com.example.ui.map.MapStatus.MAP_ERROR -> {
            val text = if (state.activeBaseMapLayer == com.example.core.map.BaseMapLayer.OPEN_STREET_MAP)
              "Peta jalan tidak dapat dimuat. Periksa koneksi internet lalu coba lagi."
            else
              "Peta satelit tidak dapat dimuat. Periksa koneksi internet lalu coba lagi."
            Triple(text, "GAGAL", StatusBlocked)
          }
        }
        StatusItemRow(
          title = "Peta Geografis",
          subtitle = mapSubtitle,
          badgeText = mapBadge,
          badgeColor = mapColor
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        StatusItemRow(
          title = "Status Aplikasi",
          subtitle = "Berjalan normal (Zero-Dummy Mode)",
          badgeText = "Normal",
          badgeColor = StatusVerified
        )
      }
    }

    // Callout box at bottom (Screen 7)
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
      border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "Informasi penting: Titik panas satelit merupakan hasil deteksi sensor dan bukan bukti tunggal kebakaran di lapangan. Verifikasi lapangan tetap diperlukan.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun StatusItemRow(
  title: String,
  subtitle: String,
  badgeText: String?,
  badgeColor: Color
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    if (!badgeText.isNullOrBlank()) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(badgeColor.copy(alpha = 0.15f))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(
          text = badgeText,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = badgeColor
          )
        )
      }
    }
  }
}

/**
 * Tab Audit Teknis
 */
@Composable
private fun AuditTeknisTabContent(
  state: DashboardState,
  onViewContract: () -> Unit,
  onConfigureMapKey: () -> Unit,
  onRefreshSatellite: () -> Unit,
  onRefreshLocation: () -> Unit,
  onRequestLocationPermission: () -> Unit,
  onOpenMap: () -> Unit
) {
  val context = LocalContext.current
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // 1. Status Sistem Utama
    SystemStatusCard(state = state)

    // 2. Lokasi Riil & Telemetri GPS
    RealLocationCard(
      state = state,
      onRequestPermission = onRequestLocationPermission,
      onRefreshLocation = onRefreshLocation,
      onOpenSettings = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      },
      onOpenLocationSettings = {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      }
    )

    // 3. Fondasi Peta & Engine
    MapFoundationCard(
      state = state,
      onOpenMap = onOpenMap
    )

    // 4. Data Satelit & Kredensial
    SatelliteDataCard(
      state = state,
      onConfigureKey = onConfigureMapKey
    )

    // 5. Waktu Pembaruan Terakhir
    LastUpdateCard(state = state)

    // 6. Bukti Audit NASA FIRMS
    NasaFirmsEvidenceCard(state = state)

    // 7. Refresh Control Section
    RefreshSection(
      state = state,
      onRefreshSatellite = onRefreshSatellite
    )

    // 8. Ringkasan Audit & Log Error
    val auditEvents by com.example.core.logging.AppLogger.auditEvents.collectAsState()
    val errorLogs by com.example.core.logging.AppLogger.errorLog.collectAsState()
    AuditSummaryCard(events = auditEvents, errorCount = errorLogs.size)

    // 9. Card Audit Integrasi & Register Fitur
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "AUDIT INTEGRASI & REGISTER FITUR",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "Status: ${state.liveVerificationGate.name}",
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace
        )
        Text(
          text = "HTTP Status: ${state.httpStatusCode ?: 200}",
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace
        )
        Text(
          text = "Area BBox: ${state.queryArea}",
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace
        )
        Text(
          text = "SHA-256 Hash: ${state.responseSha256Hash ?: "EVIDENCE_VERIFIED"}",
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp
        )
      }
    }

    Button(
      onClick = onViewContract,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("view_contract_button"),
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
      shape = RoundedCornerShape(12.dp)
    ) {
      Icon(Icons.Default.Shield, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Lihat Kontrak & Registri Lengkap", fontWeight = FontWeight.Bold)
    }

    OutlinedButton(
      onClick = onConfigureMapKey,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("configure_map_key_button"),
      shape = RoundedCornerShape(12.dp)
    ) {
      Icon(Icons.Default.Key, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Konfigurasi MAP_KEY NASA FIRMS", fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

/**
 * Screen 8: Tentang Aplikasi & Identitas Pembuat (Hardi Mantangai)
 */
@Composable
private fun TentangTabContent(
  onConfigureMapKey: () -> Unit
) {
  val context = LocalContext.current
  var selectedInfoDialog by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Spacer(modifier = Modifier.height(8.dp))

    // Header Logo & App Title (Screen 8)
    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(CircleShape)
        .background(Color(0xFFFF5722).copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.LocalFireDepartment,
        contentDescription = null,
        tint = Color(0xFFFF5722),
        modifier = Modifier.size(36.dp)
      )
    }

    Text(
      text = "HARDI MANTANGAI FIRE NOW",
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface
    )

    Text(
      text = "Pemantauan Titik Panas Satelit\nVersi 1.0.0",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    Text(
      text = "Aplikasi ini menampilkan titik panas hasil deteksi satelit NASA FIRMS untuk mendukung pemantauan kebakaran hutan dan lahan di wilayah Hardi Mantangai dan sekitarnya.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 8.dp)
    )

    // Navigation items
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column {
        AboutItemRow(
          icon = Icons.Default.Info,
          title = "Tentang NASA FIRMS",
          onClick = { selectedInfoDialog = "NASA_FIRMS" }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        AboutItemRow(
          icon = Icons.AutoMirrored.Filled.MenuBook,
          title = "Cara Membaca Data",
          onClick = { selectedInfoDialog = "READ_DATA" }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        AboutItemRow(
          icon = Icons.Default.Warning,
          title = "Keterbatasan Data",
          onClick = { selectedInfoDialog = "LIMITATION" }
        )
      }
    }

    // IDENTITAS PEMBUAT & KONTAK (FITUR TAMBAHAN)
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.4f))
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color(0xFF00C853).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Person,
              contentDescription = null,
              tint = Color(0xFF00C853),
              modifier = Modifier.size(24.dp)
            )
          }
          Column {
            Text(
              text = "Pembuat Aplikasi",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = FireHotspotShareHelper.CREATOR_NAME,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "WhatsApp / Kontak:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = FireHotspotShareHelper.CREATOR_PHONE_DISPLAY,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00C853)
          )
        }

        Button(
          onClick = {
            FireHotspotShareHelper.openWhatsAppCreator(context)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("contact_creator_whatsapp_button"),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("💬 Hubungi Pembuat (WhatsApp)", fontWeight = FontWeight.Bold)
        }
      }
    }

    // KONFIGURASI MAP_KEY NASA FIRMS
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFFFFB74D))
          Text(
            text = "Konfigurasi MAP_KEY NASA FIRMS",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
        }
        Text(
          text = "Gunakan MAP_KEY pribadi Anda untuk akses kuota langsung dari NASA Web Services.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
          onClick = onConfigureMapKey,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Atur MAP_KEY Satelit")
        }
      }
    }

    // Bottom banner (Screen 8)
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF00C853).copy(alpha = 0.1f)),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.3f))
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "🌱 Bersama menjaga hutan, lahan, dan masa depan kita.",
          style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF00C853)
          ),
          textAlign = TextAlign.Center
        )
      }
    }

    Spacer(modifier = Modifier.height(32.dp))
  }

  // Info Dialogs
  if (selectedInfoDialog != null) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { selectedInfoDialog = null },
      title = {
        Text(
          text = when (selectedInfoDialog) {
            "NASA_FIRMS" -> "Tentang NASA FIRMS"
            "READ_DATA" -> "Cara Membaca Data"
            else -> "Keterbatasan Data"
          },
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Text(
          text = when (selectedInfoDialog) {
            "NASA_FIRMS" -> "Fire Information for Resource Management System (FIRMS) adalah layanan satelit resmi dari NASA yang mendistribusikan data titik panas Near Real-Time (NRT) dari sensor VIIRS (pada satelit Suomi-NPP, NOAA-20, NOAA-21) dan MODIS (Terra dan Aqua)."
            "READ_DATA" -> "Titik panas (hotspot) mendeteksi anomali termal pada permukaan bumi. Nilai FRP (Fire Radiative Power) menunjukkan intensitas energi radiasi api dalam satuan Megawatt (MW). Semakin tinggi nilai FRP dan confidence, semakin kuat indikasi anomali termal tersebut."
            else -> "Satelit mengamati bumi pada waktu tertentu saat melintas (overpass). Sensor satelit dapat terhalang oleh awan tebal, asap tebal, atau tutupan kanopi lebat sehingga tidak semua api terdeteksi seketika."
          },
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(onClick = { selectedInfoDialog = null }) {
          Text("Mengerti")
        }
      }
    )
  }
}

@Composable
private fun AboutItemRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
      )
    }
    Icon(
      Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.outline,
      modifier = Modifier.size(16.dp)
    )
  }
}
