package com.example.ui.dashboard

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.location.LocationVerificationLevel
import com.example.core.location.RuntimeEnvironment
import com.example.core.logging.AppLogger
import com.example.ui.FoundationScreen
import com.example.ui.map.MapScreen
import com.example.ui.map.MapStatus
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusNotStarted
import com.example.ui.theme.StatusVerified
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  modifier: Modifier = Modifier,
  state: DashboardState = remember { DashboardState() },
  onRequestPermission: () -> Unit = {},
  onRefreshLocation: () -> Unit = {},
  onRefreshSatellite: () -> Unit = {},
  onSetMapKey: (String?) -> Unit = {}
) {
  val context = LocalContext.current
  var currentTab by remember { mutableStateOf(AppBottomNavTab.BERANDA) }
  var showContractScreen by remember { mutableStateOf(false) }
  var showMapScreen by remember { mutableStateOf(false) }
  var showMyLocationScreen by remember { mutableStateOf(false) }
  var showFilterDialog by remember { mutableStateOf(false) }
  var filterCriteria by remember { mutableStateOf(HotspotFilterCriteria()) }
  var selectedHotspotForDetail by remember { mutableStateOf<com.example.core.fire.FireDataRecord?>(null) }

  var showKeyDialog by remember { mutableStateOf(false) }
  var keyInput by remember { mutableStateOf("") }
  val auditEvents by AppLogger.auditEvents.collectAsState()
  val errorLogs by AppLogger.errorLog.collectAsState()

  if (showKeyDialog) {
    AlertDialog(
      onDismissRequest = { showKeyDialog = false },
      title = {
        Text("Konfigurasi NASA FIRMS MAP_KEY", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Masukkan MAP_KEY resmi NASA FIRMS Anda untuk menguji query data satelit riil.",
            style = MaterialTheme.typography.bodySmall
          )
          OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("NASA FIRMS MAP_KEY") },
            placeholder = { Text("Contoh: a1b2c3d4e5f6...") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("map_key_input_field"),
            singleLine = true
          )
          TextButton(
            onClick = {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://firms.modaps.eosdis.nasa.gov/api/map_key/")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
              }
              context.startActivity(intent)
            }
          ) {
            Text("Dapatkan MAP_KEY gratis di firms.modaps.eosdis.nasa.gov ↗", fontSize = 12.sp)
          }
          Text(
            "STATUS ARSITEKTUR: CLIENT_ONLY_LIMITATION\n" +
            "JENIS KREDENSIAL: CLIENT_SIDE_CREDENTIAL\n" +
            "BATASAN KEAMANAN: PRODUCTION_SECURITY_LIMITATION\n\n" +
            "Peringatan: Kredensial disimpan pada SharedPreferences lokal.",
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSetMapKey(keyInput.trim())
            showKeyDialog = false
          },
          modifier = Modifier.testTag("save_map_key_button")
        ) {
          Text("Simpan")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = {
              onSetMapKey(null)
              keyInput = ""
              showKeyDialog = false
            }
          ) {
            Text("Hapus Kunci")
          }
          TextButton(onClick = { showKeyDialog = false }) {
            Text("Batal")
          }
        }
      }
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      onRefreshLocation()
    } else {
      onRequestPermission()
    }
  }

  // Detail Titik Panas Dialog (Screen 3)
  if (selectedHotspotForDetail != null) {
    com.example.ui.map.FireMarkerDetailDialog(
      record = selectedHotspotForDetail!!,
      onDismiss = { selectedHotspotForDetail = null }
    )
  }

  // Filter Hotspots Dialog (Screen 4)
  if (showFilterDialog) {
    FilterHotspotsDialog(
      currentCriteria = filterCriteria,
      onApplyCriteria = { filterCriteria = it },
      onDismiss = { showFilterDialog = false }
    )
  }

  // Layar Peta khusus dari open_map_button legacy
  if (showMapScreen) {
    MapScreen(
      deviceLocation = state.deviceLocation,
      locationStatus = state.locationStatus,
      locationErrorMessage = state.locationErrorMessage,
      fireRecords = state.fireRecords,
      fireDataSourceState = state.fireDataSourceState,
      onBackToDashboard = { showMapScreen = false },
      onRefreshLocation = onRefreshLocation,
      onRefreshSatellite = onRefreshSatellite,
      onRequestPermission = {
        permissionLauncher.launch(
          arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        )
      }
    )
    return
  }

  // Layar Lokasi Saya (Screen 6)
  if (showMyLocationScreen) {
    MyLocationScreen(
      state = state,
      onBack = { showMyLocationScreen = false },
      onRefreshLocation = onRefreshLocation,
      onRequestPermission = {
        permissionLauncher.launch(
          arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        )
      },
      onOpenMap = {
        showMyLocationScreen = false
        currentTab = AppBottomNavTab.PETA
      }
    )
    return
  }

  // Layar Kontrak & Registri Audit
  if (showContractScreen) {
    Column(modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        OutlinedButton(
          onClick = { showContractScreen = false },
          modifier = Modifier.testTag("back_to_dashboard_button")
        ) {
          Text("← Kembali ke Dashboard")
        }
      }
      FoundationScreen(modifier = Modifier.weight(1f))
    }
    return
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    bottomBar = {
      MyNavigationBottomBar(
        currentTab = currentTab,
        onTabSelected = { tab ->
          currentTab = tab
          showMapScreen = false
          showMyLocationScreen = false
        }
      )
    }
  ) { innerPadding ->
    when (currentTab) {
      AppBottomNavTab.BERANDA -> {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          TopAppBar(
            title = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF5722).copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = Color(0xFFFF5722),
                    modifier = Modifier.size(20.dp)
                  )
                }
                Column {
                  Text(
                    text = "HARDI MANTANGAI FIRE NOW",
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontWeight = FontWeight.Bold,
                      letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.testTag("app_title")
                  )
                  Text(
                    text = "Pemantauan Titik Panas Satelit",
                    style = MaterialTheme.typography.labelSmall.copy(
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.testTag("system_status_subtitle")
                  )
                }
              }
            },
            actions = {
              TextButton(
                onClick = { currentTab = AppBottomNavTab.PETA },
                modifier = Modifier.testTag("open_map_button")
              ) {
                Text(
                  text = "Peta",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary
                )
              }
              TextButton(
                onClick = { showContractScreen = true },
                modifier = Modifier.testTag("view_contract_button")
              ) {
                Text(
                  text = "Kontrak",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary
                )
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
              titleContentColor = MaterialTheme.colorScheme.onSurface
            )
          )

          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            item {
              Spacer(modifier = Modifier.height(4.dp))
              // 1. Kartu Utama Titik Panas Terdeteksi (Sesuai Gambar Referensi)
              FireDetectionCard(state = state)
            }

            item {
              // 2. Empat Tombol Aksi Utama Beranda (Sesuai Gambar Referensi)
              PrimaryActionButtonsSection(
                onOpenHotspotsList = { currentTab = AppBottomNavTab.TITIK_PANAS },
                onOpenMap = { currentTab = AppBottomNavTab.PETA },
                onOpenMyLocation = { showMyLocationScreen = true },
                onRefreshSatellite = onRefreshSatellite,
                isRefreshing = state.isLoadingSatellite,
                cooldownSeconds = state.cooldownRemainingSeconds
              )
              Spacer(modifier = Modifier.height(24.dp))
            }
          }
        }
      }

      AppBottomNavTab.PETA -> {
        MapScreen(
          deviceLocation = state.deviceLocation,
          locationStatus = state.locationStatus,
          locationErrorMessage = state.locationErrorMessage,
          fireRecords = state.fireRecords,
          fireDataSourceState = state.fireDataSourceState,
          onBackToDashboard = { currentTab = AppBottomNavTab.BERANDA },
          onRefreshLocation = onRefreshLocation,
          onRefreshSatellite = onRefreshSatellite,
          onRequestPermission = {
            permissionLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
              )
            )
          },
          modifier = Modifier.padding(innerPadding)
        )
      }

      AppBottomNavTab.TITIK_PANAS -> {
        HotspotsListScreen(
          state = state,
          onSelectRecord = { selectedHotspotForDetail = it },
          onOpenFilter = { showFilterDialog = true },
          filterCriteria = filterCriteria,
          onResetFilter = { filterCriteria = HotspotFilterCriteria() },
          modifier = Modifier.padding(innerPadding)
        )
      }

      AppBottomNavTab.INFO -> {
        InfoSystemScreen(
          state = state,
          onViewContract = { showContractScreen = true },
          onConfigureMapKey = { showKeyDialog = true },
          onRefreshSatellite = onRefreshSatellite,
          onRefreshLocation = onRefreshLocation,
          onRequestLocationPermission = {
            permissionLauncher.launch(
              arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
              )
            )
          },
          onOpenMap = { currentTab = AppBottomNavTab.PETA },
          modifier = Modifier.padding(innerPadding)
        )
      }
    }
  }
}

