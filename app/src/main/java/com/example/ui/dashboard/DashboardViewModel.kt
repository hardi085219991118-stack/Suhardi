package com.example.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.fire.FireDataAgeCalculator
import com.example.core.fire.FireDataCredentialState
import com.example.core.fire.FireDataRecord
import com.example.core.fire.FireDataRepository
import com.example.core.fire.FireDataResponse
import com.example.core.fire.FireDataSourceState
import com.example.core.fire.FreshnessLevel
import com.example.core.fire.RealFireDataRepository
import com.example.core.location.AndroidLocationTracker
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.location.LocationTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import com.example.core.fire.NasaFirmsConstants
import com.example.ui.map.MapStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DashboardViewModel(
  private val locationTracker: LocationTracker,
  private val fireRepository: FireDataRepository? = null
) : ViewModel() {

  private val _uiState = MutableStateFlow(DashboardState())
  val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

  private var lastLiveRequestTimeMillis: Long = 0L
  private var cooldownTimerJob: Job? = null

  private val utcDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
  }
  private val localTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

  private var isTileValidationInProgress = false

  init {
    combine(
      locationTracker.locationStatus,
      locationTracker.currentLocation,
      locationTracker.errorMessage
    ) { status, location, errorMsg ->
      val current = _uiState.value
      val filtered = com.example.core.fire.HotspotFilterHelper.filterRecords(
        records = current.fireRecords,
        criteria = current.filterCriteria,
        deviceLocation = location
      )
      _uiState.value = current.copy(
        locationStatus = status,
        deviceLocation = location,
        locationErrorMessage = errorMsg,
        filteredFireRecords = filtered
      )
    }.launchIn(viewModelScope)

    fireRepository?.let { repo ->
      combine(
        repo.fireDataResponse,
        repo.credentialState,
        repo.dataSourceState
      ) { response, credState, sourceState ->
        mapFireResponseToUiState(response, credState, sourceState)
      }.launchIn(viewModelScope)

      // Auto Initial NASA FIRMS Fetch (Item 4)
      if (repo.credentialState.value == FireDataCredentialState.CONFIGURED) {
        refreshFireData(force = false)
      }
    }

    validateMapTiles()
  }

  fun validateMapTiles(layer: com.example.core.map.BaseMapLayer = _uiState.value.activeBaseMapLayer) {
    if (isTileValidationInProgress) return
    isTileValidationInProgress = true
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(mapStatus = MapStatus.MAP_LOADING, activeBaseMapLayer = layer)
      val result = com.example.core.map.MapTileValidator.validateTileSource(layer)
      result.fold(
        onSuccess = {
          _uiState.value = _uiState.value.copy(mapStatus = MapStatus.MAP_READY, activeBaseMapLayer = layer)
        },
        onFailure = {
          _uiState.value = _uiState.value.copy(mapStatus = MapStatus.MAP_ERROR, activeBaseMapLayer = layer)
        }
      )
      isTileValidationInProgress = false
    }
  }

  fun setMapStatus(status: MapStatus, layer: com.example.core.map.BaseMapLayer = _uiState.value.activeBaseMapLayer) {
    _uiState.value = _uiState.value.copy(mapStatus = status, activeBaseMapLayer = layer)
  }

  fun updateFilterCriteria(criteria: HotspotFilterCriteria) {
    val current = _uiState.value
    val validatedCriteria = if (current.deviceLocation == null || current.locationStatus != LocationStatus.LOCATION_AVAILABLE) {
      criteria.copy(maxDistanceKm = null)
    } else {
      criteria
    }
    val filtered = com.example.core.fire.HotspotFilterHelper.filterRecords(
      records = current.fireRecords,
      criteria = validatedCriteria,
      deviceLocation = current.deviceLocation
    )
    _uiState.value = current.copy(
      filterCriteria = validatedCriteria,
      filteredFireRecords = filtered
    )
  }

  fun resetFilterCriteria() {
    updateFilterCriteria(HotspotFilterCriteria())
  }

  private fun resolveActiveSatelliteName(response: FireDataResponse, firstRecord: FireDataRecord?): String {
    val satRecord = firstRecord?.satellite?.trim()
    if (!satRecord.isNullOrBlank()) {
      return when {
        satRecord.contains("21") -> "NOAA-21"
        satRecord.contains("20") -> "NOAA-20"
        satRecord.contains("SNPP", ignoreCase = true) || satRecord.contains("Suomi", ignoreCase = true) -> "Suomi-NPP"
        satRecord.contains("Terra", ignoreCase = true) -> "Terra"
        satRecord.contains("Aqua", ignoreCase = true) -> "Aqua"
        satRecord.contains("MODIS", ignoreCase = true) -> "MODIS"
        else -> satRecord
      }
    }
    val sensor = response.sourceSensor.trim()
    return when {
      sensor.contains("NOAA21", ignoreCase = true) || sensor.contains("NOAA-21", ignoreCase = true) -> "NOAA-21"
      sensor.contains("NOAA20", ignoreCase = true) || sensor.contains("NOAA-20", ignoreCase = true) -> "NOAA-20"
      sensor.contains("SNPP", ignoreCase = true) -> "Suomi-NPP"
      sensor.contains("MODIS", ignoreCase = true) -> "MODIS"
      else -> "Belum tersedia"
    }
  }

  private fun mapFireResponseToUiState(
    response: FireDataResponse,
    credState: FireDataCredentialState,
    sourceState: FireDataSourceState
  ) {
    val current = _uiState.value
    val firstRecord = response.records.firstOrNull()

    val formattedAcqTime = if (firstRecord?.acquisitionTimestampMillis != null) {
      utcDateFormat.format(Date(firstRecord.acquisitionTimestampMillis))
    } else if (!firstRecord?.acqDate.isNullOrBlank()) {
      "${firstRecord?.acqDate} ${firstRecord?.acqTime} UTC"
    } else {
      "Belum tersedia"
    }

    val formattedFetchTime = if (response.fetchTimeMillis > 0) {
      localTimeFormat.format(Date(response.fetchTimeMillis))
    } else {
      "Belum pernah"
    }

    val activeSatName = resolveActiveSatelliteName(response, firstRecord)

    val newState = when (sourceState) {
      FireDataSourceState.NOT_VERIFIED -> current.copy(
        fireDataState = DataState.NOT_VERIFIED,
        fireDataSourceState = FireDataSourceState.NOT_VERIFIED,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = if (credState == FireDataCredentialState.CONFIGURED) "READY FOR LIVE REQUEST" else "FIRE DATA SOURCE NOT VERIFIED",
        fireNote = if (credState == FireDataCredentialState.CONFIGURED) {
          "MAP_KEY terkonfigurasi (TEST_CREDENTIAL_ONLY). Tekan 'Perbarui Data Satelit' untuk melakukan live request ke NASA FIRMS."
        } else {
          "Sumber data titik api belum dihubungkan. Menampilkan '--' karena belum ada data (Bukan 0 titik api)."
        },
        fireRecords = emptyList(),
        satelliteState = DataState.NOT_VERIFIED,
        satelliteDisplay = "Belum tersedia",
        satelliteNote = if (credState == FireDataCredentialState.CONFIGURED) {
          "NASA FIRMS: Siap mengirim live HTTPS request."
        } else {
          "DATA SOURCE NOT VERIFIED (MAP_KEY belum dikonfigurasi)."
        },
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "Belum tersedia",
        lastUpdateNote = "Waktu akuisisi satelit belum tersedia. Waktu perangkat tidak disamakan dengan waktu satelit (Aturan 7).",
        isLoadingSatellite = false
      )

      FireDataSourceState.DATA_SOURCE_AVAILABLE -> current.copy(
        fireDataState = DataState.AVAILABLE,
        fireDataSourceState = FireDataSourceState.DATA_SOURCE_AVAILABLE,
        credentialState = credState,
        validFireRecordCount = response.validRecordCount,
        rawRecordCount = response.rawRecordCount,
        responseSha256Hash = response.responseSha256Hash,
        fireCountDisplay = response.validRecordCount.toString(),
        fireStatusText = "DATA SOURCE AVAILABLE",
        fireNote = "Ditemukan ${response.validRecordCount} deteksi titik api valid dari satelit $activeSatName.",
        fireRecords = response.records,
        satelliteState = DataState.AVAILABLE,
        satelliteDisplay = activeSatName,
        satelliteNote = "Sensor: ${response.sourceSensor} | Satelit: $activeSatName",
        freshnessLevel = response.freshness,
        isCachedFireData = response.isCached,
        lastUpdateState = DataState.AVAILABLE,
        lastUpdateDisplay = formattedAcqTime,
        lastUpdateNote = "Waktu akuisisi satelit: $formattedAcqTime. Waktu fetch perangkat: $formattedFetchTime (TIDAK DISAMAKAN).",
        lastFetchDisplay = formattedFetchTime,
        isLoadingSatellite = false,
        refreshSatelliteNote = "Data berhasil diperbarui"
      )

      FireDataSourceState.NO_DETECTIONS_IN_QUERY -> current.copy(
        fireDataState = DataState.AVAILABLE,
        fireDataSourceState = FireDataSourceState.NO_DETECTIONS_IN_QUERY,
        credentialState = credState,
        validFireRecordCount = 0,
        rawRecordCount = response.rawRecordCount,
        responseSha256Hash = response.responseSha256Hash,
        fireCountDisplay = "0",
        fireStatusText = "NO DETECTIONS IN QUERY",
        fireNote = "Tidak ada titik api terdeteksi dalam area query pada overpass satelit terakhir ($activeSatName).",
        fireRecords = emptyList(),
        satelliteState = DataState.AVAILABLE,
        satelliteDisplay = activeSatName,
        satelliteNote = "Sensor: ${response.sourceSensor} | Status: 0 Deteksi dalam query",
        freshnessLevel = FreshnessLevel.FRESHNESS_UNKNOWN,
        isCachedFireData = response.isCached,
        lastUpdateState = DataState.AVAILABLE,
        lastUpdateDisplay = "0 Deteksi (Tidak Ada Titik Api)",
        lastUpdateNote = "Query valid berhasil dieksekusi. Tidak ada hotspot teramati. Waktu fetch: $formattedFetchTime.",
        lastFetchDisplay = formattedFetchTime,
        isLoadingSatellite = false,
        refreshSatelliteNote = "Data berhasil diperbarui"
      )

      FireDataSourceState.API_CREDENTIAL_REQUIRED -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.API_CREDENTIAL_REQUIRED,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = if (credState == FireDataCredentialState.INVALID) "INVALID CREDENTIAL" else "API CREDENTIAL REQUIRED",
        fireNote = if (credState == FireDataCredentialState.INVALID) {
          "MAP_KEY NASA FIRMS ditolak atau tidak valid. Daftarkan MAP_KEY resmi di https://firms.modaps.eosdis.nasa.gov."
        } else {
          "MAP_KEY NASA FIRMS belum dikonfigurasi. Daftarkan MAP_KEY resmi di https://firms.modaps.eosdis.nasa.gov."
        },
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "Belum tersedia",
        satelliteNote = "MAP_KEY FIRMS diperlukan untuk mengakses web service NASA.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "Belum tersedia",
        isLoadingSatellite = false
      )

      FireDataSourceState.NETWORK_ERROR -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.NETWORK_ERROR,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "NETWORK ERROR",
        fireNote = "Gagal menghubungi server NASA FIRMS. Periksa koneksi internet perangkat.",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "Belum tersedia",
        satelliteNote = "Koneksi internet terputus atau DNS gagal.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "Belum tersedia",
        isLoadingSatellite = false
      )

      FireDataSourceState.TIMEOUT -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.TIMEOUT,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "TIMEOUT",
        fireNote = "Waktu koneksi ke server NASA FIRMS habis (Timeout).",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "Belum tersedia",
        satelliteNote = "Koneksi ke NASA FIRMS melebihi batas waktu.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "Belum tersedia",
        isLoadingSatellite = false
      )

      FireDataSourceState.RATE_LIMIT_EXCEEDED -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.RATE_LIMIT_EXCEEDED,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "RATE LIMIT EXCEEDED (HTTP 429)",
        fireNote = "Permintaan melebihi kuota NASA FIRMS. Cooldown dan backoff sedang aktif.",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "Belum tersedia",
        satelliteNote = "HTTP 429 Too Many Requests dari NASA FIRMS.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "Belum tersedia",
        isLoadingSatellite = false
      )

      FireDataSourceState.DATA_SOURCE_UNAVAILABLE -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "DATA SOURCE UNAVAILABLE",
        fireNote = response.error?.message ?: "Server NASA FIRMS tidak dapat dihubungi.",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "Belum tersedia",
        satelliteNote = "Server NASA FIRMS mengalami gangguan.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "Belum tersedia",
        isLoadingSatellite = false
      )

      FireDataSourceState.INVALID_DATA_RESPONSE -> current.copy(
        fireDataState = DataState.ERROR,
        fireDataSourceState = FireDataSourceState.INVALID_DATA_RESPONSE,
        credentialState = credState,
        validFireRecordCount = null,
        fireCountDisplay = "--",
        fireStatusText = "INVALID NASA FIRMS RESPONSE",
        fireNote = response.error?.message ?: "Payload CSV dari NASA FIRMS tidak valid.",
        fireRecords = emptyList(),
        satelliteState = DataState.ERROR,
        satelliteDisplay = "Belum tersedia",
        satelliteNote = "Format respons tidak dikenali.",
        lastUpdateState = DataState.NOT_AVAILABLE,
        lastUpdateDisplay = "Belum tersedia",
        isLoadingSatellite = false
      )

      FireDataSourceState.CONNECTING -> current.copy(
        fireDataSourceState = FireDataSourceState.CONNECTING,
        isLoadingSatellite = true,
        fireStatusText = "MENGHUBUNGI NASA FIRMS...",
        satelliteDisplay = "Belum tersedia",
        refreshSatelliteNote = "Menghubungkan ke NASA FIRMS..."
      )

      FireDataSourceState.CACHED -> current.copy(
        fireDataState = DataState.AVAILABLE,
        fireDataSourceState = FireDataSourceState.CACHED,
        credentialState = credState,
        validFireRecordCount = response.validRecordCount,
        fireCountDisplay = response.validRecordCount.toString(),
        fireStatusText = "CACHED DATA",
        fireNote = "Data cache tersimpan (${response.cacheAgeMillis / 1000}s lalu). BUKAN DATA LIVE.",
        fireRecords = response.records,
        satelliteState = DataState.AVAILABLE,
        satelliteDisplay = "$activeSatName (Cache)",
        satelliteNote = "Menampilkan data lokal dari cache. Usia cache: ${response.cacheAgeMillis / 1000} detik.",
        freshnessLevel = response.freshness,
        isCachedFireData = true,
        cacheAgeSeconds = response.cacheAgeMillis / 1000,
        lastUpdateState = DataState.AVAILABLE,
        lastUpdateDisplay = formattedAcqTime,
        lastUpdateNote = "Waktu akuisisi satelit: $formattedAcqTime. Data berasal dari cache lokal.",
        lastFetchDisplay = formattedFetchTime,
        isLoadingSatellite = false
      )
    }

    val auditResult = com.example.core.fire.LiveApiDiagnosticAuditor.auditPipeline(
      credState = credState,
      sourceState = sourceState,
      response = response,
      queryArea = response.requestArea
    )

    val dataAgeDisplay = if (response.records.isNotEmpty() && response.fetchTimeMillis > 0) {
      val latestAcq = response.records.mapNotNull { it.acquisitionTimestampMillis }.maxOrNull()
      if (latestAcq != null) {
        FireDataAgeCalculator.formatAgeDetail(latestAcq, response.fetchTimeMillis)
      } else {
        "BELUM TERSEDIA"
      }
    } else {
      "BELUM TERSEDIA"
    }

    val acqRangeDisplay = if (response.minAcquisitionTimeMillis != null && response.maxAcquisitionTimeMillis != null) {
      val minStr = utcDateFormat.format(Date(response.minAcquisitionTimeMillis))
      val maxStr = utcDateFormat.format(Date(response.maxAcquisitionTimeMillis))
      if (minStr == maxStr) minStr else "$minStr s/d $maxStr"
    } else if (firstRecord != null) {
      formattedAcqTime
    } else {
      "BELUM TERSEDIA"
    }

    val satDistDisplay = if (response.satelliteDistribution.isNotEmpty()) {
      response.satelliteDistribution.entries.joinToString(", ") { "${it.key}: ${it.value}" }
    } else if (firstRecord?.satellite != null) {
      firstRecord.satellite
    } else {
      "BELUM TERSEDIA"
    }

    val instDistDisplay = if (response.instrumentDistribution.isNotEmpty()) {
      response.instrumentDistribution.entries.joinToString(", ") { "${it.key}: ${it.value}" }
    } else if (firstRecord?.instrument != null) {
      firstRecord.instrument
    } else {
      "BELUM TERSEDIA"
    }

    val currentCriteria = current.filterCriteria
    val filtered = com.example.core.fire.HotspotFilterHelper.filterRecords(
      records = newState.fireRecords,
      criteria = currentCriteria,
      deviceLocation = current.deviceLocation
    )

    _uiState.value = newState.copy(
      filterCriteria = currentCriteria,
      filteredFireRecords = filtered,
      liveVerificationGate = auditResult.gate,
      diagnosticCause = auditResult.cause,
      diagnosticDetail = auditResult.detail,
      boundingBoxValidationStatus = auditResult.boundingBoxStatus,
      endpointAudited = auditResult.endpoint,
      httpStatusCode = response.httpStatusCode,
      invalidRecordCount = response.invalidRecordCount,
      queryArea = response.requestArea,
      dayRange = response.requestDayRange,
      acquisitionRangeDisplay = acqRangeDisplay,
      satelliteDistributionDisplay = satDistDisplay,
      instrumentDistributionDisplay = instDistDisplay,
      dataAgeDisplay = dataAgeDisplay,
      responseSha256Hash = response.responseSha256Hash
    )
  }

  fun requestLocation(context: Context) {
    locationTracker.requestLocation(context)
  }

  fun onPermissionResult(granted: Boolean, isPermanentlyDenied: Boolean = false, context: Context? = null) {
    if (granted) {
      if (context != null) {
        locationTracker.requestLocation(context)
      }
    } else {
      locationTracker.markPermissionDenied(isPermanentlyDenied)
    }
  }

  fun refreshLocation(context: Context) {
    locationTracker.requestLocation(context)
  }

  fun setMapStatus(status: com.example.ui.map.MapStatus) {
    _uiState.value = _uiState.value.copy(mapStatus = status)
  }

  fun refreshFireData(force: Boolean = false) {
    if (fireRepository == null) return
    val now = System.currentTimeMillis()
    val elapsed = now - lastLiveRequestTimeMillis
    if (!force && lastLiveRequestTimeMillis > 0L && elapsed < NasaFirmsConstants.MIN_REQUEST_INTERVAL_MS) {
      val remainingSec = ((NasaFirmsConstants.MIN_REQUEST_INTERVAL_MS - elapsed) / 1000L) + 1L
      _uiState.value = _uiState.value.copy(
        cooldownRemainingSeconds = remainingSec,
        isRefreshSatelliteEnabled = false,
        refreshSatelliteNote = "Data baru saja diperbarui. Tunggu sebelum memperbarui lagi."
      )
      startCooldownCountdown(remainingSec)
      return
    }

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isLoadingSatellite = true,
        refreshSatelliteNote = "Menghubungkan ke NASA FIRMS..."
      )
      val res = fireRepository.refreshFireData(force = force)
      if (res.state == FireDataSourceState.CONNECTING ||
        res.state == FireDataSourceState.DATA_SOURCE_AVAILABLE ||
        res.state == FireDataSourceState.NO_DETECTIONS_IN_QUERY ||
        res.httpStatusCode == 200
      ) {
        lastLiveRequestTimeMillis = System.currentTimeMillis()
        startCooldownCountdown(NasaFirmsConstants.MIN_REQUEST_INTERVAL_MS / 1000L)
      } else if (res.httpStatusCode == 429) {
        lastLiveRequestTimeMillis = System.currentTimeMillis()
        startCooldownCountdown(NasaFirmsConstants.RATE_LIMIT_BACKOFF_BASE_MS / 1000L)
      }
      _uiState.value = _uiState.value.copy(isLoadingSatellite = false)
    }
  }

  private fun startCooldownCountdown(totalSeconds: Long) {
    cooldownTimerJob?.cancel()
    cooldownTimerJob = viewModelScope.launch {
      var remaining = totalSeconds
      while (remaining > 0) {
        _uiState.value = _uiState.value.copy(
          cooldownRemainingSeconds = remaining,
          isRefreshSatelliteEnabled = false,
          refreshSatelliteNote = "Data baru saja diperbarui. Tunggu sebelum memperbarui lagi."
        )
        delay(1000L)
        remaining--
      }
      _uiState.value = _uiState.value.copy(
        cooldownRemainingSeconds = 0L,
        isRefreshSatelliteEnabled = true,
        refreshSatelliteNote = "Tekan untuk memperbarui data satelit NASA FIRMS."
      )
    }
  }

  fun setMapKey(key: String?): FireDataCredentialState {
    val result = fireRepository?.setMapKey(key) ?: FireDataCredentialState.NOT_CONFIGURED
    if (result == FireDataCredentialState.CONFIGURED) {
      lastLiveRequestTimeMillis = 0L
      refreshFireData(force = true)
    }
    return result
  }

  fun clearMapKey() {
    fireRepository?.clearMapKey()
  }

  fun getMapKey(): String? {
    return fireRepository?.getMapKey()
  }

  override fun onCleared() {
    super.onCleared()
    cooldownTimerJob?.cancel()
    locationTracker.stopTracking()
  }

  companion object {
    fun create(context: Context): DashboardViewModel {
      val tracker = AndroidLocationTracker(context.applicationContext)
      val fireRepo = RealFireDataRepository.create(context.applicationContext)
      return DashboardViewModel(tracker, fireRepo)
    }
  }
}

