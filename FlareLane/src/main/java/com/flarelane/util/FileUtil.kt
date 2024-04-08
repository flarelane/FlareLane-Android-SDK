package com.flarelane.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.flarelane.BaseErrorHandler
import java.net.HttpURLConnection
import java.net.URL

internal object FileUtil {
    @JvmStatic
    fun downloadImageToBitmap(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) {
            return null
        }
        var bitmap: Bitmap? = null
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            bitmap = BitmapFactory.decodeStream(connection.inputStream)
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
        } finally {
            connection?.disconnect()
        }
        return bitmap
    }
}