@Composable
fun PrimaryActionButtonsSection(
  onOpenHotspotsList: () -> Unit = {},
  onOpenMap: () -> Unit,
  onOpenMyLocation: () -> Unit,
  onRefreshSatellite: () -> Unit,
  isRefreshing: Boolean = false,
  cooldownSeconds: Long = 0L,
  lastUpdateDisplay: String = "",
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("primary_action_buttons_section"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. 🔥 Lihat Titik Panas
    Button(
      onClick = onOpenHotspotsList,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("primary_open_hotspots_button"),
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
      shape = RoundedCornerShape(14.dp)
    ) {
      Icon(Icons.Default.Whatshot, contentDescription = null, modifier = Modifier.size(24.dp))
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = "🔥 Lihat Titik Panas",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
    }

    // 2. 🗺️ Buka Peta
    Button(
      onClick = onOpenMap,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("primary_open_map_button"),
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
      shape = RoundedCornerShape(14.dp)
    ) {
      Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(24.dp))
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = "🗺️ Buka Peta",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
    }

    // 3. 📍 Lokasi Saya
    Button(
      onClick = onOpenMyLocation,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("primary_open_my_location_button"),
      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
      shape = RoundedCornerShape(14.dp)
    ) {
      Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(24.dp))
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = "📍 Lokasi Saya",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
    }

    // 4. 🔄 Perbarui Data Satelit
    Button(
      onClick = onRefreshSatellite,
      enabled = !isRefreshing && cooldownSeconds <= 0L,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("primary_refresh_satellite_button"),
      colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF263238),
        contentColor = Color.White,
        disabledContainerColor = Color(0xFF37474F).copy(alpha = 0.6f),
        disabledContentColor = Color.White.copy(alpha = 0.6f)
      ),
      shape = RoundedCornerShape(14.dp)
    ) {
      if (isRefreshing) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          color = Color.White,
          strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "Memperbarui Data...",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold
        )
      } else if (cooldownSeconds > 0L) {
        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "COOLDOWN (${cooldownSeconds}s)",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold
        )
      } else {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = "🔄 Perbarui Data Satelit",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
fun SystemStatusCard(state: DashboardState) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("system_status_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = "Status Sistem",
            tint = StatusVerified,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = "STATUS SISTEM",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(text = state.systemStatus, color = StatusVerified)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = state.systemDetail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/**
 * Real Location Card (FIRE-004)
 * Menampilkan status lokasi nyata perangkat tanpa koordinat dummy / fake.
 */
@Composable
fun RealLocationCard(
  state: DashboardState,
  onRequestPermission: () -> Unit,
  onRefreshLocation: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenLocationSettings: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("location_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      when (state.locationStatus) {
        LocationStatus.LOCATION_AVAILABLE -> StatusVerified.copy(alpha = 0.6f)
        LocationStatus.LOCATION_ERROR, LocationStatus.LOCATION_PERMISSION_DENIED -> StatusBlocked.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
      }
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header Card
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val cardTitle = when {
          state.deviceLocation?.verificationLevel == LocationVerificationLevel.REAL_DEVICE_VERIFIED -> "REAL DEVICE: VERIFIED"
          state.deviceLocation?.isMock == true -> "LOCATION: MOCK LOCATION"
          state.deviceLocation?.isFromCache == true -> "LOCATION: CACHED LOCATION"
          state.deviceLocation?.runtimeEnvironment == RuntimeEnvironment.EMULATOR -> "RUNTIME: EMULATOR (VIRTUAL)"
          state.deviceLocation?.runtimeEnvironment == RuntimeEnvironment.VIRTUAL_DEVICE -> "RUNTIME: VIRTUAL DEVICE"
          state.deviceLocation?.runtimeEnvironment == RuntimeEnvironment.CLOUD_CONTAINER -> "RUNTIME: CLOUD CONTAINER"
          state.locationStatus == LocationStatus.LOCATION_AVAILABLE -> "REAL DEVICE: NOT VERIFIED"
          else -> "LOKASI PERANGKAT"
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val icon = when (state.locationStatus) {
            LocationStatus.LOCATION_AVAILABLE -> Icons.Default.LocationOn
            LocationStatus.LOCATION_LOADING -> Icons.Default.GpsFixed
            LocationStatus.LOCATION_PROVIDER_DISABLED -> Icons.Default.GpsOff
            LocationStatus.LOCATION_PERMISSION_DENIED -> Icons.Default.LocationOff
            else -> Icons.Default.GpsNotFixed
          }
          val iconTint = when (state.locationStatus) {
            LocationStatus.LOCATION_AVAILABLE -> StatusVerified
            LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary
            LocationStatus.LOCATION_ERROR, LocationStatus.LOCATION_PERMISSION_DENIED -> StatusBlocked
            else -> StatusNotStarted
          }

          Icon(
            imageVector = icon,
            contentDescription = cardTitle,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = cardTitle,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        val badgeText = state.locationStatus.name
        val badgeColor = when (state.locationStatus) {
          LocationStatus.LOCATION_AVAILABLE -> StatusVerified
          LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary
          LocationStatus.LOCATION_ERROR, LocationStatus.LOCATION_PERMISSION_DENIED -> StatusBlocked
          else -> StatusNotStarted
        }
        StateBadge(text = badgeText, color = badgeColor)
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Content berdasarkan LocationStatus
      when (state.locationStatus) {
        LocationStatus.LOCATION_AVAILABLE -> {
          val loc = state.deviceLocation
          if (loc != null) {
            LocationDataDisplay(location = loc, onRefresh = onRefreshLocation)
          } else {
            Text(
              text = "LOCATION NOT AVAILABLE",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        LocationStatus.LOCATION_LOADING -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Mengakses sinyal GPS nyata perangkat...",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        LocationStatus.LOCATION_PERMISSION_REQUIRED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "LOCATION NOT AVAILABLE",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              ),
              color = StatusNotStarted,
              modifier = Modifier.testTag("location_status_text")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Aplikasi memerlukan izin lokasi Android nyata untuk menentukan posisi tanpa data dummy.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = onRequestPermission,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("request_permission_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
              )
            ) {
              Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("MINTA IZIN LOKASI")
            }
          }
        }

        LocationStatus.LOCATION_PERMISSION_DENIED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusBlocked,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "LOCATION PERMISSION DENIED",
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = StatusBlocked,
                  fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.testTag("location_denied_text")
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = state.locationErrorMessage
                ?: "Pengguna menolak izin akses lokasi. Koordinat dummy tidak akan digunakan.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = onRequestPermission,
                modifier = Modifier
                  .weight(1f)
                  .testTag("retry_permission_button")
              ) {
                Text("COBA LAGI")
              }
              Button(
                onClick = onOpenSettings,
                modifier = Modifier
                  .weight(1f)
                  .testTag("open_settings_button")
              ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PENGATURAN")
              }
            }
          }
        }

        LocationStatus.LOCATION_PROVIDER_DISABLED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.GpsOff,
                contentDescription = null,
                tint = StatusBlocked,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "LOCATION PROVIDER DISABLED",
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = StatusBlocked,
                  fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.testTag("location_provider_disabled_text")
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Sensor GPS/Layanan Lokasi perangkat sedang nonaktif. Mohon aktifkan GPS pada perangkat untuk memperoleh koordinat nyata.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = onOpenLocationSettings,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("enable_gps_button")
            ) {
              Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("AKTIFKAN GPS PERANGKAT")
            }
          }
        }

        LocationStatus.LOCATION_ERROR -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "LOCATION ERROR",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = StatusBlocked,
                fontFamily = FontFamily.Monospace
              ),
              modifier = Modifier.testTag("location_error_text")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = state.locationErrorMessage ?: "Terjadi kesalahan sistem saat meminta lokasi perangkat.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
              onClick = onRefreshLocation,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("retry_location_button")
            ) {
              Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("COBA LAGI MENGAMBIL LOKASI")
            }
          }
        }
      }
    }
  }
}

