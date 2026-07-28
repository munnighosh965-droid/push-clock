package com.powerclock.alarm.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

/**
 * On-device QR decoding with ZXing (Apache 2.0). Only the luminance plane is
 * read; frames are never stored.
 */
class QrAnalyzer(
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.EAN_13,
                ),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val width = image.width
            val height = image.height
            val data = ByteArray(width * height)
            if (rowStride == width) {
                buffer.get(data, 0, data.size.coerceAtMost(buffer.remaining()))
            } else {
                var offset = 0
                val row = ByteArray(rowStride)
                for (y in 0 until height) {
                    val toRead = rowStride.coerceAtMost(buffer.remaining())
                    if (toRead <= 0) break
                    buffer.get(row, 0, toRead)
                    System.arraycopy(row, 0, data, offset, width.coerceAtMost(toRead))
                    offset += width
                }
            }
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            onDecoded(result.text)
        } catch (_: Exception) {
            // No code in this frame; keep scanning.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

object QrCardGenerator {

    /** Renders the Power Clock QR card content as a bitmap for saving/printing. */
    fun render(content: String, sizePx: Int = 800): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val dark = 0xFF021024.toInt()
        val light = 0xFFC1E8FF.toInt()
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) dark else light)
            }
        }
        return bmp
    }
}
