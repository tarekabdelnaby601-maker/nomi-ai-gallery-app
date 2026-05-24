package com.example.mobilgaleri.util

import android.content.ContentResolver
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.InputStream

/**
 * Büyük görsellerle çalışırken bellek dostu bitmap işlemleri için yardımcı fonksiyonlar.
 */
object BitmapUtils {

    private const val TAG = "BitmapUtils"

    /**
     * Verilen content URI'dan Bitmap'i çözer. API seviyesine göre uyumlu metod kullanır.
     * Hata durumunda null döner.
     */
    fun decodeBitmapCompat(contentUri: String, cr: ContentResolver): Bitmap? {
        val uri = Uri.parse(contentUri)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(cr, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                cr.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap: $uri", e)
            null
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError decoding bitmap: $uri", oom)
            null
        }
    }

    /**
     * Bir InputStream'den belirtilen dikdörtgen (Rect) bölgesini Bitmap olarak çözer.
     * Özellikle büyük resimlerden sadece bir bölümü yüklemek için verimlidir.
     */
    fun decodeRegion(inputStream: InputStream, rect: Rect): Bitmap? {
        if (rect.width() <= 0 || rect.height() <= 0) {
            Log.w(TAG, "Attempted to decode region with invalid Rect: $rect")
            return null
        }

        var decoder: BitmapRegionDecoder? = null
        return try {
            decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(inputStream)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(inputStream, false)
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            decoder?.decodeRegion(rect, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding region: Rect=$rect", e)
            null
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError decoding region: Rect=$rect", oom)
            null
        } finally {
            try {
                decoder?.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error recycling BitmapRegionDecoder", e)
            }
        }
    }

    /**
     * Detection için boyut sınırıyla bitmap yükler (örneğin 1024 px).
     * Büyük fotoğraflarda bellek kullanımını azaltır.
     * Dönüş: Pair(bitmap, ölçek)
     */
    fun loadScaledBitmapForDetection(
        cr: ContentResolver,
        uri: Uri,
        maxSize: Int = 1024
    ): Pair<Bitmap?, Float> {
        return try {
            cr.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                val scale = maxOf(opts.outWidth, opts.outHeight) / maxSize.toFloat()
                val sampleSize = if (scale > 1f) scale.toInt() else 1

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                cr.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream, null, decodeOpts)
                    Pair(bmp, 1f / sampleSize)
                } ?: Pair(null, 1f)
            } ?: Pair(null, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "loadScaledBitmapForDetection hata: $uri", e)
            Pair(null, 1f)
        }
    }
}
