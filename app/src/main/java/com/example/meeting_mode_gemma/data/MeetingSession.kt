package com.example.meeting_mode_gemma.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MeetingSession(
    val sessionId: String,
    val timestamp: Long,
    val folderName: String,
    val audioFilePath: String,
    val transcriptionPath: String,
    val summaryPath: String,
    val status: MeetingStatus = MeetingStatus.RECORDING
) {
    companion object {
        fun create(): MeetingSession {
            val timestamp = System.currentTimeMillis()
            val formatter = SimpleDateFormat("hh_mma_dd_MMM_yyyy", Locale.getDefault())
            val folderName = "meeting_${formatter.format(Date(timestamp))}"
            val sessionId = timestamp.toString()

            return MeetingSession(
                sessionId = sessionId,
                timestamp = timestamp,
                folderName = folderName,
                audioFilePath = "${folderName}/full_recording.wav",
                transcriptionPath = "${folderName}/transcription.txt",
                summaryPath = "${folderName}/summary.txt"
            )
        }
    }

    fun getDisplayName(): String {
        val formatter = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}

enum class MeetingStatus {
    RECORDING,
    PROCESSING_AUDIO,
    TRANSCRIBING,
    SUMMARIZING,
    COMPLETED,
    ERROR
}