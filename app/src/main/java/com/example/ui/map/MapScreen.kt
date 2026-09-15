package com.example.ui.map

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.location.LocationVerificationLevel
import com.example.core.location.RuntimeEnvironment
import com.example.core.logging.AppError
import com.example.core.logging.AppLogger
import com.example.core.logging.ErrorType
import com.example.core.map.CoordinateValidator
import com.example.core.map.MapProviderInfo
import com.example.ui.theme.StatusBlocked
import com.example.ui.theme.StatusNotStarted
import com.example.ui.theme.StatusVerified
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.core.fire.FireDataAgeCalculator
import com.example.core.fire.FireDataRecord
import com.example.core.fire.FireDataSourceState
import com.example.ui.dashboard.HotspotFilterCriteria
import com.example.core.map.BaseMapLayer
import com.example.core.map.MapTileProviderFactory
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen Peta Geografis (FIRE-005 — Map Foundation & FIRE-008 — Verified Satellite Hotspot Markers)
 * Menampilkan peta nyata berbasis osmdroid dan posisi riil pengguna dari FIRE-004.
 * Menampilkan deteksi titik panas dari NASA FIRMS (FIRE-007 / FIRE-008) HANYA jika data otentik valid.
 *
 * MANDAT ZERO-DUMMY:
 * - Tidak ada marker api jika data belum diverifikasi atau request gagal.
 * - Tidak ada koordinat hardcoded/dummy.
 * - Koordinat divalidasi ketat sebelum ditampilkan pada peta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
  modifier: Modifier = Modifier,
  deviceLocation: DeviceLocation? = null,
  locationStatus: LocationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED,
  locationErrorMessage: String? = null,
  fireRecords: List<FireDataRecord> = emptyList(),
  totalFireRecordsCount: Int = fireRecords.size,
  filterCriteria: HotspotFilterCriteria = HotspotFilterCriteria(),
  onOpenFilter: () -> Unit = {},
  onResetFilter: () -> Unit = {},
  onMapStatusChanged: (MapStatus, BaseMapLayer) -> Unit = { _, _ -> },
  fireDataSourceState: FireDataSourceState = FireDataSourceState.NOT_VERIFIED,
  onBackToDashboard: () -> Unit = {},
  onRefreshLocation: () -> Unit = {},
  onRefreshSatellite: () -> Unit = {},
  onRequestPermission: () -> Unit = {}
) {
  val context = LocalContext.current

  // State inisialisasi peta
  var mapStatus by remember { mutableStateOf(MapStatus.MAP_LOADING) }
  var mapErrorMessage by remember { mutableStateOf<String?>(null) }
  var tileRetryKey by remember { mutableStateOf(0) }
  var mapViewRef by remember { mutableStateOf<MapView?>(null) }
  var hasInitialCentered by remember { mutableStateOf(false) }
  var selectedBaseMapLayer by remember { mutableStateOf(BaseMapLayer.SATELLITE_ESRI) }
  var showLayerMenu by remember { mutableStateOf(false) }
  var selectedFireRecord by remember { mutableStateOf<FireDataRecord?>(null) }
  var showLocationDetails by remember { mutableStateOf(false) }

  // Marker performance cache
  var lastUserLocationFingerprint by remember { mutableStateOf<Int?>(null) }
  var lastFireDataFingerprint by remember { mutableStateOf<Int?>(null) }
  var cachedUserMarker by remember { mutableStateOf<Marker?>(null) }
  val cachedFireMarkers = remember { mutableListOf<Marker>() }

  // Validasi koordinat sesuai Section 14
  val validationResult = remember(deviceLocation) {
    if (deviceLocation != null) {
      CoordinateValidator.validateLocation(deviceLocation)
    } else {
      CoordinateValidator.ValidationResult(isValid = false)
    }
  }

  // Inisialisasi konfigurasi osmdroid
  DisposableEffect(Unit) {
    try {
      Configuration.getInstance().load(
        context.applicationContext,
        context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
      )
      Configuration.getInstance().userAgentValue = context.packageName
    } catch (e: Throwable) {
      AppLogger.recordError(
        AppError(
          type = ErrorType.UNKNOWN_ERROR,
          message = "Gagal konfigurasi osmdroid: ${e.message}",
          source = "MapScreen.DisposableEffect",
          recoveryAction = "Periksa permission dan konfigurasi osmdroid",
          cause = e
        )
      )
    }

    onDispose {
      mapViewRef?.onDetach()
    }
  }

  // Validasi tile nyata sebelum menetapkan MAP_READY (A1, A2, A4, A5)
  LaunchedEffect(selectedBaseMapLayer, tileRetryKey) {
    mapStatus = MapStatus.MAP_LOADING
    onMapStatusChanged(MapStatus.MAP_LOADING, selectedBaseMapLayer)
    mapErrorMessage = null

    val centerLat = deviceLocation?.latitude ?: fireRecords.firstOrNull()?.latitude ?: -2.5
    val centerLon = deviceLocation?.longitude ?: fireRecords.firstOrNull()?.longitude ?: 114.0
    val zoom = if (deviceLocation != null) 14 else if (fireRecords.isNotEmpty()) 10 else 5

    val result = com.example.core.map.MapTileValidator.validateTileForViewport(
      layer = selectedBaseMapLayer,
      latitude = centerLat,
      longitude = centerLon,
      zoom = zoom
    )
    result.fold(
      onSuccess = {
        mapStatus = MapStatus.MAP_READY
        mapErrorMessage = null
        onMapStatusChanged(MapStatus.MAP_READY, selectedBaseMapLayer)
      },
      onFailure = { error ->
        mapStatus = MapStatus.MAP_ERROR
        mapErrorMessage = error.message ?: if (selectedBaseMapLayer == BaseMapLayer.OPEN_STREET_MAP) {
          "Peta jalan tidak dapat dimuat. Periksa koneksi internet lalu coba lagi."
        } else {
          "Peta satelit tidak dapat dimuat. Periksa koneksi internet lalu coba lagi."
        }
        onMapStatusChanged(MapStatus.MAP_ERROR, selectedBaseMapLayer)
      }
    )
  }

  // Efek perpindahan kamera saat lokasi pertama kali tersedia (Section 10)
  LaunchedEffect(deviceLocation, validationResult.isValid, mapViewRef) {
    val mv = mapViewRef
    if (mv != null && deviceLocation != null && validationResult.isValid && !hasInitialCentered) {
      try {
        val targetPoint = GeoPoint(deviceLocation.latitude, deviceLocation.longitude)
        mv.controller.setZoom(16.0)
        mv.controller.animateTo(targetPoint)
        hasInitialCentered = true
      } catch (e: Throwable) {
        AppLogger.recordError(
          AppError(
            type = ErrorType.UNKNOWN_ERROR,
            message = "Gagal mengarahkan kamera ke lokasi: ${e.message}",
            source = "MapScreen.LaunchedEffect",
            recoveryAction = "Verifikasi GeoPoint dan status MapView",
            cause = e
          )
        )
      }
    }
  }

  val isFiltered = filterCriteria.maxDistanceKm != null || filterCriteria.satellite != null || filterCriteria.maxAgeHours != null
  val indicatorText = if (isFiltered) {
    "Menampilkan ${fireRecords.size} dari $totalFireRecordsCount titik panas"
  } else {
    "Menampilkan ${fireRecords.size} titik panas"
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("map_screen"),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Peta Titik Panas",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              modifier = Modifier.testTag("map_screen_title")
            )
            Text(
              text = indicatorText,
              style = MaterialTheme.typography.labelSmall.copy(
                color = if (isFiltered) Color(0xFFFF5722) else MaterialTheme.colorScheme.primary,
                fontWeight = if (isFiltered) FontWeight.Bold else FontWeight.Normal
              ),
              modifier = Modifier.testTag("map_provider_info_text")
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onBackToDashboard,
            modifier = Modifier.testTag("back_to_dashboard_from_map_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Kembali ke Dashboard"
            )
          }
        },
        actions = {
          IconButton(
            onClick = onOpenFilter,
            modifier = Modifier.testTag("map_filter_button")
          ) {
            Icon(
              imageVector = Icons.Default.FilterList,
              contentDescription = "Penyaring",
              tint = if (isFiltered) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurface
            )
          }
          Box {
            IconButton(
              onClick = { showLayerMenu = true },
              modifier = Modifier.testTag("layer_selector_button")
            ) {
              Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Pilih Layer Peta"
              )
            }
            DropdownMenu(
              expanded = showLayerMenu,
              onDismissRequest = { showLayerMenu = false }
            ) {
              BaseMapLayer.values().forEach { layer ->
                DropdownMenuItem(
                  text = {
                    Column {
                      Text(
                        text = layer.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                          fontWeight = if (selectedBaseMapLayer == layer) FontWeight.Bold else FontWeight.Normal
                        )
                      )
                      Text(
                        text = layer.providerDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  },
                  onClick = {
                    selectedBaseMapLayer = layer
                    mapViewRef?.setTileSource(MapTileProviderFactory.getTileSource(layer))
                    showLayerMenu = false
                  }
                )
              }
            }
          }
          IconButton(
            onClick = { showLocationDetails = !showLocationDetails },
            modifier = Modifier.testTag("toggle_location_details_button")
          ) {
            Icon(
              imageVector = if (showLocationDetails) Icons.Default.Close else Icons.Default.Info,
              contentDescription = "Detail Telemetri Lokasi"
            )
          }
          IconButton(
            onClick = onRefreshLocation,
            modifier = Modifier.testTag("refresh_location_from_map_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh Lokasi"
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        )
      )
    },
    floatingActionButton = {
      // FAB Pusatkan ke Lokasi Pengguna
      if (locationStatus == LocationStatus.LOCATION_AVAILABLE && deviceLocation != null && validationResult.isValid) {
        FloatingActionButton(
          onClick = {
            mapViewRef?.let { mv ->
              try {
                val pt = GeoPoint(deviceLocation.latitude, deviceLocation.longitude)
                mv.controller.animateTo(pt)
                mv.controller.setZoom(16.5)
              } catch (e: Throwable) {
                AppLogger.recordError(
                  AppError(
                    type = ErrorType.UNKNOWN_ERROR,
                    message = "Gagal memusatkan kamera: ${e.message}",
                    source = "MapScreen.FAB",
                    recoveryAction = "Verifikasi MapView controller",
                    cause = e
                  )
                )
              }
            }
          },
          modifier = Modifier.testTag("recenter_button"),
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
          Icon(
            imageVector = Icons.Default.MyLocation,
            contentDescription = "Pusatkan ke Posisi Saya"
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // 1. Tampilan Native MapView atau Error / Loading Fallback
      if (mapStatus == MapStatus.MAP_ERROR) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(52.dp)
            )
            val errorTitle = if (selectedBaseMapLayer == BaseMapLayer.OPEN_STREET_MAP) {
              "Peta jalan tidak dapat dimuat."
            } else {
              "Peta satelit tidak dapat dimuat."
            }
            Text(
              text = errorTitle,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("map_error_title")
            )
            Text(
              text = "Periksa koneksi internet lalu coba lagi.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("map_error_subtitle")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = { tileRetryKey++ },
                modifier = Modifier.testTag("retry_tile_button")
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Coba Lagi")
              }
              if (selectedBaseMapLayer == BaseMapLayer.SATELLITE_ESRI) {
                OutlinedButton(
                  onClick = {
                    selectedBaseMapLayer = BaseMapLayer.OPEN_STREET_MAP
                    mapViewRef?.setTileSource(MapTileProviderFactory.getTileSource(BaseMapLayer.OPEN_STREET_MAP))
                    tileRetryKey++
                  },
                  modifier = Modifier.testTag("fallback_osm_button")
                ) {
                  Text("Gunakan Peta Jalan (OSM)")
                }
              }
            }
          }
        }
      } else if (mapStatus == MapStatus.MAP_LOADING) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            CircularProgressIndicator(modifier = Modifier.size(44.dp))
            val loadingMsg = if (selectedBaseMapLayer == BaseMapLayer.OPEN_STREET_MAP) {
              "Peta jalan sedang dimuat..."
            } else {
              "Citra satelit sedang dimuat..."
            }
            Text(
              text = loadingMsg,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        // Native OpenStreetMap Component
        AndroidView(
          factory = { ctx ->
            try {
              MapView(ctx).apply {
                setTileSource(MapTileProviderFactory.getTileSource(selectedBaseMapLayer))
                setMultiTouchControls(true)
                controller.setZoom(5.0) // Neutral default world view before GPS fix
                mapViewRef = this
              }
            } catch (e: Throwable) {
              mapStatus = MapStatus.MAP_ERROR
              mapErrorMessage = e.message ?: "Gagal membuat MapView"
              AppLogger.recordError(
                AppError(
                  type = ErrorType.UNKNOWN_ERROR,
                  message = "Gagal membuat MapView: ${e.message}",
                  source = "MapScreen.AndroidView.factory",
                  recoveryAction = "Periksa konfigurasi runtime MapView",
                  cause = e
                )
              )
              View(ctx)
            }
          },
          update = { mv ->
            if (mv is MapView) {
              // Update Tile Source if changed
              val expectedSource = MapTileProviderFactory.getTileSource(selectedBaseMapLayer)
              if (mv.tileProvider.tileSource.name() != expectedSource.name()) {
                mv.setTileSource(expectedSource)
              }

              // 1. User Location Marker Fingerprint & Update
              val currentUserFingerprint = if (locationStatus == LocationStatus.LOCATION_AVAILABLE &&
                deviceLocation != null &&
                validationResult.isValid
              ) {
                java.util.Objects.hash(
                  deviceLocation.latitude,
                  deviceLocation.longitude,
                  deviceLocation.accuracyMeters,
                  deviceLocation.verificationLevel,
                  deviceLocation.timeMillis
                )
              } else {
                null
              }

              if (currentUserFingerprint != lastUserLocationFingerprint) {
                cachedUserMarker = if (currentUserFingerprint != null && deviceLocation != null) {
                  Marker(mv).apply {
                    position = GeoPoint(deviceLocation.latitude, deviceLocation.longitude)
                    title = when {
                      deviceLocation.isMock -> "MOCK LOCATION (UNVERIFIED)"
                      deviceLocation.runtimeEnvironment == RuntimeEnvironment.EMULATOR ||
                      deviceLocation.runtimeEnvironment == RuntimeEnvironment.VIRTUAL_DEVICE ||
                      deviceLocation.runtimeEnvironment == RuntimeEnvironment.CLOUD_CONTAINER -> "VIRTUAL TEST LOCATION"
                      deviceLocation.isFromCache -> "CACHED LOCATION"
                      deviceLocation.verificationLevel == LocationVerificationLevel.REAL_DEVICE_VERIFIED -> "REAL DEVICE LOCATION"
                      else -> "LOKASI PERANGKAT (${deviceLocation.locationSource})"
                    }
                    val shortTime = if (deviceLocation.timeMillis > 0) {
                      SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(deviceLocation.timeMillis))
                    } else "N/A"
                    snippet = String.format(
                      Locale.US,
                      "Lat: %.6f°, Lon: %.6f°\nSumber: %s | Waktu: %s%s",
                      deviceLocation.latitude,
                      deviceLocation.longitude,
                      deviceLocation.locationSource,
                      shortTime,
                      if (deviceLocation.accuracyMeters != null) "\nAkurasi: ±%.1f m".format(deviceLocation.accuracyMeters) else ""
                    )
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                  }
                } else {
                  null
                }
                lastUserLocationFingerprint = currentUserFingerprint
              }

              // 2. Verified Satellite Hotspot Markers Fingerprint & Update
              // Audit Point 9: Identitas stabil dari seluruh record (lat, lon, acqTime, sat, inst)
              val isLiveOrCached = fireDataSourceState == FireDataSourceState.DATA_SOURCE_AVAILABLE ||
                (fireDataSourceState == FireDataSourceState.CACHED && fireRecords.isNotEmpty())

              val currentFireFingerprint = if (isLiveOrCached) {
                var hash = 17
                hash = 31 * hash + fireDataSourceState.hashCode()
                hash = 31 * hash + fireRecords.size
                for (fire in fireRecords) {
                  hash = 31 * hash + fire.latitude.hashCode()
                  hash = 31 * hash + fire.longitude.hashCode()
                  hash = 31 * hash + (fire.acquisitionTimestampMillis?.hashCode() ?: 0)
                  hash = 31 * hash + fire.satellite.hashCode()
                  hash = 31 * hash + fire.instrument.hashCode()
                }
                hash
              } else {
                0
              }

              if (currentFireFingerprint != lastFireDataFingerprint) {
                cachedFireMarkers.clear()
                if (isLiveOrCached) {
                  val validFires = fireRecords.filter { fire ->
                    CoordinateValidator.isValid(fire.latitude, fire.longitude) &&
                      !(fire.latitude == 0.0 && fire.longitude == 0.0)
                  }

                  validFires.forEach { fire ->
                    val fireMarker = Marker(mv).apply {
                      position = GeoPoint(fire.latitude, fire.longitude)
                      title = "DETEKSI TITIK PANAS NASA FIRMS"
                      val acqDateStr = fire.acqDate.ifBlank { "N/A" }
                      val acqTimeStr = if (fire.acqTime.isNotBlank()) "${fire.acqTime} UTC" else "N/A"
                      val satStr = fire.satellite.ifBlank { "N/A" }
                      val instStr = fire.instrument.ifBlank { "N/A" }
                      val confStr = fire.confidence ?: "N/A"
                      val frpStr = if (fire.frp != null) "${fire.frp} MW" else "N/A"
                      val ageStr = FireDataAgeCalculator.formatAgeDetail(fire.acquisitionTimestampMillis)

                      snippet = "Satelit: $satStr ($instStr)\nWaktu: $acqDateStr $acqTimeStr\nConfidence: $confStr | FRP: $frpStr\nUsia: $ageStr"
                      setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                      setOnMarkerClickListener { m, _ ->
                        m.showInfoWindow()
                        selectedFireRecord = fire
                        true
                      }
                    }
                    cachedFireMarkers.add(fireMarker)
                  }

                  if (validFires.isNotEmpty()) {
                    AppLogger.recordEvent("HOTSPOT_MARKERS_RENDERED: count=${validFires.size}")
                  }
                }
                lastFireDataFingerprint = currentFireFingerprint
              }

              // Pasang overlay ke MapView secara efisien
              mv.overlays.clear()
              cachedUserMarker?.let { mv.overlays.add(it) }
              mv.overlays.addAll(cachedFireMarkers)

              mv.invalidate()
            }
          },
          modifier = Modifier
            .fillMaxSize()
            .testTag("osm_map_view")
        )
      }

      // 2. Status Bar Atas (Status Peta & Status Lokasi)
      MapStatusBar(
        mapStatus = mapStatus,
        activeLayer = selectedBaseMapLayer,
        locationStatus = locationStatus,
        isValidCoordinate = validationResult.isValid,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(12.dp)
      )

      // 2a. Floating Legenda Peta
      Card(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(start = 12.dp, top = 64.dp)
          .testTag("map_legend_card"),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF5722))
            )
            val legendHotspotLabel = if (isFiltered) {
              "🔥 Titik Panas (${fireRecords.size}/$totalFireRecordsCount)"
            } else {
              "🔥 Titik Panas (${fireRecords.size})"
            }
            Text(
              text = legendHotspotLabel,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF00C853))
            )
            Text(
              text = "📍 Lokasi Saya",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
          }
          if (isFiltered) {
            TextButton(
              onClick = onResetFilter,
              contentPadding = PaddingValues(0.dp),
              modifier = Modifier.height(24.dp)
            ) {
              Text("Atur Ulang Penyaring ✕", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // 3. Floating Kontrol Peta & Telemetri
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (showLocationDetails) {
          UserLocationInfoCard(
            location = deviceLocation,
            locationStatus = locationStatus,
            validationResult = validationResult,
            locationErrorMessage = locationErrorMessage,
            onRequestPermission = onRequestPermission,
            fireRecords = fireRecords,
            fireDataSourceState = fireDataSourceState,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("user_location_info_card")
          )
        }

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("map_controls_dock"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // 🔥 Semua Titik Panas (B8, B9)
            FilledTonalButton(
              onClick = {
                val validFires = fireRecords.filter { CoordinateValidator.isValid(it.latitude, it.longitude) }
                if (validFires.isEmpty()) {
                  val msg = if (isFiltered) "Tidak ada titik panas yang sesuai dengan penyaring." else "Belum ada titik panas untuk difokuskan."
                  android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                } else if (validFires.size == 1) {
                  val single = validFires.first()
                  mapViewRef?.let { mv ->
                    mv.controller.setZoom(15.0)
                    mv.controller.animateTo(GeoPoint(single.latitude, single.longitude))
                  }
                } else {
                  mapViewRef?.let { mv ->
                    val minLat = validFires.minOf { it.latitude }
                    val maxLat = validFires.maxOf { it.latitude }
                    val minLon = validFires.minOf { it.longitude }
                    val maxLon = validFires.maxOf { it.longitude }
                    val centerLat = (minLat + maxLat) / 2.0
                    val centerLon = (minLon + maxLon) / 2.0
                    val latSpan = maxLat - minLat
                    val lonSpan = maxLon - minLon
                    val latPadding = maxOf(latSpan * 0.08, 0.01)
                    val lonPadding = maxOf(lonSpan * 0.08, 0.01)
                    try {
                      mv.zoomToBoundingBox(
                        org.osmdroid.util.BoundingBox(maxLat + latPadding, maxLon + lonPadding, minLat - latPadding, minLon - lonPadding),
                        true,
                        64
                      )
                    } catch (_: Throwable) {
                      mv.controller.setZoom(12.0)
                      mv.controller.animateTo(GeoPoint(centerLat, centerLon))
                    }
                  }
                }
              },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.testTag("fit_all_hotspots_button")
            ) {
              Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Semua Titik", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp))
            }

            // 📍 Lokasi Saya
            FilledTonalButton(
              onClick = {
                if (deviceLocation != null && validationResult.isValid) {
                  mapViewRef?.let { mv ->
                    mv.controller.setZoom(16.0)
                    mv.controller.animateTo(GeoPoint(deviceLocation.latitude, deviceLocation.longitude))
                  }
                } else {
                  android.widget.Toast.makeText(context, "Lokasi GPS belum tersedia.", android.widget.Toast.LENGTH_SHORT).show()
                }
              },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.testTag("my_location_map_button")
            ) {
              Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Lokasi Saya", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp))
            }

            // ☰ Lapisan Peta
            IconButton(
              onClick = { showLayerMenu = true },
              modifier = Modifier.size(36.dp).testTag("layer_selector_button_dock")
            ) {
              Icon(Icons.Default.Layers, contentDescription = "Lapisan Peta", modifier = Modifier.size(20.dp))
            }

            // ➕ Zoom In
            IconButton(
              onClick = { mapViewRef?.controller?.zoomIn() },
              modifier = Modifier.size(36.dp).testTag("zoom_in_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
            }

            // ➖ Zoom Out
            IconButton(
              onClick = { mapViewRef?.controller?.zoomOut() },
              modifier = Modifier.size(36.dp).testTag("zoom_out_button")
            ) {
              Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
            }

            // 🔄 Muat Ulang
            IconButton(
              onClick = {
                onRefreshLocation()
                onRefreshSatellite()
                mapViewRef?.invalidate()
              },
              modifier = Modifier.size(36.dp).testTag("refresh_map_button")
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", modifier = Modifier.size(20.dp))
            }
          }
        }
      }

      // 4. Detail Dialog jika marker titik api diklik
      selectedFireRecord?.let { fire ->
        FireMarkerDetailDialog(
          record = fire,
          userLocation = deviceLocation,
          onDismiss = { selectedFireRecord = null }
        )
      }
    }
  }
}

