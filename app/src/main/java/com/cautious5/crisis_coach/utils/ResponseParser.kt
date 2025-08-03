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
     * Extracts risk level from a general analysis response.
     * Prioritizes searching within a "Risk Level" section if available.
     */
    fun extractRiskLevel(response: String): RiskLevel {
        val sections = extractSections(response)
        val textToSearch = sections["risk level"]
            ?: sections["risk"]
            ?: response.lowercase()

        val lowercaseResponse = textToSearch.lowercase()

        return when {
            containsAny(lowercaseResponse, "critical risk", "imminent danger", "extreme risk", "evacuate") ->
                RiskLevel.CRITICAL
            containsAny(lowercaseResponse, "high risk", "significant danger", "urgent attention") ->
                RiskLevel.HIGH
            containsAny(lowercaseResponse, "moderate risk", "medium risk", "potential hazard", "caution") ->
                RiskLevel.MODERATE
            containsAny(lowercaseResponse, "low risk", "minor concern", "be mindful") ->
                RiskLevel.LOW
            else -> RiskLevel.UNKNOWN
        }
    }

    /**
     * Extracts urgency level from medical response
     */
    fun extractUrgencyLevel(response: String): UrgencyLevel {
        val sections = extractSections(response)
        // Search in the most relevant section first, then fall back to the whole response
        val textToSearch = sections["urgency level"]
            ?: sections["urgency"]
            ?: response.lowercase()

        val lowercaseResponse = textToSearch.lowercase()

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
     * Extracts safety status from structural response.
     * Prioritizes searching within a "Safety Status" section.
     */
    fun extractSafetyStatus(response: String): SafetyStatus {
        val sections = extractSections(response)
        val textToSearch = sections["safety status"]
            ?: sections["safety"]
            ?: response.lowercase()

        val lowercaseResponse = textToSearch.lowercase()

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
     * Extracts action items or recommendations from the response.
     * Prefers looking in "Recommendations" or "Actions" sections.
     */
    fun extractActionItems(response: String): List<String> {
        val sections = extractSections(response)
        val textToSearch = sections["recommendations"]
            ?: sections["recommended actions"]
            ?: sections["actions"]
            ?: response // Fallback to whole response

        return extractListItems(textToSearch).ifEmpty {
            // Fallback for non-list format
            textToSearch.split(Regex("[.!]"))
                .map { it.trim() }
                .filter { it.length > 15 } // Basic filter for sentence-like items
        }.take(5)
    }

    /**
     * Extracts key observations or findings from the response.
     * Prefers looking in "Key Findings" or "Assessment" sections.
     */
    fun extractKeyFindings(response: String, maxFindings: Int = 5): List<String> {
        val sections = extractSections(response)
        val textToSearch = sections["key findings"]
            ?: sections["observations"]
            ?: sections["assessment"]
            ?: response

        return extractListItems(textToSearch).ifEmpty {
            textToSearch.split(Regex("[.!]"))
                .map { it.trim() }
                .filter { it.length > 15 }
        }.take(maxFindings)
    }

    /**
     * Extracts numbered or bulleted lists from a given text block.
     * This is a powerful helper for structured data.
     */
    fun extractListItems(text: String): List<String> {
        val items = mutableListOf<String>()

        // Match numbered lists (e.g., "1. Do this") and bulleted lists (e.g., "- Do that")
        val listPattern = Regex("^[\\s*]*([\\d]+[.)]|[*-•])\\s+(.*)", RegexOption.MULTILINE)

        listPattern.findAll(text).forEach { matchResult ->
            // The actual content is in the second capturing group
            val item = matchResult.groupValues[2].trim()
            if (item.isNotEmpty()) {
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
     * Splits response into sections based on headers like "Section:".
     */
    private fun extractSections(response: String): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        // Handles optional numbers, periods, and spaces before the header text.
        val headerPattern = Regex("""^\s*(?:\d+\.\s*)?([A-Za-z\s]+):""", RegexOption.MULTILINE)
        val matches = headerPattern.findAll(response).toList()

        if (matches.isEmpty()) {
            // If no headers are found, treat the whole text as the primary content
            // to allow keyword search as a last resort.
            sections["assessment"] = response
            return sections
        }

        // Handle any text that might appear before the very first header
        val firstMatch = matches.first()
        if (firstMatch.range.first > 0) {
            val preamble = response.substring(0, firstMatch.range.first).trim()
            if (preamble.isNotEmpty()) {
                sections["preamble"] = preamble
            }
        }

        // Iterate through all found headers to carve up the text
        matches.forEachIndexed { index, matchResult ->
            // The header text (e.g., "Urgency Level") is in the first capturing group
            val header = matchResult.groupValues[1].trim().lowercase()
            val contentStart = matchResult.range.last + 1

            // The content for this section ends where the next header begins, or at the end of the string
            val contentEnd = if (index + 1 < matches.size) {
                matches[index + 1].range.first
            } else {
                response.length
            }

            val content = response.substring(contentStart, contentEnd).trim()
            if (content.isNotEmpty()) {
                sections[header] = content
            }
        }

        Log.d(TAG, "Extracted sections: ${sections.keys.joinToString()}")
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

    enum class RiskLevel {
        CRITICAL,
        HIGH,
        MODERATE,
        LOW,
        UNKNOWN
    }
}