package com.backgroundvideorecorder.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHomeScreen()
    }

    private fun showHomeScreen() {
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStartRecording).setOnClickListener {
            showRecordingScreen()
        }

        findViewById<Button>(R.id.btnRecordings).setOnClickListener {
            showRecordingsScreen()
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            showSettingsScreen()
        }
    }

    private fun showRecordingScreen() {
        setContentView(R.layout.activity_recording)

        findViewById<Button>(R.id.btnBackHome).setOnClickListener {
            showHomeScreen()
        }

        findViewById<Button>(R.id.btnBeginRecording).setOnClickListener {
            Toast.makeText(
                this,
                "Recording engine will be connected next.",
                Toast.LENGTH_SHORT
            ).show()
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