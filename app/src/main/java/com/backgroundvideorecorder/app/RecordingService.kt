package com.backgroundvideorecorder.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoRecordEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : LifecycleService() {

    companion object {
        const val ACTION_START = "BVR_START_RECORDING"
        const val ACTION_STOP = "BVR_STOP_RECORDING"

        const val EXTRA_CAMERA = "camera"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_AUDIO = "audio"

        private const val CHANNEL_ID = "bvr_recording_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var recording: androidx.camera.video.Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isRecording = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (isRecording) return START_NOT_STICKY

                val audioEnabled =
                    intent.getBooleanExtra(EXTRA_AUDIO, false)

                if (!hasPermission(Manifest.permission.CAMERA)) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (
                    audioEnabled &&
                    !hasPermission(Manifest.permission.RECORD_AUDIO)
                ) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                createNotificationChannel()

                val serviceType =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (audioEnabled) {
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                        }
                    } else {
                        0
                    }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        serviceType
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification()
                    )
                }

                startCameraRecording(intent)
            }
        }

        return START_NOT_STICKY
    }

    private fun startCameraRecording(intent: Intent) {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val cameraType =
                    intent.getStringExtra(EXTRA_CAMERA) ?: "BACK"

                val cameraSelector =
                    if (cameraType == "FRONT") {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                val qualityName =
                    intent.getStringExtra(EXTRA_QUALITY) ?: "720p"

                val quality = when {
                    qualityName.contains("1080") -> Quality.FHD
                    qualityName.contains("480") -> Quality.SD
                    else -> Quality.HD
                }

                val qualitySelector = QualitySelector.from(
                    quality,
                    FallbackStrategy.lowerQualityOrHigherThan(quality)
                )

                val recorderBuilder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)

                val recorder = recorderBuilder.build()

                val videoCapture =
                    VideoCapture.withOutput(recorder)

                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    cameraSelector,
                    videoCapture
                )

                startVideoFile(videoCapture, intent)

            } catch (e: Exception) {
                stopRecording()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startVideoFile(
        videoCapture: VideoCapture<Recorder>,
        intent: Intent
    ) {
        try {
            val recordingsFolder = File(
                getExternalFilesDir(null),
                "Recordings"
            )

            if (!recordingsFolder.exists()) {
                recordingsFolder.mkdirs()
            }

            val timestamp = SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(Date())

            val videoFile = File(
                recordingsFolder,
                "BVR_$timestamp.mp4"
            )

            val outputOptions = FileOutputOptions.Builder(
                videoFile
            ).build()

            var pendingRecording =
                videoCapture.output.prepareRecording(
                    this,
                    outputOptions
                )

            val audioEnabled =
                intent.getBooleanExtra(EXTRA_AUDIO, false)

            if (audioEnabled) {
                pendingRecording = pendingRecording.withAudioEnabled()
            }

            recording = pendingRecording.start(
                ContextCompat.getMainExecutor(this)
            ) { event ->

                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                    }

                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        recording = null

                        if (event.hasError()) {
                            videoFile.delete()
                        }

                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }

        } catch (e: Exception) {
            stopRecording()
        }
    }

    private fun stopRecording() {
        try {
            recording?.stop()
        } catch (_: Exception) {
        }

        if (recording == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BVR Recording",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(
                NotificationManager::class.java
            )

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BVR Recording")
            .setContentText("Video recording is active")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        try {
            recording?.stop()
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}