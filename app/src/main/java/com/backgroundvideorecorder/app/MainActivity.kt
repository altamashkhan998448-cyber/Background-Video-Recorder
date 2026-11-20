package com.backgroundvideorecorder.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var startButton: Button
    private lateinit var statusText: TextView

    private var recordingStarted = false
    private var pendingStart = false

    companion object {
        private const val PERMISSION_REQUEST = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHomeScreen()
    }

    private fun showHomeScreen() {
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.txtRecordingStatus)
        startButton = findViewById(R.id.btnStartRecording)

        val cameraGroup = findViewById<RadioGroup>(R.id.rgCamera)
        val qualitySpinner = findViewById<Spinner>(R.id.spVideoQuality)
        val audioSwitch = findViewById<Switch>(R.id.switchAudio)
        val previewSwitch = findViewById<Switch>(R.id.switchPreview)

        statusText.text = "● READY TO SET UP"
        startButton.text = "START RECORDING"

        startButton.setOnClickListener {
            if (recordingStarted) {
                stopVideoRecording()
            } else {
                pendingStart = true
                requestRecordingPermissions(
                    audioSwitch.isChecked
                )
            }
        }

        cameraGroup.setOnCheckedChangeListener { _, _ -> }

        qualitySpinner.setOnItemSelectedListener(
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val quality =
                        parent?.getItemAtPosition(position).toString()

                    if (quality.contains("1080")) {
                        Toast.makeText(
                            this@MainActivity,
                            "1080p will be unlocked with an ad later.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }
        )

        findViewById<Button>(R.id.btnRecordings).setOnClickListener {
            showRecordingsScreen()
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            showSettingsScreen()
        }
    }

    private fun requestRecordingPermissions(audioEnabled: Boolean) {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA
        )

        if (audioEnabled) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startVideoRecording()
        } else {
            requestPermissions(
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == PERMISSION_REQUEST && pendingStart) {
            pendingStart = false

            val cameraGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

            val audioSwitch =
                findViewById<Switch>(R.id.switchAudio)

            val audioGranted =
                !audioSwitch.isChecked ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

            if (cameraGranted && audioGranted) {
                startVideoRecording()
            } else {
                Toast.makeText(
                    this,
                    "Camera and selected audio permissions are required.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startVideoRecording() {
        val cameraGroup =
            findViewById<RadioGroup>(R.id.rgCamera)

        val qualitySpinner =
            findViewById<Spinner>(R.id.spVideoQuality)

        val audioSwitch =
            findViewById<Switch>(R.id.switchAudio)

        val camera = if (
            cameraGroup.checkedRadioButtonId == R.id.rbFrontCamera
        ) {
            "FRONT"
        } else {
            "BACK"
        }

        val quality =
            qualitySpinner.selectedItem?.toString() ?: "720p"

        if (quality.contains("1080")) {
            Toast.makeText(
                this,
                "1080p is reserved for the future ad unlock.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = Intent(
            this,
            RecordingService::class.java
        ).apply {
            action = RecordingService.ACTION_START
            putExtra(RecordingService.EXTRA_CAMERA, camera)
            putExtra(RecordingService.EXTRA_QUALITY, quality)
            putExtra(
                RecordingService.EXTRA_AUDIO,
                audioSwitch.isChecked
            )
        }

        try {
            ContextCompat.startForegroundService(this, intent)

            recordingStarted = true
            startButton.text = "STOP RECORDING"
            statusText.text = "● STARTING RECORDING"

        } catch (e: Exception) {
            recordingStarted = false
            startButton.text = "START RECORDING"
            statusText.text = "● SETUP REQUIRED"

            Toast.makeText(
                this,
                "Could not start recording: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopVideoRecording() {
        val intent = Intent(
            this,
            RecordingService::class.java
        ).apply {
            action = RecordingService.ACTION_STOP
        }

        startService(intent)

        recordingStarted = false
        startButton.text = "START RECORDING"
        statusText.text = "● RECORDING STOPPED"
    }

    private fun showRecordingsScreen() {
        setContentView(R.layout.activity_recordings)

        findViewById<Button>(R.id.btnBackRecordings).setOnClickListener {
            showHomeScreen()
        }

        findViewById<Button>(R.id.btnRecycleBin).setOnClickListener {
            Toast.makeText(
                this,
                "Recycle Bin will be connected next.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showSettingsScreen() {
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btnSettingsBack).setOnClickListener {
            showHomeScreen()
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        showHomeScreen()
    }
}