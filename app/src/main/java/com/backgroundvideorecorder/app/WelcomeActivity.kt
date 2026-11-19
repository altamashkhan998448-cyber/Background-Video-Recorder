package com.backgroundvideorecorder.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.VideoView
import android.widget.FrameLayout
import android.widget.Toast

class WelcomeActivity : Activity() {

    private lateinit var videoView: VideoView
    private var openedHome = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        window.decorView.systemUiVisibility =
            (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        val layout = FrameLayout(this)

        videoView = VideoView(this)

        layout.addView(
            videoView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(layout)

        playWelcomeVideo()
    }

    private fun playWelcomeVideo() {

        try {
            val videoUri = Uri.parse(
                "android.resource://$packageName/${R.raw.bvr_welcome}"
            )

            videoView.setVideoURI(videoUri)

            videoView.setOnPreparedListener { mediaPlayer ->
                mediaPlayer.setVolume(0f, 0f)
                mediaPlayer.isLooping = false
                videoView.start()
            }

            videoView.setOnCompletionListener {
                openHome()
            }

            videoView.setOnErrorListener { _, _, _ ->
                openHome()
                true
            }

        } catch (e: Exception) {
            openHome()
        }
    }

    private fun openHome() {

        if (openedHome) return

        openedHome = true

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        // Welcome video cannot be skipped.
    }
}