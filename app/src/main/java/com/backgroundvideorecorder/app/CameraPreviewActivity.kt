package com.backgroundvideorecorder.app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraPreviewActivity : Activity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var cameraGroup: RadioGroup

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isRecording = false
    private var recordingStartTime = 0L

    private val timerHandler = Handler(Looper.getMainLooper())

    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val elapsed =
                    SystemClock.elapsedRealtime() - recordingStartTime

                val seconds = elapsed / 1000
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val remainingSeconds = seconds % 60

                timerText.text = String.format(
                    Locale.getDefault(),
                    "%02d:%02d:%02d",
                    hours,
                    minutes,
                    remainingSeconds
                )

                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val cameraGranted =
                permissions[Manifest.permission.CAMERA] == true ||
                    hasPermission(Manifest.permission.CAMERA)

            if (cameraGranted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_camera_preview)

        previewView = findViewById(R.id.cameraPreview)
        statusText = findViewById(R.id.txtRecordingStatus)
        timerText = findViewById(R.id.txtRecordingTimer)

        startButton = findViewById(R.id.btnPreviewStart)
        stopButton = findViewById(R.id.btnPreviewStop)
        cameraGroup = findViewById(R.id.rgPreviewCamera)

        stopButton.isEnabled = false

        startButton.setOnClickListener {
            startRecording()
        }

        stopButton.setOnClickListener {
            stopRecording()
        }

        findViewById<Button>(R.id.btnPreviewBack).setOnClickListener {
            finish()
        }

        cameraGroup.setOnCheckedChangeListener { _, checkedId ->

            if (checkedId == R.id.rbPreviewFront) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            }

            if (!isRecording && hasPermission(Manifest.permission.CAMERA)) {
                startCamera()
            }
        }

        checkPermissions()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissions() {

        val permissions = mutableListOf<String>()

        if (!hasPermission(Manifest.permission.CAMERA)) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            startCamera()
        }
    }

    private fun startCamera() {

        val cameraFuture = ProcessCameraProvider.getInstance(this)

        cameraFuture.addListener({

            try {
                cameraProvider = cameraFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            listOf(
                                Quality.FHD,
                                Quality.HD,
                                Quality.SD
                            ),
                            FallbackStrategy.lowerQualityOrHigherThan(
                                Quality.SD
                            )
                        )
                    )
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider?.unbindAll()

                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    videoCapture
                )

                statusText.text = "● READY"

                Toast.makeText(
                    this,
                    "Camera is ready!",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                Toast.makeText(
                    this,
                    "Camera error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {

        if (isRecording) return

        val capture = videoCapture

        if (capture == null) {
            Toast.makeText(
                this,
                "Camera is not ready.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {

            val fileName = "BVR_" +
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(Date()) +
                ".mp4"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/BackgroundVideoRecorder"
                )
            }

            val outputOptions = MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(contentValues)
                .build()

            var pendingRecording: PendingRecording =
                capture.output.prepareRecording(
                    this,
                    outputOptions
                )

            val audioEnabled =
                hasPermission(Manifest.permission.RECORD_AUDIO)

            if (audioEnabled) {
                pendingRecording = pendingRecording.withAudioEnabled()
            }

            recording = pendingRecording.start(
                ContextCompat.getMainExecutor(this)
            ) { event ->

                when (event) {

                    is VideoRecordEvent.Start -> {

                        isRecording = true
                        recordingStartTime =
                            SystemClock.elapsedRealtime()

                        statusText.text = "● RECORDING"
                        statusText.setTextColor(
                            android.graphics.Color.RED
                        )

                        startButton.isEnabled = false
                        stopButton.isEnabled = true

                        timerHandler.post(timerRunnable)
                    }

                    is VideoRecordEvent.Finalize -> {

                        isRecording = false
                        timerHandler.removeCallbacks(timerRunnable)

                        recording = null

                        startButton.isEnabled = true
                        stopButton.isEnabled = false

                        if (!event.hasError()) {

                            statusText.text = "● SAVED"
                            statusText.setTextColor(
                                android.graphics.Color.GREEN
                            )

                            Toast.makeText(
                                this,
                                "Video saved successfully!",
                                Toast.LENGTH_LONG
                            ).show()

                        } else {

                            statusText.text = "● ERROR"

                            Toast.makeText(
                                this,
                                "Recording failed: ${event.error}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Recording error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopRecording() {
        recording?.stop()
        statusText.text = "● SAVING VIDEO..."
        stopButton.isEnabled = false
    }

    override fun onDestroy() {

        timerHandler.removeCallbacks(timerRunnable)

        recording?.close()

        cameraProvider?.unbindAll()

        cameraExecutor.shutdown()

        super.onDestroy()
    }
}