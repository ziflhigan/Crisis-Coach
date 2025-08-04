package com.cautious5.crisis_coach.model.services.whisper

/**
 * Listener interface for Whisper speech recognition engine events.
 * Defines callbacks for updates, final results, and errors.
 */
interface WhisperListener {
    /**
     * Called when the Whisper engine provides status updates (e.g., "Processing...").
     * This can be used for displaying intermediate states in the UI.
     * @param message The status message.
     */
    fun onUpdateReceived(message: String)

    /**
     * Called when the final transcription result is ready.
     * @param result The final transcribed text.
     */
    fun onResultReceived(result: String)

    /**
     * Called when an error occurs during the transcription process.
     * @param error A descriptive error message.
     */
    fun onError(error: String)
}

/**
 * Listener interface for audio recording events.
 * Defines callbacks for audio data, status updates, and errors.
 */
interface AudioRecorderListener {
    /**
     * Called when the recorder provides status updates (e.g., "Recording started").
     * @param message The status message.
     */
    fun onUpdateReceived(message: String)

    /**
     * Called when a new chunk of audio data is captured from the microphone.
     * @param samples A FloatArray of audio samples, normalized to the range [-1.0, 1.0].
     */
    fun onDataReceived(samples: FloatArray)

    /**
     * Called when an error occurs during the recording process.
     * @param error A descriptive error message.
     */
    fun onError(error: String)
}