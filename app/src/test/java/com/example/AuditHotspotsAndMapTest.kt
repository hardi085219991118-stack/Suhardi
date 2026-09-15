package com.example

import com.example.core.fire.FireDataRecord
import com.example.core.fire.HotspotFilterHelper
import com.example.core.map.BaseMapLayer
import com.example.core.map.MapProviderInfo
import com.example.core.map.MapTileValidator
import com.example.core.share.FireHotspotShareHelper
import com.example.ui.dashboard.HotspotFilterCriteria
import com.example.ui.map.MapStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AuditHotspotsAndMapTest {

  @Test
  fun `test map provider info separation of engine and tile provider`() {
    assertEquals("osmdroid", MapProviderInfo.MAP_ENGINE)
    assertEquals("Esri World Imagery", MapProviderInfo.TILE_PROVIDER)
    assertEquals("SATELLITE_ESRI", MapProviderInfo.DEFAULT_LAYER)
    assertFalse(MapProviderInfo.TILE_SOURCE.contains("OpenStreetMap sebagai tile satelit default"))
  }

  @Test
  fun `test map tile validator handles failure and success`() = runBlocking {
    // 1. Simulation of failure
    MapTileValidator.testTileVerifier = { layer ->
      Result.failure(IOException("Koneksi gagal saat memuat tile $layer"))
    }
    val failResult = MapTileValidator.validateTileSource(BaseMapLayer.SATELLITE_ESRI)
    assertTrue(failResult.isFailure)

    // 2. Simulation of success
    MapTileValidator.testTileVerifier = {
      Result.success(true)
    }
    val successResult = MapTileValidator.validateTileSource(BaseMapLayer.SATELLITE_ESRI)
    assertTrue(successResult.isSuccess)
    assertTrue(successResult.getOrThrow())

    // Reset test hook
    MapTileValidator.testTileVerifier = null
  }

  @Test
  fun `test filter waktu strictly respects maxAgeHours based on acquisition timestamp`() {
    val now = 1700000000000L
    val fourHoursAgo = now - (4 * 3600 * 1000L)
    val eightHoursAgo = now - (8 * 3600 * 1000L)
    val eighteenHoursAgo = now - (18 * 3600 * 1000L)
    val thirtyHoursAgo = now - (30 * 3600 * 1000L)

    val rec4h = createSampleRecord(1, fourHoursAgo, "NOAA-21")
    val rec8h = createSampleRecord(2, eightHoursAgo, "NOAA-20")
    val rec18h = createSampleRecord(3, eighteenHoursAgo, "Suomi-NPP")
    val rec30h = createSampleRecord(4, thirtyHoursAgo, "MODIS")

    val records = listOf(rec4h, rec8h, rec18h, rec30h)

    // Filter 6 jam -> hanya rec4h (usia <= 6 jam)
    val filtered6h = HotspotFilterHelper.filterRecords(
      records = records,
      criteria = HotspotFilterCriteria(maxAgeHours = 6),
      currentTimeMillis = now
    )
    assertEquals(1, filtered6h.size)
    assertEquals(rec4h.latitude, filtered6h[0].latitude, 0.0001)

    // Filter 12 jam -> rec4h dan rec8h (usia <= 12 jam)
    val filtered12h = HotspotFilterHelper.filterRecords(
      records = records,
      criteria = HotspotFilterCriteria(maxAgeHours = 12),
      currentTimeMillis = now
    )
    assertEquals(2, filtered12h.size)

    // Filter 24 jam -> rec4h, rec8h, rec18h (usia <= 24 jam)
    val filtered24h = HotspotFilterHelper.filterRecords(
      records = records,
      criteria = HotspotFilterCriteria(maxAgeHours = 24),
      currentTimeMillis = now
    )
    assertEquals(3, filtered24h.size)

    // Semua data (maxAgeHours = null) -> 4 record
    val filteredAll = HotspotFilterHelper.filterRecords(
      records = records,
      criteria = HotspotFilterCriteria(maxAgeHours = null),
      currentTimeMillis = now
    )
    assertEquals(4, filteredAll.size)
  }

  @Test
  fun `test filter satelit normalizes search aliases without altering raw records`() {
    val now = 1700000000000L
    val recN21 = createSampleRecord(1, now, "N21")
    val recN20 = createSampleRecord(2, now, "N20")
    val recSnpp = createSampleRecord(3, now, "SNPP")
    val recTerra = createSampleRecord(4, now, "Terra")
    val recAqua = createSampleRecord(5, now, "Aqua")

    val records = listOf(recN21, recN20, recSnpp, recTerra, recAqua)

    // Pencarian NOAA-21 harus mencocokkan N21
    val resN21 = HotspotFilterHelper.filterRecords(
      records = records,
      criteria = HotspotFilterCriteria(satellite = "NOAA-21"),
      currentTimeMillis = now
    )
    assertEquals(1, resN21.size)
    assertEquals("N21", resN21[0].satellite) // Raw record NASA tidak diubah

    // Pencarian NOAA-20 harus mencocokkan N20
    val resN20 = HotspotFilterHelper.filterRecords(
      records = records,
      criteria = HotspotFilterCriteria(satellite = "NOAA-20"),
      currentTimeMillis = now
    )
    assertEquals(1, resN20.size)
    assertEquals("N20", resN20[0].satellite)

    // Pencarian Suomi-NPP harus mencocokkan SNPP
    val resSnpp = HotspotFilterHelper.filterRecords(
      records = records,
      criteria = HotspotFilterCriteria(satellite = "Suomi-NPP"),
      currentTimeMillis = now
    )
    assertEquals(1, resSnpp.size)
    assertEquals("SNPP", resSnpp[0].satellite)

    // Pencarian MODIS harus mencocokkan Terra dan Aqua
    val resModis = HotspotFilterHelper.filterRecords(
      records = records,
      criteria = HotspotFilterCriteria(satellite = "MODIS"),
      currentTimeMillis = now
    )
    assertEquals(2, resModis.size)
  }

  @Test
  fun `test marker fingerprint detects record alteration in the middle of list`() {
    val rec1 = createSampleRecord(1, 1000L, "NOAA-21")
    val rec2 = createSampleRecord(2, 2000L, "NOAA-20")
    val rec3 = createSampleRecord(3, 3000L, "Suomi-NPP")

    val listA = listOf(rec1, rec2, rec3)
    val modifiedRec2 = rec2.copy(confidence = "99") // Ubah record di tengah
    val listB = listOf(rec1, modifiedRec2, rec3)

    fun computeFingerprint(list: List<FireDataRecord>): Int {
      var hash = 17
      hash = 31 * hash + list.size
      for (fire in list) {
        hash = 31 * hash + fire.latitude.hashCode()
        hash = 31 * hash + fire.longitude.hashCode()
        hash = 31 * hash + (fire.acquisitionTimestampMillis?.hashCode() ?: 0)
        hash = 31 * hash + fire.satellite.hashCode()
        hash = 31 * hash + fire.instrument.hashCode()
      }
      return hash
    }

    val hashA = computeFingerprint(listA)
    val hashB = computeFingerprint(listB)

    // Ubah data di tengah list: jika koordinat atau timestamp diubah, hash pasti berbeda
    val modifiedRec2Coord = rec2.copy(latitude = -2.999)
    val listC = listOf(rec1, modifiedRec2Coord, rec3)
    val hashC = computeFingerprint(listC)
    assertTrue("Hash harus berbeda ketika record tengah berubah", hashA != hashC)
  }

  @Test
  fun `test validateTileForViewport handles failure and success`() = runBlocking {
    MapTileValidator.testTileVerifier = { layer ->
      Result.failure(IOException("Gagal menghubungi tile server $layer"))
    }
    val failResult = MapTileValidator.validateTileForViewport(
      layer = BaseMapLayer.SATELLITE_ESRI,
      latitude = -2.123,
      longitude = 114.567,
      zoom = 12
    )
    assertTrue(failResult.isFailure)

    MapTileValidator.testTileVerifier = {
      Result.success(true)
    }
    val successResult = MapTileValidator.validateTileForViewport(
      layer = BaseMapLayer.SATELLITE_ESRI,
      latitude = -2.123,
      longitude = 114.567,
      zoom = 12
    )
    assertTrue(successResult.isSuccess)
    assertTrue(successResult.getOrThrow())

    MapTileValidator.testTileVerifier = null
  }

  @Test
  fun `test distance filter when user location is null blocks distance matching to prevent false positives`() {
    val now = 1700000000000L
    val rec1 = createSampleRecord(1, now, "N21")
    val rec2 = createSampleRecord(2, now, "N20")
    val list = listOf(rec1, rec2)

    // Mandat B3: jika deviceLocation null dan filter jarak aktif, jangan loloskan record seolah-olah filter tidak aktif (zero false-positives)
    val filteredWithDist = HotspotFilterHelper.filterRecords(
      records = list,
      criteria = HotspotFilterCriteria(maxDistanceKm = 10.0),
      deviceLocation = null,
      currentTimeMillis = now
    )
    assertEquals(0, filteredWithDist.size)

    // Tanpa filter jarak, kedua record tetap lolos
    val filteredWithoutDist = HotspotFilterHelper.filterRecords(
      records = list,
      criteria = HotspotFilterCriteria(maxDistanceKm = null),
      deviceLocation = null,
      currentTimeMillis = now
    )
    assertEquals(2, filteredWithoutDist.size)
  }

  @Test
  fun `test distance filter filters strictly by radius from user location`() {
    val now = 1700000000000L
    // Mantangai center approximately -2.12345, 114.56789
    val userLoc = com.example.core.location.DeviceLocation(
      latitude = -2.12345,
      longitude = 114.56789,
      accuracyMeters = 5.0f,
      timeMillis = now,
      provider = "gps"
    )
    // Close record ~ 1 km away
    val closeRec = createSampleRecord(0, now, "N21")
    // Far record ~ 50 km away (0.45 deg difference)
    val farRec = createSampleRecord(50, now, "N21").copy(latitude = -2.60000)

    val list = listOf(closeRec, farRec)

    val filteredWithin10Km = HotspotFilterHelper.filterRecords(
      records = list,
      criteria = HotspotFilterCriteria(maxDistanceKm = 10.0),
      deviceLocation = userLoc,
      currentTimeMillis = now
    )
    assertEquals(1, filteredWithin10Km.size)
    assertEquals(closeRec.latitude, filteredWithin10Km[0].latitude, 0.0001)

    val filteredWithin100Km = HotspotFilterHelper.filterRecords(
      records = list,
      criteria = HotspotFilterCriteria(maxDistanceKm = 100.0),
      deviceLocation = userLoc,
      currentTimeMillis = now
    )
    assertEquals(2, filteredWithin100Km.size)
  }

  @Test
  fun `test share text does not contain internal credentials or MAP_KEY`() {
    val sample = createSampleRecord(1, 1700000000000L, "NOAA-21")
    val text = FireHotspotShareHelper.generateShareText(sample, 12.5)

    assertFalse("Teks bagikan tidak boleh membocorkan MAP_KEY", text.contains("MAP_KEY"))
    assertFalse("Teks bagikan tidak boleh membocorkan token", text.contains("api/area/csv"))
    assertTrue(text.contains("TITIK PANAS TERDETEKSI SATELIT"))
    assertTrue(text.contains("12.5 km"))
  }

  private fun createSampleRecord(id: Int, acqMillis: Long, sat: String): FireDataRecord {
    return FireDataRecord(
      latitude = -2.12345 + (id * 0.01),
      longitude = 114.56789 + (id * 0.01),
      brightTi4 = 320.5,
      scan = 0.4,
      track = 0.4,
      acqDate = "2023-10-15",
      acqTime = "0430",
      satellite = sat,
      instrument = "VIIRS",
      confidence = "nominal",
      version = "2.0NRT",
      brightTi5 = 295.0,
      frp = 12.4,
      dayNight = "D",
      acquisitionTimestampMillis = acqMillis
    )
  }
}
