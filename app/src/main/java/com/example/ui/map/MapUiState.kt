package com.example.ui.map

import com.example.core.fire.FireDataRecord
import com.example.core.fire.FireDataSourceState
import com.example.core.map.CoordinateValidator
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.map.MapProviderInfo

/**
 * UI State untuk MapScreen (FIRE-005 Map Foundation & FIRE-008 Verified Satellite Hotspot Markers).
 *
 * Aturan Zero-Dummy:
 * - fireMarkerCount dinamis berdasarkan jumlah FireDataRecord valid dari data satelit NASA FIRMS.
 * - Bernilai null saat unverified atau error (menghasilkan display "--" / UNKNOWN).
 * - Bernilai 0 jika query berhasil namun 0 deteksi hotspot.
 * - Bernilai N jika terdapat N record deteksi hotspot satelit terverifikasi.
 */
data class MapUiState(
  val mapStatus: MapStatus = MapStatus.MAP_LOADING,
  val mapErrorMessage: String? = null,
  val mapProviderName: String = MapProviderInfo.PROVIDER_NAME,
  val credentialStatus: String = MapProviderInfo.CREDENTIAL_STATUS,

  // Location Integration (FIRE-004)
  val locationStatus: LocationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED,
  val deviceLocation: DeviceLocation? = null,
  val isLocationValid: Boolean = false,
  val coordinateErrorMessage: String? = null,

  // Camera & Interaction
  val isCenteredOnUser: Boolean = false,

  // Dynamic Satellite Hotspot Marker Count (FIRE-008)
  val fireMarkerCount: Int? = null
) {
  val fireMarkerDisplay: String get() = fireMarkerCount?.toString() ?: "--"

  companion object {
    fun fromFireData(
      records: List<FireDataRecord>,
      state: FireDataSourceState,
      deviceLocation: DeviceLocation? = null,
      locationStatus: LocationStatus = LocationStatus.LOCATION_PERMISSION_REQUIRED
    ): MapUiState {
      val markerCount: Int? = when (state) {
        FireDataSourceState.DATA_SOURCE_AVAILABLE,
        FireDataSourceState.CACHED -> {
          records.count {
            CoordinateValidator.isValid(it.latitude, it.longitude) &&
              !(it.latitude == 0.0 && it.longitude == 0.0)
          }
        }
        FireDataSourceState.NO_DETECTIONS_IN_QUERY,
        FireDataSourceState.NOT_VERIFIED,
        FireDataSourceState.CONNECTING -> 0
        FireDataSourceState.API_CREDENTIAL_REQUIRED,
        FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
        FireDataSourceState.NETWORK_ERROR,
        FireDataSourceState.TIMEOUT,
        FireDataSourceState.INVALID_DATA_RESPONSE,
        FireDataSourceState.RATE_LIMIT_EXCEEDED -> null
      }
      return MapUiState(
        deviceLocation = deviceLocation,
        locationStatus = locationStatus,
        fireMarkerCount = markerCount
      )
    }
  }
}
