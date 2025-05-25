package com.tubes.purry.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

fun generateQRCodeBitmap(content: String, width: Int = 512, height: Int = 512): Bitmap {
    val hints = hashMapOf<EncodeHintType, Any>().apply {
        put(EncodeHintType.MARGIN, 1) // QR padding kecil
    }

    val bitMatrix: BitMatrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        width,
        height,
        hints
    )

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}

fun previewAndShareQrCode(context: Context, songId: String, songTitle: String, artist: String) {
    val uriString = "purrytify://song/$songId"
    val qrBitmap: Bitmap = generateQRCodeBitmap(uriString)

    val fileName = "qr_preview_${songId}.png"
    val file = File(context.cacheDir, fileName)

    // Pastikan file benar-benar tersimpan sebagai PNG
    FileOutputStream(file).use { out ->
        qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    // Tambahkan flag untuk memastikan preview muncul
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Scan QR ini untuk membuka \"$songTitle\" oleh $artist di Purrytify 🎶")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) // tambahkan ini juga
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        clipData = android.content.ClipData.newRawUri("QR", uri)
    }

    // Share langsung tanpa dialog kalau kamu yakin sudah preview
    context.startActivity(Intent.createChooser(intent, "Bagikan QR Lagu"))
}


fun shareQrCode(context: Context, songId: String, songTitle: String, artist: String) {
    val uriString = "purrytify://song/$songId"
    val qrBitmap: Bitmap = generateQRCodeBitmap(uriString)

    val fileName = "qr_song_${songId}.png"
    val file = File(context.cacheDir, fileName)
    FileOutputStream(file).use { out ->
        qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider", // pastikan match dengan manifest
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Scan QR ini untuk membuka \"$songTitle\" oleh $artist di Purrytify 🎶")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Bagikan QR Lagu"))
}
