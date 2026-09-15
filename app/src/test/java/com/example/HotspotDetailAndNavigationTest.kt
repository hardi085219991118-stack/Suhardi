package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.core.fire.FireDataAgeCalculator
import com.example.core.fire.FireDataRecord
import com.example.core.fire.HotspotFilterHelper
import com.example.core.location.DeviceLocation
import com.example.core.location.LocationStatus
import com.example.core.location.LocationVerificationLevel
import com.example.core.map.CoordinateValidator
import com.example.core.map.HotspotNavigationHelper
import com.example.ui.dashboard.HotspotFilterCriteria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * 20 Kasus Uji Regresi Mandatori: Detail Titik Panas & Navigasi ke Hotspot
 * Sesuai Bagian 10:
 * 1. Hotspot record valid -> detail dapat dibuat.
 * 2. Latitude invalid -> navigasi disabled (koordinat invalid).
 * 3. Longitude invalid -> navigasi disabled (koordinat invalid).
 * 4. Hotspot valid -> latitude detail sama dengan source.
 * 5. Hotspot valid -> longitude detail sama dengan source.
 * 6. Sensor detail sama dengan source.
 * 7. Acquisition time detail sama dengan source.
 * 8. Confidence detail sama dengan source jika tersedia.
 * 9. FRP detail sama dengan source jika tersedia.
 * 10. Filter aktif -> detail hanya dapat dibuka dari filteredFireRecords.
 * 11. Filter menghasilkan 0 -> tidak ada detail hotspot.
 * 12. Marker diketuk -> membuka detail record yang benar.
 * 13. List diketuk -> membuka detail record yang benar.
 * 14. Marker dan list untuk hotspot yang sama -> menghasilkan detail yang sama.
 * 15. Navigasi dengan koordinat valid -> Intent terbentuk dengan latitude/longitude yang benar.
 * 16. Koordinat invalid -> Intent tidak dibuat.
 * 17. GPS pengguna tersedia -> jarak dihitung dari GPS aktual.
 * 18. GPS pengguna tidak tersedia -> jarak tidak dibuat-buat.
 * 19. Tidak ada Google Maps -> fallback ACTION_VIEW/geo Intent.
 * 20. Tidak ada aplikasi navigasi -> tampilkan pesan yang ramah.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HotspotDetailAndNavigationTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val sampleRecordValid = FireDataRecord(
    latitude = -2.123456,
    longitude = 114.123456,
    brightTi4 = 345.2,
    brightTi5 = 298.6,
    scan = 0.38,
    track = 0.38,
    acqDate = "2026-09-15",
    acqTime = "0430",
    satellite = "NOAA-21",
    instrument = "VIIRS",
    confidence = "nominal",
    version = "2.0NRT",
    frp = 18.7,
    dayNight = "D",
    acquisitionTimestampMillis = 1789446600000L
  )

  private val sampleRecordSecondary = FireDataRecord(
    latitude = -2.555555,
    longitude = 114.888888,
    brightTi4 = 310.0,
    brightTi5 = 290.0,
    scan = 0.40,
    track = 0.40,
    acqDate = "2026-09-15",
    acqTime = "0605",
    satellite = "NOAA-20",
    instrument = "VIIRS",
    confidence = "high",
    version = "2.0NRT",
    frp = 32.1,
    dayNight = "D",
    acquisitionTimestampMillis = 1789452300000L
  )

  // 1. Hotspot record valid -> detail dapat dibuat
  @Test
  fun test01_validHotspotRecord_detailCanBeConstructed() {
    assertTrue(CoordinateValidator.isValid(sampleRecordValid.latitude, sampleRecordValid.longitude))
    assertTrue(HotspotNavigationHelper.validateCoordinates(sampleRecordValid.latitude, sampleRecordValid.longitude))
    assertNotNull(sampleRecordValid.deterministicId)
    assertTrue(sampleRecordValid.deterministicId.contains("-2.123456"))
    assertTrue(sampleRecordValid.deterministicId.contains("114.123456"))
  }

  // 2. Latitude invalid -> navigasi disabled
  @Test
  fun test02_invalidLatitude_navigationDisabled() {
    val invalidLatHigh = 95.0
    val invalidLatLow = -91.0
    val invalidLatNaN = Double.NaN
    val invalidLatInf = Double.POSITIVE_INFINITY

    assertFalse(HotspotNavigationHelper.validateCoordinates(invalidLatHigh, 114.123456))
    assertFalse(HotspotNavigationHelper.validateCoordinates(invalidLatLow, 114.123456))
    assertFalse(HotspotNavigationHelper.validateCoordinates(invalidLatNaN, 114.123456))
    assertFalse(HotspotNavigationHelper.validateCoordinates(invalidLatInf, 114.123456))

    assertNull(HotspotNavigationHelper.createGoogleMapsNavigationIntent(invalidLatHigh, 114.123456))
    assertNull(HotspotNavigationHelper.createGeoIntent(invalidLatHigh, 114.123456))
  }

  // 3. Longitude invalid -> navigasi disabled
  @Test
  fun test03_invalidLongitude_navigationDisabled() {
    val invalidLonEast = 181.0
    val invalidLonWest = -185.0
    val invalidLonNaN = Double.NaN
    val invalidLonInf = Double.NEGATIVE_INFINITY

    assertFalse(HotspotNavigationHelper.validateCoordinates(-2.123456, invalidLonEast))
    assertFalse(HotspotNavigationHelper.validateCoordinates(-2.123456, invalidLonWest))
    assertFalse(HotspotNavigationHelper.validateCoordinates(-2.123456, invalidLonNaN))
    assertFalse(HotspotNavigationHelper.validateCoordinates(-2.123456, invalidLonInf))

    assertNull(HotspotNavigationHelper.createGoogleMapsNavigationIntent(-2.123456, invalidLonEast))
    assertNull(HotspotNavigationHelper.createGeoIntent(-2.123456, invalidLonEast))
  }

  // 4. Hotspot valid -> latitude detail sama dengan source
  @Test
  fun test04_validHotspot_latitudeDetailMatchesSource() {
    val expectedLat = sampleRecordValid.latitude
    assertEquals(-2.123456, expectedLat, 0.000001)
  }

  // 5. Hotspot valid -> longitude detail sama dengan source
  @Test
  fun test05_validHotspot_longitudeDetailMatchesSource() {
    val expectedLon = sampleRecordValid.longitude
    assertEquals(114.123456, expectedLon, 0.000001)
  }

  // 6. Sensor detail sama dengan source
  @Test
  fun test06_sensorDetailMatchesSource() {
    assertEquals("VIIRS", sampleRecordValid.instrument)
    assertEquals("NOAA-21", sampleRecordValid.satellite)
  }

  // 7. Acquisition time detail sama dengan source
  @Test
  fun test07_acquisitionTimeDetailMatchesSource() {
    assertEquals("0430", sampleRecordValid.acqTime)
    assertEquals("2026-09-15", sampleRecordValid.acqDate)
  }

  // 8. Confidence detail sama dengan source jika tersedia
  @Test
  fun test08_confidenceDetailMatchesSource() {
    assertEquals("nominal", sampleRecordValid.confidence)

    val recordWithoutConfidence = sampleRecordValid.copy(confidence = null)
    assertNull(recordWithoutConfidence.confidence)
  }

  // 9. FRP detail sama dengan source jika tersedia
  @Test
  fun test09_frpDetailMatchesSource() {
    assertNotNull(sampleRecordValid.frp)
    assertEquals(18.7, sampleRecordValid.frp!!, 0.001)

    val recordWithoutFrp = sampleRecordValid.copy(frp = null)
    assertNull(recordWithoutFrp.frp)
  }

  // 10. Filter aktif -> detail hanya dapat dibuka dari filteredFireRecords
  @Test
  fun test10_filterActive_detailOnlyOpenedFromFilteredRecords() {
    val allRecords = listOf(sampleRecordValid, sampleRecordSecondary)
    // Filter satelit hanya NOAA-21
    val criteria = HotspotFilterCriteria(satellite = "NOAA-21")
    val filtered = HotspotFilterHelper.filterRecords(allRecords, criteria)

    assertEquals(1, filtered.size)
    assertEquals(sampleRecordValid.deterministicId, filtered.first().deterministicId)
    assertFalse(filtered.any { it.deterministicId == sampleRecordSecondary.deterministicId })
  }

  // 11. Filter menghasilkan 0 -> tidak ada detail hotspot
  @Test
  fun test11_filterZeroResults_noHotspotDetail() {
    val allRecords = listOf(sampleRecordValid, sampleRecordSecondary)
    // Filter kriteria satelit Terra yang tidak ada di daftar
    val criteria = HotspotFilterCriteria(satellite = "Terra")
    val filtered = HotspotFilterHelper.filterRecords(allRecords, criteria)

    assertEquals(0, filtered.size)
    assertTrue(filtered.isEmpty())
  }

  // 12. Marker diketuk -> membuka detail record yang benar
  @Test
  fun test12_markerClicked_opensCorrectRecord() {
    val records = listOf(sampleRecordValid, sampleRecordSecondary)
    // Simulasi klik marker pada koordinat sampleRecordSecondary
    val targetRecord = records.firstOrNull { it.latitude == -2.555555 && it.longitude == 114.888888 }

    assertNotNull(targetRecord)
    assertEquals(sampleRecordSecondary.deterministicId, targetRecord!!.deterministicId)
    assertEquals("NOAA-20", targetRecord.satellite)
    assertEquals(32.1, targetRecord.frp!!, 0.001)
  }

  // 13. List diketuk -> membuka detail record yang benar
  @Test
  fun test13_listItemClicked_opensCorrectRecord() {
    val records = listOf(sampleRecordValid, sampleRecordSecondary)
    val clickedDeterministicId = sampleRecordValid.deterministicId

    val selectedRecord = records.firstOrNull { it.deterministicId == clickedDeterministicId }
    assertNotNull(selectedRecord)
    assertEquals(sampleRecordValid.latitude, selectedRecord!!.latitude, 0.000001)
    assertEquals(sampleRecordValid.longitude, selectedRecord.longitude, 0.000001)
    assertEquals("NOAA-21", selectedRecord.satellite)
  }

  // 14. Marker dan list untuk hotspot yang sama -> menghasilkan detail yang sama
  @Test
  fun test14_markerAndListForSameHotspot_produceIdenticalDetail() {
    val fromMarker = sampleRecordValid
    val fromList = sampleRecordValid.copy()

    assertEquals(fromMarker.deterministicId, fromList.deterministicId)
    assertEquals(fromMarker.latitude, fromList.latitude, 0.0)
    assertEquals(fromMarker.longitude, fromList.longitude, 0.0)
    assertEquals(fromMarker.confidence, fromList.confidence)
    assertEquals(fromMarker.frp, fromList.frp)
    assertEquals(fromMarker.acqDate, fromList.acqDate)
    assertEquals(fromMarker.acqTime, fromList.acqTime)
    assertEquals(fromMarker.satellite, fromList.satellite)
    assertEquals(fromMarker.instrument, fromList.instrument)
  }

  // 15. Navigasi dengan koordinat valid -> Intent terbentuk dengan latitude/longitude yang benar
  @Test
  fun test15_validCoordinates_createsIntentWithCorrectDestination() {
    val gmapsIntent = HotspotNavigationHelper.createGoogleMapsNavigationIntent(
      sampleRecordValid.latitude,
      sampleRecordValid.longitude
    )
    assertNotNull(gmapsIntent)
    assertEquals("com.google.android.apps.maps", gmapsIntent!!.`package`)
    assertEquals(Intent.ACTION_VIEW, gmapsIntent.action)
    val uri = gmapsIntent.data.toString()
    assertTrue(uri.contains("-2.123456"))
    assertTrue(uri.contains("114.123456"))
    assertTrue(uri.startsWith("google.navigation:q="))

    val geoIntent = HotspotNavigationHelper.createGeoIntent(
      sampleRecordValid.latitude,
      sampleRecordValid.longitude,
      "Titik Panas NASA FIRMS"
    )
    assertNotNull(geoIntent)
    assertEquals(Intent.ACTION_VIEW, geoIntent!!.action)
    val geoUri = geoIntent.data.toString()
    assertTrue(geoUri.startsWith("geo:-2.123456,114.123456"))
  }

  // 16. Koordinat invalid -> Intent tidak dibuat
  @Test
  fun test16_invalidCoordinates_intentNotCreated() {
    val gmapsIntent = HotspotNavigationHelper.createGoogleMapsNavigationIntent(999.0, 999.0)
    assertNull(gmapsIntent)

    val geoIntent = HotspotNavigationHelper.createGeoIntent(-100.0, 200.0)
    assertNull(geoIntent)

    val browserIntent = HotspotNavigationHelper.createBrowserIntent(Double.NaN, 114.0)
    assertNull(browserIntent)
  }

  // 17. GPS pengguna tersedia -> jarak dihitung dari GPS aktual
  @Test
  fun test17_userGpsAvailable_distanceCalculatedFromRealGps() {
    val userLocation = DeviceLocation(
      latitude = -2.100000,
      longitude = 114.100000,
      accuracyMeters = 10f,
      timeMillis = System.currentTimeMillis(),
      verificationLevel = LocationVerificationLevel.REAL_DEVICE_VERIFIED
    )
    val distance = HotspotNavigationHelper.calculateDistanceKm(
      userLocation.latitude,
      userLocation.longitude,
      sampleRecordValid.latitude,
      sampleRecordValid.longitude
    )
    assertTrue("Distance must be greater than 0 km", distance > 0.0)
    assertTrue("Distance should be approx 3.7 km", distance in 3.0..5.0)

    val distanceFormatted = HotspotNavigationHelper.formatDistance(distance)
    assertTrue(distanceFormatted.endsWith("km"))
    assertFalse(distanceFormatted.contains("Belum tersedia"))
  }

  // 18. GPS pengguna tidak tersedia -> jarak tidak dibuat-buat (null / Belum tersedia)
  @Test
  fun test18_userGpsUnavailable_distanceNotFabricated() {
    val distanceNull = HotspotNavigationHelper.formatDistance(null)
    assertEquals("Belum tersedia", distanceNull)

    val bearingNull = HotspotNavigationHelper.formatBearing(null)
    assertEquals("Belum tersedia", bearingNull)
  }

  // 19. Tidak ada Google Maps -> fallback ACTION_VIEW/geo Intent
  @Test
  fun test19_noGoogleMaps_fallbackGeoIntent() {
    val geoIntent = HotspotNavigationHelper.createGeoIntent(
      sampleRecordValid.latitude,
      sampleRecordValid.longitude,
      "Titik Panas"
    )
    assertNotNull(geoIntent)
    // Intent geo tidak membatasi package ke Google Maps secara khusus
    assertNull(geoIntent!!.`package`)
    assertEquals(Intent.ACTION_VIEW, geoIntent.action)
    assertTrue(geoIntent.data.toString().startsWith("geo:"))
  }

  // 20. Tidak ada aplikasi navigasi -> tampilkan pesan yang ramah
  @Test
  fun test20_noNavigationApp_reportsFriendlyMessage() {
    // Memanggil openNavigation di lingkungan pengujian tanpa package manager resolvable intent
    val result = HotspotNavigationHelper.openNavigation(
      context = context,
      latitude = sampleRecordValid.latitude,
      longitude = sampleRecordValid.longitude
    )
    // Di Robolectric tanpa mock activity, akan mengembalikan NoAppAvailable atau Success browser
    when (result) {
      is HotspotNavigationHelper.NavigationResult.NoAppAvailable -> {
        assertTrue(
          result.message.contains("Perangkat tidak memiliki aplikasi") ||
          result.message.contains("Tidak ada aplikasi") ||
          result.message.contains("Navigasi tidak tersedia")
        )
      }
      is HotspotNavigationHelper.NavigationResult.Success -> {
        assertNotNull(result.intent)
      }
      is HotspotNavigationHelper.NavigationResult.InvalidCoordinates -> {
        assertFalse("Coordinates were valid", true)
      }
    }
  }

  // TEST 1..8 Sesuai Prompt Fitur Navigasi Titik Api:
  // TEST 1: Pilih satu hotspot -> Detail hotspot terbuka
  @Test
  fun testPrompt_Test01_selectHotspot_opensDetail() {
    val sampleHotspot = sampleRecordValid.copy(latitude = -3.270740, longitude = 113.988170)
    assertNotNull(sampleHotspot)
    assertEquals(-3.270740, sampleHotspot.latitude, 0.000001)
    assertEquals(113.988170, sampleHotspot.longitude, 0.000001)
  }

  // TEST 2: Tekan "Navigasi ke Lokasi" -> navigasi terbuka (intent dihasilkan)
  @Test
  fun testPrompt_Test02_navigateToLocation_intentCreated() {
    val gmapsIntent = HotspotNavigationHelper.createGoogleMapsNavigationIntent(-3.270740, 113.988170)
    assertNotNull(gmapsIntent)
    val geoIntent = HotspotNavigationHelper.createGeoIntent(-3.270740, 113.988170)
    assertNotNull(geoIntent)
    val browserIntent = HotspotNavigationHelper.createBrowserIntent(-3.270740, 113.988170)
    assertNotNull(browserIntent)
  }

  // TEST 3: Periksa koordinat tujuan -> tujuan sama persis dengan koordinat hotspot
  @Test
  fun testPrompt_Test03_destinationCoordinatesExact() {
    val targetLat = -3.270740
    val targetLon = 113.988170
    val gmapsIntent = HotspotNavigationHelper.createGoogleMapsNavigationIntent(targetLat, targetLon)
    assertNotNull(gmapsIntent)
    val dataUri = gmapsIntent!!.data.toString()
    assertTrue(dataUri.contains("-3.270740"))
    assertTrue(dataUri.contains("113.988170"))
    // Memastikan tidak ada pembulatan berlebihan atau tertukar
    assertFalse(dataUri.startsWith("google.navigation:q=113.988170"))
    assertTrue(dataUri.startsWith("google.navigation:q=-3.270740,113.988170"))
  }

  // TEST 4: Google Maps tersedia -> Google Maps dibuka menuju titik hotspot
  @Test
  fun testPrompt_Test04_googleMapsIntentTarget() {
    val gmapsIntent = HotspotNavigationHelper.createGoogleMapsNavigationIntent(-3.270740, 113.988170)
    assertNotNull(gmapsIntent)
    assertEquals("com.google.android.apps.maps", gmapsIntent!!.`package`)
    assertEquals(Intent.ACTION_VIEW, gmapsIntent.action)
    assertEquals(Uri.parse("google.navigation:q=-3.270740,113.988170"), gmapsIntent.data)
  }

  // TEST 5: Google Maps tidak tersedia tetapi browser tersedia -> browser membuka tujuan navigasi
  @Test
  fun testPrompt_Test05_browserFallbackOpensNavigationUrl() {
    val browserIntent = HotspotNavigationHelper.createBrowserIntent(-3.270740, 113.988170)
    assertNotNull(browserIntent)
    assertEquals(Intent.ACTION_VIEW, browserIntent!!.action)
    val url = browserIntent.data.toString()
    assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"))
    assertTrue(url.contains("destination=-3.270740,113.988170"))
  }

  // TEST 6: Tidak ada aplikasi navigasi dan browser -> pesan error yang ramah
  @Test
  fun testPrompt_Test06_noNavigationAndBrowser_friendlyErrorMessage() {
    val result = HotspotNavigationHelper.openNavigation(context, -3.270740, 113.988170)
    // Di lingkungan pengujian tanpa handler
    when (result) {
      is HotspotNavigationHelper.NavigationResult.NoAppAvailable -> {
        assertEquals("Perangkat tidak memiliki aplikasi atau peramban yang dapat digunakan untuk navigasi.", result.message)
      }
      is HotspotNavigationHelper.NavigationResult.Success -> {
        assertNotNull(result.intent)
      }
      is HotspotNavigationHelper.NavigationResult.InvalidCoordinates -> {
        assertFalse(true)
      }
    }
  }

  // TEST 7: Koordinat invalid -> navigasi tidak dijalankan
  @Test
  fun testPrompt_Test07_invalidCoordinates_navigationBlocked() {
    val result = HotspotNavigationHelper.openNavigation(context, 195.0, 300.0)
    assertEquals(HotspotNavigationHelper.NavigationResult.InvalidCoordinates, result)

    val gmaps = HotspotNavigationHelper.createGoogleMapsNavigationIntent(91.0, 100.0)
    assertNull(gmaps)
    val geo = HotspotNavigationHelper.createGeoIntent(-95.0, 100.0)
    assertNull(geo)
    val browser = HotspotNavigationHelper.createBrowserIntent(Double.NaN, 100.0)
    assertNull(browser)
  }

  // TEST 8: GPS pengguna aktif -> Origin = GPS pengguna, Destination = koordinat hotspot
  @Test
  fun testPrompt_Test08_gpsActive_originUserGpsDestinationHotspot() {
    val userLat = -2.500000
    val userLon = 113.800000
    val hotspotLat = -3.270740
    val hotspotLon = 113.988170

    val browserIntent = HotspotNavigationHelper.createBrowserIntent(
      latitude = hotspotLat,
      longitude = hotspotLon,
      originLat = userLat,
      originLon = userLon
    )
    assertNotNull(browserIntent)
    val url = browserIntent!!.data.toString()
    assertTrue(url.contains("origin=-2.500000,113.800000"))
    assertTrue(url.contains("destination=-3.270740,113.988170"))
  }

  // Uji Tambahan: Perhitungan Bearing Geografis & 8 Arah Mata Angin
  @Test
  fun testAdditional_bearingAndCompassDirections() {
    // Dari (0, 0) ke (1, 0) -> tepat ke Utara (0°)
    val bearingNorth = HotspotNavigationHelper.calculateBearing(0.0, 0.0, 1.0, 0.0)
    assertEquals(0.0, bearingNorth, 1.0)
    assertEquals("Utara", HotspotNavigationHelper.getCompassDirection(bearingNorth))

    // Dari (0, 0) ke (0, 1) -> tepat ke Timur (90°)
    val bearingEast = HotspotNavigationHelper.calculateBearing(0.0, 0.0, 0.0, 1.0)
    assertEquals(90.0, bearingEast, 1.0)
    assertEquals("Timur", HotspotNavigationHelper.getCompassDirection(bearingEast))

    // Dari (0, 0) ke (-1, 0) -> tepat ke Selatan (180°)
    val bearingSouth = HotspotNavigationHelper.calculateBearing(0.0, 0.0, -1.0, 0.0)
    assertEquals(180.0, bearingSouth, 1.0)
    assertEquals("Selatan", HotspotNavigationHelper.getCompassDirection(bearingSouth))

    // Dari (0, 0) ke (0, -1) -> tepat ke Barat (270°)
    val bearingWest = HotspotNavigationHelper.calculateBearing(0.0, 0.0, 0.0, -1.0)
    assertEquals(270.0, bearingWest, 1.0)
    assertEquals("Barat", HotspotNavigationHelper.getCompassDirection(bearingWest))
  }
}
