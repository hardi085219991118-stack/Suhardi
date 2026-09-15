package com.example.core.map

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * Arsitektur Provider Layer Peta Sesuai Prioritas 5 & Bagian 11:
 * - BASE MAP: OpenStreetMap (MAPNIK)
 * - SATELLITE: Provider imagery terkonfigurasi (Esri World Imagery / USGS)
 * - FIRE OVERLAY: Marker titik api NASA FIRMS
 *
 * Mencegah manipulasi: Tidak menggunakan screenshot atau background satelit tiruan.
 */
enum class BaseMapLayer(val displayName: String, val shortName: String, val providerDescription: String) {
  SATELLITE_ESRI(
    displayName = "Citra Satelit (Esri World Imagery)",
    shortName = "Citra Satelit",
    providerDescription = "Esri ArcGIS World Imagery Tile Service — Citra satelit resolusi tinggi."
  ),
  OPEN_STREET_MAP(
    displayName = "Peta Standar (OpenStreetMap)",
    shortName = "Peta Jalan",
    providerDescription = "OpenStreetMap Standard Tiles (Mapnik) — Proyeksi native & open data."
  ),
  SATELLITE_USGS(
    displayName = "Citra Satelit (USGS The National Map)",
    shortName = "USGS Satelit",
    providerDescription = "USGS Orthoimagery Public Domain Tiles."
  )
}

/**
 * Esri World Imagery Tile Source kustom untuk osmdroid.
 * Menggunakan format ArcGIS REST: baseUrl + level(zoom) + "/" + row(y) + "/" + column(x)
 * Tanpa ekstensi file karena endpoint ArcGIS REST tidak menerima ekstensi .jpg.
 * Mendukung ARCGIS_API_KEY jika dikonfigurasi.
 */
class EsriWorldImageryTileSource : OnlineTileSourceBase(
  "EsriWorldImagery",
  0,
  18,
  256,
  "",
  arrayOf(
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
    "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"
  )
) {
  override fun getTileURLString(pMapTileIndex: Long): String {
    val zoom = MapTileIndex.getZoom(pMapTileIndex)
    val col = MapTileIndex.getX(pMapTileIndex)
    val row = MapTileIndex.getY(pMapTileIndex)
    val rawUrl = "$baseUrl$zoom/$row/$col"
    return ArcGisConfig.appendTokenIfAvailable(rawUrl)
  }
}

object MapTileProviderFactory {

  val ESRI_WORLD_IMAGERY: ITileSource = EsriWorldImageryTileSource()

  fun getTileSource(layer: BaseMapLayer): ITileSource {
    return when (layer) {
      BaseMapLayer.SATELLITE_ESRI -> ESRI_WORLD_IMAGERY
      BaseMapLayer.OPEN_STREET_MAP -> TileSourceFactory.MAPNIK
      BaseMapLayer.SATELLITE_USGS -> TileSourceFactory.USGS_SAT
    }
  }
}

/**
 * Data kelas hasil validasi berulang dengan fallback otomatis.
 */
data class LayerValidationOutcome(
  val effectiveLayer: BaseMapLayer,
  val isFallback: Boolean,
  val checkResult: TileCheckResult
)

/**
 * Validator ketersediaan tile peta satelit nyata.
 * MAP_READY TIDAK BOLEH DITETAPKAN HANYA KARENA MapView BERHASIL DIBUAT.
 * MAP_READY HANYA SETELAH TILE NYATA BERHASIL DIMUAT.
 *
 * Validasi viewport:
 * Menguji tile nyata yang sesuai dengan posisi kamera dan layer aktif,
 * bukan satu tile sampel tetap yang tidak berhubungan dengan area kerja.
 */
object MapTileValidator {
  // Test hook untuk regression test tanpa hitting network
  var testTileVerifier: (suspend (BaseMapLayer) -> Result<Boolean>)? = null

  /**
   * Menghitung indeks tile XYZ Web Mercator berdasarkan koordinat lintang, bujur, dan tingkat zoom.
   */
  fun calculateTileIndex(latitude: Double, longitude: Double, zoom: Int): Triple<Int, Int, Int> {
    val z = zoom.coerceIn(0, 19)
    val n = 1 shl z
    val x = (((longitude + 180.0) / 360.0) * n).toInt().coerceIn(0, n - 1)
    val latRad = Math.toRadians(latitude.coerceIn(-85.05112878, 85.05112878))
    val y = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    return Triple(x, y, z)
  }

  fun getTileUrl(layer: BaseMapLayer, x: Int, y: Int, z: Int): String {
    val raw = when (layer) {
      BaseMapLayer.SATELLITE_ESRI ->
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
      BaseMapLayer.OPEN_STREET_MAP ->
        "https://tile.openstreetmap.org/$z/$x/$y.png"
      BaseMapLayer.SATELLITE_USGS ->
        "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/$z/$y/$x"
    }
    return if (layer == BaseMapLayer.SATELLITE_ESRI) {
      ArcGisConfig.appendTokenIfAvailable(raw)
    } else {
      raw
    }
  }

  suspend fun validateTileForViewport(
    layer: BaseMapLayer = BaseMapLayer.SATELLITE_ESRI,
    latitude: Double = -2.15,
    longitude: Double = 114.65,
    zoom: Int = 10
  ): Result<Boolean> {
    testTileVerifier?.let { return it(layer) }

    val checkResult = TileHealthChecker.checkTileHealth(
      layer = layer,
      latitude = latitude,
      longitude = longitude,
      zoom = zoom
    )

    return if (checkResult.isValid) {
      Result.success(true)
    } else {
      Result.failure(java.io.IOException(checkResult.errorMessage ?: checkResult.failureReason?.userFriendlyMessage ?: "Pemeriksaan tile gagal"))
    }
  }

