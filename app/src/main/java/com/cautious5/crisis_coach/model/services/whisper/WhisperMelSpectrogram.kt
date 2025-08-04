package com.cautious5.crisis_coach.model.services.whisper

import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A helper object to compute Mel Spectrograms from raw audio data,
 * as required by the Whisper TFLite model.
 *
 * This version includes a performant FFT and the correct normalization scheme
 * to match the data distribution the Whisper model was trained on.
 */
object WhisperMelSpectrogram {

    // Model-specific audio constants
    private const val SAMPLE_RATE = 16000
    private const val N_FFT = 400
    private const val N_MELS = 80
    private const val HOP_LENGTH = 160
    private const val CHUNK_LENGTH = 30
    private const val N_SAMPLES = CHUNK_LENGTH * SAMPLE_RATE // 480,000 samples
    private const val N_FRAMES = N_SAMPLES / HOP_LENGTH      // 3,000 frames

    // Pre-computed constants for efficiency
    private val melFilters = melFilterBank()
    private val hannWindow = hannWindow()

    // Internal helper class for the FFT algorithm
    private data class Complex(val re: Double, val im: Double) {
        operator fun plus(other: Complex) = Complex(re + other.re, im + other.im)
        operator fun minus(other: Complex) = Complex(re - other.re, im - other.im)
        operator fun times(other: Complex) = Complex(re * other.re - im * other.im, re * other.im + im * other.re)
        fun magnitude(): Float = sqrt(re * re + im * im).toFloat()
    }

    /**
     * Calculates the next power of two for a given integer.
     * Required for padding the input to the Radix-2 FFT algorithm.
     */
    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) {
            p *= 2
        }
        return p
    }

    /**
     * Computes the Mel Spectrogram for a given 30-second audio chunk.
     *
     * @param audioSamples A FloatArray of raw audio, expected to be 480,000 samples long.
     * @return A FloatArray representing the [80, 3000] Mel Spectrogram, correctly normalized.
     */
    fun compute(audioSamples: FloatArray): FloatArray {
        val spectrogram = FloatArray(N_MELS * N_FRAMES)
        val paddedFftSize = nextPowerOfTwo(N_FFT) // Will be 512

        // Pass 1: Calculate the log-mel values for each frame of the audio
        for (i in 0 until N_FRAMES) {
            val frame = FloatArray(N_FFT)
            val frameStart = i * HOP_LENGTH
            val frameEnd = frameStart + N_FFT

            if (frameEnd <= audioSamples.size) {
                // Apply a Hann window to the frame
                for (j in 0 until N_FFT) {
                    frame[j] = audioSamples[frameStart + j] * hannWindow[j]
                }

                // Zero-pad the frame to the next power of two for the FFT
                val paddedFrame = Array(paddedFftSize) { Complex(0.0, 0.0) }
                for (j in 0 until N_FFT) {
                    paddedFrame[j] = Complex(frame[j].toDouble(), 0.0)
                }

                // Perform the Fast Fourier Transform
                val fftResult = fft(paddedFrame)
                // Calculate magnitude squared from the first half of the FFT result
                val fftMag = FloatArray(N_FFT / 2 + 1) { fftResult[it].magnitude().pow(2) }

                // Apply the Mel filter bank to the FFT magnitudes
                for (melIndex in 0 until N_MELS) {
                    var melEnergy = 0.0f
                    for (fftIndex in fftMag.indices) {
                        melEnergy += fftMag[fftIndex] * melFilters[melIndex][fftIndex]
                    }
                    // Calculate the log base 10 of the energy (dB scale)
                    val logMelEnergy = log10(melEnergy.toDouble().coerceAtLeast(1e-10)).toFloat()
                    // Store in row-major order (mel-bands are rows, time-frames are columns)
                    spectrogram[melIndex * N_FRAMES + i] = if (logMelEnergy.isFinite()) logMelEnergy else -10.0f
                }
            }
        }

        // Pass 2: Perform the final normalization on the entire spectrogram
        var mmax = -1e20f
        for (value in spectrogram) {
            if (value > mmax) {
                mmax = value
            }
        }
        mmax -= 8.0f

        // Clamp values and shift into the model's expected range [-4.0, 4.0]
        for (i in spectrogram.indices) {
            if (spectrogram[i] < mmax) {
                spectrogram[i] = mmax
            }
        }

        return spectrogram
    }

    private fun hannWindow(): FloatArray {
        return FloatArray(N_FFT) { i ->
            (0.5 * (1 - cos(2 * Math.PI * i / (N_FFT - 1)))).toFloat()
        }
    }

    private fun melFilterBank(): Array<FloatArray> {
        val minMel = 0.0
        val maxMel = 2595.0 * log10(1.0 + (SAMPLE_RATE / 2.0) / 700.0)
        val melPoints = DoubleArray(N_MELS + 2) { i ->
            minMel + i * (maxMel - minMel) / (N_MELS + 1)
        }
        val freqPoints = melPoints.map { 700.0 * (10.0.pow(it / 2595.0) - 1.0) }
        val fftBinPoints = freqPoints.map { (N_FFT + 1) * it / SAMPLE_RATE }.map { it.toInt() }

        val filters = Array(N_MELS) { FloatArray(N_FFT / 2 + 1) }
        for (i in 0 until N_MELS) {
            val start = fftBinPoints[i]
            val center = fftBinPoints[i + 1]
            val end = fftBinPoints[i + 2]
            for (j in start until center) {
                if (j < filters[i].size) filters[i][j] = (j - start).toFloat() / (center - start)
            }
            for (j in center until end) {
                if (j < filters[i].size) filters[i][j] = (end - j).toFloat() / (end - center)
            }
        }
        return filters
    }

    /**
     * A performant Radix-2 Cooley-Tukey Fast Fourier Transform implementation.
     * Input size MUST be a power of two.
     */
    private fun fft(x: Array<Complex>): Array<Complex> {
        val n = x.size
        if (n == 1) return arrayOf(x[0])

        require(n % 2 == 0) { "FFT size must be a power of 2. Found $n" }

        val even = Array(n / 2) { x[2 * it] }
        val odd = Array(n / 2) { x[2 * it + 1] }

        val fftEven = fft(even)
        val fftOdd = fft(odd)

        val result = Array(n) { Complex(0.0, 0.0) }
        for (k in 0 until n / 2) {
            val angle = -2 * Math.PI * k / n
            val t = Complex(cos(angle), sin(angle)) * fftOdd[k]
            result[k] = fftEven[k] + t
            result[k + n / 2] = fftEven[k] - t
        }
        return result
    }
}