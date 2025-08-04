package com.cautious5.crisis_coach.model.services.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Whisper speech recognition engine for offline transcription.
 * This class wraps the TensorFlow Lite model, manages audio buffering,
 * and decodes the model's output into text.
 */
class Whisper(private val context: Context) {

    companion object {
        private const val TAG = "WhisperEngine"
        const val ACTION_TRANSCRIBE = "transcribe"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_LENGTH_SECONDS = 30
        private const val SAMPLES_PER_CHUNK = SAMPLE_RATE * CHUNK_LENGTH_SECONDS
        private const val TOKEN_EOT = 50257
        private const val MAX_OUTPUT_TOKENS = 448
    }

    private var interpreter: Interpreter? = null
    private var listener: WhisperListener? = null
    private val isModelLoaded = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)
    private val audioBuffer = mutableListOf<Float>()
    private val vocab = mutableMapOf<Int, String>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var processingJob: Job? = null

    fun setListener(listener: WhisperListener) {
        this.listener = listener
    }

    fun loadModel(modelPath: String, vocabPath: String, multilingual: Boolean) {
        if (isModelLoaded.get()) {
            Log.w(TAG, "Model is already loaded.")
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.Main) { listener?.onUpdateReceived("Loading model...") }

                // Call the restored loadVocabulary method.
                Log.d(TAG, "Loading vocabulary from: $vocabPath")
                loadVocabulary(vocabPath)

                Log.d(TAG, "Loading TFLite model from: $modelPath")
                val modelBuffer = loadModelFile(modelPath)
                val options = Interpreter.Options().apply { numThreads = 4 }
                interpreter = Interpreter(modelBuffer, options)

                isModelLoaded.set(true)
                Log.i(TAG, "Whisper model and vocabulary loaded successfully.")
                withContext(Dispatchers.Main) { listener?.onUpdateReceived("Model ready.") }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Whisper model or vocabulary", e)
                isModelLoaded.set(false)
                withContext(Dispatchers.Main) { listener?.onError("Model load failed: ${e.message}") }
            }
        }
    }

    private fun loadVocabulary(assetPath: String) {
        context.assets.open(assetPath).use { input ->
            val allBytes = input.readBytes()
            val bb = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN)

            val magic = bb.int
            require(magic == 0x5553454E) { "Unexpected vocab magic: 0x${magic.toUInt().toString(16)}" }

            val nMel  = bb.int   // 80
            val nFft  = bb.int   // 400
            bb.position(bb.position() + nMel * nFft * 4)            // skip filters

            val nVocab = bb.int
            require(nVocab in 10_000..200_000) { "Unreasonable vocab size: $nVocab" }

            repeat(nVocab) { id ->
                val len = bb.int
                require(len >= 0 && bb.remaining() >= len) {
                    "Corrupt token at id=$id (len=$len, remaining=${bb.remaining()})"
                }
                val bytes = ByteArray(len).also { bb.get(it) }
                vocab[id] = String(bytes, Charsets.UTF_8)
            }
        }
        Log.i(TAG, "Vocabulary loaded (${"%,d".format(vocab.size)} tokens)")
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    fun writeBuffer(samples: FloatArray) {
        synchronized(audioBuffer) {
            audioBuffer.addAll(samples.toList())
        }
    }

    fun start() {
        if (!isModelLoaded.get()) {
            listener?.onError("Model not loaded.")
            return
        }
        if (isProcessing.get()) {
            Log.w(TAG, "Already processing.")
            return
        }
        isProcessing.set(true)
        processingJob = scope.launch {
            withContext(Dispatchers.Main) { listener?.onUpdateReceived("Processing speech...") }
            transcribe()
            isProcessing.set(false)
        }
    }

    fun stop() {
        if (!isProcessing.get()) return
        processingJob?.cancel()
        isProcessing.set(false)
        Log.d(TAG, "Whisper processing stopped.")
    }

    private suspend fun transcribe() = withContext(Dispatchers.IO) {
        try {
            val audioData = synchronized(audioBuffer) {
                val data = audioBuffer.toFloatArray()
                audioBuffer.clear()
                data
            }

            if (audioData.isEmpty()) {
                Log.w(TAG, "No audio data to transcribe.")
                withContext(Dispatchers.Main) { listener?.onResultReceived("") }
                return@withContext
            }

            val inputSamples = FloatArray(SAMPLES_PER_CHUNK)
            System.arraycopy(audioData, 0, inputSamples, 0, min(audioData.size, SAMPLES_PER_CHUNK))

            Log.d(TAG, "Calculating Mel Spectrogram...")
            val spectrogramStartTime = System.currentTimeMillis()
            val spectrogram = WhisperMelSpectrogram.compute(inputSamples)
            val spectrogramTime = System.currentTimeMillis() - spectrogramStartTime
            Log.d(TAG, "Spectrogram calculated in ${spectrogramTime}ms.")

            val inputBuffer = ByteBuffer.allocateDirect(spectrogram.size * 4).apply {
                order(ByteOrder.nativeOrder())
                asFloatBuffer().put(spectrogram)
            }

            val inputShape = intArrayOf(1, 80, 3000)
            interpreter?.resizeInput(0, inputShape)
            interpreter?.allocateTensors()

            val outputBuffer = ByteBuffer.allocateDirect(MAX_OUTPUT_TOKENS * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            val outputs = mapOf(0 to outputBuffer)

            Log.d(TAG, "Running default TFLite inference...")
            val inferenceStartTime = System.currentTimeMillis()
            // This is the default "run" method, not runSignature
            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            val inferenceTime = System.currentTimeMillis() - inferenceStartTime
            Log.i(TAG, "Default inference completed in ${inferenceTime}ms")

            val transcription = decode(outputBuffer)
            Log.i(TAG, "Transcription result: $transcription")

            withContext(Dispatchers.Main) {
                listener?.onResultReceived(transcription)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            withContext(Dispatchers.Main) { listener?.onError("Transcription failed: ${e.message}") }
        }
    }

    private fun decode(buffer: ByteBuffer): String {
        val outputTensor = interpreter!!.getOutputTensor(0)
        val tokenCount = outputTensor.shape()[1] // Shape is [1, 448] -> get 448

        buffer.rewind()
        val intBuffer = buffer.asIntBuffer()
        val result = StringBuilder()

        for (i in 0 until tokenCount) {
            // Break loop if buffer has fewer tokens than expected
            if (i >= intBuffer.limit()) break

            val token = intBuffer.get(i)
            if (token == TOKEN_EOT) break
            result.append(vocab[token] ?: "")
        }
        return result.toString().replace(" ", " ").trim()
    }

    fun release() {
        stop()
        scope.cancel()
        interpreter?.close()
        interpreter = null
        isModelLoaded.set(false)
        synchronized(audioBuffer) {
            audioBuffer.clear()
        }
        vocab.clear()
        Log.d(TAG, "Whisper engine resources released.")
    }
}