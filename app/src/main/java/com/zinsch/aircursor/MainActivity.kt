package com.zinsch.aircursor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var sensitivityLabel: TextView
    private lateinit var smoothingLabel: TextView
    private lateinit var fpsLabel: TextView
    private lateinit var sensitivity: SeekBar
    private lateinit var smoothing: SeekBar
    private lateinit var fps: SeekBar
    private lateinit var scrollSwitch: Switch
    private lateinit var hapticSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSettings()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 24)
            setBackgroundColor(0xFF090812.toInt())
        }

        fun heading(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(0xFFECE5FF.toInt())
        }
        fun caption(text: String) = TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFFBDAED8.toInt())
            setPadding(0, 5, 0, 8)
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }

        root.addView(heading("A I R C U R S O R", 25f))
        root.addView(caption("HAND TRACKING • TOUCHLESS CONTROL • ANDROID"))
        status = caption("Status: belum aktif")
        root.addView(status)

        root.addView(heading("Cara menggunakan"))
        root.addView(caption(
            "1. Beri izin kamera dan tampil di atas aplikasi lain.\\n" +
            "2. Aktifkan layanan AirCursor di Pengaturan Aksesibilitas.\\n" +
            "3. Tekan MULAI, lalu arahkan telunjuk ke kamera depan.\\n" +
            "4. Cubit-lepas untuk klik; tahan cubitan untuk drag.\\n" +
            "5. Gerakkan telunjuk dan jari tengah terbuka untuk scroll."
        ))

        root.addView(button("IZIN OVERLAY") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        root.addView(button("AKTIFKAN AKSESIBILITAS") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("IZIN KAMERA") {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 11)
        })

        root.addView(heading("Pengaturan performa"))
        sensitivityLabel = caption("Sensitivitas: 1.00×")
        root.addView(sensitivityLabel)
        sensitivity = SeekBar(this).apply { max = 100 }
        root.addView(sensitivity)

        smoothingLabel = caption("Smoothing: 0.28")
        root.addView(smoothingLabel)
        smoothing = SeekBar(this).apply { max = 100 }
        root.addView(smoothing)

        fpsLabel = caption("Hand tracking: 15 FPS")
        root.addView(fpsLabel)
        fps = SeekBar(this).apply { max = 16 }
        root.addView(fps)

        scrollSwitch = Switch(this).apply {
            text = "Aktifkan scroll dua jari"
            setTextColor(0xFFECE5FF.toInt())
        }
        root.addView(scrollSwitch)
        hapticSwitch = Switch(this).apply {
            text = "Haptic feedback (reserved)"
            setTextColor(0xFFECE5FF.toInt())
            isEnabled = false
        }
        root.addView(hapticSwitch)

        root.addView(button("SIMPAN PENGATURAN") { saveSettings() })
        root.addView(button("MULAI AIRCURSOR") { startTracking() })
        root.addView(button("HENTIKAN AIRCURSOR") {
            stopService(Intent(this, HandTrackingService::class.java))
            status.text = "Status: berhenti"
        })
        root.addView(caption(
            "Catatan: tracking kamera berjalan sebagai foreground service saat dimulai dari aplikasi. " +
            "Android dapat membatasi kamera jika layanan dihentikan sistem atau izin dicabut."
        ))

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun loadSettings() {
        sensitivity.progress = ((SettingsStore.sensitivity(this) - 0.5f) * 100f).toInt()
        smoothing.progress = (SettingsStore.smoothing(this) * 100f).toInt()
        fps.progress = SettingsStore.inference(this) - 8
        scrollSwitch.isChecked = SettingsStore.scrollEnabled(this)
        hapticSwitch.isChecked = SettingsStore.haptic(this)
        updateLabels()
        sensitivity.setOnSeekBarChangeListener(listener { updateLabels() })
        smoothing.setOnSeekBarChangeListener(listener { updateLabels() })
        fps.setOnSeekBarChangeListener(listener { updateLabels() })
    }

    private fun listener(onChange: () -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange()
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun updateLabels() {
        val sens = 0.5f + sensitivity.progress / 100f
        val smooth = smoothing.progress.coerceIn(12, 65) / 100f
        val rate = (fps.progress + 8).coerceIn(8, 24)
        sensitivityLabel.text = "Sensitivitas: %.2f×".format(sens)
        smoothingLabel.text = "Smoothing: %.2f".format(smooth)
        fpsLabel.text = "Hand tracking: $rate FPS"
    }

    private fun saveSettings() {
        SettingsStore.save(
            this,
            0.5f + sensitivity.progress / 100f,
            smoothing.progress.coerceIn(12, 65) / 100f,
            (fps.progress + 8).coerceIn(8, 24),
            scrollSwitch.isChecked,
            hapticSwitch.isChecked
        )
        status.text = "Status: pengaturan tersimpan"
    }

    private fun startTracking() {
        saveSettings()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 11)
            status.text = "Berikan izin kamera dahulu"
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            status.text = "Aktifkan izin overlay dahulu"
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        }
        val intent = Intent(this, HandTrackingService::class.java)
        ContextCompat.startForegroundService(this, intent)
        status.text = "Status: memulai hand tracking"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 11 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            status.text = "Izin kamera diberikan. Tekan MULAI AIRCURSOR."
        }
    }
}
