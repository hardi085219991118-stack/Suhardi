package com.example.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.R

/**
 * Helper pembuat icon marker titik api satelit (🔥) nyata.
 * Sesuai Bagian 5:
 * - Marker visual titik api yang benar-benar berbentuk flame / api (🔥).
 * - Menggunakan drawable/vector asset lokal (R.drawable.ic_fire_marker).
 * - Bukan pin standar atau marker default osmdroid.
 * - Ringan, scalable, tidak blur, dan terpisah dari marker pengguna.
 */
object FireMarkerIconHelper {
  private var cachedFlameIcon: Drawable? = null

  fun getFlameIcon(context: Context): Drawable {
    cachedFlameIcon?.let { return it }

    val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_fire_marker)
    if (vectorDrawable == null) {
      val fallback = BitmapDrawable(context.resources, Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888))
      return fallback
    }

    try {
      val density = context.resources.displayMetrics.density
      val sizePx = (36 * density).toInt().coerceAtLeast(48)
      val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
      vectorDrawable.draw(canvas)
      val drawable = BitmapDrawable(context.resources, bitmap)
      cachedFlameIcon = drawable
      return drawable
    } catch (e: Exception) {
      // Fallback jika createBitmap gagal (misal di JVM headless environment tanpa Robolectric graphics)
      return vectorDrawable
    }
  }

  fun clearCacheForTesting() {
    cachedFlameIcon = null
  }
}
