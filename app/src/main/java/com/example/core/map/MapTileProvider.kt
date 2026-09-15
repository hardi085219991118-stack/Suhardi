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

