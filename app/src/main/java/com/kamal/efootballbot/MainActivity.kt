package com.kamal.efootballbot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenVisionService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            startForegroundService(serviceIntent)
            Toast.makeText(this, "تم تشغيل الزر العائم! افتح eFootball الآن", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "eFootball Pro AI (Multi-Touch)"
            textSize = 22f
            gravity = Gravity.CENTER
        }

        val btnAccessibility = Button(this).apply {
            text = "1. تفعيل خدمة اللمس (Accessibility)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnOverlay = Button(this).apply {
            text = "2. تفعيل الظهور فوق التطبيقات (Overlay)"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "مفعل مسبقاً!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnStart = Button(this).apply {
            text = "3. تشغيل البوت وإظهار الزر العائم"
            setOnClickListener {
                if (BotAccessibilityService.instance == null) {
                    Toast.makeText(this@MainActivity, "فعّل خدمة اللمس أولاً (الخطوة 1)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
            }
        }

        layout.addView(title)
        layout.addView(btnAccessibility)
        layout.addView(btnOverlay)
        layout.addView(btnStart)
        setContentView(layout)
    }
}