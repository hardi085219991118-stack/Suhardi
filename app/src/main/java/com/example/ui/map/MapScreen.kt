package com.example.ui.map

import android.content.Context
import android.view.View
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.core.map.HotspotNavigationHelper
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
/**
 * Konfigurasi layer, batas zoom, dan latar belakang tile untuk mencegah tampilan citra satelit putih saat zoom.
 */
private fun configureMapViewLayer(mapView: MapView, layer: BaseMapLayer) {
  val expectedSource = if (layer == BaseMapLayer.SATELLITE_USGS) {
    // USGS The National Map tidak memiliki coverage citra untuk Indonesia/Kalimantan (404 Not Found).
    // Agar tidak menampilkan layar putih/kosong (Bug 1), gunakan Esri World Imagery sebagai visual fallback.
    MapTileProviderFactory.ESRI_WORLD_IMAGERY
  } else {
    MapTileProviderFactory.getTileSource(layer)
  }
  if (mapView.tileProvider.tileSource.name() != expectedSource.name()) {
    mapView.setTileSource(expectedSource)
  }
  val maxZ = when (layer) {
    BaseMapLayer.SATELLITE_ESRI -> 18.0
    BaseMapLayer.SATELLITE_USGS -> 16.0
    BaseMapLayer.OPEN_STREET_MAP -> 19.0
  }
  mapView.maxZoomLevel = maxZ
  mapView.minZoomLevel = 3.0
  if (mapView.zoomLevelDouble > maxZ) {
    mapView.controller.setZoom(maxZ)
  }
  val isSatellite = layer != BaseMapLayer.OPEN_STREET_MAP
  val bgCol = if (isSatellite) android.graphics.Color.rgb(18, 26, 20) else android.graphics.Color.rgb(238, 238, 238)
  val lineCol = if (isSatellite) android.graphics.Color.rgb(26, 36, 28) else android.graphics.Color.rgb(218, 218, 218)
  try {
    mapView.overlayManager.tilesOverlay.loadingBackgroundColor = bgCol
    mapView.overlayManager.tilesOverlay.loadingLineColor = lineCol
    mapView.setBackgroundColor(bgCol)
  } catch (_: Throwable) {}
}

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
  var isFallbackActive by remember { mutableStateOf(false) }
  var fallbackReason by remember { mutableStateOf<String?>(null) }
  var retryAttempt by remember { mutableStateOf(0) }
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
      val osmdroidBasePath = java.io.File(context.cacheDir, "osmdroid")
      val osmdroidTileCache = java.io.File(osmdroidBasePath, "tiles")
      if (!osmdroidTileCache.exists()) osmdroidTileCache.mkdirs()
      Configuration.getInstance().osmdroidBasePath = osmdroidBasePath
      Configuration.getInstance().osmdroidTileCache = osmdroidTileCache
      Configuration.getInstance().userAgentValue = "HardiMantangaiFireNow/1.0 (Android; ZeroDummy)"
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

  // Validasi tile nyata dengan Retry Backoff & Fallback otomatis (Section 5, 10, 11, 12)
  LaunchedEffect(selectedBaseMapLayer, tileRetryKey) {
    mapStatus = MapStatus.MAP_LOADING
    onMapStatusChanged(MapStatus.MAP_LOADING, selectedBaseMapLayer)
    mapErrorMessage = null

    val centerLat = deviceLocation?.latitude ?: fireRecords.firstOrNull()?.latitude ?: -2.15
    val centerLon = deviceLocation?.longitude ?: fireRecords.firstOrNull()?.longitude ?: 114.65
    val zoom = if (deviceLocation != null) 14 else if (fireRecords.isNotEmpty()) 10 else 10

    val outcome = com.example.core.map.MapTileValidator.validateWithRetryAndFallback(
      desiredLayer = selectedBaseMapLayer,
      latitude = centerLat,
      longitude = centerLon,
      zoom = zoom,
      onRetryAttempt = { attempt, _ ->
        retryAttempt = attempt
      }
    )

    if (outcome.isFallback) {
      isFallbackActive = true
      fallbackReason = outcome.checkResult.errorMessage ?: outcome.checkResult.failureReason?.userFriendlyMessage
      mapStatus = MapStatus.MAP_READY
      mapViewRef?.let { configureMapViewLayer(it, BaseMapLayer.OPEN_STREET_MAP) }
      onMapStatusChanged(MapStatus.MAP_READY, BaseMapLayer.OPEN_STREET_MAP)
    } else if (outcome.checkResult.isValid) {
      isFallbackActive = false
      fallbackReason = null
      mapStatus = MapStatus.MAP_READY
      mapViewRef?.let { configureMapViewLayer(it, selectedBaseMapLayer) }
      onMapStatusChanged(MapStatus.MAP_READY, selectedBaseMapLayer)
    } else {
      isFallbackActive = false
      mapStatus = MapStatus.MAP_ERROR
      mapErrorMessage = outcome.checkResult.errorMessage ?: outcome.checkResult.failureReason?.userFriendlyMessage
      onMapStatusChanged(MapStatus.MAP_ERROR, selectedBaseMapLayer)
    }
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
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(end = 4.dp),
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = "Peta Titik Panas",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("map_screen_title")
            )
            Text(
              text = indicatorText,
              style = MaterialTheme.typography.labelSmall.copy(
                color = if (isFiltered) Color(0xFFFF5722) else MaterialTheme.colorScheme.primary,
                fontWeight = if (isFiltered) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.testTag("fit_all_hotspots_button")
          ) {
            Icon(
              imageVector = Icons.Default.Whatshot,
              contentDescription = "Fokus Semua Titik Panas",
              tint = Color(0xFFFF5722)
            )
          }
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
                contentDescription = "Pilih Layer Peta",
                tint = MaterialTheme.colorScheme.primary
              )
            }
            DropdownMenu(
              expanded = showLayerMenu,
              onDismissRequest = { showLayerMenu = false }
            ) {
              BaseMapLayer.values().forEach { layer ->
                val isSelected = selectedBaseMapLayer == layer
                DropdownMenuItem(
                  text = {
                    Column {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                          text = layer.displayName,
                          style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                          )
                        )
                        if (isSelected) {
                          Spacer(modifier = Modifier.width(6.dp))
                          Text(
                            text = "[Aktif]",
                            style = MaterialTheme.typography.labelSmall.copy(
                              fontWeight = FontWeight.Bold,
                              color = MaterialTheme.colorScheme.primary
                            )
                          )
                        }
                      }
                      Text(
                        text = if (layer == BaseMapLayer.SATELLITE_USGS) {
                          "${layer.providerDescription} (Wilayah AS; otomatis fallback ke Esri di Kalimantan)"
                        } else {
                          layer.providerDescription
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  },
                  onClick = {
                    selectedBaseMapLayer = layer
                    mapViewRef?.let { configureMapViewLayer(it, layer) }
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
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        )
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // 1. Tampilan Native MapView (Selalu aktif, tidak ditimpa layar kosong)
      AndroidView(
          factory = { ctx ->
            try {
              MapView(ctx).apply {
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                configureMapViewLayer(this, selectedBaseMapLayer)
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
              // Update layer configuration and zoom clamping
              val activeEffectiveLayer = if (isFallbackActive) BaseMapLayer.OPEN_STREET_MAP else selectedBaseMapLayer
              configureMapViewLayer(mv, activeEffectiveLayer)

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

                  val flameIcon = com.example.core.map.FireMarkerIconHelper.getFlameIcon(mv.context)
                  validFires.forEach { fire ->
                    val fireMarker = Marker(mv).apply {
                      position = GeoPoint(fire.latitude, fire.longitude)
                      icon = flameIcon
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

              // Pasang overlay ke MapView secara berurutan sesuai Section 7:
              // BASEMAP -> HOTSPOT NASA FIRMS -> GPS USER
              mv.overlays.clear()
              mv.overlays.addAll(cachedFireMarkers)
              cachedUserMarker?.let { mv.overlays.add(it) }

              mv.invalidate()
            }
          },
          modifier = Modifier
            .fillMaxSize()
            .testTag("osm_map_view")
        )

      // 2. Status Bar Atas (Status Peta & Status Lokasi)
      MapStatusBar(
        mapStatus = mapStatus,
        activeLayer = if (isFallbackActive) BaseMapLayer.OPEN_STREET_MAP else selectedBaseMapLayer,
        isFallbackActive = isFallbackActive,
        fallbackReason = fallbackReason,
        onRetry = {
          isFallbackActive = false
          fallbackReason = null
          selectedBaseMapLayer = BaseMapLayer.SATELLITE_ESRI
          mapViewRef?.let { configureMapViewLayer(it, BaseMapLayer.SATELLITE_ESRI) }
          tileRetryKey++
        },
        locationStatus = locationStatus,
        isValidCoordinate = validationResult.isValid,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(12.dp)
      )

      // 2b. Banner Non-Coverage USGS (Jujur dan menyediakan tindakan fallback)
      if (selectedBaseMapLayer == BaseMapLayer.SATELLITE_USGS) {
        Card(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 68.dp, start = 12.dp, end = 12.dp)
            .fillMaxWidth()
            .testTag("usgs_coverage_banner"),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              tint = Color(0xFFFFB74D),
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "USGS tidak tersedia di lokasi ini.",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
              )
              Text(
                text = "Menampilkan Citra Satelit Esri sebagai alternatif.",
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFB0BEC5), fontSize = 10.sp)
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Button(
              onClick = {
                selectedBaseMapLayer = BaseMapLayer.SATELLITE_ESRI
                mapViewRef?.let { configureMapViewLayer(it, BaseMapLayer.SATELLITE_ESRI) }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color(0xFF0D1520)),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              shape = RoundedCornerShape(6.dp),
              modifier = Modifier.testTag("switch_to_esri_button")
            ) {
              Text("Gunakan Esri", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // 2a. Floating Legenda Peta
      Card(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(start = 12.dp, top = if (selectedBaseMapLayer == BaseMapLayer.SATELLITE_USGS) 130.dp else 64.dp)
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

      // 3. Kontrol Sisi Kanan (Kanan Atas: Zoom In/Out, Kanan Tengah/Bawah: GPS / Posisi Saya)
      Column(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 64.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Kontrol Zoom (+ / -) di sisi kanan atas
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
          tonalElevation = 4.dp,
          shadowElevation = 4.dp,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            IconButton(
              onClick = {
                val mv = mapViewRef
                if (mv != null && mv.zoomLevelDouble < mv.maxZoomLevel) {
                  mv.controller.zoomIn()
                }
              },
              modifier = Modifier
                .size(44.dp)
                .testTag("zoom_in_button")
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Perbesar Peta (+)",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
              )
            }

            HorizontalDivider(
              modifier = Modifier.width(28.dp),
              thickness = 1.dp,
              color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            IconButton(
              onClick = {
                val mv = mapViewRef
                if (mv != null && mv.zoomLevelDouble > mv.minZoomLevel) {
                  mv.controller.zoomOut()
                }
              },
              modifier = Modifier
                .size(44.dp)
                .testTag("zoom_out_button")
            ) {
              Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Perkecil Peta (−)",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        // Tombol GPS / Lokasi (berjarak aman di bawah tombol zoom)
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
          tonalElevation = 4.dp,
          shadowElevation = 4.dp,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
          IconButton(
            onClick = {
              if (deviceLocation != null && validationResult.isValid) {
                mapViewRef?.let { mv ->
                  val pt = GeoPoint(deviceLocation.latitude, deviceLocation.longitude)
                  mv.controller.animateTo(pt)
                  mv.controller.setZoom(16.5)
                }
              } else {
                onRequestPermission()
                onRefreshLocation()
                val msg = if (locationStatus == LocationStatus.LOCATION_PROVIDER_DISABLED) {
                  "Sensor GPS nonaktif. Silakan aktifkan GPS perangkat."
                } else {
                  "Mencari sinyal GPS..."
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier
              .size(44.dp)
              .testTag("recenter_button")
          ) {
            Icon(
              imageVector = Icons.Default.MyLocation,
              contentDescription = "Pusatkan ke Posisi Saya (GPS)",
              tint = if (deviceLocation != null && validationResult.isValid) Color(0xFF00C853) else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
          }
        }
      }

      // 4. Bagian Bawah Area Peta: [ Lokasi Saya ]   [ Layer ]   [ Buka Peta ]
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // 1. [ Lokasi Saya ]
            FilledTonalButton(
              onClick = {
                if (deviceLocation != null && validationResult.isValid) {
                  mapViewRef?.let { mv ->
                    mv.controller.setZoom(16.0)
                    mv.controller.animateTo(GeoPoint(deviceLocation.latitude, deviceLocation.longitude))
                  }
                } else {
                  onRequestPermission()
                  onRefreshLocation()
                  val msg = if (locationStatus == LocationStatus.LOCATION_PROVIDER_DISABLED) {
                    "Sensor GPS nonaktif. Silakan aktifkan GPS perangkat."
                  } else {
                    "Lokasi GPS belum tersedia. Memperbarui sinyal..."
                  }
                  android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("my_location_map_button"),
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Lokasi Saya",
                tint = Color(0xFF00C853),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Lokasi Saya",
                style = MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            // 2. [ Titik Panas ]
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
                    mv.controller.setZoom(12.0)
                    mv.controller.animateTo(GeoPoint(centerLat, centerLon))
                  }
                }
              },
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("fit_all_hotspots_dock"),
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(0xFFFF5722).copy(alpha = 0.15f),
                contentColor = Color(0xFFFF5722)
              )
            ) {
              Icon(
                imageVector = Icons.Default.Whatshot,
                contentDescription = "Fokus Titik Panas",
                tint = Color(0xFFFF5722),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Titik Panas",
                style = MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            // 3. [ Buka Peta ]
            FilledTonalButton(
              onClick = {
                val center = mapViewRef?.mapCenter
                val lat = center?.latitude ?: deviceLocation?.latitude ?: fireRecords.firstOrNull()?.latitude ?: -2.15
                val lon = center?.longitude ?: deviceLocation?.longitude ?: fireRecords.firstOrNull()?.longitude ?: 114.65
                val mapIntent = HotspotNavigationHelper.createGoogleMapsNavigationIntent(lat, lon, "Hardi Mantangai")
                  ?: HotspotNavigationHelper.createGeoIntent(lat, lon, "Hardi Mantangai")
                try {
                  context.startActivity(mapIntent)
                } catch (_: Exception) {
                  android.widget.Toast.makeText(context, "Aplikasi peta tidak ditemukan.", android.widget.Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("open_map_action_button"),
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(0xFF1976D2).copy(alpha = 0.15f),
                contentColor = Color(0xFF1976D2)
              )
            ) {
              Icon(
                imageVector = Icons.Default.Map,
                contentDescription = "Buka Peta",
                tint = Color(0xFF1976D2),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Buka Peta",
                style = MaterialTheme.typography.labelMedium.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        // Legal Attribution (Section 6 & Esri Policy)
        val attributionText = when {
          selectedBaseMapLayer == BaseMapLayer.OPEN_STREET_MAP || isFallbackActive -> "© OpenStreetMap contributors"
          selectedBaseMapLayer == BaseMapLayer.SATELLITE_ESRI -> "Tiles © Esri — Source: Esri, Maxar, Earthstar Geographics"
          else -> "USGS The National Map"
        }
        Text(
          text = attributionText,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)),
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
        )
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
  isFallbackActive: Boolean = false,
  fallbackReason: String? = null,
  onRetry: () -> Unit = {},
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
      if (isFallbackActive) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
      else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // MAP STATUS
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f, fill = false)
        ) {
          val iconColor = when {
            isFallbackActive -> MaterialTheme.colorScheme.error
            mapStatus == MapStatus.MAP_READY -> StatusVerified
            mapStatus == MapStatus.MAP_ERROR -> StatusBlocked
            else -> MaterialTheme.colorScheme.primary
          }
          Icon(
            imageVector = when {
              isFallbackActive -> Icons.Default.Warning
              mapStatus == MapStatus.MAP_READY -> Icons.Default.Map
              mapStatus == MapStatus.MAP_ERROR -> Icons.Default.Warning
              else -> Icons.Default.Map
            },
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
          )
          val mapStatusLabel = when {
            isFallbackActive -> "⚠️ SATELIT GAGAL — MENGGUNAKAN PETA STANDAR"
            activeLayer == BaseMapLayer.SATELLITE_USGS -> "🛰️ USGS (Non-Coverage) — Esri Aktif"
            mapStatus == MapStatus.MAP_READY -> when (activeLayer) {
              BaseMapLayer.SATELLITE_ESRI -> "🛰️ Citra Satelit | Status: AKTIF"
              BaseMapLayer.OPEN_STREET_MAP -> "🗺️ Peta Standar | Status: AKTIF"
              BaseMapLayer.SATELLITE_USGS -> "🛰️ USGS (Non-Coverage) — Esri Aktif"
            }
            mapStatus == MapStatus.MAP_ERROR -> if (activeLayer == BaseMapLayer.OPEN_STREET_MAP) "Peta Standar Gagal" else "Peta Satelit Gagal"
            mapStatus == MapStatus.MAP_LOADING -> if (activeLayer == BaseMapLayer.OPEN_STREET_MAP) "Memuat Peta Standar..." else "Memuat Citra Satelit..."
            else -> "Status Peta"
          }
          Text(
            text = mapStatusLabel,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            ),
            color = iconColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            LocationStatus.LOCATION_PERMISSION_DENIED, LocationStatus.LOCATION_PERMISSION_REQUIRED -> Icons.Default.LocationOff
            else -> Icons.Default.LocationOff
          }
          val locColor = when (locationStatus) {
            LocationStatus.LOCATION_AVAILABLE -> if (isValidCoordinate) StatusVerified else StatusBlocked
            LocationStatus.LOCATION_LOADING -> MaterialTheme.colorScheme.primary
            LocationStatus.LOCATION_ERROR, LocationStatus.LOCATION_PERMISSION_DENIED, LocationStatus.LOCATION_PROVIDER_DISABLED, LocationStatus.LOCATION_PERMISSION_REQUIRED -> StatusBlocked
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
            locationStatus == LocationStatus.LOCATION_LOADING -> "MENCARI SINYAL GPS..."
            locationStatus == LocationStatus.LOCATION_PROVIDER_DISABLED -> "GPS NONAKTIF"
            locationStatus == LocationStatus.LOCATION_PERMISSION_DENIED || locationStatus == LocationStatus.LOCATION_PERMISSION_REQUIRED -> "IZIN LOKASI BELUM DIBERIKAN"
            else -> "STATUS LOKASI"
          }
          Text(
            text = locText,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp
            ),
            color = locColor,
            modifier = Modifier.testTag("map_location_status_badge")
          )
        }
      }

      // Baris Fallback Diagnostik & Tombol Coba Lagi
      if (isFallbackActive) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = fallbackReason ?: "Citra satelit tidak dapat dihubungi. Mengalihkan ke OpenStreetMap.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = Modifier
              .height(28.dp)
              .testTag("retry_tile_button")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Coba Lagi", style = MaterialTheme.typography.labelSmall)
          }
        }
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
  val context = LocalContext.current
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
              text = "Izin lokasi belum diberikan",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = StatusBlocked
              )
            )
            Text(
              text = locationErrorMessage ?: "Pengguna menolak izin akses lokasi. Posisi pengguna tidak dapat ditampilkan.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
              onClick = onRequestPermission,
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
            ) {
              Text("Minta Izin Lokasi", fontWeight = FontWeight.Bold)
            }
          }
        }

        locationStatus == LocationStatus.LOCATION_PROVIDER_DISABLED -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "GPS nonaktif",
              style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = StatusBlocked
              )
            )
            Text(
              text = "Sensor GPS nonaktif pada perangkat. Aktifkan GPS untuk menampilkan posisi Anda di peta.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
              onClick = {
                try {
                  val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                  context.startActivity(intent)
                } catch (_: Exception) {}
              },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
            ) {
              Text("Buka Pengaturan Lokasi", fontWeight = FontWeight.Bold)
            }
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
  com.example.ui.dashboard.HotspotDetailDialog(
    record = record,
    userLocation = userLocation,
    onDismiss = onDismiss
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
