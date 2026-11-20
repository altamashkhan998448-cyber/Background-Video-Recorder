package com.backgroundvideorecorder.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import java.io.File

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

        statusText.text = "● READY TO SET UP"
        startButton.text = "START RECORDING"

        startButton.setOnClickListener {
            if (recordingStarted) {
                stopVideoRecording()
            } else {
                pendingStart = true
                requestRecordingPermissions(audioSwitch.isChecked)
            }
        }

        cameraGroup.setOnCheckedChangeListener { _, _ -> }

        qualitySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
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
            pendingStart = false
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
                    "Required permissions were not granted.",
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

        val camera =
            if (cameraGroup.checkedRadioButtonId == R.id.rbFrontCamera) {
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

    // RECORDINGS SCREEN

    private fun showRecordingsScreen() {

        setContentView(R.layout.activity_recordings)

        findViewById<Button>(R.id.btnBackRecordings).setOnClickListener {
            showHomeScreen()
        }

        findViewById<Button>(R.id.btnRecycleBin).setOnClickListener {
            showRecycleBin()
        }

        loadSavedVideos()
    }

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

        updateStorageInformation()
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
            .setMessage("This video will be moved to the Recycle Bin.")
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

    // STORAGE INFORMATION

    private fun updateStorageInformation() {

        val storageText =
            findViewById<TextView>(R.id.txtRecordingsStorage)

        val folder = getRecordingsFolder()

        val usedBytes = folder.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

        val freeBytes = (1024L * 1024L * 1024L - usedBytes)
            .coerceAtLeast(0L)

        storageText.text =
            "${formatSize(usedBytes)} USED / ${formatSize(freeBytes)} FREE"
    }

    private fun formatSize(bytes: Long): String {

        val mb = bytes / (1024.0 * 1024.0)

        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
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