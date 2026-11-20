package com.backgroundvideorecorder.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHomeScreen()
    }

    private fun showHomeScreen() {
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.txtRecordingStatus)
        val startButton = findViewById<Button>(R.id.btnStartRecording)

        val cameraGroup = findViewById<RadioGroup>(R.id.rgCamera)
        val qualitySpinner = findViewById<Spinner>(R.id.spVideoQuality)
        val audioSwitch = findViewById<Switch>(R.id.switchAudio)
        val previewSwitch = findViewById<Switch>(R.id.switchPreview)

        status.text = "● SETUP REQUIRED"
        startButton.text = "START RECORDING"

        startButton.setOnClickListener {
            Toast.makeText(
                this,
                "Recording engine abhi connect nahi hua hai. Agla step mein connect karenge.",
                Toast.LENGTH_LONG
            ).show()
        }

        cameraGroup.setOnCheckedChangeListener { _, checkedId ->
            val camera = if (checkedId == R.id.rbFrontCamera) {
                "Front Camera"
            } else {
                "Back Camera"
            }

            Toast.makeText(this, camera + " selected", Toast.LENGTH_SHORT).show()
        }

        qualitySpinner.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val quality = parent?.getItemAtPosition(position).toString()

                    if (quality.contains("1080")) {
                        Toast.makeText(
                            this@MainActivity,
                            "1080p will be available through an ad later.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {
                }
            }
        )

        audioSwitch.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) "Audio ON" else "Audio OFF"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        previewSwitch.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) "Live Preview ON" else "Live Preview OFF"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnRecordings).setOnClickListener {
            showRecordingsScreen()
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            showSettingsScreen()
        }
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