// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Decode with orientation, bound memory and re-encode without location/EXIF metadata. */
suspend fun localJpeg(context: Context, uri: Uri, longestSide: Int = 1280): ByteArray = withContext(Dispatchers.IO) {
    val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder, info, _ ->
        require(info.size.width > 0 && info.size.height > 0) { "Cannot open photo" }
        val ratio=longestSide.toDouble()/maxOf(info.size.width,info.size.height)
        if(ratio<1) decoder.setTargetSize(maxOf(1,(info.size.width*ratio).toInt()),maxOf(1,(info.size.height*ratio).toInt()))
        decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
    }
    try {
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,output))
            output.toByteArray().also { require(it.size <= 2*1024*1024) { "Photo is too large" } }
        }
    } finally { bitmap.recycle() }
}
