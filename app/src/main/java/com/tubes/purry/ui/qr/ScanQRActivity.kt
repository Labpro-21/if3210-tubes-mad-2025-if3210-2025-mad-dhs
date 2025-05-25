package com.tubes.purry.ui.qr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.tubes.purry.MainActivity

class ScanQRActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Periksa izin kamera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            startScanner()
        }
    }

    private fun startScanner() {
        IntentIntegrator(this).apply {
            setPrompt("Arahkan kamera ke QR lagu")
            setOrientationLocked(true)
            setBeepEnabled(true)
            initiateScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner()
            } else {
                Toast.makeText(this, "Izin kamera diperlukan untuk scan QR", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                try {
                    val scannedUri = Uri.parse(result.contents)

                    if (scannedUri.scheme == "purrytify" && scannedUri.host == "song") {
                        val rawId = scannedUri.lastPathSegment
                        val idInt = rawId?.toIntOrNull()

                        if (idInt != null && idInt > 0) {
                            val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
                                setClass(this@ScanQRActivity, MainActivity::class.java)
                                setData(Uri.parse("purrytify://song/$idInt"))
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }

                            startActivity(deepLinkIntent)
                            finish()
                        } else {
                            Toast.makeText(this, "ID lagu tidak valid: $rawId", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        Toast.makeText(this, "QR tidak valid untuk lagu Purrytify", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "QR tidak dikenali", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                Toast.makeText(this, "Scan dibatalkan", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

}
