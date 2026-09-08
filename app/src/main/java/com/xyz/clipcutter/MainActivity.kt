package com.xyz.clipcutter

import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var seekBar: SeekBar
    private lateinit var txtPosition: TextView
    private lateinit var recyclerClips: RecyclerView
    private lateinit var progressExport: ProgressBar
    private lateinit var txtExportStatus: TextView
    private lateinit var filmstripContainer: LinearLayout
    private lateinit var edtStartTime: EditText
    private lateinit var edtEndTime: EditText
    private lateinit var btnAddClip: Button
    private lateinit var btnCancelEdit: Button
    private lateinit var btnPlayPause: Button
    private lateinit var btnSpeed: Button
    private lateinit var btnCancelExport: Button

    private var videoUri: Uri? = null
    private var videoDurationMs: Int = 0
    private var markStartMs: Int? = null
    private var markEndMs: Int? = null
    private var previewEndMs: Int? = null
    private var editingIndex: Int? = null

    private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private var speedIndex = 2 // 1x

    private val clips = mutableListOf<ClipRange>()
    private lateinit var adapter: ClipAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var isTrackingProgress = false

    private var exportJob: Job? = null
    @Volatile private var isExportCancelled = false

    private lateinit var prefs: SharedPreferences

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadVideo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("clipcutter_prefs", MODE_PRIVATE)

        playerView = findViewById(R.id.playerView)
        seekBar = findViewById(R.id.seekBar)
        txtPosition = findViewById(R.id.txtPosition)
        recyclerClips = findViewById(R.id.recyclerClips)
        progressExport = findViewById(R.id.progressExport)
        txtExportStatus = findViewById(R.id.txtExportStatus)
        filmstripContainer = findViewById(R.id.filmstripContainer)
        edtStartTime = findViewById(R.id.edtStartTime)
        edtEndTime = findViewById(R.id.edtEndTime)
        btnAddClip = findViewById(R.id.btnAddClip)
        btnCancelEdit = findViewById(R.id.btnCancelEdit)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnCancelExport = findViewById(R.id.btnCancelExport)

        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.text = if (isPlaying) "Pause" else "Play"
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && videoDurationMs == 0) {
                    videoDurationMs = exoPlayer.duration.toInt().coerceAtLeast(0)
                    seekBar.max = videoDurationMs
                    generateFilmstrip(videoUri, videoDurationMs)
                }
            }
        })

        adapter = ClipAdapter(
            clips,
            onPreview = { index -> previewClip(index) },
            onEdit = { index -> startEditClip(index) },
            onRemove = { index -> removeClip(index) }
        )
        recyclerClips.layoutManager = LinearLayoutManager(this)
        recyclerClips.adapter = adapter

        findViewById<Button>(R.id.btnPickVideo).setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        findViewById<Button>(R.id.btnInfo).setOnClickListener {
            startActivity(Intent(this, InfoActivity::class.java))
        }

        btnPlayPause.setOnClickListener {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }

        findViewById<Button>(R.id.btnMinus10).setOnClickListener { nudge(-10_000) }
        findViewById<Button>(R.id.btnMinus5).setOnClickListener { nudge(-5_000) }
        findViewById<Button>(R.id.btnPlus5).setOnClickListener { nudge(5_000) }
        findViewById<Button>(R.id.btnPlus10).setOnClickListener { nudge(10_000) }

        btnSpeed.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            val speed = speeds[speedIndex]
            exoPlayer.setPlaybackSpeed(speed)
            btnSpeed.text = "Speed: ${formatSpeed(speed)}x"
        }

        findViewById<Button>(R.id.btnMarkStart).setOnClickListener {
            markStartMs = exoPlayer.currentPosition.toInt()
            edtStartTime.setText(ClipRange.formatTime(markStartMs!!))
        }

        findViewById<Button>(R.id.btnMarkEnd).setOnClickListener {
            markEndMs = exoPlayer.currentPosition.toInt()
            edtEndTime.setText(ClipRange.formatTime(markEndMs!!))
        }

        btnAddClip.setOnClickListener { addOrUpdateClip() }

        btnCancelEdit.setOnClickListener { exitEditMode() }

        findViewById<Button>(R.id.btnExportAll).setOnClickListener {
            promptNameAndExport()
        }

        btnCancelExport.setOnClickListener {
            isExportCancelled = true
            FFmpegKit.cancel()
            txtExportStatus.text = "Cancelling..."
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) exoPlayer.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        restoreState()
        startProgressLoop()
        checkForCrashLogs()
    }

    private fun checkForCrashLogs() {
        if (!CrashHandler.hasCrashLogs(this)) return
        AlertDialog.Builder(this)
            .setTitle("Crash log found")
            .setMessage("ClipCutter crashed last time it ran. A log was saved locally — share it so it can be looked into?")
            .setPositiveButton("Share") { _, _ -> shareCrashLog() }
            .setNegativeButton("Dismiss", null)
            .setNeutralButton("Delete") { _, _ -> CrashHandler.clearCrashLogs(this) }
            .show()
    }

    private fun shareCrashLog() {
        val file = CrashHandler.latestCrashLog(this) ?: return
        val uri = FileProvider.getUriForFile(this, "com.xyz.clipcutter.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share crash log"))
    }

    private fun formatSpeed(speed: Float): String {
        return if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    }

    private fun nudge(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0, videoDurationMs.toLong())
        exoPlayer.seekTo(target)
    }

    private fun loadVideo(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers (notably some Gallery/Photos apps) don't support persistable
            // permissions. Playback still works this session; it just may not survive
            // a full app restart. Not fatal.
        }

        videoUri = uri
        videoDurationMs = 0
        markStartMs = null
        markEndMs = null
        filmstripContainer.removeAllViews()

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        saveState()
    }

    private fun startProgressLoop() {
        if (isTrackingProgress) return
        isTrackingProgress = true
        val runnable = object : Runnable {
            override fun run() {
                val pos = exoPlayer.currentPosition.toInt()
                if (videoDurationMs > 0) {
                    seekBar.max = videoDurationMs
                    seekBar.progress = pos.coerceAtMost(videoDurationMs)
                    txtPosition.text = "${ClipRange.formatTime(pos)} / ${ClipRange.formatTime(videoDurationMs)}"
                }
                val previewEnd = previewEndMs
                if (previewEnd != null && pos >= previewEnd) {
                    exoPlayer.pause()
                    previewEndMs = null
                }
                handler.postDelayed(this, 200)
            }
        }
        handler.post(runnable)
    }

    private fun previewClip(index: Int) {
        val clip = clips.getOrNull(index) ?: return
        exoPlayer.seekTo(clip.startMs.toLong())
        previewEndMs = clip.endMs
        exoPlayer.play()
    }

    private fun startEditClip(index: Int) {
        val clip = clips.getOrNull(index) ?: return
        editingIndex = index
        markStartMs = clip.startMs
        markEndMs = clip.endMs
        edtStartTime.setText(ClipRange.formatTime(clip.startMs))
        edtEndTime.setText(ClipRange.formatTime(clip.endMs))
        btnAddClip.text = "Update Clip ${index + 1}"
        btnCancelEdit.visibility = Button.VISIBLE
        exoPlayer.seekTo(clip.startMs.toLong())
    }

    private fun exitEditMode() {
        editingIndex = null
        markStartMs = null
        markEndMs = null
        edtStartTime.text.clear()
        edtEndTime.text.clear()
        btnAddClip.text = "Add Clip to List"
        btnCancelEdit.visibility = Button.GONE
    }

    private fun removeClip(index: Int) {
        if (index < 0 || index >= clips.size) return
        clips.removeAt(index)
        adapter.notifyDataSetChanged()
        if (editingIndex == index) exitEditMode()
        saveState()
    }

    /** Reads typed mm:ss fields if both are valid; otherwise falls back to the marked positions. */
    private fun addOrUpdateClip() {
        val typedStart = ClipRange.parseTimeToMs(edtStartTime.text.toString())
        val typedEnd = ClipRange.parseTimeToMs(edtEndTime.text.toString())

        val start = typedStart ?: markStartMs
        val end = typedEnd ?: markEndMs

        if (start == null || end == null) {
            Toast.makeText(this, "Set both a start and end point first (mark or type them).", Toast.LENGTH_SHORT).show()
            return
        }
        if (end <= start) {
            Toast.makeText(this, "End must be after start.", Toast.LENGTH_SHORT).show()
            return
        }
        if (videoDurationMs > 0 && end > videoDurationMs) {
            Toast.makeText(this, "End is beyond the video's length.", Toast.LENGTH_SHORT).show()
            return
        }

        val newClip = ClipRange(start, end)
        val editIdx = editingIndex
        if (editIdx != null) {
            clips[editIdx] = newClip
            adapter.notifyItemChanged(editIdx)
        } else {
            clips.add(newClip)
            adapter.notifyItemInserted(clips.size - 1)
        }

        exitEditMode()
        saveState()
    }

    private fun promptNameAndExport() {
        val uri = videoUri
        if (uri == null) {
            Toast.makeText(this, "Pick a video first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (clips.isEmpty()) {
            Toast.makeText(this, "Add at least one clip first.", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this)
        input.hint = "e.g. MyPodcastEp12"

        AlertDialog.Builder(this)
            .setTitle("Name for this batch")
            .setView(input)
            .setPositiveButton("Export") { _, _ ->
                val rawName = input.text.toString().ifBlank { "clip" }
                val safeName = rawName.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "clip" }
                exportAllClips(uri, safeName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportAllClips(sourceUri: Uri, baseName: String) {
        exoPlayer.pause()
        isExportCancelled = false
        progressExport.visibility = ProgressBar.VISIBLE
        txtExportStatus.visibility = TextView.VISIBLE
        btnCancelExport.visibility = Button.VISIBLE
        progressExport.max = clips.size
        progressExport.progress = 0

        val outDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (outDir != null && !outDir.exists()) outDir.mkdirs()

        val safInput = FFmpegKitConfig.getSafParameterForRead(this, sourceUri)
        val exportedFiles = mutableListOf<File>()
        val clipsSnapshot = clips.toList()

        exportJob = lifecycleScope.launch(Dispatchers.IO) {
            for ((index, clip) in clipsSnapshot.withIndex()) {
                if (isExportCancelled) break

                withContext(Dispatchers.Main) {
                    txtExportStatus.text = "Exporting clip ${index + 1} of ${clipsSnapshot.size}..."
                }

                val outputFile = File(outDir, "$baseName clip ${index + 1}.mp4")
                val startSec = clip.startMs / 1000.0
                val durationSec = clip.durationMs / 1000.0

                val vf = "scale=-2:720:force_original_aspect_ratio=decrease,pad=ceil(iw/2)*2:ceil(ih/2)*2"

                val command = "-y -ss $startSec -i \"$safInput\" -t $durationSec " +
                        "-vf \"$vf\" -c:v h264_mediacodec -b:v 3M -c:a aac -b:a 128k " +
                        "-movflags +faststart \"${outputFile.absolutePath}\""

                val session = FFmpegKit.execute(command)

                if (ReturnCode.isSuccess(session.returnCode)) {
                    exportedFiles.add(outputFile)
                } else if (!ReturnCode.isCancel(session.returnCode)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Clip ${index + 1} failed to export. Check logcat for the FFmpeg error.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                withContext(Dispatchers.Main) {
                    progressExport.progress = index + 1
                }

                if (isExportCancelled) break
            }

            withContext(Dispatchers.Main) {
                progressExport.visibility = ProgressBar.GONE
                txtExportStatus.visibility = TextView.GONE
                btnCancelExport.visibility = Button.GONE
                if (isExportCancelled) {
                    Toast.makeText(this@MainActivity, "Export cancelled. ${exportedFiles.size} clip(s) finished before stopping.", Toast.LENGTH_LONG).show()
                }
                showExportResults(exportedFiles)
            }
        }
    }

    private fun showExportResults(files: List<File>) {
        if (files.isEmpty()) {
            Toast.makeText(this, "No clips exported successfully.", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Exported ${files.size} clip(s)")
            .setMessage(files.joinToString("\n") { it.name })
            .setPositiveButton("Share first clip") { _, _ -> shareFile(files.first()) }
            .setNegativeButton("Done", null)
            .show()
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "com.xyz.clipcutter.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share clip"))
    }

    private fun generateFilmstrip(uri: Uri?, durationMs: Int) {
        if (uri == null || durationMs <= 0) return
        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this@MainActivity, uri)
                val count = 20
                val stepUs = (durationMs.toLong() * 1000) / count
                for (i in 0 until count) {
                    val timeUs = stepUs * i
                    val frame = try {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (e: Exception) {
                        null
                    }
                    withContext(Dispatchers.Main) {
                        val iv = ImageView(this@MainActivity)
                        val widthPx = (36 * resources.displayMetrics.density).toInt()
                        iv.layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT)
                        iv.scaleType = ImageView.ScaleType.CENTER_CROP
                        if (frame != null) iv.setImageBitmap(frame)
                        filmstripContainer.addView(iv)
                    }
                }
            } catch (e: Exception) {
                // Some formats/content providers don't support frame extraction.
                // The filmstrip just stays empty in that case — not fatal to the app.
            } finally {
                retriever.release()
            }
        }
    }

    private fun saveState() {
        prefs.edit()
            .putString("video_uri", videoUri?.toString())
            .putString("clips_json", ClipRange.listToJson(clips))
            .apply()
    }

    private fun restoreState() {
        val savedClipsJson = prefs.getString("clips_json", null)
        if (savedClipsJson != null) {
            clips.clear()
            clips.addAll(ClipRange.listFromJson(savedClipsJson))
            adapter.notifyDataSetChanged()
        }

        val savedUriString = prefs.getString("video_uri", null)
        if (savedUriString != null) {
            try {
                val uri = Uri.parse(savedUriString)
                loadVideo(uri)
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't reopen the last video — please pick it again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveState()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        exportJob?.cancel()
        exoPlayer.release()
    }
}
