package com.example.meeting_mode_gemma.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorder(private val context: Context) {

    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var outputFile: File? = null

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNELS = 1
        private const val BYTES_PER_SAMPLE = 2
    }

    fun startRecording(outputFilePath: String): Boolean {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return false
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Audio recording permission not granted")
            return false
        }

        outputFile = File(outputFilePath)
        Log.d(TAG, "=== STARTING AUDIO RECORDING ===")
        Log.d(TAG, "Output file: ${outputFile?.absolutePath}")
        Log.d(TAG, "Parent directory: ${outputFile?.parentFile?.absolutePath}")

        // CRITICAL: Ensure parent directory exists before recording
        val parentDir = outputFile?.parentFile
        if (parentDir != null) {
            if (!parentDir.exists()) {
                Log.d(TAG, "Parent directory doesn't exist, creating it...")
                val created = parentDir.mkdirs()
                Log.d(TAG, "Parent directory creation result: $created")

                if (!created && !parentDir.exists()) {
                    Log.e(TAG, "Failed to create parent directory: ${parentDir.absolutePath}")
                    return false
                }
            }
            Log.d(TAG, "Parent directory exists: ${parentDir.exists()}")
            Log.d(TAG, "Parent directory writable: ${parentDir.canWrite()}")
        } else {
            Log.e(TAG, "Parent directory is null!")
            return false
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        Log.d(TAG, "Buffer size: $bufferSize")

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                Log.e(TAG, "AudioRecord state: ${audioRecord?.state}")
                return false
            }

            isRecording.set(true)
            audioRecord?.startRecording()

            Log.d(TAG, "AudioRecord started successfully")
            Log.d(TAG, "Recording state: ${audioRecord?.recordingState}")

            recordingThread = Thread { recordingLoop(bufferSize) }
            recordingThread?.start()

            Log.d(TAG, "Recording thread started")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            return false
        }
    }

    fun stopRecording() {
        if (!isRecording.get()) {
            Log.d(TAG, "Not currently recording")
            return
        }

        Log.d(TAG, "=== STOPPING AUDIO RECORDING ===")
        isRecording.set(false)

        try {
            recordingThread?.join()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread = null

            Log.d(TAG, "Recording stopped successfully")

            // Verify the file was created
            outputFile?.let { file ->
                Log.d(TAG, "Final file check:")
                Log.d(TAG, "  File exists: ${file.exists()}")
                Log.d(TAG, "  File size: ${file.length()} bytes")
                Log.d(TAG, "  File path: ${file.absolutePath}")
                Log.d(TAG, "  File readable: ${file.canRead()}")
                Log.d(TAG, "  File writable: ${file.canWrite()}")

                // List parent directory contents
                val parentDir = file.parentFile
                if (parentDir?.exists() == true) {
                    Log.d(TAG, "Parent directory contents after recording:")
                    parentDir.listFiles()?.forEach { sibling ->
                        Log.d(TAG, "    - ${sibling.name} (${sibling.length()} bytes)")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    private fun recordingLoop(bufferSize: Int) {
        val audioData = ByteArray(bufferSize)
        val audioBuffer = mutableListOf<ByteArray>()
        var totalBytesRecorded = 0

        Log.d(TAG, "=== RECORDING LOOP STARTED ===")

        try {
            while (isRecording.get()) {
                val bytesRead = audioRecord?.read(audioData, 0, bufferSize) ?: 0
                if (bytesRead > 0) {
                    audioBuffer.add(audioData.copyOf(bytesRead))
                    totalBytesRecorded += bytesRead

                    // Log progress every 100 chunks
                    if (audioBuffer.size % 100 == 0) {
                        Log.d(TAG, "Recorded ${audioBuffer.size} chunks, $totalBytesRecorded bytes total")
                    }
                } else if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord read error: $bytesRead")
                    break
                }
            }

            Log.d(TAG, "Recording loop finished. Total chunks: ${audioBuffer.size}, Total bytes: $totalBytesRecorded")

            // Save recorded data to WAV file
            if (totalBytesRecorded > 0) {
                saveAsWav(audioBuffer)
            } else {
                Log.e(TAG, "No audio data recorded!")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in recording loop", e)
        }
    }

    private fun saveAsWav(audioBuffer: List<ByteArray>) {
        if (outputFile == null) {
            Log.e(TAG, "Output file is null")
            return
        }

        Log.d(TAG, "=== SAVING WAV FILE ===")

        try {
            val totalDataSize = audioBuffer.sumOf { it.size }
            Log.d(TAG, "Total data size to save: $totalDataSize bytes")
            Log.d(TAG, "Saving to: ${outputFile?.absolutePath}")

            // Verify parent directory exists one more time before writing
            val parentDir = outputFile?.parentFile
            if (parentDir?.exists() != true) {
                Log.e(TAG, "Parent directory disappeared! Recreating...")
                val created = parentDir?.mkdirs() ?: false
                if (!created) {
                    throw IOException("Could not create parent directory: ${parentDir?.absolutePath}")
                }
            }

            FileOutputStream(outputFile).use { fos ->
                // Write WAV header
                writeWavHeader(fos, totalDataSize)
                Log.d(TAG, "WAV header written")

                // Write audio data
                var bytesWritten = 0
                audioBuffer.forEach { chunk ->
                    fos.write(chunk)
                    bytesWritten += chunk.size
                }

                Log.d(TAG, "Audio data written: $bytesWritten bytes")
                fos.flush()
                fos.close()
            }

            Log.d(TAG, "Recording saved successfully to: ${outputFile?.absolutePath}")
            Log.d(TAG, "Final file size: ${outputFile?.length()} bytes")

        } catch (e: IOException) {
            Log.e(TAG, "Error saving recording", e)
            Log.e(TAG, "Output file path: ${outputFile?.absolutePath}")
            Log.e(TAG, "Output file parent exists: ${outputFile?.parentFile?.exists()}")
            Log.e(TAG, "Output file parent writable: ${outputFile?.parentFile?.canWrite()}")
            Log.e(TAG, "Output file parent path: ${outputFile?.parentFile?.absolutePath}")
        }
    }

    private fun writeWavHeader(fos: FileOutputStream, dataSize: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header.put("RIFF".toByteArray(StandardCharsets.UTF_8))
        header.putInt(36 + dataSize) // Total file size - 8
        header.put("WAVE".toByteArray(StandardCharsets.UTF_8))

        // fmt chunk
        header.put("fmt ".toByteArray(StandardCharsets.UTF_8))
        header.putInt(16) // fmt chunk size
        header.putShort(1) // Audio format (PCM)
        header.putShort(CHANNELS.toShort()) // Number of channels
        header.putInt(SAMPLE_RATE) // Sample rate
        header.putInt(SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE) // Byte rate
        header.putShort((CHANNELS * BYTES_PER_SAMPLE).toShort()) // Block align
        header.putShort((BYTES_PER_SAMPLE * 8).toShort()) // Bits per sample

        // data chunk
        header.put("data".toByteArray(StandardCharsets.UTF_8))
        header.putInt(dataSize) // Data size

        fos.write(header.array())
    }
}