@Composable
fun LocationDataDisplay(
  location: DeviceLocation,
  onRefresh: () -> Unit
) {
  val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()) }
  val formattedFixTime = remember(location.timeMillis) {
    if (location.timeMillis > 0) timeFormat.format(Date(location.timeMillis)) else "Waktu tidak valid"
  }
  val isStale = remember(location.timeMillis) { location.isStale() }
  val locationAgeDisplay = remember(location.timeMillis) { location.getLocationAgeDisplay() }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // 1. Status Banner
    when {
      location.isMock -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "CRITICAL: MOCK / VIRTUAL LOCATION TERDETEKSI (DATA TIDAK TERVERIFIKASI)",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onErrorContainer
          )
        }
      }
      isStale -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "STALE LOCATION (> 15 menit): Koordinat lama tersimpan, bukan Current GPS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onErrorContainer
          )
        }
      }
      location.isFromCache -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "CACHED LOCATION: Lokasi terakhir tersimpan, bukan Real-time GPS Now",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onTertiaryContainer
          )
        }
      }
      else -> {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = "RUNTIME FIX: ${location.locationSource}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }
    }

    // 2. Real Device & Runtime Environment Disclaimer (Section 10)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
      Column {
        Text(
          text = if (location.verificationLevel == LocationVerificationLevel.REAL_DEVICE_VERIFIED) {
            "REAL DEVICE: VERIFIED (Perangkat Fisik Nyata Terkonfirmasi Lapangan)"
          } else {
            "REAL DEVICE: NOT VERIFIED (Verifikasi fisik manual lapangan belum dilakukan)"
          },
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          ),
          color = if (location.verificationLevel == LocationVerificationLevel.REAL_DEVICE_VERIFIED) StatusVerified else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "RUNTIME: ${location.runtimeEnvironment.displayName} (${location.runtimeEnvironment.description})",
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // 3. LATITUDE & LONGITUDE
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "LATITUDE",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
        )
        Text(
          text = String.format(Locale.US, "%.6f°", location.latitude),
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          ),
          modifier = Modifier.testTag("location_latitude_value")
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "LONGITUDE",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
        )
        Text(
          text = String.format(Locale.US, "%.6f°", location.longitude),
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          ),
          modifier = Modifier.testTag("location_longitude_value")
        )
      }
    }

    HorizontalDivider(
      modifier = Modifier.padding(vertical = 4.dp),
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    // 4. ACCURACY & LOCATION AGE (Section 13 & 15)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "ACCURACY",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )
        Text(
          text = if (location.accuracyMeters != null) {
            String.format(Locale.US, "± %.1f m", location.accuracyMeters)
          } else {
            "ACCURACY NOT AVAILABLE"
          },
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier.testTag("location_accuracy_value")
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "LOCATION AGE",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )
        Text(
          text = locationAgeDisplay,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier.testTag("location_age_value")
        )
      }
    }

    // 5. LOCATION SOURCE & LOCATION FIX TIME (Section 13)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "LOCATION SOURCE",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )
        Text(
          text = location.locationSource,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier.testTag("location_provider_value")
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "LOCATION TIME (${if (location.isFromCache) "Cache" else "Fix"})",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )
        Text(
          text = formattedFixTime,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace
          ),
          modifier = Modifier.testTag("location_time_value")
        )
      }
    }

    // 6. RUNTIME ENVIRONMENT & VERIFICATION STATUS (Section 7, 8, 13)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "RUNTIME ENVIRONMENT",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )
        Text(
          text = location.runtimeEnvironment.name,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier.testTag("location_runtime_env_value")
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "VERIFICATION STATUS",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )
        Text(
          text = location.verificationLevel.name,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
          ),
          color = when (location.verificationLevel) {
            LocationVerificationLevel.REAL_DEVICE_VERIFIED -> StatusVerified
            LocationVerificationLevel.MOCK, LocationVerificationLevel.UNVERIFIED -> StatusBlocked
            else -> MaterialTheme.colorScheme.onSurface
          },
          modifier = Modifier.testTag("location_verification_value")
        )
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Section 14: Tombol PERBARUI LOKASI
    OutlinedButton(
      onClick = onRefresh,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("refresh_location_button")
    ) {
      Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("PERBARUI LOKASI")
    }
  }
}

