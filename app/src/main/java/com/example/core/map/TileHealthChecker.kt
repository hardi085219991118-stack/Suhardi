package com.example.core.map

import android.util.Log
import com.example.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Diagnostic Failure Reasons untuk pemeriksaan kesehatan tile satelit.
 * Mengikuti arahan Bagian 16: Jangan semua error diterjemahkan menjadi "Periksa koneksi internet".
 */
enum class TileFailureReason(val userFriendlyMessage: String) {
  DNS_LOOKUP_FAILED("Layanan citra satelit tidak dapat dihubungi (DNS bermasalah)."),
  CONNECTION_TIMEOUT("Koneksi ke layanan citra satelit terlalu lama (Timeout)."),
  SSL_HANDSHAKE_ERROR("Koneksi HTTPS aman ke layanan citra satelit gagal."),
  UNAUTHORIZED_401("Konfigurasi akses citra satelit tidak valid (401 Unauthorized)."),
  FORBIDDEN_403("Akses layanan citra satelit ditolak (403 Forbidden)."),
  NOT_FOUND_404("Tile citra satelit tidak ditemukan pada koordinat ini (404 Not Found)."),
  RATE_LIMITED_429("Batas permintaan layanan citra satelit terlampaui (429 Rate Limited)."),
  SERVER_ERROR_5XX("Server citra satelit sedang mengalami gangguan (Server 5xx)."),
  HTTP_ERROR_OTHER("Layanan citra satelit merespons status tidak terduga."),
  INVALID_CONTENT_TYPE("Respons bukan merupakan format gambar citra satelit valid."),
  EMPTY_OR_TRUNCATED_PAYLOAD("Ukuran tile citra satelit tidak mencukupi atau kosong."),
  CORRUPT_IMAGE_DATA("Data gambar citra satelit korup atau format tidak sesuai."),
  UNKNOWN_ERROR("Terjadi kendala saat memuat tile citra satelit.")
}

/**
 * Hasil komprehensif uji kesehatan tile peta (Section 4 & Section 15).
 */
data class TileCheckResult(
  val isValid: Boolean,
  val provider: String,
  val tileUrl: String,
  val zoom: Int,
  val x: Int,
  val y: Int,
  val httpCode: Int? = null,
  val contentType: String? = null,
  val payloadSizeBytes: Int = 0,
  val failureReason: TileFailureReason? = null,
  val errorMessage: String? = null,
  val isFallback: Boolean = false,
  val fallbackProvider: String? = null
) {
  fun formatDiagnosticLog(): String {
    val sb = StringBuilder()
    sb.appendLine("[Satellite]")
    sb.appendLine("Provider: $provider")
    sb.appendLine("Zoom: $zoom")
    sb.appendLine("X: $x")
    sb.appendLine("Y: $y")
    sb.appendLine("HTTP: ${httpCode ?: "N/A"}")
    sb.appendLine("Content-Type: ${contentType ?: "N/A"}")
    sb.appendLine("Tile: ${if (isValid) "VALID" else "INVALID"}")
    sb.appendLine("Status: ${if (isValid) "SUCCESS" else "FAILED"}")
    if (isFallback && fallbackProvider != null) {
      sb.appendLine("Fallback: $fallbackProvider")
    }
    if (failureReason != null) {
      sb.appendLine("Reason: ${failureReason.name} (${errorMessage ?: failureReason.userFriendlyMessage})")
    }
    return sb.toString().trimEnd()
  }
}

/**
 * Penguji kesehatan nyata untuk tile peta satelit (TileHealthChecker).
 * Memeriksa DNS, HTTPS, HTTP status (401, 403, 404, 429, 5xx), Content-Type,
 * ukuran payload (>500 byte), dan integritas magic bytes gambar.
 */
object TileHealthChecker {

  private const val TAG = "Satellite"
  private const val MIN_VALID_TILE_SIZE_BYTES = 500

  // OkHttpClient terkonfigurasi dengan timeout wajar untuk jaringan mobile Android
  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .callTimeout(15, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()
  }

