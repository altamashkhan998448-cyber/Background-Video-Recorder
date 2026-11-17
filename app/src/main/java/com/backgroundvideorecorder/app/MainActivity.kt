package com.backgroundvideorecorder.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val startButton = findViewById<Button>(R.id.btnStartRecording)
        val recordingsButton = findViewById<Button>(R.id.btnRecordings)
        val settingsButton = findViewById<Button>(R.id.btnSettings)

        startButton.setOnClickListener {
            Toast.makeText(
                this,
                "Recording setup is coming soon!",
                Toast.LENGTH_SHORT
            ).show()
        }

        recordingsButton.setOnClickListener {
            Toast.makeText(
                this,
                "Recordings section is coming soon!",
                Toast.LENGTH_SHORT
            ).show()
        }

        settingsButton.setOnClickListener {
            Toast.makeText(
                this,
                "Settings section is coming soon!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}