@Composable
fun FireDetectionCard(state: DashboardState) {
  val badgeColor = when (state.fireDataState) {
    DataState.AVAILABLE -> StatusVerified
    DataState.NOT_VERIFIED -> StatusNotStarted
    DataState.ERROR -> StatusBlocked
    else -> StatusNotStarted
  }
  val isVerified = state.fireDataState == DataState.AVAILABLE || state.validFireRecordCount != null || state.fireRecords.isNotEmpty()
  val statusDataText = when (state.fireDataState) {
    DataState.AVAILABLE -> "Tersedia (${state.fireRecords.size} valid)"
    DataState.NOT_VERIFIED -> state.fireStatusText
    DataState.ERROR -> "Gagal memuat data"
    else -> state.fireStatusText
  }

  val satText = if (state.satelliteDisplay != "BELUM TERSEDIA") state.satelliteDisplay else "VIIRS / NOAA-21 (NRT)"
  val ageText = if (state.fireRecords.isNotEmpty()) {
    val latestTs = state.fireRecords.mapNotNull { it.acquisitionTimestampMillis }.maxOrNull() ?: 0L
    if (latestTs > 0L) com.example.core.fire.FireDataAgeCalculator.formatAgeDetail(latestTs) else "Data NRT terkini"
  } else {
    if (state.dataAgeDisplay != "BELUM TERSEDIA") state.dataAgeDisplay else "Data NRT terkini"
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("fire_detection_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(18.dp),
    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF5722).copy(alpha = 0.45f))
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(Color(0xFFFF5722).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Whatshot,
              contentDescription = "Titik Panas",
              tint = Color(0xFFFF5722),
              modifier = Modifier.size(24.dp)
            )
          }
          Text(
            text = "Titik Panas Terdeteksi",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(
          text = if (state.fireDataState == DataState.AVAILABLE) "Data Tersedia" else "Perlu Verifikasi",
          color = badgeColor
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = state.fireCountDisplay,
          style = MaterialTheme.typography.displayMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            fontSize = 46.sp
          ),
          color = Color(0xFFFF5722),
          modifier = Modifier.testTag("fire_count_value")
        )
        Text(
          text = if (isVerified) "titik panas valid" else "titik panas",
          style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 8.dp)
        )
      }

      Text(
        text = "Data berdasarkan hasil deteksi satelit NASA FIRMS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
      Spacer(modifier = Modifier.height(10.dp))

      // 1. Waktu akuisisi satelit
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Waktu akuisisi satelit:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = if (state.lastUpdateDisplay != "BELUM TERSEDIA") state.lastUpdateDisplay else "Data resmi NASA FIRMS",
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      // 2. Satelit aktif
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Satelit aktif:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = satText,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      // 3. Status data
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Status data:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = statusDataText,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      // 4. Usia data
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Usia data:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = ageText,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}

