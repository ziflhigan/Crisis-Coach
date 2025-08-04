package com.cautious5.crisis_coach.model.services.whisper

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Audio recorder for capturing microphone input for the Whisper engine.
 * Records in 16kHz mono PCM 16-bit format, as required by Whisper.
 */
class AudioRecorder(context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"

        // Audio configuration required by the Whisper model
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var bufferSize: Int = 0
    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null
    private var listener: AudioRecorderListener? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        try {
            initializeAudioRecord()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecorder initialization failed", e)
            listener?.onError("Recorder initialization failed: ${e.message}")
        }
    }

    fun setListener(listener: AudioRecorderListener) {
        this.listener = listener
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording.get()) {
            Log.w(TAG, "Recording is already in progress.")
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized. Cannot start recording.")
            listener?.onError("AudioRecord not initialized.")
            return
        }

        try {
            audioRecord?.startRecording()
            isRecording.set(true)
            Log.i(TAG, "Audio recording started.")
            scope.launch(Dispatchers.Main) { listener?.onUpdateReceived("Recording started") }

            recordingJob = scope.launch {
                recordingLoop()
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start recording", e)
            scope.launch(Dispatchers.Main) { listener?.onError("Failed to start recording: ${e.message}") }
            isRecording.set(false)
        }
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) {
            return
        }

        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            Log.i(TAG, "Audio recording stopped.")
            scope.launch(Dispatchers.Main) { listener?.onUpdateReceived("Recording stopped") }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to stop AudioRecord", e)
        }
    }

    private suspend fun recordingLoop() {
        val buffer = ShortArray(bufferSize / 2)

        while (currentCoroutineContext().isActive) {
            val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0

            if (readResult > 0) {
                val floatSamples = FloatArray(readResult) { i ->
                    buffer[i] / 32767.0f // Normalize to [-1.0, 1.0]
                }
                withContext(Dispatchers.Main) {
                    listener?.onDataReceived(floatSamples)
                }
            } else {
                handleReadError(readResult)
                break
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeAudioRecord() {
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        if (bufferSize <= 0) {
            throw IllegalStateException("AudioRecord.getMinBufferSize failed with result: $bufferSize")
        }

        bufferSize *= BUFFER_SIZE_FACTOR

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord could not be initialized.")
        }
        Log.d(TAG, "AudioRecord initialized with buffer size: $bufferSize")
    }

    private suspend fun handleReadError(errorCode: Int) {
        val errorMessage = when (errorCode) {
            AudioRecord.ERROR_INVALID_OPERATION -> "Invalid operation"
            AudioRecord.ERROR_BAD_VALUE -> "Bad value"
            AudioRecord.ERROR_DEAD_OBJECT -> "Dead object"
            AudioRecord.ERROR -> "Generic error"
            else -> "Unknown read error: $errorCode"
        }
        Log.e(TAG, "AudioRecord read error: $errorMessage")
        withContext(Dispatchers.Main) {
            listener?.onError("Audio recording error: $errorMessage")
        }
    }

    fun release() {
        stop()
        scope.cancel()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "AudioRecorder resources released.")
    }
}