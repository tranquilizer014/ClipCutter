package com.xyz.clipcutter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class InfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        findViewById<Button>(R.id.btnLinkRepo).setOnClickListener {
            openUrl("https://github.com/tranquilizer014/ClipCutter")
        }
        findViewById<Button>(R.id.btnLinkGithubProfile).setOnClickListener {
            openUrl("https://github.com/tranquilizer014")
        }
        findViewById<Button>(R.id.btnLinkTelegram).setOnClickListener {
            openUrl("https://t.me/tranquilizer014")
        }
        findViewById<Button>(R.id.btnLinkReddit).setOnClickListener {
            openUrl("https://www.reddit.com/u/tranquilizer014")
        }
        findViewById<Button>(R.id.btnLinkSubreddit).setOnClickListener {
            openUrl("https://www.reddit.com/r/tranquilizer/")
        }

        findViewById<Button>(R.id.btnShareCrashLog).setOnClickListener {
            shareCrashLog()
        }
        findViewById<Button>(R.id.btnClearCrashLogs).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear crash logs?")
                .setMessage("This deletes all saved crash logs from this device.")
                .setPositiveButton("Clear") { _, _ ->
                    CrashHandler.clearCrashLogs(this)
                    Toast.makeText(this, "Crash logs cleared.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun shareCrashLog() {
        val file = CrashHandler.latestCrashLog(this)
        if (file == null) {
            Toast.makeText(this, "No crash logs saved on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "com.xyz.clipcutter.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share crash log"))
    }
}
