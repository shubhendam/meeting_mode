package com.example.meeting_mode_gemma.processing

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.meeting_mode_gemma.data.MeetingSession
import java.io.File

class AudioProcessor(private val context: Context) {

    companion object {
        private const val TAG = "AudioProcessor"
        private const val CHUNK_DURATION_SECONDS = 30
        private const val OVERLAP_SECONDS = 5
        private const val APP_FOLDER_NAME = "MeetingModeGemma"
    }

    data class AudioChunk(
        val chunkIndex: Int,
        val filePath: String,
        val startTimeSeconds: Float,
        val endTimeSeconds: Float
    )

    fun processAudioFile(session: MeetingSession): List<AudioChunk> {
        val audioFile = File(getSessionDirectory(session), "full_recording.wav")

        Log.d(TAG, "=== PROCESSING AUDIO FILE ===")
        Log.d(TAG, "Looking for audio file at: ${audioFile.absolutePath}")
        Log.d(TAG, "Audio file exists: ${audioFile.exists()}")

        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file does not exist: ${audioFile.absolutePath}")

            // List parent directory contents for debugging
            val parentDir = audioFile.parentFile
            if (parentDir?.exists() == true) {
                Log.d(TAG, "Parent directory contents:")
                parentDir.listFiles()?.forEach { file ->
                    Log.d(TAG, "  - ${file.name} (${file.length()} bytes)")
                }
            } else {
                Log.d(TAG, "Parent directory doesn't exist: ${parentDir?.absolutePath}")
            }

            return emptyList()
        }

        Log.d(TAG, "Audio file size: ${audioFile.length()} bytes")

        return try {
            // Create temp directory for chunks
            val tempDir = File(getSessionDirectory(session), "temp")
            tempDir.mkdirs()
            Log.d(TAG, "Created temp directory: ${tempDir.absolutePath}")

            // For now, create a single chunk (simplified)
            // In a real implementation, you would split the audio based on duration
            val chunkFile = File(tempDir, "chunk_0.wav")
            audioFile.copyTo(chunkFile, overwrite = true)

            Log.d(TAG, "Created chunk file: ${chunkFile.absolutePath}")
            Log.d(TAG, "Chunk file size: ${chunkFile.length()} bytes")
            Log.d(TAG, "Chunk file exists: ${chunkFile.exists()}")

            val chunks = listOf(
                AudioChunk(
                    chunkIndex = 0,
                    filePath = chunkFile.absolutePath,
                    startTimeSeconds = 0f,
                    endTimeSeconds = 30f // Simplified for now
                )
            )

            Log.d(TAG, "Created ${chunks.size} audio chunks for session ${session.sessionId}")

            // Log temp directory contents
            Log.d(TAG, "Temp directory contents:")
            tempDir.listFiles()?.forEach { file ->
                Log.d(TAG, "  - ${file.name} (${file.length()} bytes)")
            }

            chunks
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio file", e)
            emptyList()
        }
    }

    fun cleanupTempFiles(session: MeetingSession) {
        val tempDir = File(getSessionDirectory(session), "temp")
        if (tempDir.exists()) {
            Log.d(TAG, "Cleaning up temp files in: ${tempDir.absolutePath}")

            // Log what we're about to delete
            tempDir.listFiles()?.forEach { file ->
                Log.d(TAG, "  Deleting: ${file.name}")
            }

            val deleted = tempDir.deleteRecursively()
            Log.d(TAG, "Temp files cleanup successful: $deleted")
        } else {
            Log.d(TAG, "No temp directory to clean up")
        }
    }

    private fun getSessionDirectory(session: MeetingSession): File {
        return File(getAppDirectory(), session.folderName)
    }

    private fun getAppDirectory(): File {
        // Get the stored storage path from shared preferences - same as MeetingViewModel
        val storedPath = context.getSharedPreferences("storage_prefs", 0)
            .getString("storage_path", null)

        return if (storedPath != null) {
            File(storedPath)
        } else {
            // Fallback to app-specific directory
            File(context.getExternalFilesDir(null), APP_FOLDER_NAME)
        }
    }
}