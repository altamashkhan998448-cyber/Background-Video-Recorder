package com.backgroundvideorecorder.app

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView

    private lateinit var previewSwitch: Switch
    private lateinit var previewContainer: FrameLayout
    private lateinit var previewPlaceholder: TextView

    private lateinit var cameraGroup: RadioGroup
    private lateinit var qualitySpinner: Spinner
    private lateinit var audioSwitch: Switch

    private lateinit var storageTotal: TextView
    private lateinit var storageUsed: TextView
    private lateinit var storageAvailable: TextView
    private lateinit var storagePercent: TextView
    private lateinit var storageProgress: ProgressBar
    private lateinit var storageInfo: TextView

    private var recordingStarted = false
    private var pendingStart = false
    private var homeVisible = false
    private var receiverRegistered = false

    private var recordingStartElapsed = 0L
    private var recordingElapsedMs = 0L

    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val storageLimitBytes =
        1024L * 1024L * 1024L

    companion object {
        private const val PERMISSION_REQUEST = 200
        private const val PREVIEW_PERMISSION_REQUEST = 201
    }

    private val timerRunnable = object : Runnable {

        override fun run() {

            if (recordingStarted && homeVisible) {

                val elapsed =
                    SystemClock.elapsedRealtime() -
                        recordingStartElapsed

                timerText.text = formatTime(elapsed)

                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    private val recordingReceiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {

            if (intent?.action != RecordingService.ACTION_STATE) {
                return
            }

            val status = intent.getStringExtra(
                RecordingService.EXTRA_STATUS
            ) ?: return

            val elapsed = intent.getLongExtra(
                RecordingService.EXTRA_ELAPSED_MS,
                0L
            )

            when (status) {

                RecordingService.STATUS_STARTING -> {

                    recordingStarted = true

                    if (homeVisible) {
                        statusText.text = "● STARTING"
                        startButton.text = "STOP RECORDING"
                    }
                }

                RecordingService.STATUS_RECORDING -> {

                    recordingStarted = true

                    if (elapsed > 0L) {
                        recordingElapsedMs = elapsed
                        recordingStartElapsed =
                            SystemClock.elapsedRealtime() - elapsed
                    } else if (recordingStartElapsed == 0L) {
                        recordingStartElapsed =
                            SystemClock.elapsedRealtime()
                    }

                    if (homeVisible) {
                        statusText.text = "● RECORDING"
                        startButton.text = "STOP RECORDING"

                        mainHandler.removeCallbacks(timerRunnable)
                        mainHandler.post(timerRunnable)

                        showPreviewUnavailable()
                    }
                }

                RecordingService.STATUS_STOPPED -> {

                    recordingStarted = false
                    recordingStartElapsed = 0L
                    recordingElapsedMs = elapsed

                    mainHandler.removeCallbacks(timerRunnable)

                    if (homeVisible) {
                        statusText.text = "● STOPPED"
                        startButton.text = "START RECORDING"
                        timerText.text = formatTime(elapsed)

                        updateStorageInformation()
                        restorePreview()
                    }
                }

                RecordingService.STATUS_STORAGE_FULL -> {

                    recordingStarted = false
                    recordingStartElapsed = 0L

                    mainHandler.removeCallbacks(timerRunnable)

                    if (homeVisible) {
                        statusText.text = "● STORAGE FULL"
                        startButton.text = "START RECORDING"
                        timerText.text = formatTime(elapsed)

                        updateStorageInformation()
                        restorePreview()

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Storage Full")
                            .setMessage(
                                "Your 1 GB recording storage allowance is full. " +
                                    "Free up space or unlock more storage later."
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }

                RecordingService.STATUS_ERROR -> {

                    recordingStarted = false
                    recordingStartElapsed = 0L

                    mainHandler.removeCallbacks(timerRunnable)

                    val error = intent.getStringExtra(
                        RecordingService.EXTRA_ERROR
                    ) ?: "Something went wrong."

                    if (homeVisible) {
                        statusText.text = "● ERROR"
                        startButton.text = "START RECORDING"

                        restorePreview()

                        Toast.makeText(
                            this@MainActivity,
                            error,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHomeScreen()
    }

    override fun onStart() {
        super.onStart()

        if (!receiverRegistered) {

            val filter = IntentFilter(
                RecordingService.ACTION_STATE
            )

            ContextCompat.registerReceiver(
                this,
                recordingReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            receiverRegistered = true
        }
    }

    override fun onStop() {
        super.onStop()

        if (receiverRegistered) {
            unregisterReceiver(recordingReceiver)
            receiverRegistered = false
        }

        mainHandler.removeCallbacks(timerRunnable)
        unbindPreview()
    }

    override fun onResume() {
        super.onResume()

        if (homeVisible && !recordingStarted) {
            updateStorageInformation()
            restorePreview()
        }
    }

    // HOME SCREEN

    private fun showHomeScreen() {

        homeVisible = true

        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.btnStartRecording)
        statusText = findViewById(R.id.txtRecordingStatus)
        timerText = findViewById(R.id.txtRecordingTimer)

        previewSwitch = findViewById(R.id.switchPreview)
        previewContainer =
            findViewById(R.id.cameraPreviewContainer)

        previewPlaceholder =
            findViewById(R.id.txtPreviewPlaceholder)

        cameraGroup = findViewById(R.id.rgCamera)
        qualitySpinner = findViewById(R.id.spVideoQuality)
        audioSwitch = findViewById(R.id.switchAudio)

        storageTotal = findViewById(R.id.txtStorageTotal)
        storageUsed = findViewById(R.id.txtStorageUsed)
        storageAvailable = findViewById(R.id.txtStorageAvailable)
        storagePercent = findViewById(R.id.txtStoragePercent)
        storageProgress = findViewById(R.id.storageProgress)
        storageInfo = findViewById(R.id.txtStorageInfo)

        setupQualitySpinner()

        startButton.text =
            if (recordingStarted) {
                "STOP RECORDING"
            } else {
                "START RECORDING"
            }

        statusText.text =
            if (recordingStarted) {
                "● RECORDING"
            } else {
                "● READY"
            }

        timerText.text = formatTime(recordingElapsedMs)

        startButton.setOnClickListener {

            if (recordingStarted) {
                stopVideoRecording()
            } else {
                pendingStart = true
                requestRecordingPermissions(audioSwitch.isChecked)
            }
        }

        previewSwitch.setOnCheckedChangeListener { _, checked ->

            if (checked) {
                requestPreviewPermission()
            } else {
                unbindPreview()
                previewPlaceholder.visibility = View.VISIBLE
                previewPlaceholder.text = "Live Preview is OFF"
            }
        }

        cameraGroup.setOnCheckedChangeListener { _, _ ->

            if (previewSwitch.isChecked && !recordingStarted) {
                bindPreview()
            }
        }

        findViewById<Button>(R.id.btnRecordings)
            .setOnClickListener {
                homeVisible = false
                showRecordingsScreen()
            }

        findViewById<Button>(R.id.btnSettings)
            .setOnClickListener {
                homeVisible = false
                showSettingsScreen()
            }

        updateStorageInformation()

        if (recordingStarted) {
            mainHandler.removeCallbacks(timerRunnable)
            mainHandler.post(timerRunnable)
            showPreviewUnavailable()
        } else {
            restorePreview()
        }
    }

    private fun setupQualitySpinner() {

        val qualities = arrayOf(
            "480p",
            "720p",
            "1080p (Ad Unlock)"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            qualities
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        qualitySpinner.adapter = adapter
        qualitySpinner.setSelection(1)

        qualitySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    if (position == 2) {

                        Toast.makeText(
                            this@MainActivity,
                            "1080p will be unlocked through an ad later.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }
    }

    // PERMISSIONS

    private fun requestRecordingPermissions(
        audioEnabled: Boolean
    ) {

        val permissions = mutableListOf(
            Manifest.permission.CAMERA
        )

        if (audioEnabled) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        val missingPermissions = permissions.filter {

            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {

            pendingStart = false
            startVideoRecording()

        } else {

            requestPermissions(
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST
            )
        }
    }

    private fun requestPreviewPermission() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bindPreview()
        } else {

            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                PREVIEW_PERMISSION_REQUEST
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
                    "Required permissions were not granted.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        if (requestCode == PREVIEW_PERMISSION_REQUEST) {

            val cameraGranted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

            if (cameraGranted && previewSwitch.isChecked) {
                bindPreview()
            } else {
                previewSwitch.isChecked = false
            }
        }
    }

    // CAMERA PREVIEW

    private fun bindPreview() {

        if (recordingStarted || !previewSwitch.isChecked) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            previewPlaceholder.text = "Camera permission required"
            return
        }

        previewPlaceholder.visibility = View.VISIBLE
        previewPlaceholder.text = "Starting camera preview..."

        val cameraPreview = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        previewView = cameraPreview

        previewContainer.removeAllViews()
        previewContainer.addView(cameraPreview)

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            try {

                if (
                    isFinishing ||
                    isDestroyed ||
                    recordingStarted ||
                    !previewSwitch.isChecked
                ) {
                    return@addListener
                }

                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val selector =
                    if (
                        cameraGroup.checkedRadioButtonId ==
                        R.id.rbFrontCamera
                    ) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                val preview = Preview.Builder().build()

                preview.setSurfaceProvider(
                    cameraPreview.surfaceProvider
                )

                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    selector,
                    preview
                )

                previewPlaceholder.visibility = View.GONE

            } catch (e: Exception) {

                previewPlaceholder.visibility = View.VISIBLE
                previewPlaceholder.text =
                    "Preview unavailable: ${e.message}"

                Toast.makeText(
                    this@MainActivity,
                    "Camera preview could not start.",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun unbindPreview() {

        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }

        cameraProvider = null
        previewView = null

        if (::previewContainer.isInitialized) {
            previewContainer.removeAllViews()

            if (::previewPlaceholder.isInitialized) {
                previewContainer.addView(previewPlaceholder)
            }
        }
    }

    private fun restorePreview() {

        if (!homeVisible || !::previewSwitch.isInitialized) {
            return
        }

        if (previewSwitch.isChecked) {
            bindPreview()
        } else {
            previewPlaceholder.visibility = View.VISIBLE
            previewPlaceholder.text = "Live Preview is OFF"
        }
    }

    private fun showPreviewUnavailable() {

        if (!homeVisible || !::previewPlaceholder.isInitialized) {
            return
        }

        unbindPreview()

        previewPlaceholder.visibility = View.VISIBLE
        previewPlaceholder.text =
            "Camera is being used for recording."
    }    // START RECORDING

    private fun startVideoRecording() {

        val camera =
            if (
                cameraGroup.checkedRadioButtonId ==
                R.id.rbFrontCamera
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
                "1080p will be available through an ad later.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        if (getUsedStorageBytes() >= storageLimitBytes) {

            updateStorageInformation()

            AlertDialog.Builder(this)
                .setTitle("Storage Full")
                .setMessage(
                    "Your 1 GB recording storage allowance is full."
                )
                .setPositiveButton("OK", null)
                .show()

            return
        }

        unbindPreview()

        val intent = Intent(
            this,
            RecordingService::class.java
        ).apply {

            action = RecordingService.ACTION_START

            putExtra(
                RecordingService.EXTRA_CAMERA,
                camera
            )

            putExtra(
                RecordingService.EXTRA_QUALITY,
                quality
            )

            putExtra(
                RecordingService.EXTRA_AUDIO,
                audioSwitch.isChecked
            )
        }

        try {

            ContextCompat.startForegroundService(
                this,
                intent
            )

            recordingStarted = true
            startButton.text = "STOP RECORDING"
            statusText.text = "● STARTING"

        } catch (e: Exception) {

            recordingStarted = false
            startButton.text = "START RECORDING"
            statusText.text = "● ERROR"

            restorePreview()

            Toast.makeText(
                this,
                "Could not start recording: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // STOP RECORDING

    private fun stopVideoRecording() {

        val intent = Intent(
            this,
            RecordingService::class.java
        ).apply {
            action = RecordingService.ACTION_STOP
        }

        startService(intent)

        statusText.text = "● STOPPING"
        startButton.isEnabled = false

        mainHandler.postDelayed({

            if (::startButton.isInitialized) {
                startButton.isEnabled = true
            }

        }, 1500)
    }

    // STORAGE

    private fun getRecordingsFolder(): File {

        return File(
            getExternalFilesDir(null),
            "Recordings"
        )
    }

    private fun getRecycleBinFolder(): File {

        return File(
            getExternalFilesDir(null),
            "RecycleBin"
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

    private fun updateStorageInformation() {

        if (!::storageUsed.isInitialized) {
            return
        }

        val usedBytes = getUsedStorageBytes()

        val availableBytes =
            (storageLimitBytes - usedBytes).coerceAtLeast(0L)

        val percentage =
            ((usedBytes.toDouble() / storageLimitBytes) * 100)
                .toInt()
                .coerceIn(0, 100)

        storageTotal.text =
            "TOTAL STORAGE: ${formatSize(storageLimitBytes)}"

        storageUsed.text =
            "Used: ${formatSize(usedBytes)}"

        storageAvailable.text =
            "Available: ${formatSize(availableBytes)}"

        storagePercent.text = "$percentage% USED"

        storageProgress.max = 100
        storageProgress.progress = percentage

        storageInfo.text =
            "${formatSize(usedBytes)} USED / " +
                "${formatSize(availableBytes)} FREE"
    }

    private fun formatSize(bytes: Long): String {

        val mb = bytes / (1024.0 * 1024.0)

        return if (mb >= 1024.0) {

            String.format(
                Locale.getDefault(),
                "%.2f GB",
                mb / 1024.0
            )

        } else {

            String.format(
                Locale.getDefault(),
                "%.1f MB",
                mb
            )
        }
    }

    private fun formatTime(milliseconds: Long): String {

        val totalSeconds = milliseconds / 1000L

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }

    // RECORDINGS SCREEN

    private fun showRecordingsScreen() {

        homeVisible = false

        setContentView(R.layout.activity_recordings)

        findViewById<Button>(
            R.id.btnBackRecordings
        ).setOnClickListener {
            showHomeScreen()
        }

        findViewById<Button>(
            R.id.btnRecycleBin
        ).setOnClickListener {
            showRecycleBin()
        }

        loadSavedVideos()
    }

    private fun loadSavedVideos() {

        val container =
            findViewById<LinearLayout>(R.id.videosContainer)

        val emptyMessage =
            findViewById<TextView>(R.id.txtNoVideos)

        container.removeAllViews()

        val folder = getRecordingsFolder()

        if (!folder.exists()) {
            folder.mkdirs()
        }

        val videos = folder.listFiles()
            ?.filter {
                it.isFile &&
                    it.extension.lowercase() in
                    listOf("mp4", "mkv", "3gp", "webm")
            }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        emptyMessage.visibility =
            if (videos.isEmpty()) View.VISIBLE else View.GONE

        for (video in videos) {
            addVideoItem(container, video)
        }

        updateRecordingsStorage()
    }

    private fun addVideoItem(
        container: LinearLayout,
        video: File
    ) {

        val card = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundColor(0xFF151D1A.toInt())

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            params.bottomMargin = 12
            layoutParams = params
        }

        val name = TextView(this).apply {
            text = video.name
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }

        val size = TextView(this).apply {
            text = formatSize(video.length())
            textSize = 13f
            setTextColor(0xFF00D6A0.toInt())
            setPadding(0, 6, 0, 10)
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val playButton = Button(this).apply {
            text = "PLAY"
            setOnClickListener {
                playVideo(video)
            }
        }

        val moreButton = Button(this).apply {
            text = "MORE"
            setOnClickListener {
                showVideoOptions(video)
            }
        }

        buttons.addView(
            playButton,
            LinearLayout.LayoutParams(0, 52, 1f)
        )

        buttons.addView(
            moreButton,
            LinearLayout.LayoutParams(0, 52, 1f)
        )

        card.addView(name)
        card.addView(size)
        card.addView(buttons)

        container.addView(card)
    }

    // VIDEO PLAYER

    private fun playVideo(video: File) {

        if (!video.exists()) {
            Toast.makeText(
                this,
                "Video file not found.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val videoView = VideoView(this)

        val dialog = AlertDialog.Builder(this)
            .setTitle(video.name)
            .setView(videoView)
            .setNegativeButton("CLOSE", null)
            .create()

        dialog.setOnDismissListener {
            videoView.stopPlayback()
        }

        dialog.show()

        videoView.setVideoURI(Uri.fromFile(video))

        videoView.setOnPreparedListener {
            videoView.start()
        }

        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(
                this,
                "Unable to play this video.",
                Toast.LENGTH_SHORT
            ).show()
            true
        }
    }

    // VIDEO OPTIONS

    private fun showVideoOptions(video: File) {

        val options = arrayOf(
            "Play Video",
            "Rename",
            "Move to Recycle Bin"
        )

        AlertDialog.Builder(this)
            .setTitle(video.name)
            .setItems(options) { _, which ->

                when (which) {
                    0 -> playVideo(video)
                    1 -> renameVideo(video)
                    2 -> moveToRecycleBin(video)
                }
            }
            .show()
    }

    // RENAME VIDEO

    private fun renameVideo(video: File) {

        val input = EditText(this).apply {
            setText(video.nameWithoutExtension)
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Video")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->

                val newName = input.text.toString().trim()

                if (newName.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Enter a valid name.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val safeName = newName
                    .replace("/", "_")
                    .replace("\\", "_")

                val newFile = File(
                    video.parentFile,
                    "$safeName.${video.extension}"
                )

                if (newFile.exists()) {

                    Toast.makeText(
                        this,
                        "A video with this name already exists.",
                        Toast.LENGTH_SHORT
                    ).show()

                } else if (video.renameTo(newFile)) {

                    Toast.makeText(
                        this,
                        "Video renamed.",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadSavedVideos()

                } else {

                    Toast.makeText(
                        this,
                        "Rename failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    // MOVE TO RECYCLE BIN

    private fun moveToRecycleBin(video: File) {

        AlertDialog.Builder(this)
            .setTitle("Move to Recycle Bin?")
            .setMessage(
                "This video will be moved to the Recycle Bin."
            )
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("MOVE") { _, _ ->

                val bin = getRecycleBinFolder()

                if (!bin.exists()) {
                    bin.mkdirs()
                }

                var destination = File(bin, video.name)

                if (destination.exists()) {
                    destination = File(
                        bin,
                        "${System.currentTimeMillis()}_${video.name}"
                    )
                }

                if (video.renameTo(destination)) {

                    Toast.makeText(
                        this,
                        "Moved to Recycle Bin.",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadSavedVideos()

                } else {

                    Toast.makeText(
                        this,
                        "Could not move video.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    // RECYCLE BIN

    private fun showRecycleBin() {

        val bin = getRecycleBinFolder()

        if (!bin.exists()) {
            bin.mkdirs()
        }

        val files = bin.listFiles()?.filter { it.isFile }
            ?: emptyList()

        if (files.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Recycle Bin")
                .setMessage("Recycle Bin is empty.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val names = files.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Recycle Bin")
            .setItems(names) { _, position ->
                showRecycleBinOptions(files[position])
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showRecycleBinOptions(file: File) {

        val options = arrayOf(
            "Restore",
            "Delete Permanently"
        )

        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->

                when (which) {
                    0 -> restoreVideo(file)
                    1 -> permanentlyDelete(file)
                }
            }
            .show()
    }

    private fun restoreVideo(file: File) {

        val folder = getRecordingsFolder()

        if (!folder.exists()) {
            folder.mkdirs()
        }

        val destination = File(folder, file.name)

        if (destination.exists()) {
            Toast.makeText(
                this,
                "A file with this name already exists.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (file.renameTo(destination)) {

            Toast.makeText(
                this,
                "Video restored.",
                Toast.LENGTH_SHORT
            ).show()

            showRecordingsScreen()

        } else {

            Toast.makeText(
                this,
                "Restore failed.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun permanentlyDelete(file: File) {

        AlertDialog.Builder(this)
            .setTitle("Delete Permanently?")
            .setMessage("This video cannot be recovered.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->

                if (file.delete()) {

                    Toast.makeText(
                        this,
                        "Video permanently deleted.",
                        Toast.LENGTH_SHORT
                    ).show()

                    showRecycleBin()

                } else {

                    Toast.makeText(
                        this,
                        "Delete failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun updateRecordingsStorage() {

        val storageText =
            findViewById<TextView>(R.id.txtRecordingsStorage)

        val usedBytes = getUsedStorageBytes()

        val freeBytes =
            (storageLimitBytes - usedBytes).coerceAtLeast(0L)

        storageText.text =
            "${formatSize(usedBytes)} USED / " +
                "${formatSize(freeBytes)} FREE"
    }

    // SETTINGS

    private fun showSettingsScreen() {

        homeVisible = false

        setContentView(R.layout.activity_settings)

        findViewById<Button>(
            R.id.btnSettingsBack
        ).setOnClickListener {
            showHomeScreen()
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        showHomeScreen()
    }
}