package com.example.meeting_mode_gemma.ui

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meeting_mode_gemma.audio.AudioRecorder
import com.example.meeting_mode_gemma.data.MeetingSession
import com.example.meeting_mode_gemma.data.MeetingStatus
import com.example.meeting_mode_gemma.processing.AudioProcessor
import com.example.meeting_mode_gemma.processing.GemmaAudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MeetingUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val currentStatus: MeetingStatus = MeetingStatus.RECORDING,
    val processingMessage: String = "",
    val currentSession: MeetingSession? = null,
    val completedMeetings: List<MeetingSession> = emptyList(),
    val showCompletedDialog: Boolean = false
)

class MeetingViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application
    private val audioRecorder = AudioRecorder(context)
    private val audioProcessor = AudioProcessor(context)
    private val gemmaEngine = GemmaAudioEngine(context)

    private val _uiState = MutableStateFlow(MeetingUiState())
    val uiState = _uiState.asStateFlow()

    companion object {
        private const val TAG = "MeetingViewModel"
        private const val APP_FOLDER_NAME = "MeetingModeGemma"
    }

    init {
        initializeStorage()
        loadCompletedMeetings()
    }

    private fun initializeStorage() {
        Log.d(TAG, "=== INITIALIZING STORAGE ===")

        // Try multiple storage locations in order of preference
        val storageOptions = listOf(
            // Option 1: Downloads directory (publicly accessible, no special permissions needed)
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APP_FOLDER_NAME),
            // Option 2: Documents directory (publicly accessible)
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), APP_FOLDER_NAME),
            // Option 3: App-specific external files directory (always works, but harder to find)
            File(context.getExternalFilesDir(null), APP_FOLDER_NAME)
        )

        var selectedStorage: File? = null

        for (storageDir in storageOptions) {
            Log.d(TAG, "Trying storage location: ${storageDir.absolutePath}")

            try {
                if (!storageDir.exists()) {
                    val created = storageDir.mkdirs()
                    Log.d(TAG, "  Directory creation result: $created")
                }

                if (storageDir.exists() && storageDir.canWrite()) {
                    Log.d(TAG, "  SUCCESS: Storage location is writable")
                    selectedStorage = storageDir
                    break
                } else {
                    Log.d(TAG, "  FAILED: Directory exists: ${storageDir.exists()}, writable: ${storageDir.canWrite()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "  ERROR: Failed to create directory", e)
            }
        }

        if (selectedStorage != null) {
            Log.d(TAG, "Selected storage location: ${selectedStorage.absolutePath}")
            // Store the selected storage for later use
            context.getSharedPreferences("storage_prefs", 0)
                .edit()
                .putString("storage_path", selectedStorage.absolutePath)
                .apply()
        } else {
            Log.e(TAG, "CRITICAL: No writable storage location found!")
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            val session = MeetingSession.create()

            // Create session directory BEFORE attempting to record
            val sessionDir = getSessionDirectory(session)
            Log.d(TAG, "=== CREATING SESSION DIRECTORY ===")
            Log.d(TAG, "Session directory: ${sessionDir.absolutePath}")

            if (!sessionDir.exists()) {
                val created = sessionDir.mkdirs()
                Log.d(TAG, "Session directory creation result: $created")

                if (!created && !sessionDir.exists()) {
                    Log.e(TAG, "CRITICAL: Failed to create session directory!")
                    _uiState.update {
                        it.copy(
                            currentStatus = MeetingStatus.ERROR,
                            processingMessage = "Failed to create recording directory. Storage not accessible."
                        )
                    }
                    return@launch
                }
            }

            Log.d(TAG, "Session directory ready: ${sessionDir.exists()}")
            Log.d(TAG, "Session directory writable: ${sessionDir.canWrite()}")

            val audioFilePath = getAudioFilePath(session)
            Log.d(TAG, "Audio file path: $audioFilePath")

            if (audioRecorder.startRecording(audioFilePath)) {
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        currentSession = session,
                        currentStatus = MeetingStatus.RECORDING,
                        processingMessage = ""
                    )
                }
                Log.d(TAG, "Started recording for session: ${session.sessionId}")
            } else {
                Log.e(TAG, "Failed to start recording")
                _uiState.update {
                    it.copy(
                        currentStatus = MeetingStatus.ERROR,
                        processingMessage = "Failed to start recording. Check permissions."
                    )
                }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            audioRecorder.stopRecording()

            _uiState.update {
                it.copy(
                    isRecording = false,
                    isProcessing = true,
                    currentStatus = MeetingStatus.PROCESSING_AUDIO,
                    processingMessage = "Processing your recording...",
                    showCompletedDialog = true
                )
            }

            // Start background processing
            uiState.value.currentSession?.let { session ->
                startBackgroundProcessing(session)
            }
        }
    }

    private fun startBackgroundProcessing(session: MeetingSession) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Check if audio file was created
                    val audioFile = File(getAudioFilePath(session))
                    Log.d(TAG, "=== CHECKING AUDIO FILE ===")
                    Log.d(TAG, "Looking for audio file at: ${audioFile.absolutePath}")
                    Log.d(TAG, "Audio file exists: ${audioFile.exists()}")

                    if (!audioFile.exists()) {
                        // List the session directory to see what's there
                        val sessionDir = getSessionDirectory(session)
                        Log.e(TAG, "Audio file missing! Session directory contents:")
                        sessionDir.listFiles()?.forEach { file ->
                            Log.e(TAG, "  Found: ${file.name} (${file.length()} bytes)")
                        } ?: Log.e(TAG, "  Session directory is empty or null")

                        throw Exception("Audio file was not created at: ${audioFile.absolutePath}")
                    }

                    Log.d(TAG, "=== AUDIO FILE FOUND ===")
                    Log.d(TAG, "Audio file size: ${audioFile.length()} bytes")

                    // List all files in session directory
                    val sessionDir = getSessionDirectory(session)
                    Log.d(TAG, "Session directory contents:")
                    sessionDir.listFiles()?.forEach { file ->
                        Log.d(TAG, "  - ${file.name} (${file.length()} bytes)")
                    }

                    // Process audio chunks
                    _uiState.update { it.copy(processingMessage = "Creating audio chunks...") }
                    val chunks = audioProcessor.processAudioFile(session)

                    if (chunks.isEmpty()) {
                        throw Exception("No audio chunks created")
                    }

                    Log.d(TAG, "Created ${chunks.size} audio chunks")

                    // Initialize transcription file
                    val transcriptionFile = File(getSessionDirectory(session), "transcription.txt")
                    transcriptionFile.writeText("") // Start with empty file

                    // Transcribe chunks sequentially
                    _uiState.update { it.copy(processingMessage = "Transcribing audio...", currentStatus = MeetingStatus.TRANSCRIBING) }

                    chunks.forEachIndexed { index, chunk ->
                        _uiState.update { it.copy(processingMessage = "Transcribing chunk ${index + 1}/${chunks.size}...") }
                        Log.d(TAG, "Processing chunk ${index + 1}/${chunks.size}: ${chunk.filePath}")

                        val result = gemmaEngine.transcribeAudio(chunk.filePath)
                        result.onSuccess { transcription ->
                            val chunkText = "Chunk ${index + 1}: $transcription\n\n"

                            // Append to transcription file immediately
                            transcriptionFile.appendText(chunkText)
                            Log.d(TAG, "Chunk ${index + 1} completed and saved")

                        }.onFailure { error ->
                            Log.e(TAG, "Transcription failed for chunk ${index + 1}", error)
                            val errorText = "Chunk ${index + 1}: [Transcription failed: ${error.message}]\n\n"
                            transcriptionFile.appendText(errorText)
                        }
                    }

                    Log.d(TAG, "All chunks transcribed. Reading full transcription...")

                    // Read the complete transcription
                    val fullTranscription = transcriptionFile.readText()

                    if (fullTranscription.isBlank()) {
                        throw Exception("No transcription content was generated")
                    }

                    // Generate summary
                    _uiState.update { it.copy(processingMessage = "Generating meeting summary...", currentStatus = MeetingStatus.SUMMARIZING) }
                    Log.d(TAG, "Starting summary generation with ${fullTranscription.length} characters")

                    val summaryResult = gemmaEngine.generateSummary(fullTranscription)
                    summaryResult.onSuccess { summary ->
                        val summaryFile = File(getSessionDirectory(session), "summary.txt")
                        summaryFile.writeText(summary)
                        Log.d(TAG, "Summary saved: ${summaryFile.absolutePath}")
                    }.onFailure { error ->
                        Log.e(TAG, "Summary generation failed", error)
                        val summaryFile = File(getSessionDirectory(session), "summary.txt")
                        summaryFile.writeText("Summary generation failed: ${error.message}\n\nTranscription:\n$fullTranscription")
                    }

                    // Cleanup temp files
                    audioProcessor.cleanupTempFiles(session)

                    // Final file verification
                    Log.d(TAG, "=== FINAL FILES CREATED ===")
                    val finalSessionDir = getSessionDirectory(session)
                    finalSessionDir.listFiles()?.forEach { file ->
                        Log.d(TAG, "  - ${file.name} (${file.length()} bytes) - ${file.absolutePath}")
                    }
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        currentStatus = MeetingStatus.COMPLETED,
                        processingMessage = "Processing complete!"
                    )
                }
                loadCompletedMeetings()

            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        currentStatus = MeetingStatus.ERROR,
                        processingMessage = "Processing failed: ${e.message}"
                    )
                }
            } finally {
                // Always cleanup engine resources
                gemmaEngine.cleanup()
            }
        }
    }

    fun dismissCompletedDialog() {
        _uiState.update { it.copy(showCompletedDialog = false) }
    }

    fun openMeetingSummary(session: MeetingSession) {
        val sessionDir = getSessionDirectory(session)
        Log.d(TAG, "=== OPENING MEETING SUMMARY ===")
        Log.d(TAG, "Session directory: ${sessionDir.absolutePath}")

        val audioFile = File(sessionDir, "full_recording.wav")
        val transcriptionFile = File(sessionDir, "transcription.txt")
        val summaryFile = File(sessionDir, "summary.txt")

        Log.d(TAG, "Audio file exists: ${audioFile.exists()} (${audioFile.length()} bytes)")
        Log.d(TAG, "Transcription exists: ${transcriptionFile.exists()}")
        Log.d(TAG, "Summary exists: ${summaryFile.exists()}")

        if (transcriptionFile.exists()) {
            try {
                val transcriptionContent = transcriptionFile.readText()
                Log.d(TAG, "Transcription content preview: ${transcriptionContent.take(200)}...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read transcription", e)
            }
        }
    }

    private fun loadCompletedMeetings() {
        viewModelScope.launch {
            val meetingsDir = getAppDirectory()
            if (!meetingsDir.exists()) {
                Log.d(TAG, "Meetings directory doesn't exist yet")
                return@launch
            }

            Log.d(TAG, "Loading meetings from: ${meetingsDir.absolutePath}")

            val completedMeetings = meetingsDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("meeting_") }
                ?.mapNotNull { dir ->
                    Log.d(TAG, "Found meeting directory: ${dir.name}")
                    val audioFile = File(dir, "full_recording.wav")
                    if (audioFile.exists()) {
                        Log.d(TAG, "  - Has audio file: ${audioFile.length()} bytes")
                        MeetingSession(
                            sessionId = dir.name,
                            timestamp = audioFile.lastModified(),
                            folderName = dir.name,
                            audioFilePath = "${dir.name}/full_recording.wav",
                            transcriptionPath = "${dir.name}/transcription.txt",
                            summaryPath = "${dir.name}/summary.txt",
                            status = if (File(dir, "summary.txt").exists()) MeetingStatus.COMPLETED else MeetingStatus.PROCESSING_AUDIO
                        )
                    } else {
                        Log.d(TAG, "  - No audio file found")
                        null
                    }
                }
                ?.sortedByDescending { it.timestamp }
                ?: emptyList()

            Log.d(TAG, "Loaded ${completedMeetings.size} completed meetings")
            _uiState.update { it.copy(completedMeetings = completedMeetings) }
        }
    }

    private fun getAudioFilePath(session: MeetingSession): String {
        val sessionDir = getSessionDirectory(session)
        val audioFile = File(sessionDir, "full_recording.wav")
        Log.d(TAG, "Audio file path: ${audioFile.absolutePath}")
        return audioFile.absolutePath
    }

    private fun getSessionDirectory(session: MeetingSession): File {
        return File(getAppDirectory(), session.folderName)
    }

    private fun getAppDirectory(): File {
        // Get the stored storage path from shared preferences
        val storedPath = context.getSharedPreferences("storage_prefs", 0)
            .getString("storage_path", null)

        return if (storedPath != null) {
            File(storedPath)
        } else {
            // Fallback to app-specific directory
            File(context.getExternalFilesDir(null), APP_FOLDER_NAME)
        }
    }

    fun getStorageLocationForUI(): String {
        val appDir = getAppDirectory()
        return when {
            appDir.absolutePath.contains("/Download/") -> "/Downloads/MeetingModeGemma/"
            appDir.absolutePath.contains("/Documents/") -> "/Documents/MeetingModeGemma/"
            else -> "App Files/MeetingModeGemma/"
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up engine resources when ViewModel is destroyed
        gemmaEngine.cleanup()
    }
}