@Composable
fun SatelliteDataCard(
  state: DashboardState,
  onConfigureKey: () -> Unit = {}
) {
  val badgeColor = when (state.satelliteState) {
    DataState.AVAILABLE -> StatusVerified
    DataState.NOT_VERIFIED -> StatusNotStarted
    DataState.ERROR -> StatusBlocked
    else -> StatusNotStarted
  }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("satellite_data_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = if (state.satelliteState == DataState.AVAILABLE) Icons.Default.CloudDone else Icons.Default.CloudOff,
            contentDescription = "Data Satelit",
            tint = badgeColor,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = "DATA SATELIT",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(text = state.satelliteDisplay, color = badgeColor)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = state.satelliteNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(4.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .padding(horizontal = 8.dp, vertical = 6.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "ARSITEKTUR: ${state.architectureStatus} (${state.credentialType})",
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "BATASAN: ${state.securityLimitation} (Penyimpanan lokal perangkat)",
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 9.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
          )
          if (state.responseSha256Hash != null) {
            Text(
              text = "RESPONSE HASH: ${state.responseSha256Hash.take(16)}...",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Status Kredensial: ${state.credentialState.name}",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
          onClick = onConfigureKey,
          modifier = Modifier.testTag("configure_key_button")
        ) {
          Icon(
            imageVector = Icons.Default.Key,
            contentDescription = "Konfigurasi MAP_KEY",
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("MAP_KEY", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

@Composable
fun LastUpdateCard(state: DashboardState) {
  val badgeColor = when (state.lastUpdateState) {
    DataState.AVAILABLE -> StatusVerified
    DataState.NOT_VERIFIED -> StatusNotStarted
    DataState.ERROR -> StatusBlocked
    else -> StatusNotStarted
  }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("last_update_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "Data Terakhir",
            tint = badgeColor,
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = "DATA TERAKHIR",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(text = state.lastUpdateDisplay, color = badgeColor)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = state.lastUpdateNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun NasaFirmsEvidenceCard(state: DashboardState) {
  val isVerified = state.liveVerificationGate == com.example.core.fire.LiveVerificationGate.LIVE_API_VERIFIED
  val badgeColor = if (isVerified) StatusVerified else StatusNotStarted

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("nasa_firms_evidence_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = "Evidence Panel",
            tint = badgeColor,
            modifier = Modifier.size(20.dp)
          )
          Text(
            text = "EVIDENCE: NASA FIRMS",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(
          text = if (isVerified) "VERIFIED" else "NOT VERIFIED",
          color = badgeColor
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(6.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .padding(8.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(
            text = "GATE: ${state.liveVerificationGate.name}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = if (isVerified) StatusVerified else MaterialTheme.colorScheme.primary
          )
          Text(
            text = "DIAGNOSIS: ${state.diagnosticCause.name}",
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold
            ),
            color = when (state.diagnosticCause) {
              com.example.core.fire.LiveApiDiagnosticCause.LIVE_REQUEST_SUCCESS -> StatusVerified
              com.example.core.fire.LiveApiDiagnosticCause.REQUEST_NOT_STARTED -> MaterialTheme.colorScheme.primary
              else -> StatusBlocked
            },
            modifier = Modifier.testTag("diagnostic_cause_text")
          )
          Text(
            text = "DETAIL: ${state.diagnosticDetail}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("diagnostic_detail_text")
          )
          Text(
            text = "HTTP STATUS: ${state.httpStatusCode ?: "N/A"} | SENSOR: ${state.satelliteSensorName}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "AREA BBOX: ${state.queryArea} | DAY RANGE: ${state.dayRange}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "BBOX STATUS: ${state.boundingBoxValidationStatus}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("audited_bbox_status_text")
          )
          Text(
            text = "ENDPOINT: ${state.endpointAudited}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.testTag("audited_endpoint_text")
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("RAW RECORDS", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
          Text(
            text = state.rawRecordCount.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text("VALID RECORDS", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
          Text(
            text = state.validFireRecordCount?.toString() ?: "N/A",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text("INVALID RECORDS", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
          Text(
            text = state.invalidRecordCount.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
          )
        }
      }

      HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
      )

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "FETCH TIME: ${state.lastFetchDisplay}",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "SATELLITE ACQUISITION: ${state.acquisitionRangeDisplay}",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "DATA AGE: ${state.dataAgeDisplay} | FRESHNESS: ${state.freshnessLevel.name}",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "SATELLITE DIST: ${state.satelliteDistributionDisplay}",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "INSTRUMENT DIST: ${state.instrumentDistributionDisplay}",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "CREDENTIAL: ${state.credentialState.name} (${state.credentialType})",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "MAP_KEY: HIDDEN (TIDAK DITAMPILKAN DI UI / LOGCAT)",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Text(
          text = "SECURITY: ${state.securityLimitation} (${state.architectureStatus})",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        if (state.responseSha256Hash != null) {
          Text(
            text = "RESPONSE SHA-256: ${state.responseSha256Hash}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
    }
  }
}

@Composable
fun RefreshSection(
  state: DashboardState,
  onRefreshSatellite: () -> Unit = {}
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      val isCooldown = state.cooldownRemainingSeconds > 0L
      OutlinedButton(
        onClick = {
          AppLogger.recordEvent("User clicked Refresh: Memperbarui data satelit NASA FIRMS")
          onRefreshSatellite()
        },
        enabled = state.isRefreshSatelliteEnabled && !state.isLoadingSatellite && !isCooldown,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("refresh_button")
      ) {
        if (state.isLoadingSatellite) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("MENGHUBUNGI NASA FIRMS...")
        } else if (isCooldown) {
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "Cooldown Aktif",
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("COOLDOWN (${state.cooldownRemainingSeconds}s)")
        } else {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Perbarui Data",
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("PERBARUI DATA SATELIT")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = state.refreshSatelliteNote,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun AuditSummaryCard(events: List<String>, errorCount: Int) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("audit_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "AUDIT LOGGER STATUS",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = if (errorCount == 0) "0 Active Errors" else "$errorCount Errors",
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
          ),
          color = if (errorCount == 0) StatusVerified else StatusBlocked
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      events.take(2).forEach { event ->
        Text(
          text = "• $event",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun StateBadge(text: String, color: Color, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(color.copy(alpha = 0.15f))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
      ),
      color = color
    )
  }
}

/**
 * Map Foundation Card (FIRE-005)
 * Menampilkan integrasi peta geografis dengan posisi pengguna tanpa marker api palsu.
 */
@Composable
fun MapFoundationCard(
  state: DashboardState,
  onOpenMap: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("map_status_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      when (state.mapStatus) {
        MapStatus.MAP_READY -> StatusVerified.copy(alpha = 0.5f)
        MapStatus.MAP_ERROR -> StatusBlocked.copy(alpha = 0.5f)
        MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
      }
    )
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Map,
            contentDescription = "Peta Geografis",
            tint = when (state.mapStatus) {
              MapStatus.MAP_READY -> StatusVerified
              MapStatus.MAP_ERROR -> StatusBlocked
              MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(22.dp)
          )
          Text(
            text = "PETA GEOGRAFIS (FIRE-005)",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        StateBadge(
          text = state.mapStatus.name,
          color = when (state.mapStatus) {
            MapStatus.MAP_READY -> StatusVerified
            MapStatus.MAP_ERROR -> StatusBlocked
            MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.primary
          },
          modifier = Modifier.testTag("map_card_status_badge")
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Status Peta & Lokasi
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(
            text = "MAP STATUS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          )
          Text(
            text = state.mapStatus.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.testTag("map_status_text")
          )
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "LOCATION STATUS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          )
          Text(
            text = state.locationStatus.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.testTag("map_location_status_text")
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Detail Koordinat jika lokasi tersedia
      if (state.locationStatus == LocationStatus.LOCATION_AVAILABLE && state.deviceLocation != null) {
        val loc = state.deviceLocation
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "LATITUDE", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(
              text = String.format(Locale.US, "%.6f°", loc.latitude),
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
              modifier = Modifier.testTag("map_latitude_text")
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "LONGITUDE", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(
              text = String.format(Locale.US, "%.6f°", loc.longitude),
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
              modifier = Modifier.testTag("map_longitude_text")
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "ACCURACY", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(
              text = if (loc.accuracyMeters != null) String.format(Locale.US, "±%.1fm", loc.accuracyMeters) else "N/A",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
              modifier = Modifier.testTag("map_accuracy_text")
            )
          }
        }
      } else {
        Text(
          text = "LOCATION NOT AVAILABLE — Posisi pengguna belum terhubung ke kamera peta.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("map_location_not_available_text")
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "Provider: ${state.mapProviderName} | Credential: ${state.mapCredentialStatus}",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(4.dp))

      val fireMarkersNotice = when {
        state.fireDataSourceState == com.example.core.fire.FireDataSourceState.DATA_SOURCE_AVAILABLE && state.fireRecords.isNotEmpty() ->
          "DETEKSI TITIK PANAS NASA FIRMS: ${state.fireRecords.size} MARKER (FIRE-008 AKTIF)"
        state.fireDataSourceState == com.example.core.fire.FireDataSourceState.NO_DETECTIONS_IN_QUERY ->
          "0 DETEKSI TITIK PANAS DALAM OVERPASS SATELIT (FIRE-008)"
        state.fireDataSourceState == com.example.core.fire.FireDataSourceState.CACHED && state.fireRecords.isNotEmpty() ->
          "TITIK PANAS SESSION CACHE: ${state.fireRecords.size} MARKER (FIRE-008)"
        state.credentialState == com.example.core.fire.FireDataCredentialState.CONFIGURED ->
          "MAP_KEY TERKONFIGURASI — TEKAN PERBARUI DATA UNTUK MARKER FIRE-008"
        else ->
          "DATA SATELIT BELUM DIVERIFIKASI — MARKER TITIK PANAS DITAHAN (ZERO-DUMMY)"
      }
      val fireMarkersNoticeColor = when {
        state.fireDataSourceState == com.example.core.fire.FireDataSourceState.DATA_SOURCE_AVAILABLE ||
          state.fireDataSourceState == com.example.core.fire.FireDataSourceState.NO_DETECTIONS_IN_QUERY -> StatusVerified
        state.credentialState == com.example.core.fire.FireDataCredentialState.CONFIGURED -> MaterialTheme.colorScheme.primary
        else -> StatusNotStarted
      }
      Text(
        text = fireMarkersNotice,
        style = MaterialTheme.typography.labelSmall.copy(
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        ),
        color = fireMarkersNoticeColor,
        modifier = Modifier.testTag("fire_markers_notice_text")
      )

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = onOpenMap,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("open_map_screen_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
      ) {
        Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("BUKA PETA GEOGRAFIS")
      }
    }
  }
}
