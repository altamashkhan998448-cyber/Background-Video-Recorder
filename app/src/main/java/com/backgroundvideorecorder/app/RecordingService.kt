package com.backgroundvideorecorder.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
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
        const val ACTION_STATE = "com.backgroundvideorecorder.app.RECORDING_STATE"

        const val EXTRA_CAMERA = "camera"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_AUDIO = "audio"

        const val EXTRA_STATUS = "status"
        const val EXTRA_ELAPSED_MS = "elapsed_ms"
        const val EXTRA_RECORDED_BYTES = "recorded_bytes"
        const val EXTRA_ERROR = "error"

        const val STATUS_STARTING = "STARTING"
        const val STATUS_RECORDING = "RECORDING"
        const val STATUS_STOPPED = "STOPPED"
        const val STATUS_ERROR = "ERROR"
        const val STATUS_STORAGE_FULL = "STORAGE_FULL"

        const val DEFAULT_STORAGE_LIMIT_BYTES =
            1024L * 1024L * 1024L

        private const val CHANNEL_ID = "bvr_recording_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var recording: androidx.camera.video.Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var isRecording = false
    private var startRequested = false
    private var storageLimitTriggered = false

    private var recordingStartedAt = 0L
    private var usedBytesBeforeRecording = 0L

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {

            ACTION_STOP -> {
                stopRecording()
            }

            ACTION_START -> {

                if (startRequested || recording != null) {
                    return START_NOT_STICKY
                }

                startRequested = true
                storageLimitTriggered = false

                sendState(STATUS_STARTING)

                val audioEnabled =
                    intent.getBooleanExtra(EXTRA_AUDIO, false)

                if (!hasPermission(Manifest.permission.CAMERA)) {
                    startRequested = false
                    sendState(
                        STATUS_ERROR,
                        error = "Camera permission is missing."
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (
                    audioEnabled &&
                    !hasPermission(Manifest.permission.RECORD_AUDIO)
                ) {
                    startRequested = false
                    sendState(
                        STATUS_ERROR,
                        error = "Microphone permission is missing."
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                usedBytesBeforeRecording = getUsedStorageBytes()

                if (
                    usedBytesBeforeRecording >=
                    DEFAULT_STORAGE_LIMIT_BYTES
                ) {
                    startRequested = false
                    sendState(STATUS_STORAGE_FULL)
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

                try {
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

                } catch (e: Exception) {
                    startRequested = false
                    sendState(
                        STATUS_ERROR,
                        error = e.message ?: "Unable to start recording."
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
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

                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()

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
                startRequested = false
                sendState(
                    STATUS_ERROR,
                    error = e.message ?: "Camera setup failed."
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startVideoFile(
        videoCapture: VideoCapture<Recorder>,
        intent: Intent
    ) {
        try {
            val recordingsFolder = getRecordingsFolder()

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
                        startRequested = false
                        recordingStartedAt =
                            SystemClock.elapsedRealtime()

                        sendState(STATUS_RECORDING)
                    }

                    is VideoRecordEvent.Status -> {

                        val recordedBytes =
                            event.recordingStats.numBytesRecorded

                        val elapsed =
                            if (recordingStartedAt > 0L) {
                                SystemClock.elapsedRealtime() -
                                    recordingStartedAt
                            } else {
                                0L
                            }

                        sendState(
                            STATUS_RECORDING,
                            elapsed,
                            recordedBytes
                        )

                        val totalUsed =
                            usedBytesBeforeRecording + recordedBytes

                        if (
                            totalUsed >= DEFAULT_STORAGE_LIMIT_BYTES &&
                            !storageLimitTriggered
                        ) {
                            storageLimitTriggered = true

                            try {
                                recording?.stop()
                            } catch (_: Exception) {
                            }
                        }
                    }

                    is VideoRecordEvent.Finalize -> {

                        isRecording = false
                        startRequested = false
                        recording = null

                        val finalBytes =
                            if (videoFile.exists()) {
                                videoFile.length()
                            } else {
                                0L
                            }

                        val elapsed =
                            if (recordingStartedAt > 0L) {
                                SystemClock.elapsedRealtime() -
                                    recordingStartedAt
                            } else {
                                0L
                            }

                        recordingStartedAt = 0L

                        if (event.hasError()) {
                            if (videoFile.exists() &&
                                videoFile.length() == 0L
                            ) {
                                videoFile.delete()
                            }

                            sendState(
                                STATUS_ERROR,
                                elapsed,
                                finalBytes,
                                "Recording could not be finalized."
                            )

                        } else if (storageLimitTriggered) {

                            sendState(
                                STATUS_STORAGE_FULL,
                                elapsed,
                                finalBytes
                            )

                        } else {

                            sendState(
                                STATUS_STOPPED,
                                elapsed,
                                finalBytes
                            )
                        }

                        cameraProvider?.unbindAll()

                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }

        } catch (e: Exception) {

            isRecording = false
            startRequested = false

            sendState(
                STATUS_ERROR,
                error = e.message ?: "Recording failed."
            )

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRecording() {

        try {
            if (recording != null) {
                recording?.stop()
            } else {
                isRecording = false
                startRequested = false

                sendState(STATUS_STOPPED)

                cameraProvider?.unbindAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } catch (e: Exception) {
            sendState(
                STATUS_ERROR,
                error = e.message ?: "Could not stop recording."
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun getRecordingsFolder(): File {
        return File(
            getExternalFilesDir(null),
            "Recordings"
        )
    }

    private fun getUsedStorageBytes(): Long {

        val folder = getRecordingsFolder()

        if (!folder.exists()) {
            return 0L
        }

        return folder.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun sendState(
        status: String,
        elapsedMs: Long = 0L,
        recordedBytes: Long = 0L,
        error: String? = null
    ) {
        val stateIntent = Intent(ACTION_STATE).apply {
            setPackage(packageName)

            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_ELAPSED_MS, elapsedMs)
            putExtra(EXTRA_RECORDED_BYTES, recordedBytes)

            if (error != null) {
                putExtra(EXTRA_ERROR, error)
            }
        }

        sendBroadcast(stateIntent)
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