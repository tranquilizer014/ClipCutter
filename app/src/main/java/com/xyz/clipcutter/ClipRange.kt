package com.xyz.clipcutter

data class ClipRange(
    val startMs: Int,
    val endMs: Int
) {
    val durationMs: Int get() = endMs - startMs

    fun label(index: Int): String {
        return "Clip ${index + 1}:  ${formatTime(startMs)}  -  ${formatTime(endMs)}  (${formatTime(durationMs)})"
    }

    companion object {
        fun formatTime(ms: Int): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
    }
}
