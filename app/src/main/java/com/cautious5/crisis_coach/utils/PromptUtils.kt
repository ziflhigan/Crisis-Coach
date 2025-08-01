package com.cautious5.crisis_coach.utils

/**
 * Centralized utilities for building prompts for the Gemma model
 * Ensures consistent prompt formatting across all features
 */
object PromptUtils {

    /**
     * System prompt templates for different use cases
     */
    object SystemPrompts {
        val MEDICAL_ASSISTANT = """
            You are an experienced field medic AI assistant analyzing medical situations in emergency scenarios.
            Provide clear, actionable advice while acknowledging when professional medical care is required.
            Focus on immediate care that can be provided in the field.
        """.trimIndent()

        val STRUCTURAL_ENGINEER = """
            You are a structural engineer AI assistant analyzing damage to buildings and infrastructure.
            Focus on safety assessment, structural integrity, and immediate risks.
            Provide clear guidance on whether areas are safe to enter or require evacuation.
        """.trimIndent()

        val TRANSLATOR = """
            You are a multilingual assistant helping with emergency communication.
            Provide accurate, natural translations that preserve urgency and clarity.
            When requested, include pronunciation guides to help non-native speakers.
        """.trimIndent()

        val EMERGENCY_ADVISOR = """
            You are a disaster response AI assistant with expert emergency knowledge.
            Provide concise, actionable advice based on established emergency protocols.
            Always prioritize safety and clarity in your responses.
        """.trimIndent()
    }

    /**
     * Builds a translation prompt with optional pronunciation guide
     */
    fun buildTranslationPrompt(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        includePronunciation: Boolean = false,
        contextHint: String? = null
    ): String {
        val basePrompt = buildString {
            appendLine(SystemPrompts.TRANSLATOR)
            appendLine()

            contextHint?.let {
                appendLine("Context: $it")
                appendLine()
            }

            appendLine("Translate the following $sourceLanguage text to $targetLanguage:")
            appendLine("\"$text\"")
        }

        return if (includePronunciation) {
            """
            $basePrompt
            
            Please provide:
            1. The translation in $targetLanguage
            2. A pronunciation guide in $sourceLanguage script
            
            Format your response as:
            Translation: [translated text]
            Pronunciation: [pronunciation guide]
            
            Don't include any markdown syntax.
            """.trimIndent()
        } else {
            "$basePrompt\n\nProvide only the translation:"
        }
    }

    /**
     * Builds a medical analysis prompt
     */
    fun buildMedicalAnalysisPrompt(
        specificQuestion: String? = null,
        patientContext: String? = null,
        urgencyHint: String? = null
    ): String {
        return buildString {
            appendLine(SystemPrompts.MEDICAL_ASSISTANT)
            appendLine()

            appendLine("Please analyze this medical image and provide:")
            appendLine("1. A clear assessment of what you observe")
            appendLine("2. The urgency level (Critical, High, Medium, Low)")
            appendLine("3. Immediate care recommendations")
            appendLine("4. Whether professional medical care is required")

            patientContext?.let {
                appendLine()
                appendLine("Patient context: $it")
            }

            specificQuestion?.let {
                appendLine()
                appendLine("Specific question: $it")
            }

            urgencyHint?.let {
                appendLine()
                appendLine("Note: $it")
            }

            appendLine()
            appendLine("Provide your medical assessment:")
        }
    }

    /**
     * Builds a structural analysis prompt
     */
    fun buildStructuralAnalysisPrompt(
        structureType: String,
        specificConcerns: String? = null,
        additionalContext: String? = null
    ): String {
        return buildString {
            appendLine(SystemPrompts.STRUCTURAL_ENGINEER)
            appendLine()

            appendLine("Analyzing damage to: $structureType")
            appendLine()
            appendLine("Please provide:")
            appendLine("1. Assessment of visible damage or structural issues")
            appendLine("2. Safety status (Safe, Caution, Unsafe, Critical)")
            appendLine("3. Identified problems or concerns")
            appendLine("4. Immediate actions required")

            specificConcerns?.let {
                appendLine()
                appendLine("Specific concerns: $it")
            }

            additionalContext?.let {
                appendLine()
                appendLine("Additional context: $it")
            }

            appendLine()
            appendLine("Provide your structural assessment:")
        }
    }

    /**
     * Builds a RAG prompt with retrieved context
     */
    fun buildRAGPrompt(
        question: String,
        retrievedDocs: List<String>,
        maxDocsToInclude: Int = 3,
        includeConfidenceNote: Boolean = true
    ): String {
        val docs = retrievedDocs.take(maxDocsToInclude)

        return buildString {
            appendLine(SystemPrompts.EMERGENCY_ADVISOR)
            appendLine()

            appendLine("Question: $question")
            appendLine()

            if (docs.isNotEmpty()) {
                appendLine("Relevant Information:")
                docs.forEachIndexed { index, doc ->
                    appendLine("${index + 1}. $doc")
                }
                appendLine()

                appendLine("Based on the above information, provide a comprehensive answer to the question.")

                if (includeConfidenceNote) {
                    appendLine("If the information is insufficient or unclear, acknowledge this in your response.")
                }
            } else {
                appendLine("No specific information was found in the knowledge base.")
                appendLine("Provide a general answer based on emergency response best practices.")
            }

            appendLine()
            appendLine("Answer:")
        }
    }

    /**
     * Builds a general image analysis prompt
     */
    fun buildGeneralImageAnalysisPrompt(
        question: String,
        focusArea: String? = null
    ): String {
        return buildString {
            appendLine(SystemPrompts.EMERGENCY_ADVISOR)
            appendLine()

            appendLine("Analyzing image for emergency response purposes.")

            focusArea?.let {
                appendLine("Focus area: $it")
            }

            appendLine()
            appendLine("Question: $question")
            appendLine()
            appendLine("Please provide:")
            appendLine("- Detailed description of relevant observations")
            appendLine("- Safety concerns or hazards identified")
            appendLine("- Recommended actions or precautions")
            appendLine()
            appendLine("Your analysis:")
        }
    }

    /**
     * Formats a list of items into a structured prompt section
     */
    fun formatListSection(title: String, items: List<String>): String {
        return if (items.isEmpty()) {
            ""
        } else {
            buildString {
                appendLine(title)
                items.forEach { item ->
                    appendLine("- $item")
                }
            }
        }
    }

    /**
     * Adds safety disclaimers to responses when needed
     */
    fun addSafetyDisclaimer(
        response: String,
        disclaimerType: DisclaimerType = DisclaimerType.GENERAL
    ): String {
        val disclaimer = when (disclaimerType) {
            DisclaimerType.MEDICAL ->
                "\n\nNote: This AI assessment is not a substitute for professional medical care. " +
                        "If possible, seek qualified medical assistance."
            DisclaimerType.STRUCTURAL ->
                "\n\nWarning: This assessment is based on visible indicators only. " +
                        "A professional structural inspection is recommended when available."
            DisclaimerType.GENERAL ->
                "\n\nImportant: This is AI-generated advice for emergency situations. " +
                        "Always prioritize safety and seek professional help when possible."
        }

        return response + disclaimer
    }

    enum class DisclaimerType {
        MEDICAL,
        STRUCTURAL,
        GENERAL
    }
}