/**
 * Top Status Bar displaying MAP STATUS and LOCATION STATUS
 */
@Composable
private fun MapStatusBar(
  mapStatus: MapStatus,
  activeLayer: BaseMapLayer = BaseMapLayer.SATELLITE_ESRI,
  locationStatus: LocationStatus,
  isValidCoordinate: Boolean,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ),
    shape = RoundedCornerShape(10.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // MAP STATUS
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Map,
          contentDescription = null,
          tint = when (mapStatus) {
            MapStatus.MAP_READY -> StatusVerified
            MapStatus.MAP_ERROR -> StatusBlocked
            MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.primary
          },
          modifier = Modifier.size(16.dp)
        )
        val mapStatusLabel = when (mapStatus) {
          MapStatus.MAP_READY -> if (activeLayer == BaseMapLayer.OPEN_STREET_MAP) "Peta Jalan Tersedia" else "Citra Satelit Siap"
          MapStatus.MAP_ERROR -> if (activeLayer == BaseMapLayer.OPEN_STREET_MAP) "Peta Jalan Gagal" else "Peta Satelit Gagal"
          MapStatus.MAP_LOADING -> if (activeLayer == BaseMapLayer.OPEN_STREET_MAP) "Memuat Peta Jalan..." else "Memuat Citra Satelit..."
        }
        Text(
          text = mapStatusLabel,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold
          ),
          color = when (mapStatus) {
            MapStatus.MAP_READY -> StatusVerified
            MapStatus.MAP_ERROR -> StatusBlocked
            MapStatus.MAP_LOADING -> MaterialTheme.colorScheme.primary
          },
          modifier = Modifier.testTag("map_status_badge")
        )
      }

      // LOCATION STATUS
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val locIcon = when (locationStatus) {
          LocationStatus.LOCATION_AVAILABLE -> if (isValidCoordinate) Icons.Default.LocationOn else Icons.Default.Warning
          LocationStatus.LOCATION_LOADING -> Icons.Default.GpsFixed
          LocationStatus.LOCATION_PROVIDER_DISABLED -> Icons.Default.GpsOff
          LocationStatus.LOCATION_PERMISSION_DENIED -> Icons.Default.LocationOff
          else -> Icons.Default.LocationOff
        }
        val locColor = when (locationStatus) {
          LocationStatus.LOCATION_AVAILABLE -> if (isValidCoordinate) StatusVerified else StatusBlocked
          LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary
          LocationStatus.LOCATION_ERROR, LocationStatus.LOCATION_PERMISSION_DENIED -> StatusBlocked
          else -> StatusNotStarted
        }

        Icon(
          imageVector = locIcon,
          contentDescription = null,
          tint = locColor,
          modifier = Modifier.size(16.dp)
        )
        val locText = when {
          locationStatus == LocationStatus.LOCATION_AVAILABLE && !isValidCoordinate -> "KOORDINAT TIDAK VALID"
          locationStatus == LocationStatus.LOCATION_AVAILABLE -> "GPS AKTIF"
          locationStatus == LocationStatus.LOCATION_LOADING -> "MENCARI GPS..."
          locationStatus == LocationStatus.LOCATION_PROVIDER_DISABLED -> "GPS NONAKTIF"
          locationStatus == LocationStatus.LOCATION_PERMISSION_DENIED -> "IZIN DITOLAK"
          else -> locationStatus.name
        }
        Text(
          text = locText,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          ),
          color = locColor,
          modifier = Modifier.testTag("map_location_status_badge")
        )
      }
    }
  }
}

