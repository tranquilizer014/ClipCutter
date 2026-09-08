package com.xyz.clipcutter

import org.json.JSONArray
import org.json.JSONObject

data class ClipRange(
    val startMs: Int,
    val endMs: Int
) {
    val durationMs: Int get() = endMs - startMs

    fun label(index: Int): String {
        return "Clip ${index + 1}:  ${formatTime(startMs)}  -  ${formatTime(endMs)}  (${formatTime(durationMs)})"
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("start", startMs)
        obj.put("end", endMs)
        return obj
    }

    companion object {
        fun formatTime(ms: Int): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

        /** Parses "mm:ss" or plain seconds into milliseconds. Returns null if invalid. */
        fun parseTimeToMs(input: String): Int? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            return try {
                if (trimmed.contains(":")) {
                    val parts = trimmed.split(":")
                    if (parts.size != 2) return null
                    val minutes = parts[0].trim().toInt()
                    val seconds = parts[1].trim().toDouble()
                    ((minutes * 60 + seconds) * 1000).toInt()
                } else {
                    (trimmed.toDouble() * 1000).toInt()
                }
            } catch (e: NumberFormatException) {
                null
            }
        }

        fun fromJson(obj: JSONObject): ClipRange {
            return ClipRange(obj.getInt("start"), obj.getInt("end"))
        }

        fun listToJson(clips: List<ClipRange>): String {
            val arr = JSONArray()
            clips.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String): MutableList<ClipRange> {
            val result = mutableListOf<ClipRange>()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    result.add(fromJson(arr.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // Corrupt or empty saved state — just start fresh.
            }
            return result
        }
    }
}
