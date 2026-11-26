
package com.example.poolaimassist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode == RESULT_OK && result.data != null) {
                try {
                    val serviceIntent = Intent(this, OverlayService::class.java).apply {
                        putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }

                    statusText.text = "Starting Aim Assist... Please wait for the floating panel."

                } catch (e: Exception) {
                    statusText.text = "Service error: ${e.message}"
                }

            } else {
                statusText.text = "Screen capture permission denied."
            }
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startFlow()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
        }

        statusText = TextView(this).apply {
            textSize = 17f
            text = "Pool Aim Assist Ready"
        }
        root.addView(statusText)

        root.addView(Button(this).apply {
            text = "Grant overlay permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(permissionIntent)
                } else {
                    statusText.text = "Overlay permission granted."
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Start Aim Assist"
            setOnClickListener {
                startFlow()
            }
        })

        root.addView(Button(this).apply {
            text = "Stop Aim Assist"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                statusText.text = "Aim Assist stopped."
            }
        })

        setContentView(root)
    }

    private fun startFlow() {
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = "Please grant overlay permission first."
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        try {
            val manager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            projectionLauncher.launch(manager.createScreenCaptureIntent())

        } catch (e: Exception) {
            statusText.text = "Error: ${e.message}"
        }
    }
}
