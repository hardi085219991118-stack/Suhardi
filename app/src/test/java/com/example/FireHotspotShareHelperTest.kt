package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.core.fire.FireDataRecord
import com.example.core.share.FireHotspotShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FireHotspotShareHelperTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val sampleRecordA = FireDataRecord(
    latitude = -2.684321,
    longitude = 114.441876,
    brightTi4 = 325.5,
    scan = 0.4,
    track = 0.4,
    acqDate = "2026-09-15",
    acqTime = "0605",
    satellite = "NOAA-21",
    instrument = "VIIRS",
    confidence = "82%",
    version = "2.0NRT",
    brightTi5 = 295.0,
    frp = 12.3,
    dayNight = "D"
  )

  private val sampleRecordB = FireDataRecord(
    latitude = -2.701234,
    longitude = 114.450987,
    brightTi4 = 310.0,
    scan = 0.375,
    track = 0.375,
    acqDate = "2026-09-15",
    acqTime = "0605",
    satellite = "NOAA-20",
    instrument = "VIIRS",
    confidence = "68%",
    version = "2.0NRT",
    brightTi5 = 290.0,
    frp = 7.6,
    dayNight = "D"
  )

  // TEST 1: Hotspot memiliki latitude dan longitude valid -> link Google Maps berhasil dibuat.
  @Test
  fun test1_validLatLong_createsGoogleMapsUrl() {
    val url = FireHotspotShareHelper.generateGoogleMapsUrl(-2.684321, 114.441876)
    assertNotNull(url)
    assertTrue(url!!.contains("https://www.google.com/maps/search/?api=1&query=-2.684321,114.441876"))
  }

  // TEST 2: Latitude invalid -> link Google Maps tidak dibuat.
  @Test
  fun test2_invalidLatitude_returnsNull() {
    val urlTooHigh = FireHotspotShareHelper.generateGoogleMapsUrl(95.0, 114.441876)
    assertNull(urlTooHigh)

    val urlTooLow = FireHotspotShareHelper.generateGoogleMapsUrl(-95.0, 114.441876)
    assertNull(urlTooLow)

    val urlNaN = FireHotspotShareHelper.generateGoogleMapsUrl(Double.NaN, 114.441876)
    assertNull(urlNaN)
  }

  // TEST 3: Longitude invalid -> link Google Maps tidak dibuat.
  @Test
  fun test3_invalidLongitude_returnsNull() {
    val urlTooEast = FireHotspotShareHelper.generateGoogleMapsUrl(-2.684321, 185.0)
    assertNull(urlTooEast)

    val urlTooWest = FireHotspotShareHelper.generateGoogleMapsUrl(-2.684321, -185.0)
    assertNull(urlTooWest)

    val urlNaN = FireHotspotShareHelper.generateGoogleMapsUrl(-2.684321, Double.NaN)
    assertNull(urlNaN)
  }

  // TEST 4: Hotspot dipilih -> sharing menggunakan koordinat hotspot yang dipilih.
  @Test
  fun test4_shareTextUsesSelectedHotspotCoordinates() {
    val shareText = FireHotspotShareHelper.generateShareText(sampleRecordA, distanceKm = 1.2)
    assertTrue(shareText.contains("-2.684321"))
    assertTrue(shareText.contains("114.441876"))
    assertTrue(shareText.contains("NOAA-21"))
    assertTrue(shareText.contains("12.3 MW"))
    assertTrue(shareText.contains("82%"))
    assertTrue(shareText.contains("1.2 km"))
  }

  // TEST 5: Hotspot A dipilih -> sharing tidak menggunakan data Hotspot B.
  @Test
  fun test5_hotspotASelected_doesNotContainHotspotBData() {
    val shareTextA = FireHotspotShareHelper.generateShareText(sampleRecordA)
    assertFalse(shareTextA.contains("-2.701234"))
    assertFalse(shareTextA.contains("114.450987"))
    assertFalse(shareTextA.contains("7.6 MW"))
    assertFalse(shareTextA.contains("NOAA-20"))
  }

  // TEST 6: MAP_KEY tidak boleh masuk ke teks sharing.
  @Test
  fun test6_noMapKeyLeakInShareText() {
    val shareText = FireHotspotShareHelper.generateShareText(sampleRecordA)
    assertFalse(shareText.contains("MAP_KEY", ignoreCase = true))
    assertFalse(shareText.contains("FIRMS_KEY", ignoreCase = true))
    assertFalse(shareText.contains("api_key", ignoreCase = true))
  }

  // TEST 7: Token/API key tidak boleh masuk ke teks sharing.
  @Test
  fun test7_noTokensInShareText() {
    val shareText = FireHotspotShareHelper.generateShareText(sampleRecordA)
    assertFalse(shareText.contains("Bearer", ignoreCase = true))
    assertFalse(shareText.contains("token", ignoreCase = true))
    assertFalse(shareText.contains("SHA-256", ignoreCase = true))
    assertFalse(shareText.contains("SECRET", ignoreCase = true))
  }

  // TEST 8: Sharing format text plain & message structured.
  @Test
  fun test8_shareContentStructure() {
    val text = FireHotspotShareHelper.generateShareText(sampleRecordA)
    assertTrue(text.contains("TITIK PANAS TERDETEKSI SATELIT"))
    assertTrue(text.contains("HARDI MANTANGAI FIRE NOW"))
    assertTrue(text.contains("https://www.google.com/maps/search/?api=1"))
  }

  // TEST 9 & 10: Validasi Google Maps Intent fallback
  @Test
  fun test9_openGoogleMapsDoesNotThrow() {
    // Memastikan pemicuan fungsi aman pada mock/device runtime tanpa unhandled exception
    FireHotspotShareHelper.openGoogleMaps(context, sampleRecordA.latitude, sampleRecordA.longitude)
  }

  // TEST 11: Nomor WhatsApp pembuat -> 6285219991118 untuk intent.
  @Test
  fun test11_creatorWhatsAppNumberMatchesSpecification() {
    assertEquals("6285219991118", FireHotspotShareHelper.CREATOR_WHATSAPP_NUMBER)
    assertEquals("085219991118", FireHotspotShareHelper.CREATOR_PHONE_DISPLAY)
    assertEquals("Hardi Mantangai", FireHotspotShareHelper.CREATOR_NAME)
  }

  // TEST 12: Nilai default ketika info tidak tersedia
  @Test
  fun test12_partialDataFallbackHandling() {
    val incompleteRecord = FireDataRecord(
      latitude = -2.5,
      longitude = 114.5,
      brightTi4 = null,
      scan = null,
      track = null,
      acqDate = "",
      acqTime = "",
      satellite = "",
      instrument = "",
      confidence = null,
      version = "",
      brightTi5 = null,
      frp = null,
      dayNight = ""
    )
    val text = FireHotspotShareHelper.generateShareText(incompleteRecord, distanceKm = null)
    assertTrue(text.contains("Tidak tersedia"))
    assertTrue(text.contains("Jarak dari lokasi saya: Tidak tersedia"))
    assertFalse(text.contains("null"))
  }
}
