package com.cautious5.crisis_coach.utils

import android.util.Log

/**
 * Centralized utilities for parsing AI model responses
 * Extracts structured information from free-form text
 */
object ResponseParser {

    private const val TAG = "ResponseParser"

    /**
     * Parses a translation response with optional pronunciation
     */
    data class TranslationResponse(
        val translatedText: String,
        val pronunciationGuide: String? = null,
        val confidence: Float = 1.0f
    )

    fun parseTranslationResponse(
        response: String,
        expectPronunciation: Boolean = false
    ): TranslationResponse {
        return if (expectPronunciation) {
            // Try structured format first
            val translationMatch = findPattern(response, "Translation:", "Pronunciation:", "\n", "$")
            val pronunciationMatch = findPattern(response, "Pronunciation:", "$")

            if (translationMatch != null) {
                TranslationResponse(
                    translatedText = translationMatch,
                    pronunciationGuide = pronunciationMatch
                )
            } else {
                // Fallback to simple format
                TranslationResponse(translatedText = response.trim())
            }
        } else {
            TranslationResponse(translatedText = response.trim())
        }
    }

    /**
     * Extracts urgency level from medical response
     */
    fun extractUrgencyLevel(response: String): UrgencyLevel {
        val lowercaseResponse = response.lowercase()

        return when {
            containsAny(lowercaseResponse, "critical", "life-threatening", "immediate", "emergency") ->
                UrgencyLevel.CRITICAL
            containsAny(lowercaseResponse, "high urgency", "urgent", "serious", "severe") ->
                UrgencyLevel.HIGH
            containsAny(lowercaseResponse, "moderate", "medium urgency", "concerning") ->
                UrgencyLevel.MEDIUM
            containsAny(lowercaseResponse, "low urgency", "minor", "non-urgent") ->
                UrgencyLevel.LOW
            else -> UrgencyLevel.UNKNOWN
        }
    }

    /**
     * Extracts safety status from structural response
     */
    fun extractSafetyStatus(response: String): SafetyStatus {
        val lowercaseResponse = response.lowercase()

        return when {
            containsAny(lowercaseResponse, "critical", "collapse", "imminent danger", "evacuate immediately") ->
                SafetyStatus.CRITICAL
            containsAny(lowercaseResponse, "unsafe", "dangerous", "do not enter", "structural failure") ->
                SafetyStatus.UNSAFE
            containsAny(lowercaseResponse, "caution", "concerning", "monitor", "partially safe") ->
                SafetyStatus.CAUTION
            containsAny(lowercaseResponse, "safe", "stable", "no immediate danger", "structurally sound") ->
                SafetyStatus.SAFE
            else -> SafetyStatus.UNKNOWN
        }
    }

    /**
     * Extracts action items from response
     */
    fun extractActionItems(response: String): List<String> {
        val actionIndicators = listOf(
            "should", "must", "need to", "recommend", "advise",
            "immediately", "first", "then", "next", "finally"
        )

        return response.split(Regex("[.!]"))
            .map { it.trim() }
            .filter { sentence ->
                actionIndicators.any { indicator ->
                    sentence.lowercase().contains(indicator)
                }
            }
            .take(5) // Limit to top 5 actions
    }

    /**
     * Extracts key observations or findings
     */
    fun extractKeyFindings(response: String, maxFindings: Int = 5): List<String> {
        val findingIndicators = listOf(
            "observe", "see", "notice", "appears", "shows",
            "indicates", "suggests", "reveals", "displays"
        )

        return response.split(Regex("[.!]"))
            .map { it.trim() }
            .filter { sentence ->
                sentence.length > 20 && // Skip very short sentences
                        findingIndicators.any { indicator ->
                            sentence.lowercase().contains(indicator)
                        }
            }
            .take(maxFindings)
    }