  /**
   * Menguji tile nyata untuk viewport dan layer yang ditentukan.
   */
  suspend fun checkTileHealth(
    layer: BaseMapLayer,
    latitude: Double,
    longitude: Double,
    zoom: Int,
    urlOverride: String? = null
  ): TileCheckResult = withContext(Dispatchers.IO) {
    val (x, y, z) = MapTileValidator.calculateTileIndex(latitude, longitude, zoom)
    val rawUrl = urlOverride ?: MapTileValidator.getTileUrl(layer, x, y, z)
    val finalUrl = if (layer == BaseMapLayer.SATELLITE_ESRI) {
      ArcGisConfig.appendTokenIfAvailable(rawUrl)
    } else {
      rawUrl
    }

    val providerName = layer.displayName

    AppLogger.recordEvent("SATELLITE_TILE_REQUEST: provider=$providerName z=$z x=$x y=$y url=$finalUrl")

    // 1. DNS Pre-check
    try {
      val uri = URI(finalUrl)
      val host = uri.host
      if (!host.isNullOrBlank()) {
        InetAddress.getAllByName(host)
      }
    } catch (e: UnknownHostException) {
      val result = TileCheckResult(
        isValid = false,
        provider = providerName,
        tileUrl = finalUrl,
        zoom = z,
        x = x,
        y = y,
        failureReason = TileFailureReason.DNS_LOOKUP_FAILED,
        errorMessage = TileFailureReason.DNS_LOOKUP_FAILED.userFriendlyMessage
      )
      logAndRecordResult(result)
      return@withContext result
    } catch (e: Throwable) {
      // Non-fatal if URI parsing fails; OkHttp will handle network
    }

    // 2. HTTPS Request & Response Validation
    val request = Request.Builder()
      .url(finalUrl)
      .header("User-Agent", "HardiMantangaiFireNow/1.0 (Android; ZeroDummy)")
      .header("Accept", "image/jpeg,image/png,image/*;q=0.8,*/*;q=0.5")
      .build()

    try {
      httpClient.newCall(request).execute().use { response ->
        val httpCode = response.code
        val contentType = response.header("Content-Type")?.lowercase()
        val bodyBytes = response.body?.bytes()
        val bodySize = bodyBytes?.size ?: 0

        // Status code check
        when {
          httpCode == 401 -> {
            val res = TileCheckResult(
              isValid = false,
              provider = providerName,
              tileUrl = finalUrl,
              zoom = z,
              x = x,
              y = y,
              httpCode = httpCode,
              contentType = contentType,
              payloadSizeBytes = bodySize,
              failureReason = TileFailureReason.UNAUTHORIZED_401,
              errorMessage = TileFailureReason.UNAUTHORIZED_401.userFriendlyMessage
            )
            logAndRecordResult(res)
            return@withContext res
          }
          httpCode == 403 -> {
            val res = TileCheckResult(
              isValid = false,
              provider = providerName,
              tileUrl = finalUrl,
              zoom = z,
              x = x,
              y = y,
              httpCode = httpCode,
              contentType = contentType,
              payloadSizeBytes = bodySize,
              failureReason = TileFailureReason.FORBIDDEN_403,
              errorMessage = TileFailureReason.FORBIDDEN_403.userFriendlyMessage
            )
            logAndRecordResult(res)
            return@withContext res
          }
          httpCode == 404 -> {
            val res = TileCheckResult(
              isValid = false,
              provider = providerName,
              tileUrl = finalUrl,
              zoom = z,
              x = x,
              y = y,
              httpCode = httpCode,
              contentType = contentType,
              payloadSizeBytes = bodySize,
              failureReason = TileFailureReason.NOT_FOUND_404,
              errorMessage = TileFailureReason.NOT_FOUND_404.userFriendlyMessage
            )
            logAndRecordResult(res)
            return@withContext res
          }
          httpCode == 429 -> {
            val res = TileCheckResult(
              isValid = false,
              provider = providerName,
              tileUrl = finalUrl,
              zoom = z,
              x = x,
              y = y,
              httpCode = httpCode,
              contentType = contentType,
              payloadSizeBytes = bodySize,
              failureReason = TileFailureReason.RATE_LIMITED_429,
              errorMessage = TileFailureReason.RATE_LIMITED_429.userFriendlyMessage
            )
            logAndRecordResult(res)
            return@withContext res
          }
          httpCode in 500..599 -> {
            val res = TileCheckResult(
              isValid = false,
              provider = providerName,
              tileUrl = finalUrl,
              zoom = z,
              x = x,
              y = y,
              httpCode = httpCode,
              contentType = contentType,
              payloadSizeBytes = bodySize,
              failureReason = TileFailureReason.SERVER_ERROR_5XX,
              errorMessage = "${TileFailureReason.SERVER_ERROR_5XX.userFriendlyMessage} (HTTP $httpCode)"
            )
            logAndRecordResult(res)
            return@withContext res
          }
          !response.isSuccessful -> {
            val res = TileCheckResult(
              isValid = false,
              provider = providerName,
              tileUrl = finalUrl,
              zoom = z,
              x = x,
              y = y,
              httpCode = httpCode,
              contentType = contentType,
              payloadSizeBytes = bodySize,
              failureReason = TileFailureReason.HTTP_ERROR_OTHER,
              errorMessage = "Layanan citra satelit merespons HTTP $httpCode."
            )
            logAndRecordResult(res)
            return@withContext res
          }
        }

        // Content-Type validation
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.contains("octet-stream"))) {
          val res = TileCheckResult(
            isValid = false,
            provider = providerName,
            tileUrl = finalUrl,
            zoom = z,
            x = x,
            y = y,
            httpCode = httpCode,
            contentType = contentType,
            payloadSizeBytes = bodySize,
            failureReason = TileFailureReason.INVALID_CONTENT_TYPE,
            errorMessage = "Format respons bukan gambar citra satelit valid ($contentType)."
          )
          logAndRecordResult(res)
          return@withContext res
        }

        // Body size validation
        if (bodyBytes == null || bodySize < MIN_VALID_TILE_SIZE_BYTES) {
          val res = TileCheckResult(
            isValid = false,
            provider = providerName,
            tileUrl = finalUrl,
            zoom = z,
            x = x,
            y = y,
            httpCode = httpCode,
            contentType = contentType,
            payloadSizeBytes = bodySize,
            failureReason = TileFailureReason.EMPTY_OR_TRUNCATED_PAYLOAD,
            errorMessage = "Data tile citra satelit tidak mencukupi ($bodySize byte)."
          )
          logAndRecordResult(res)
          return@withContext res
        }

        // Magic bytes image format integrity check (JPEG, PNG, WebP)
        val isImageValid = isRecognizedImageHeader(bodyBytes)
        if (!isImageValid) {
          val res = TileCheckResult(
            isValid = false,
            provider = providerName,
            tileUrl = finalUrl,
            zoom = z,
            x = x,
            y = y,
            httpCode = httpCode,
            contentType = contentType,
            payloadSizeBytes = bodySize,
            failureReason = TileFailureReason.CORRUPT_IMAGE_DATA,
            errorMessage = TileFailureReason.CORRUPT_IMAGE_DATA.userFriendlyMessage
          )
          logAndRecordResult(res)
          return@withContext res
        }

        // Semuanya valid
        val successResult = TileCheckResult(
          isValid = true,
          provider = providerName,
          tileUrl = finalUrl,
          zoom = z,
          x = x,
          y = y,
          httpCode = httpCode,
          contentType = contentType,
          payloadSizeBytes = bodySize
        )
        logAndRecordResult(successResult)
        return@withContext successResult
      }
    } catch (e: SocketTimeoutException) {
      val res = TileCheckResult(
        isValid = false,
        provider = providerName,
        tileUrl = finalUrl,
        zoom = z,
        x = x,
        y = y,
        failureReason = TileFailureReason.CONNECTION_TIMEOUT,
        errorMessage = TileFailureReason.CONNECTION_TIMEOUT.userFriendlyMessage
      )
      logAndRecordResult(res)
      return@withContext res
    } catch (e: SSLException) {
      val res = TileCheckResult(
        isValid = false,
        provider = providerName,
        tileUrl = finalUrl,
        zoom = z,
        x = x,
        y = y,
        failureReason = TileFailureReason.SSL_HANDSHAKE_ERROR,
        errorMessage = TileFailureReason.SSL_HANDSHAKE_ERROR.userFriendlyMessage
      )
      logAndRecordResult(res)
      return@withContext res
    } catch (e: IOException) {
      val res = TileCheckResult(
        isValid = false,
        provider = providerName,
        tileUrl = finalUrl,
        zoom = z,
        x = x,
        y = y,
        failureReason = TileFailureReason.UNKNOWN_ERROR,
        errorMessage = e.message ?: "Koneksi ke layanan citra satelit terputus."
      )
      logAndRecordResult(res)
      return@withContext res
    } catch (e: Throwable) {
      val res = TileCheckResult(
        isValid = false,
        provider = providerName,
        tileUrl = finalUrl,
        zoom = z,
        x = x,
        y = y,
        failureReason = TileFailureReason.UNKNOWN_ERROR,
        errorMessage = e.message ?: "Gangguan tak terduga pada pemeriksaan tile satelit."
      )
      logAndRecordResult(res)
      return@withContext res
    }
  }

  /**
   * Memeriksa header magic bytes dari citra raster standar:
   * - JPEG: 0xFF, 0xD8, 0xFF
   * - PNG: 0x89, 'P', 'N', 'G' (0x89, 0x50, 0x4E, 0x47)
   * - WebP: 'R', 'I', 'F', 'F' ... 'W', 'E', 'B', 'P'
   */
  private fun isRecognizedImageHeader(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false

    // JPEG
    if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
      return true
    }

    // PNG
    if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
      return true
    }

    // WebP / RIFF
    if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
      return true
    }

    return false
  }

  private fun logAndRecordResult(result: TileCheckResult) {
    val diagnostic = result.formatDiagnosticLog()
    Log.d(TAG, diagnostic)
    if (result.isValid) {
      AppLogger.recordEvent("SATELLITE_TILE_VALID: ${result.provider} z=${result.zoom} size=${result.payloadSizeBytes}B")
    } else {
      AppLogger.recordEvent("SATELLITE_TILE_FAILED: ${result.provider} reason=${result.failureReason} msg=${result.errorMessage}")
    }
  }
}
