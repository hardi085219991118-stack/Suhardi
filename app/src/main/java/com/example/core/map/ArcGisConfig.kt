package com.example.core.map

/**
 * Konfigurasi resmi ArcGIS API Key untuk layanan citra satelit Esri World Imagery.
 *
 * Kebijakan Kejujuran & Zero-Dummy:
 * - Tidak menggunakan token palsu atau dummy token.
 * - Tidak menampilkan "API AKTIF" jika key belum valid atau belum dikonfigurasi.
 * - Jika ARCGIS_API_KEY belum dikonfigurasi, sistem menggunakan endpoint public World Imagery
 *   resmi dari Esri (server.arcgisonline.com / services.arcgisonline.com) yang memang dapat
 *   diakses secara terbuka untuk penayangan tile peta satelit standar.
 */
object ArcGisConfig {

  @Volatile
  private var runtimeApiKey: String? = null

  /**
   * Mengambil API key ArcGIS jika ada dari BuildConfig atau konfigurasi runtime.
   */
  fun getApiKey(): String? {
    runtimeApiKey?.let { if (it.isNotBlank()) return it.trim() }

    return try {
      val field = com.example.BuildConfig::class.java.getField("ARCGIS_API_KEY")
      val key = field.get(null) as? String
      key?.trim()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
      null
    }
  }

  /**
   * Menetapkan API key pada level runtime jika pengguna mengaturnya via UI atau preferences.
   */
  fun setRuntimeApiKey(key: String?) {
    runtimeApiKey = key?.trim()?.takeIf { it.isNotBlank() }
  }

  /**
   * Memeriksa apakah ARCGIS_API_KEY terkonfigurasi.
   */
  fun isConfigured(): Boolean = !getApiKey().isNullOrBlank()

  /**
   * Status konfigurasi yang transparan dan jujur kepada pengguna.
   */
  fun getStatusDescription(): String {
    val key = getApiKey()
    return if (!key.isNullOrBlank()) {
      "ARCGIS_API_KEY: TERKONFIGURASI (${key.take(4)}****)"
    } else {
      "ARCGIS_API_KEY: TIDAK DIKONFIGURASI (PUBLIC REST TIER)"
    }
  }

  /**
   * Menambahkan parameter token jika ARCGIS_API_KEY tersedia.
   */
  fun appendTokenIfAvailable(url: String): String {
    val key = getApiKey() ?: return url
    return if (url.contains("?")) {
      "$url&token=$key"
    } else {
      "$url?token=$key"
    }
  }
}
