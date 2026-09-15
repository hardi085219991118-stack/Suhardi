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
 */
class EsriWorldImageryTileSource : OnlineTileSourceBase(
  "EsriWorldImagery",
  0,
  19,
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
    return "$baseUrl$zoom/$row/$col"
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
    return when (layer) {
      BaseMapLayer.SATELLITE_ESRI ->
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
      BaseMapLayer.OPEN_STREET_MAP ->
        "https://tile.openstreetmap.org/$z/$x/$y.png"
      BaseMapLayer.SATELLITE_USGS ->
        "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/$z/$y/$x"
    }
  }

  suspend fun validateTileForViewport(
    layer: BaseMapLayer = BaseMapLayer.SATELLITE_ESRI,
    latitude: Double = -2.15,
    longitude: Double = 114.65,
    zoom: Int = 10
  ): Result<Boolean> {
    testTileVerifier?.let { return it(layer) }

    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      val (x, y, z) = calculateTileIndex(latitude, longitude, zoom)
      val tileUrl = getTileUrl(layer, x, y, z)
      try {
        val client = okhttp3.OkHttpClient.Builder()
          .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
          .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
          .build()

        val request = okhttp3.Request.Builder()
          .url(tileUrl)
          .header("User-Agent", "HardiMantangaiFireNow/1.0 (Android; ZeroDummy)")
          .build()

        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            Result.success(true)
          } else {
            Result.failure(java.io.IOException("HTTP ${response.code} saat memuat tile peta ($tileUrl)"))
          }
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }

  suspend fun validateTileSource(
    layer: BaseMapLayer = BaseMapLayer.SATELLITE_ESRI
  ): Result<Boolean> {
    return validateTileForViewport(layer = layer, latitude = -2.15, longitude = 114.65, zoom = 10)
  }
}


