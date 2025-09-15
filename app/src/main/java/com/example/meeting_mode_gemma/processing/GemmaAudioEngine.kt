package com.example.meeting_mode_gemma.processing

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GemmaAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "GemmaAudioEngine"
        private const val MODEL_PATH = "/data/local/tmp/gemma-3n-E2B-it-int4.task"
        private const val TRANSCRIPTION_TIMEOUT_SECONDS = 120L
        private const val SUMMARIZATION_TIMEOUT_SECONDS = 180L
    }

    private var transcriptionEngine: LlmInference? = null
    private var transcriptionSession: LlmInferenceSession? = null
    private var summarizationEngine: LlmInference? = null
    private var summarizationSession: LlmInferenceSession? = null

    /**
     * Transcribes an audio file chunk and returns the transcription result
     */
    suspend fun transcribeAudio(audioFilePath: String): Result<String> {
        return try {
            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) {
                return Result.failure(Exception("Audio file not found: $audioFilePath"))
            }

            Log.d(TAG, "Transcribing audio file: $audioFilePath (${audioFile.length()} bytes)")

            // Initialize transcription engine and session if needed
            if (transcriptionEngine == null || transcriptionSession == null) {
                initializeTranscriptionEngine()
            }

            val session = transcriptionSession ?: return Result.failure(Exception("Transcription session not initialized"))

            // Read audio file as ByteArray
            val audioBytes = readAudioFileAsBytes(audioFile)

            // Create transcription prompt
            val transcriptionPrompt = """
                Transcribe and translate this audio to English. If the speech is in any language other than English, provide an accurate English translation. If the speech is already in English, transcribe it exactly. Be accurate and maintain the original meaning. Only return the transcribed/translated text without any additional commentary.
            """.trimIndent()

            // Process audio using session following your JetsonViewModel pattern
            val transcriptionResult = processAudioWithSession(session, audioBytes, transcriptionPrompt)

            Log.d(TAG, "Transcription completed: ${transcriptionResult.take(100)}...")
            Result.success(transcriptionResult)

        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)

            // Retry once on failure
            try {
                Log.d(TAG, "Retrying transcription...")
                cleanupTranscriptionResources()
                initializeTranscriptionEngine()

                val audioFile = File(audioFilePath)
                val audioBytes = readAudioFileAsBytes(audioFile)
                val transcriptionPrompt = """
                    Transcribe and translate this audio to English. If the speech is in any language other than English, provide an accurate English translation. If the speech is already in English, transcribe it exactly. Be accurate and maintain the original meaning. Only return the transcribed/translated text without any additional commentary.
                """.trimIndent()

                val session = transcriptionSession ?: return Result.failure(Exception("Retry failed: session not initialized"))
                val retryResult = processAudioWithSession(session, audioBytes, transcriptionPrompt)

                Log.d(TAG, "Transcription retry successful")
                Result.success(retryResult)

            } catch (retryException: Exception) {
                Log.e(TAG, "Transcription retry also failed", retryException)
                Result.failure(retryException)
            }
        }
    }

    /**
     * Generates a summary from the full transcription text
     */
    suspend fun generateSummary(fullTranscription: String): Result<String> {
        return try {
            Log.d(TAG, "Generating summary for transcription of ${fullTranscription.length} characters")

            // Clean up transcription resources first
            cleanupTranscriptionResources()

            // Initialize summarization engine and session
            initializeSummarizationEngine()

            val session = summarizationSession ?: return Result.failure(Exception("Summarization session not initialized"))

            // Create summarization prompt with context
            val summarizationPrompt = """
                The following is a transcription of a meeting recorded in chunks. Each chunk represents approximately 30 seconds of audio and has been transcribed and translated to English. Please create a comprehensive meeting summary that includes:

                1. Key discussion points
                2. Important decisions made
                3. Action items identified
                4. Next steps planned
                5. Main outcomes

                Format the summary in a clear, organized manner with appropriate headings. Here is the full transcription:

                $fullTranscription
            """.trimIndent()

            // Process summarization using session
            val summaryResult = processTextWithSession(session, summarizationPrompt)

            Log.d(TAG, "Summary generated: ${summaryResult.length} characters")
            Result.success(summaryResult)

        } catch (e: Exception) {
            Log.e(TAG, "Error during summary generation", e)
            Result.failure(e)
        } finally {
            // Clean up summarization resources
            cleanupSummarizationResources()
        }
    }

    /**
     * Clean up all resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up all resources")
        cleanupTranscriptionResources()
        cleanupSummarizationResources()
    }

    private fun initializeTranscriptionEngine() {
        try {
            Log.d(TAG, "Initializing transcription engine...")

            // Create LLM Inference following your JetsonViewModel pattern
            val llmInferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
                .setMaxTokens(512)
                .build()

            transcriptionEngine = LlmInference.createFromOptions(context, llmInferenceOptions)

            // Create session following your JetsonViewModel pattern
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.9f)
                .setTemperature(0.1f) // Low temperature for accuracy
                .build()

            transcriptionSession = LlmInferenceSession.createFromOptions(transcriptionEngine!!, sessionOptions)
            Log.d(TAG, "Transcription engine initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize transcription engine", e)
            throw e
        }
    }

    private fun initializeSummarizationEngine() {
        try {
            Log.d(TAG, "Initializing summarization engine...")

            // Create LLM Inference for summarization
            val llmInferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
                .setMaxTokens(2048) // More tokens for longer summaries
                .build()

            summarizationEngine = LlmInference.createFromOptions(context, llmInferenceOptions)

            // Create session for summarization
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.9f)
                .setTemperature(0.7f) // Higher temperature for creativity
                .build()

            summarizationSession = LlmInferenceSession.createFromOptions(summarizationEngine!!, sessionOptions)
            Log.d(TAG, "Summarization engine initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize summarization engine", e)
            throw e
        }
    }

    private suspend fun processAudioWithSession(
        session: LlmInferenceSession,
        audioBytes: ByteArray,
        prompt: String
    ): String = suspendCoroutine { continuation ->

        val resultBuilder = StringBuilder()
        val latch = CountDownLatch(1)
        var hasError = false
        var errorMessage = ""

        try {
            // Add text prompt first - following your JetsonViewModel pattern
            session.addQueryChunk(prompt)

            // Add audio data - following your JetsonViewModel pattern
            session.addAudio(audioBytes)

            // Generate response asynchronously - following your JetsonViewModel pattern
            session.generateResponseAsync { partialResult, done ->
                try {
                    resultBuilder.append(partialResult)

                    if (done) {
                        latch.countDown()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in audio response callback", e)
                    hasError = true
                    errorMessage = e.message ?: "Unknown error in response callback"
                    latch.countDown()
                }
            }

            // Wait for completion with timeout
            val completed = latch.await(TRANSCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (!completed) {
                continuation.resume("Transcription timeout - partial result: ${resultBuilder}")
            } else if (hasError) {
                continuation.resume("Error during transcription: $errorMessage")
            } else {
                continuation.resume(resultBuilder.toString())
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio with session", e)
            continuation.resume("Processing error: ${e.message}")
        }
    }

    private suspend fun processTextWithSession(
        session: LlmInferenceSession,
        prompt: String
    ): String = suspendCoroutine { continuation ->

        val resultBuilder = StringBuilder()
        val latch = CountDownLatch(1)
        var hasError = false
        var errorMessage = ""

        try {
            // Add text prompt - following your JetsonViewModel pattern
            session.addQueryChunk(prompt)

            // Generate response asynchronously - following your JetsonViewModel pattern
            session.generateResponseAsync { partialResult, done ->
                try {
                    resultBuilder.append(partialResult)

                    if (done) {
                        latch.countDown()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in text response callback", e)
                    hasError = true
                    errorMessage = e.message ?: "Unknown error in response callback"
                    latch.countDown()
                }
            }

            // Wait for completion with timeout
            val completed = latch.await(SUMMARIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (!completed) {
                continuation.resume("Summarization timeout - partial result: ${resultBuilder}")
            } else if (hasError) {
                continuation.resume("Error during summarization: $errorMessage")
            } else {
                continuation.resume(resultBuilder.toString())
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing text with session", e)
            continuation.resume("Processing error: ${e.message}")
        }
    }

    private fun readAudioFileAsBytes(audioFile: File): ByteArray {
        return try {
            FileInputStream(audioFile).use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading audio file", e)
            throw e
        }
    }

    private fun cleanupTranscriptionResources() {
        try {
            transcriptionSession?.close()
            transcriptionSession = null

            transcriptionEngine?.close()
            transcriptionEngine = null

            Log.d(TAG, "Transcription resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up transcription resources", e)
        }
    }

    private fun cleanupSummarizationResources() {
        try {
            summarizationSession?.close()
            summarizationSession = null

            summarizationEngine?.close()
            summarizationEngine = null

            Log.d(TAG, "Summarization resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up summarization resources", e)
        }
    }
}