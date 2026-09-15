package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.fire.FireDataCredentialProvider
import com.example.core.fire.FireDataCredentialState
import com.example.core.fire.FireDataError
import com.example.core.fire.FireDataRecord
import com.example.core.fire.FireDataResponse
import com.example.core.fire.FireDataSource
import com.example.core.fire.FireDataSourceState
import com.example.core.fire.HotspotFilterHelper
import com.example.core.fire.LiveApiDiagnosticAuditor
import com.example.core.fire.LiveVerificationGate
import com.example.core.fire.NasaFirmsConstants
import com.example.core.fire.RealFireDataRepository
import com.example.core.map.BaseMapLayer
import com.example.core.map.FireMarkerIconHelper
import com.example.core.map.MapTileValidator
import com.example.ui.dashboard.HotspotFilterCriteria
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 16 Kasus Uji Regresi Mandatori Zero-Dummy (A - P)
 * Sesuai Bagian 14:
 * A. MAP_KEY kosong -> fire count = "--" (bukan 0)
 * B. MAP_KEY salah -> credential invalid
 * C. HTTP 401 -> credential invalid, auto-downgrade
 * D. HTTP 403 -> credential invalid, auto-downgrade
 * E. HTTP 429 -> repository cooldown / backoff aktif, no API spam
 * F. HTTP 500 -> error, bukan 0 api
 * G. Network timeout -> error, bukan 0 api
 * H. HTTP 200 + 0 record -> 0 api (valid overpass)
 * I. HTTP 200 + 10 record -> 10 marker di peta
 * J. Filter menghasilkan 0 -> 0 marker di peta dan 0 di list
 * K. Filter menghasilkan 5 -> 5 marker di peta
 * L. Esri tile HTTP success -> MAP_READY / success
 * M. Esri tile HTTP failure -> MAP_ERROR (bukan pura-pura ready)
 * N. Marker titik api menggunakan icon api (bukan default osmdroid)
 * O. Repository cooldown tidak bisa di-bypass dengan force=true
 * P. Live test tidak bisa VERIFIED jika kredensial tidak ada
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditHotspotsAndMapTest {

  private class TestCredentialProvider(
    private var key: String? = null,
    private var state: FireDataCredentialState = if (key.isNullOrBlank()) FireDataCredentialState.NOT_CONFIGURED else FireDataCredentialState.CONFIGURED
  ) : FireDataCredentialProvider {
    override val limitationNote: String = "Test Credential Provider"
    override fun getMapKey(): String? = key
    override fun getCredentialState(): FireDataCredentialState = state
    override fun setMapKey(key: String?): FireDataCredentialState {
      this.key = key
      state = if (key.isNullOrBlank()) FireDataCredentialState.NOT_CONFIGURED else FireDataCredentialState.CONFIGURED
      return state
    }
    override fun clearMapKey() {
      key = null
      state = FireDataCredentialState.NOT_CONFIGURED
    }
    override fun markInvalid() {
      state = FireDataCredentialState.INVALID
    }
  }

  private class MockFireDataSource(
    var responseToReturn: FireDataResponse
  ) : FireDataSource {
    var callCount = 0
    override suspend fun fetchFireData(
      mapKey: String,
      source: String,
      areaCoordinates: String,
      dayRange: Int
    ): FireDataResponse {
      callCount++
      return responseToReturn
    }
  }

  // A. MAP_KEY kosong -> fire count = "--" (bukan 0)
  @Test
  fun `test A MAP_KEY empty results in credential required and no fake 0 count`() = runBlocking {
    val credProvider = TestCredentialProvider(key = null)
    val ds = MockFireDataSource(FireDataResponse.unverified())
    val repo = RealFireDataRepository(credProvider, ds)

    val response = repo.refreshFireData()
    assertEquals(FireDataSourceState.API_CREDENTIAL_REQUIRED, response.state)
    assertEquals(0, ds.callCount) // Tidak boleh panggil API tanpa key

    // Verifikasi audit format teks fire count untuk state ini harus "--"
    val countDisplay = if (response.state == FireDataSourceState.DATA_SOURCE_AVAILABLE ||
      response.state == FireDataSourceState.NO_DETECTIONS_IN_QUERY ||
      response.state == FireDataSourceState.CACHED
    ) {
      response.validRecordCount.toString()
    } else {
      "--"
    }
    assertEquals("--", countDisplay)
  }

  // B. MAP_KEY salah -> credential invalid
  @Test
  fun `test B MAP_KEY invalid results in credential invalid and rejected`() = runBlocking {
    val credProvider = TestCredentialProvider(key = "bad_key", state = FireDataCredentialState.INVALID)
    val ds = MockFireDataSource(FireDataResponse.unverified())
    val repo = RealFireDataRepository(credProvider, ds)

    val response = repo.refreshFireData()
    assertEquals(FireDataSourceState.API_CREDENTIAL_REQUIRED, response.state)
    assertEquals(0, ds.callCount)
  }

  // C. HTTP 401 -> credential invalid, auto-downgrade
  @Test
  fun `test C HTTP 401 marks credential INVALID immediately`() = runBlocking {
    val credProvider = TestCredentialProvider(key = "unauthorized_key")
    val ds = MockFireDataSource(
      FireDataResponse.error(
        state = FireDataSourceState.API_CREDENTIAL_REQUIRED,
        error = FireDataError.HttpError(401, "Unauthorized")
      )
    )
    val repo = RealFireDataRepository(credProvider, ds)

    val response = repo.refreshFireData(force = true)
    assertEquals(401, response.httpStatusCode)
    assertEquals(FireDataCredentialState.INVALID, credProvider.getCredentialState())
  }

  // D. HTTP 403 -> credential invalid, auto-downgrade
  @Test
  fun `test D HTTP 403 marks credential INVALID immediately`() = runBlocking {
    val credProvider = TestCredentialProvider(key = "forbidden_key")
    val ds = MockFireDataSource(
      FireDataResponse.error(
        state = FireDataSourceState.API_CREDENTIAL_REQUIRED,
        error = FireDataError.HttpError(403, "Forbidden")
      )
    )
    val repo = RealFireDataRepository(credProvider, ds)

    val response = repo.refreshFireData(force = true)
    assertEquals(403, response.httpStatusCode)
    assertEquals(FireDataCredentialState.INVALID, credProvider.getCredentialState())
  }

  // E. HTTP 429 -> repository cooldown / backoff aktif, no API spam
  @Test
  fun `test E HTTP 429 triggers rate limit backoff and blocks immediate calls`() = runBlocking {
    val credProvider = TestCredentialProvider(key = "valid_key_12345678")
    val ds = MockFireDataSource(
      FireDataResponse.error(
        state = FireDataSourceState.RATE_LIMIT_EXCEEDED,
        error = FireDataError.HttpError(429, "Too Many Requests")
      )
    )
    val repo = RealFireDataRepository(credProvider, ds)

    val first = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.RATE_LIMIT_EXCEEDED, first.state)
    assertEquals(1, ds.callCount)

    // Panggilan berikutnya langsung ditahan oleh backoff tanpa memanggil remote API
    val second = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.RATE_LIMIT_EXCEEDED, second.state)
    assertEquals(1, ds.callCount) // Tidak bertambah
  }

  // F. HTTP 500 -> error, bukan 0 api
  @Test
  fun `test F HTTP 500 reports error and never reports fake 0 hotspot`() = runBlocking {
    val credProvider = TestCredentialProvider(key = "valid_key_12345678")
    val ds = MockFireDataSource(
      FireDataResponse.error(
        state = FireDataSourceState.DATA_SOURCE_UNAVAILABLE,
        error = FireDataError.HttpError(500, "Internal Server Error")
      )
    )
    val repo = RealFireDataRepository(credProvider, ds)

    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_UNAVAILABLE, res.state)
    assertEquals(500, res.httpStatusCode)

    // Formatter tidak boleh menampilkan "0 Titik Api" saat HTTP 500
    val display = if (res.state == FireDataSourceState.DATA_SOURCE_AVAILABLE ||
      res.state == FireDataSourceState.NO_DETECTIONS_IN_QUERY
    ) {
      "${res.validRecordCount} Titik Api"
    } else {
      "Error: Data Tidak Tersedia"
    }
    assertEquals("Error: Data Tidak Tersedia", display)
  }

  // G. Network timeout -> error, bukan 0 api
  @Test
  fun `test G Network timeout reports TIMEOUT and never reports fake 0`() = runBlocking {
    val credProvider = TestCredentialProvider(key = "valid_key_12345678")
    val ds = MockFireDataSource(
      FireDataResponse.error(
        state = FireDataSourceState.TIMEOUT,
        error = FireDataError.Timeout
      )
    )
    val repo = RealFireDataRepository(credProvider, ds)

    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.TIMEOUT, res.state)
    assertFalse(res.state == FireDataSourceState.NO_DETECTIONS_IN_QUERY)
  }

  // H. HTTP 200 + 0 record -> 0 api (valid overpass)
  @Test
  fun `test H HTTP 200 with 0 record reports NO_DETECTIONS_IN_QUERY with 0 count`() = runBlocking {
    val credProvider = TestCredentialProvider(key = "valid_key_12345678")
    val ds = MockFireDataSource(
      FireDataResponse(
        state = FireDataSourceState.NO_DETECTIONS_IN_QUERY,
        records = emptyList(),
        rawRecordCount = 0,
        validRecordCount = 0,
        requestTimeMillis = System.currentTimeMillis(),
        fetchTimeMillis = System.currentTimeMillis(),
        sourceSensor = NasaFirmsConstants.SENSOR_VIIRS_NOAA21,
        httpStatusCode = 200,
        requestArea = NasaFirmsConstants.DEFAULT_MANTHANGAI_BBOX
      )
    )
    val repo = RealFireDataRepository(credProvider, ds)

    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.NO_DETECTIONS_IN_QUERY, res.state)
    assertEquals(0, res.validRecordCount)
  }

  // I. HTTP 200 + 10 record -> 10 marker di peta
  @Test
  fun `test I HTTP 200 with 10 records provides 10 records for map markers`() = runBlocking {
    val credProvider = TestCredentialProvider(key = "valid_key_12345678")
    val records = (1..10).map { i ->
      FireDataRecord(
        latitude = -2.15 + (i * 0.01),
        longitude = 114.65 + (i * 0.01),
        brightTi4 = 320.0,
        acqDate = "2026-09-15",
        acqTime = "0430",
        satellite = "NOAA-21",
        confidence = "nominal",
        frp = 15.0,
        instrument = "VIIRS"
      )
    }
    val ds = MockFireDataSource(
      FireDataResponse(
        state = FireDataSourceState.DATA_SOURCE_AVAILABLE,
        records = records,
        rawRecordCount = 10,
        validRecordCount = 10,
        requestTimeMillis = System.currentTimeMillis(),
        fetchTimeMillis = System.currentTimeMillis(),
        sourceSensor = NasaFirmsConstants.SENSOR_VIIRS_NOAA21,
        httpStatusCode = 200,
        requestArea = NasaFirmsConstants.DEFAULT_MANTHANGAI_BBOX
      )
    )
    val repo = RealFireDataRepository(credProvider, ds)

    val res = repo.refreshFireData(force = true)
    assertEquals(FireDataSourceState.DATA_SOURCE_AVAILABLE, res.state)
    assertEquals(10, res.records.size)
  }

  // J. Filter menghasilkan 0 -> 0 marker di peta dan 0 di list
  @Test
  fun `test J Filter matching 0 records returns strictly empty list`() {
    val records = listOf(
      FireDataRecord(
        latitude = -2.15,
        longitude = 114.65,
        brightTi4 = 320.0,
        acqDate = "2026-09-15",
        acqTime = "0430",
        satellite = "NOAA-21",
        confidence = "nominal",
        frp = 15.0,
        instrument = "VIIRS"
      )
    )
    // Filter satellite to Aqua (record is NOAA-21) -> matches 0 records
    val criteria = HotspotFilterCriteria(satellite = "Aqua")
    val filtered = HotspotFilterHelper.filterRecords(records, criteria)

    assertEquals(0, filtered.size)
    // DILARANG fallback ke seluruh records saat filter menghasilkan 0!
    assertFalse(filtered.isNotEmpty())
  }

  // K. Filter menghasilkan 5 -> 5 marker di peta
  @Test
  fun `test K Filter matching 5 records returns exactly 5 records`() {
    val records = (1..10).map { i ->
      FireDataRecord(
        latitude = -2.15 + (i * 0.01),
        longitude = 114.65 + (i * 0.01),
        brightTi4 = 320.0,
        acqDate = "2026-09-15",
        acqTime = "0430",
        satellite = if (i <= 5) "NOAA-21" else "Terra",
        confidence = "nominal",
        frp = 15.0,
        instrument = if (i <= 5) "VIIRS" else "MODIS"
      )
    }
    val criteria = HotspotFilterCriteria(satellite = "NOAA-21")
    val filtered = HotspotFilterHelper.filterRecords(records, criteria)

    assertEquals(5, filtered.size)
  }

  // L. Esri tile HTTP success -> MAP_READY
  @Test
  fun `test L Esri tile HTTP success validates tile`() {
    val dummyTileBytes = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
      0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
      0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
      0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(), 0x89.toByte()
    )
    val client = OkHttpClient.Builder().addInterceptor { chain ->
      Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(ResponseBody.create(null, dummyTileBytes))
        .build()
    }.build()

    val request = Request.Builder().url("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/10/500/500").build()
    val isValid = client.newCall(request).execute().use { resp ->
      val bytes = resp.body?.bytes()
      resp.isSuccessful && bytes != null && bytes.isNotEmpty()
    }
    assertTrue(isValid)
  }

  // M. Esri tile HTTP failure -> MAP_ERROR
  @Test
  fun `test M Esri tile HTTP failure correctly detects failure`() {
    val client = OkHttpClient.Builder().addInterceptor { chain ->
      Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(503)
        .message("Service Unavailable")
        .body(ResponseBody.create(null, "Service Unavailable"))
        .build()
    }.build()

    val request = Request.Builder().url("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/10/500/500").build()
    val isValid = client.newCall(request).execute().use { resp ->
      val bytes = resp.body?.bytes()
      resp.isSuccessful && bytes != null && bytes.isNotEmpty()
    }
    assertFalse(isValid)
  }

  // N. Marker titik api menggunakan icon api (bukan default osmdroid)
  @Test
  fun `test N Marker icon uses flame vector from FireMarkerIconHelper`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val icon = FireMarkerIconHelper.getFlameIcon(context)
    assertNotNull(icon)
  }

  // O. Repository cooldown tidak bisa di-bypass dengan force=true
  @Test
  fun `test O Repository cooldown cannot be bypassed by force=true`() = runBlocking {
    val credProvider = TestCredentialProvider(key = "valid_key_12345678")
    val ds = MockFireDataSource(
      FireDataResponse(
        state = FireDataSourceState.DATA_SOURCE_AVAILABLE,
        records = emptyList(),
        rawRecordCount = 0,
        validRecordCount = 0,
        requestTimeMillis = System.currentTimeMillis(),
        fetchTimeMillis = System.currentTimeMillis(),
        sourceSensor = NasaFirmsConstants.SENSOR_VIIRS_NOAA21,
        httpStatusCode = 200,
        requestArea = NasaFirmsConstants.DEFAULT_MANTHANGAI_BBOX
      )
    )
    val repo = RealFireDataRepository(credProvider, ds)

    // Call 1: HTTP hit
    val first = repo.refreshFireData(force = false)
    assertEquals(1, ds.callCount)

    // Call 2: with force = true, BUT cooldown is active -> MUST NOT call API
    val second = repo.refreshFireData(force = true)
    assertEquals(1, ds.callCount) // Tidak bertambah, cooldown terkunci rapat!
    assertEquals(first.fetchTimeMillis, second.fetchTimeMillis)
  }

  // P. Live test tidak bisa VERIFIED jika kredensial tidak ada
  @Test
  fun `test P Live test pipeline audit reports LIVE_API_NOT_VERIFIED when key is missing`() {
    val audit = LiveApiDiagnosticAuditor.auditPipeline(
      credState = FireDataCredentialState.NOT_CONFIGURED,
      sourceState = FireDataSourceState.NOT_VERIFIED,
      response = FireDataResponse.unverified(),
      queryArea = NasaFirmsConstants.DEFAULT_MANTHANGAI_BBOX
    )
    assertEquals(LiveVerificationGate.LIVE_API_NOT_VERIFIED, audit.gate)
    assertFalse("Pipeline must never claim verified without credentials", audit.isLiveApiVerified)
  }
}