    /**
     * Extracts numbered or bulleted lists from response
     */
    fun extractListItems(response: String): List<String> {
        val items = mutableListOf<String>()

        // Extract numbered lists (1., 2., etc.)
        val numberedPattern = Regex("\\d+\\.\\s*(.+?)(?=\\d+\\.|$)", RegexOption.DOT_MATCHES_ALL)
        numberedPattern.findAll(response).forEach { match ->
            items.add(match.groupValues[1].trim())
        }

        // Extract bulleted lists (-, *, •)
        val bulletPattern = Regex("[-*•]\\s*(.+?)(?=[-*•]|\\n|$)")
        bulletPattern.findAll(response).forEach { match ->
            val item = match.groupValues[1].trim()
            if (item.isNotBlank()) {
                items.add(item)
            }
        }

        return items.distinct()
    }

    /**
     * Extracts confidence indicators from response
     */
    fun extractConfidence(response: String): Float {
        val lowercaseResponse = response.lowercase()

        return when {
            containsAny(lowercaseResponse, "definitely", "certainly", "clearly", "obvious") -> 0.9f
            containsAny(lowercaseResponse, "likely", "probably", "appears to be", "seems") -> 0.7f
            containsAny(lowercaseResponse, "possibly", "might", "could be", "uncertain") -> 0.5f
            containsAny(lowercaseResponse, "unclear", "cannot determine", "insufficient", "unsure") -> 0.3f
            else -> 0.6f // Default moderate confidence
        }
    }

    /**
     * Extracts yes/no answer from response
     */
    fun extractBooleanAnswer(response: String): Boolean? {
        val lowercaseResponse = response.lowercase()

        return when {
            lowercaseResponse.startsWith("yes") ||
                    containsAny(lowercaseResponse, "affirmative", "correct", "true", "confirmed") -> true

            lowercaseResponse.startsWith("no") ||
                    containsAny(lowercaseResponse, "negative", "incorrect", "false", "denied") -> false

            else -> null
        }
    }

    /**
     * Cleans and formats response text
     */
    fun cleanResponse(response: String): String {
        return response
            .trim()
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .replace(Regex("\\n{3,}"), "\n\n") // Limit consecutive newlines
            .replace(Regex("^\\W+|\\W+$"), "") // Remove leading/trailing non-word chars
    }

    /**
     * Splits response into sections based on headers
     */
    fun extractSections(response: String): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        val lines = response.lines()

        var currentSection = "main"
        val currentContent = StringBuilder()

        for (line in lines) {
            // Detect section headers (e.g., "Assessment:", "Recommendations:", etc.)
            val headerMatch = Regex("^([A-Za-z\\s]+):").find(line)

            if (headerMatch != null) {
                // Save previous section
                if (currentContent.isNotEmpty()) {
                    sections[currentSection] = currentContent.toString().trim()
                    currentContent.clear()
                }

                currentSection = headerMatch.groupValues[1].trim().lowercase()
                // Add the rest of the line after the colon
                val remainder = line.substring(headerMatch.value.length).trim()
                if (remainder.isNotEmpty()) {
                    currentContent.appendLine(remainder)
                }
            } else {
                currentContent.appendLine(line)
            }
        }

        // Save last section
        if (currentContent.isNotEmpty()) {
            sections[currentSection] = currentContent.toString().trim()
        }

        return sections
    }

    // Helper methods

    private fun findPattern(
        text: String,
        startMarker: String,
        vararg endMarkers: String
    ): String? {
        val startIndex = text.indexOf(startMarker)
        if (startIndex == -1) return null

        val contentStart = startIndex + startMarker.length
        var endIndex = text.length

        for (endMarker in endMarkers) {
            val markerIndex = text.indexOf(endMarker, contentStart)
            if (markerIndex != -1 && markerIndex < endIndex) {
                endIndex = markerIndex
            }
        }

        return text.substring(contentStart, endIndex).trim()
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    /**
     * Common enums used across the app
     */
    enum class UrgencyLevel {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        UNKNOWN
    }

    enum class SafetyStatus {
        SAFE,
        CAUTION,
        UNSAFE,
        CRITICAL,
        UNKNOWN
    }
}