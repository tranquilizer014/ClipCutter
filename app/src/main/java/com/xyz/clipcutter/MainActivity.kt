package com.xyz.clipcutter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var seekBar: SeekBar
    private lateinit var txtPosition: TextView
    private lateinit var txtMarks: TextView
    private lateinit var recyclerClips: RecyclerView
    private lateinit var progressExport: ProgressBar
    private lateinit var txtExportStatus: TextView

    private var videoUri: Uri? = null
    private var videoDurationMs: Int = 0
    private var markStartMs: Int? = null
    private var markEndMs: Int? = null

    private val clips = mutableListOf<ClipRange>()
    private lateinit var adapter: ClipAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var isTrackingProgress = false

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadVideo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        videoView = findViewById(R.id.videoView)
        seekBar = findViewById(R.id.seekBar)
        txtPosition = findViewById(R.id.txtPosition)
        txtMarks = findViewById(R.id.txtMarks)
        recyclerClips = findViewById(R.id.recyclerClips)
        progressExport = findViewById(R.id.progressExport)
        txtExportStatus = findViewById(R.id.txtExportStatus)

        adapter = ClipAdapter(clips) { index ->
            clips.removeAt(index)
            adapter.notifyDataSetChanged()
        }
        recyclerClips.layoutManager = LinearLayoutManager(this)
        recyclerClips.adapter = adapter

        findViewById<Button>(R.id.btnPickVideo).setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }

        findViewById<Button>(R.id.btnPlayPause).setOnClickListener {
            if (videoView.isPlaying) videoView.pause() else videoView.start()
        }

        findViewById<Button>(R.id.btnMarkStart).setOnClickListener {
            markStartMs = videoView.currentPosition
            updateMarksLabel()
        }

        findViewById<Button>(R.id.btnMarkEnd).setOnClickListener {
            markEndMs = videoView.currentPosition
            updateMarksLabel()
        }

        findViewById<Button>(R.id.btnAddClip).setOnClickListener {
            addClipFromMarks()
        }

        findViewById<Button>(R.id.btnExportAll).setOnClickListener {
            promptNameAndExport()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) videoView.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun loadVideo(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers don't support persistable permissions; safe to ignore for one-off playback.
        }

        videoUri = uri
        markStartMs = null
        markEndMs = null
        updateMarksLabel()

        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            videoDurationMs = mp.duration
            seekBar.max = videoDurationMs
            videoView.start()
            startProgressLoop()
        }
        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "Could not play this video format for preview.", Toast.LENGTH_LONG).show()
            true
        }
    }

    private fun startProgressLoop() {
        if (isTrackingProgress) return
        isTrackingProgress = true
        val runnable = object : Runnable {
            override fun run() {
                val pos = videoView.currentPosition
                seekBar.progress = pos
                txtPosition.text = "${ClipRange.formatTime(pos)} / ${ClipRange.formatTime(videoDurationMs)}"
                handler.postDelayed(this, 200)
            }
        }
        handler.post(runnable)
    }

    private fun updateMarksLabel() {
        val s = markStartMs?.let { ClipRange.formatTime(it) } ?: "--"
        val e = markEndMs?.let { ClipRange.formatTime(it) } ?: "--"
        txtMarks.text = "Start: $s | End: $e"
    }

    private fun addClipFromMarks() {
        val start = markStartMs
        val end = markEndMs
        if (start == null || end == null) {
            Toast.makeText(this, "Mark both a start and end point first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (end <= start) {
            Toast.makeText(this, "End must be after start.", Toast.LENGTH_SHORT).show()
            return
        }
        clips.add(ClipRange(start, end))
        adapter.notifyItemInserted(clips.size - 1)
        markStartMs = null
        markEndMs = null
        updateMarksLabel()
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
        videoView.pause()
        progressExport.visibility = ProgressBar.VISIBLE
        txtExportStatus.visibility = TextView.VISIBLE
        progressExport.max = clips.size
        progressExport.progress = 0

        val outDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (outDir != null && !outDir.exists()) outDir.mkdirs()

        val safInput = FFmpegKitConfig.getSafParameterForRead(this, sourceUri)
        val exportedFiles = mutableListOf<File>()

        lifecycleScope.launch(Dispatchers.IO) {
            for ((index, clip) in clips.withIndex()) {
                withContext(Dispatchers.Main) {
                    txtExportStatus.text = "Exporting clip ${index + 1} of ${clips.size}..."
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
                } else {
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
            }

            withContext(Dispatchers.Main) {
                progressExport.visibility = ProgressBar.GONE
                txtExportStatus.visibility = TextView.GONE
                showExportResults(exportedFiles)
            }
        }
    }

    private fun showExportResults(files: List<File>) {
        if (files.isEmpty()) {
            Toast.makeText(this, "No clips exported successfully.", Toast.LENGTH_LONG).show()
            return
        }

        val container = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("Exported ${files.size} clip(s)")
            .setMessage(files.joinToString("\n") { it.name })
            .setPositiveButton("Share first clip") { _, _ -> shareFile(files.first()) }
            .setNegativeButton("Done", null)

        dialogBuilder.show()
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