  suspend fun validateTileSource(
    layer: BaseMapLayer = BaseMapLayer.SATELLITE_ESRI
  ): Result<Boolean> {
    return validateTileForViewport(layer = layer, latitude = -2.15, longitude = 114.65, zoom = 10)
  }

  /**
   * Section 5 & 12: Pengujian tile dengan Retry Backoff (MAX RETRY = 3; 1s, 2s, 4s)
   * serta Fallback Otomatis ke OpenStreetMap jika Esri World Imagery gagal.
   */
  suspend fun validateWithRetryAndFallback(
    desiredLayer: BaseMapLayer = BaseMapLayer.SATELLITE_ESRI,
    latitude: Double = -2.15,
    longitude: Double = 114.65,
    zoom: Int = 10,
    onRetryAttempt: ((attempt: Int, delayMs: Long) -> Unit)? = null
  ): LayerValidationOutcome {
    testTileVerifier?.let { verifier ->
      val res = verifier(desiredLayer)
      return if (res.isSuccess) {
        LayerValidationOutcome(
          effectiveLayer = desiredLayer,
          isFallback = false,
          checkResult = TileCheckResult(
            isValid = true,
            provider = desiredLayer.displayName,
            tileUrl = "",
            zoom = zoom,
            x = 0,
            y = 0
          )
        )
      } else {
        LayerValidationOutcome(
          effectiveLayer = BaseMapLayer.OPEN_STREET_MAP,
          isFallback = true,
          checkResult = TileCheckResult(
            isValid = false,
            provider = desiredLayer.displayName,
            tileUrl = "",
            zoom = zoom,
            x = 0,
            y = 0,
            errorMessage = res.exceptionOrNull()?.message
          )
        )
      }
    }

    if (desiredLayer != BaseMapLayer.SATELLITE_ESRI) {
      val res = TileHealthChecker.checkTileHealth(desiredLayer, latitude, longitude, zoom)
      if (!res.isValid) {
        // Fallback sesuai hierarki: USGS -> Esri World Imagery -> OSM
        val fallbackLayer = if (desiredLayer == BaseMapLayer.SATELLITE_USGS) {
          BaseMapLayer.SATELLITE_ESRI
        } else {
          BaseMapLayer.SATELLITE_ESRI
        }
        return LayerValidationOutcome(
          effectiveLayer = fallbackLayer,
          isFallback = true,
          checkResult = res.copy(
            isFallback = true,
            fallbackProvider = fallbackLayer.displayName
          )
        )
      }
      return LayerValidationOutcome(
        effectiveLayer = desiredLayer,
        isFallback = false,
        checkResult = res
      )
    }

    // Urutan endpoint satelit:
    // PRIORITAS 1: server.arcgisonline.com
    // PRIORITAS 2: services.arcgisonline.com (secondary endpoint)
    val (x, y, z) = calculateTileIndex(latitude, longitude, zoom)
    val endpoints = listOf(
      "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x",
      "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
    )

    val delaysMs = listOf(0L, 1000L, 2000L, 4000L)
    var lastResult: TileCheckResult? = null

    for (attempt in 1..3) {
      val delay = delaysMs.getOrElse(attempt) { 1000L }
      if (attempt > 1) {
        onRetryAttempt?.invoke(attempt, delay)
        kotlinx.coroutines.delay(delay)
      }

      // Coba endpoint prioritas 1 lalu prioritas 2
      for (endpoint in endpoints) {
        val check = TileHealthChecker.checkTileHealth(
          layer = BaseMapLayer.SATELLITE_ESRI,
          latitude = latitude,
          longitude = longitude,
          zoom = zoom,
          urlOverride = endpoint
        )
        if (check.isValid) {
          return LayerValidationOutcome(
            effectiveLayer = BaseMapLayer.SATELLITE_ESRI,
            isFallback = false,
            checkResult = check
          )
        }
        lastResult = check
      }
    }

    // GAGAL SETELAH 3 RETRY: Aktifkan fallback otomatis ke OpenStreetMap (Section 5)
    val fallbackResult = lastResult?.copy(
      isFallback = true,
      fallbackProvider = BaseMapLayer.OPEN_STREET_MAP.displayName
    ) ?: TileCheckResult(
      isValid = false,
      provider = BaseMapLayer.SATELLITE_ESRI.displayName,
      tileUrl = "",
      zoom = z,
      x = x,
      y = y,
      isFallback = true,
      fallbackProvider = BaseMapLayer.OPEN_STREET_MAP.displayName,
      failureReason = TileFailureReason.UNKNOWN_ERROR,
      errorMessage = "Semua percobaan citra satelit gagal setelah 3 kali retry."
    )

    com.example.core.logging.AppLogger.recordEvent(
      "SATELLITE_FALLBACK: provider=Esri World Imagery fallback=OpenStreetMap reason=${fallbackResult.failureReason}"
    )

    return LayerValidationOutcome(
      effectiveLayer = BaseMapLayer.OPEN_STREET_MAP,
      isFallback = true,
      checkResult = fallbackResult
    )
  }
}


