package com.example.core.map

/**
 * Informasi eksplisit provider peta geografis:
 *
 * MAP ENGINE: osmdroid
 * TILE PROVIDER: Esri World Imagery
 * DEFAULT LAYER: SATELLITE_ESRI
 * FALLBACK TILE PROVIDER: OpenStreetMap
 * Credential: NOT_REQUIRED (Layanan peta tanpa proprietary API key)
 * Zero Fake Map: Peta native interaktif dengan proyeksi koordinat geografis nyata.
 */
object MapProviderInfo {
  const val MAP_ENGINE = "osmdroid"
  const val TILE_PROVIDER = "Esri World Imagery"
  const val DEFAULT_LAYER = "SATELLITE_ESRI"
  const val PROVIDER_NAME = "osmdroid"
  const val CREDENTIAL_STATUS = "NOT_REQUIRED"
  const val TILE_SOURCE = "Esri World Imagery"
  const val FALLBACK_TILE_PROVIDER = "OpenStreetMap"
  const val ZERO_FIRE_MARKERS_POLICY = "ZERO-DUMMY: Marker titik panas hanya dirender dari data satelit NASA FIRMS otentik (FIRE-008)"
}
