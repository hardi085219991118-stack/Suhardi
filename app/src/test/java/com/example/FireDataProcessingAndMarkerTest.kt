package com.example

import com.example.core.contract.FeatureStatus
import com.example.core.fire.FireDataAgeCalculator
import com.example.core.fire.FireDataParser
import com.example.core.fire.FireDataRecord
import com.example.core.fire.FireDataSourceState
import com.example.core.map.BaseMapLayer
import com.example.core.map.CoordinateValidator
import com.example.core.map.MapTileProviderFactory
import com.example.core.registry.FeatureRegistry
import com.example.ui.map.MapUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/**
 * Pengujian komprehensif untuk implementasi:
 * - FIRE-007: Fire Data Processing (Parser, Age Calculation, Quality Validation)
 * - FIRE-008: Verified Fire Markers (Marker Generation, Coordinate Validation, BaseMap Layering)
 *
 * Sesuai filosofi Zero-Dummy & Evidence-Based Monitoring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FireDataProcessingAndMarkerTest {

  // 1. Feature Registry Verification
  @Test
  fun `FIRE-007 status is DATA_PROCESSING_VERIFIED with correct metadata in FeatureRegistry`() {
    val feature = FeatureRegistry.getFeature("FIRE-007")
    assertNotNull(feature)
    assertEquals(FeatureStatus.DATA_PROCESSING_VERIFIED, feature?.status)
    assertEquals("Fire Data Processing", feature?.name)
  }

  @Test
  fun `FIRE-008 status is MARKERS_VERIFIED with correct metadata in FeatureRegistry`() {
    val feature = FeatureRegistry.getFeature("FIRE-008")
    assertNotNull(feature)
    assertEquals(FeatureStatus.MARKERS_VERIFIED, feature?.status)
    assertEquals("Verified Satellite Hotspot Markers", feature?.name)
  }

  // 2. FireDataAgeCalculator Tests
  @Test
  fun `FireDataAgeCalculator categorizes under 1 hour correctly`() {
    val now = 100_000_000L
    val acq = now - (30 * 60 * 1000L) // 30 mins ago
    val category = FireDataAgeCalculator.categorize(acq, now)
    assertEquals(FireDataAgeCalculator.AgeCategory.UNDER_1_HOUR, category)
    val detail = FireDataAgeCalculator.formatAgeDetail(acq, now)
    assertTrue(detail.contains("< 1 jam"))
  }

  @Test
  fun `FireDataAgeCalculator categorizes 1 to 3 hours correctly`() {
    val now = 100_000_000L
    val acq = now - (2 * 3600 * 1000L) // 2 hours ago
    val category = FireDataAgeCalculator.categorize(acq, now)
    assertEquals(FireDataAgeCalculator.AgeCategory.FROM_1_TO_3_HOURS, category)
    val detail = FireDataAgeCalculator.formatAgeDetail(acq, now)
    assertTrue(detail.contains("1–3 jam"))
  }

  @Test
  fun `FireDataAgeCalculator categorizes 3 to 6 hours correctly`() {
    val now = 100_000_000L
    val acq = now - (4 * 3600 * 1000L)
    val category = FireDataAgeCalculator.categorize(acq, now)
    assertEquals(FireDataAgeCalculator.AgeCategory.FROM_3_TO_6_HOURS, category)
  }

  @Test
  fun `FireDataAgeCalculator categorizes 6 to 12 hours correctly`() {
    val now = 100_000_000L
    val acq = now - (8 * 3600 * 1000L)
    val category = FireDataAgeCalculator.categorize(acq, now)
    assertEquals(FireDataAgeCalculator.AgeCategory.FROM_6_TO_12_HOURS, category)
  }

  @Test
  fun `FireDataAgeCalculator categorizes 12 to 24 hours correctly`() {
    val now = 100_000_000L
    val acq = now - (18 * 3600 * 1000L)
    val category = FireDataAgeCalculator.categorize(acq, now)
    assertEquals(FireDataAgeCalculator.AgeCategory.FROM_12_TO_24_HOURS, category)
  }

  @Test
  fun `FireDataAgeCalculator categorizes over 24 hours correctly`() {
    val now = 1_000_000_000_000L
    val acq = now - (36 * 3600 * 1000L)
    val category = FireDataAgeCalculator.categorize(acq, now)
    assertEquals(FireDataAgeCalculator.AgeCategory.OVER_24_HOURS, category)
  }

  @Test
  fun `FireDataAgeCalculator handles null and invalid timestamps gracefully`() {
    assertEquals(FireDataAgeCalculator.AgeCategory.UNKNOWN, FireDataAgeCalculator.categorize(null))
    assertEquals(FireDataAgeCalculator.AgeCategory.UNKNOWN, FireDataAgeCalculator.categorize(0L))
    assertEquals(FireDataAgeCalculator.AgeCategory.UNKNOWN, FireDataAgeCalculator.categorize(-100L))
    assertEquals("BELUM TERSEDIA", FireDataAgeCalculator.formatAgeDetail(null))
  }

  // 3. BaseMapLayer and MapTileProviderFactory Tests
  @Test
  fun `MapTileProviderFactory returns MAPNIK for OPEN_STREET_MAP`() {
    val tileSource = MapTileProviderFactory.getTileSource(BaseMapLayer.OPEN_STREET_MAP)
    assertEquals(TileSourceFactory.MAPNIK.name(), tileSource.name())
  }

  @Test
  fun `MapTileProviderFactory returns Esri tile source for SATELLITE_ESRI`() {
    val tileSource = MapTileProviderFactory.getTileSource(BaseMapLayer.SATELLITE_ESRI)
    assertEquals("EsriWorldImagery", tileSource.name())
  }

  @Test
  fun `MapTileProviderFactory returns USGS tile source for SATELLITE_USGS`() {
    val tileSource = MapTileProviderFactory.getTileSource(BaseMapLayer.SATELLITE_USGS)
    assertEquals(TileSourceFactory.USGS_SAT.name(), tileSource.name())
  }

  // 4. Zero-Dummy Coordinate and Filter Validation
  @Test
  fun `CoordinateValidator accepts valid Kalimantan coordinates`() {
    assertTrue(CoordinateValidator.isValid(-2.5857, 114.4412))
    val result = CoordinateValidator.validate(-2.5857, 114.4412)
    assertTrue(result.isValid)
  }

  @Test
  fun `CoordinateValidator rejects out-of-range coordinates`() {
    assertFalse(CoordinateValidator.isValid(91.0, 114.0))
    assertFalse(CoordinateValidator.isValid(-91.0, 114.0))
    assertFalse(CoordinateValidator.isValid(-2.0, 181.0))
    assertFalse(CoordinateValidator.isValid(-2.0, -181.0))
    val result = CoordinateValidator.validate(95.0, 114.0)
    assertFalse(result.isValid)
    assertNotNull(result.errorMessage)
  }

  // 5. Fire Parser & Record Filtering
  @Test
  fun `FireDataParser extracts clean records and drops malformed lines`() {
    val csv = """
      latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
      -2.5857,114.4412,325.4,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      95.0,200.0,300.0,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      invalid,coord,300.0,0.4,0.4,2026-09-14,0410,NOAA-21,VIIRS,nominal,2.0NRT,298.2,14.5,D
      -3.1234,115.5678,340.1,0.5,0.5,2026-09-14,0515,NOAA-20,VIIRS,high,2.0NRT,301.0,25.0,D
    """.trimIndent()

    val parseResult = FireDataParser.parseCsv(csv)
    // Valid count should be 2 (-2.5857 and -3.1234), out-of-range (95.0,200.0) and invalid format should be dropped
    assertEquals(2, parseResult.validCount)
    assertEquals(2, parseResult.records.size)
    assertEquals(2, parseResult.invalidCount)
  }

  // 6. Dynamic Satellite Marker Count Tests (BUG 10)
  private fun createSampleRecord(lat: Double, lon: Double): FireDataRecord {
    return FireDataRecord(
      latitude = lat,
      longitude = lon,
      brightTi4 = 330.0,
      scan = 0.4,
      track = 0.4,
      acqDate = "2026-09-14",
      acqTime = "0410",
      satellite = "NOAA-21",
      instrument = "VIIRS",
      confidence = "nominal",
      version = "2.0NRT",
      brightTi5 = 295.0,
      frp = 12.5,
      dayNight = "D",
      acquisitionTimestampMillis = 1773547800000L
    )
  }

  @Test
  fun `MapUiState fromFireData with 1 valid record produces 1 marker`() {
    val records = listOf(createSampleRecord(-2.5857, 114.4412))
    val state = MapUiState.fromFireData(records, FireDataSourceState.DATA_SOURCE_AVAILABLE)
    assertEquals(1, state.fireMarkerCount)
    assertEquals("1", state.fireMarkerDisplay)
  }

  @Test
  fun `MapUiState fromFireData with 5 valid records produces 5 markers`() {
    val records = (1..5).map { createSampleRecord(-2.0 - (it * 0.1), 114.0 + (it * 0.1)) }
    val state = MapUiState.fromFireData(records, FireDataSourceState.DATA_SOURCE_AVAILABLE)
    assertEquals(5, state.fireMarkerCount)
    assertEquals("5", state.fireMarkerDisplay)
  }

  @Test
  fun `MapUiState fromFireData with 10 valid records produces 10 markers`() {
    val records = (1..10).map { createSampleRecord(-2.0 - (it * 0.05), 114.0 + (it * 0.05)) }
    val state = MapUiState.fromFireData(records, FireDataSourceState.DATA_SOURCE_AVAILABLE)
    assertEquals(10, state.fireMarkerCount)
    assertEquals("10", state.fireMarkerDisplay)
  }

  @Test
  fun `MapUiState fromFireData with 988 valid records produces 988 markers`() {
    val records = (1..988).map { createSampleRecord(-2.0 - ((it % 50) * 0.01), 114.0 + ((it % 50) * 0.01)) }
    val state = MapUiState.fromFireData(records, FireDataSourceState.DATA_SOURCE_AVAILABLE)
    assertEquals(988, state.fireMarkerCount)
    assertEquals("988", state.fireMarkerDisplay)
  }

  @Test
  fun `MapUiState fromFireData with NOT_VERIFIED produces 0 markers`() {
    val records = listOf(createSampleRecord(-2.5857, 114.4412))
    val state = MapUiState.fromFireData(records, FireDataSourceState.NOT_VERIFIED)
    assertEquals(0, state.fireMarkerCount)
    assertEquals("0", state.fireMarkerDisplay)
  }

  @Test
  fun `MapUiState fromFireData with NO_DETECTIONS_IN_QUERY produces 0 markers`() {
    val state = MapUiState.fromFireData(emptyList(), FireDataSourceState.NO_DETECTIONS_IN_QUERY)
    assertEquals(0, state.fireMarkerCount)
    assertEquals("0", state.fireMarkerDisplay)
  }

  @Test
  fun `MapUiState fromFireData with ERROR produces null unknown marker count`() {
    val errorStates = listOf(
      FireDataSourceState.API_CREDENTIAL_REQUIRED,
      FireDataSourceState.NETWORK_ERROR,
      FireDataSourceState.TIMEOUT,
      FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
      FireDataSourceState.INVALID_DATA_RESPONSE,
      FireDataSourceState.RATE_LIMIT_EXCEEDED
    )
    for (errorState in errorStates) {
      val state = MapUiState.fromFireData(emptyList(), errorState)
      assertNull("State $errorState must have null fireMarkerCount", state.fireMarkerCount)
      assertEquals("--", state.fireMarkerDisplay)
    }
  }

  @Test
  fun `MapUiState fromFireData with CACHED and valid records produces count equals valid records`() {
    val records = (1..7).map { createSampleRecord(-2.0 - (it * 0.1), 114.0 + (it * 0.1)) }
    val state = MapUiState.fromFireData(records, FireDataSourceState.CACHED)
    assertEquals(7, state.fireMarkerCount)
    assertEquals("7", state.fireMarkerDisplay)
  }
}