/**
 * User Location Information Card adhering to Section 11 Prompt 005.
 */
@Composable
fun UserLocationInfoCard(
  location: DeviceLocation?,
  locationStatus: LocationStatus,
  validationResult: CoordinateValidator.ValidationResult,
  locationErrorMessage: String?,
  onRequestPermission: () -> Unit,
  fireRecords: List<FireDataRecord> = emptyList(),
  fireDataSourceState: FireDataSourceState = FireDataSourceState.NOT_VERIFIED,
  modifier: Modifier = Modifier
) {
  val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()) }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("user_location_info_card"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ),
    shape = RoundedCornerShape(14.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      when {
        locationStatus == LocationStatus.LOCATION_AVAILABLE && validationResult.isValid -> StatusVerified.copy(alpha = 0.6f)
        locationStatus == LocationStatus.LOCATION_PERMISSION_DENIED || locationStatus == LocationStatus.LOCATION_ERROR -> StatusBlocked.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
      }
    )
  ) {
    val cardTitle = when {
      location?.verificationLevel == LocationVerificationLevel.REAL_DEVICE_VERIFIED -> "REAL DEVICE: VERIFIED"
      location?.isMock == true -> "LOCATION: MOCK LOCATION"
      location?.isFromCache == true -> "LOCATION: CACHED LOCATION"
      location?.runtimeEnvironment == RuntimeEnvironment.EMULATOR -> "RUNTIME: EMULATOR (VIRTUAL TEST LOCATION)"
      location?.runtimeEnvironment == RuntimeEnvironment.VIRTUAL_DEVICE -> "RUNTIME: VIRTUAL DEVICE (VIRTUAL TEST LOCATION)"
      location?.runtimeEnvironment == RuntimeEnvironment.CLOUD_CONTAINER -> "RUNTIME: CLOUD CONTAINER (VIRTUAL TEST LOCATION)"
      location != null -> "REAL DEVICE: NOT VERIFIED"
      else -> "MAP INITIAL VIEW"
    }

    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Header Card
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.LocationOn,
              contentDescription = cardTitle,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(16.dp)
            )
          }
          Column {
            Text(
              text = cardTitle,
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("user_location_title")
            )
            val badgeFireText = when {
              fireDataSourceState == FireDataSourceState.DATA_SOURCE_AVAILABLE && fireRecords.isNotEmpty() ->
                "${fireRecords.size} DETEKSI TITIK PANAS NASA FIRMS"
              fireDataSourceState == FireDataSourceState.NO_DETECTIONS_IN_QUERY ->
                "0 DETEKSI TITIK PANAS (QUERY RESMI NASA)"
              fireDataSourceState == FireDataSourceState.CACHED && fireRecords.isNotEmpty() ->
                "${fireRecords.size} TITIK PANAS (SESSION CACHE)"
              else ->
                "ZERO-DUMMY: DATA TITIK PANAS BELUM TERHUBUNG"
            }
            val badgeFireColor = when {
              fireDataSourceState == FireDataSourceState.DATA_SOURCE_AVAILABLE && fireRecords.isNotEmpty() ->
                StatusVerified
              fireDataSourceState == FireDataSourceState.NO_DETECTIONS_IN_QUERY ->
                StatusVerified
              else ->
                StatusNotStarted
            }
            Text(
              text = badgeFireText,
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = badgeFireColor,
              modifier = Modifier.testTag("zero_fire_markers_badge")
            )
          }
        }

        // Credential badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = "CREDENTIAL: ${MapProviderInfo.CREDENTIAL_STATUS}",
            style = MaterialTheme.typography.labelSmall.copy(
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(vertical = 2.dp)
      )

      // Detail Koordinat & Status
      when {
        locationStatus == LocationStatus.LOCATION_AVAILABLE && location != null && validationResult.isValid -> {
          // Source & Truth Status Banner
          if (location.isMock) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "CRITICAL: MOCK / FAKE PROVIDER TERDETEKSI (UNVERIFIED)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onErrorContainer
              )
            }
          } else if (location.isStale()) {
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
          } else if (location.isFromCache) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "CACHED LOCATION (Posisi terakhir tersimpan, bukan Real-time GPS Now)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
            }
          } else {
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

          // Real device verification disclaimer
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

          // Latitude & Longitude
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
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.testTag("user_latitude_text")
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
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.testTag("user_longitude_text")
              )
            }
          }

          // Accuracy & Location Age
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
                  "BELUM TERSEDIA"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.testTag("user_accuracy_text")
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
                text = location.getLocationAgeDisplay(),
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.testTag("user_age_text")
              )
            }
          }

          // Location Source & Timestamp
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
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.testTag("user_source_text")
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
                text = if (location.timeMillis > 0) {
                  timeFormat.format(Date(location.timeMillis))
                } else {
                  "BELUM TERSEDIA"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.testTag("user_time_text")
              )
            }
          }

          // Runtime Env & Verification Status Row
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
                )
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
                }
              )
            }
          }
        }

        locationStatus == LocationStatus.LOCATION_AVAILABLE && !validationResult.isValid -> {
          // Invalid coordinate state (Section 14)
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("invalid_location_warning")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusBlocked,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "INVALID LOCATION DATA",
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace,
                  color = StatusBlocked
                )
              )
            }
            Text(
              text = validationResult.errorMessage ?: "Koordinat berada di luar batas rentang geografis valid.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        locationStatus == LocationStatus.LOCATION_LOADING -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Mengakses sinyal GPS perangkat nyata...",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        locationStatus == LocationStatus.LOCATION_PERMISSION_DENIED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "LOCATION PERMISSION DENIED",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = StatusBlocked
              )
            )
            Text(
              text = locationErrorMessage ?: "Pengguna menolak izin akses lokasi. Posisi pengguna tidak dapat ditampilkan.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
              onClick = onRequestPermission,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("MINTA IZIN LOKASI LAGI")
            }
          }
        }

        locationStatus == LocationStatus.LOCATION_PROVIDER_DISABLED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "LOCATION PROVIDER DISABLED",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = StatusBlocked
              )
            )
            Text(
              text = "Sensor GPS nonaktif pada perangkat. Aktifkan GPS untuk menampilkan posisi saya di peta.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        else -> {
          // LOCATION NOT AVAILABLE (Section 8 & 11)
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("location_not_available_box")
          ) {
            Text(
              text = "LOCATION NOT AVAILABLE",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = StatusNotStarted
              )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "LATITUDE", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                Text(
                  text = "BELUM TERSEDIA",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.testTag("user_latitude_text")
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "LONGITUDE", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                Text(
                  text = "BELUM TERSEDIA",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.testTag("user_longitude_text")
                )
              }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "ACCURACY", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                Text(
                  text = "BELUM TERSEDIA",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.testTag("user_accuracy_text")
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "LOCATION TIME", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
                Text(
                  text = "BELUM TERSEDIA",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.testTag("user_time_text")
                )
              }
            }
            if (locationStatus == LocationStatus.LOCATION_PERMISSION_REQUIRED) {
              Spacer(modifier = Modifier.height(6.dp))
              Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("MINTA IZIN LOKASI")
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Dialog rincian hotspot terverifikasi NASA FIRMS saat marker titik api disentuh.
 * Dilengkapi aksi: Buka di Google Maps, Bagikan Titik Panas, dan Salin Koordinat.
 */
@Composable
fun FireMarkerDetailDialog(
  record: FireDataRecord,
  userLocation: DeviceLocation? = null,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val acqTimeStr = if (record.acqTime.isNotBlank()) "${record.acqTime} UTC" else "Tidak tersedia"
  val ageStr = FireDataAgeCalculator.formatAgeDetail(record.acquisitionTimestampMillis)
  val coordStr = String.format(Locale.US, "%.6f°, %.6f°", record.latitude, record.longitude)

  val distanceKm = remember(record, userLocation) {
    if (userLocation != null && CoordinateValidator.isValid(userLocation.latitude, userLocation.longitude)) {
      com.example.core.share.FireHotspotShareHelper.calculateDistanceKm(
        userLocation.latitude,
        userLocation.longitude,
        record.latitude,
        record.longitude
      )
    } else null
  }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(Color(0xFFD32F2F).copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.LocalFireDepartment,
          contentDescription = null,
          tint = Color(0xFFD32F2F),
          modifier = Modifier.size(28.dp)
        )
      }
    },
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "DETAIL TITIK PANAS",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          modifier = Modifier.testTag("fire_marker_detail_title")
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(StatusVerified.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Text(
            text = "DATA RESMI NASA FIRMS",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = StatusVerified
            )
          )
        }
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        // Koordinat dengan tombol salin
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Koordinat",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = coordStr,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
              fontFamily = FontFamily.Monospace
            )
          }
          IconButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
              val clip = android.content.ClipData.newPlainText("Koordinat Hotspot", coordStr)
              clipboard?.setPrimaryClip(clip)
              android.widget.Toast.makeText(context, "Koordinat disalin: $coordStr", android.widget.Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Salin Koordinat",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        DetailRow("Satelit", record.satellite.ifBlank { "Tidak tersedia" })
        DetailRow("Instrumen", record.instrument.ifBlank { "Tidak tersedia" })
        DetailRow("Tanggal Akuisisi", record.acqDate.ifBlank { "Tidak tersedia" })
        DetailRow("Waktu Akuisisi", acqTimeStr)
        DetailRow("Usia Data Satelit", ageStr)
        DetailRow("Tingkat Keyakinan", record.confidence ?: "Tidak tersedia")
        DetailRow("Daya Radiasi (FRP)", if (record.frp != null) "${record.frp} MW" else "Tidak tersedia")
        if (record.scan != null && record.track != null) {
          DetailRow("Resolusi Pixel", "${record.scan} × ${record.track} km")
        }
        DetailRow("Sumber", "NASA FIRMS (NRT)")
        if (distanceKm != null) {
          DetailRow("Jarak Dari Posisi Anda", String.format(Locale.US, "%.1f km", distanceKm))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Tombol Buka di Google Maps
        Button(
          onClick = {
            com.example.core.share.FireHotspotShareHelper.openGoogleMaps(context, record.latitude, record.longitude)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("open_in_google_maps_button"),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("🗺️ Buka di Google Maps", fontWeight = FontWeight.Bold)
        }

        // Tombol Bagikan Titik Panas
        Button(
          onClick = {
            com.example.core.share.FireHotspotShareHelper.shareHotspot(context, record, distanceKm)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("share_fire_hotspot_button"),
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE64A19)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("📤 Bagikan Titik Panas", fontWeight = FontWeight.Bold)
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
        Text("Tutup", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  )
}

@Composable
private fun DetailRow(label: String, value: String) {
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
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}
