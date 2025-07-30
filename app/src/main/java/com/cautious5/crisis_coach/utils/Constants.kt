package com.cautious5.crisis_coach.utils

/**
 * App-wide constants and configuration values for Crisis Coach
 * Centralizes all configuration to avoid magic numbers and ensure consistency
 */
object Constants {

    // App Information
    const val APP_NAME = "Crisis Coach"
    const val APP_VERSION = "1.0.0"

    // Model Configuration
    const val DEFAULT_MODEL_VARIANT = "E2B" // E2B or E4B
    const val MODEL_INITIALIZATION_TIMEOUT_MS = 30000L
    const val MODEL_INFERENCE_TIMEOUT_MS = 15000L

    // Device Capability Thresholds
    const val MIN_RAM_FOR_E4B_MB = 6144L // 6GB RAM minimum for E4B model
    const val MIN_RAM_FOR_APP_MB = 1024L // 1GB RAM minimum to run app
    const val MIN_STORAGE_FOR_MODEL_MB = 4096L // 4GB storage minimum

    // UI Configuration
    const val BOTTOM_NAV_HEIGHT_DP = 80
    const val CARD_ELEVATION_DP = 4
    const val BUTTON_HEIGHT_DP = 56
    const val LARGE_BUTTON_HEIGHT_DP = 72
    const val ANIMATION_DURATION_MS = 300
    const val LOADING_ANIMATION_DURATION_MS = 1000

    // Voice Input Configuration
    const val VOICE_INPUT_MAX_DURATION_MS = 30000L // 30 seconds max recording
    const val VOICE_INPUT_MIN_DURATION_MS = 1000L // 1 second minimum
    const val VOICE_RECOGNITION_LANGUAGE_DEFAULT = "en-US"

    // Translation Configuration
    const val TRANSLATION_MAX_TEXT_LENGTH = 1000
    const val TRANSLATION_DEBOUNCE_DELAY_MS = 500L
    const val DEFAULT_SOURCE_LANGUAGE = "en-US"
    const val DEFAULT_TARGET_LANGUAGE = "ar"

    // Image Analysis Configuration
    const val IMAGE_MAX_SIZE_PX = 1024
    const val IMAGE_COMPRESSION_QUALITY = 90
    const val IMAGE_ANALYSIS_TIMEOUT_MS = 20000L
    const val CAMERA_PREVIEW_ASPECT_RATIO = "4:3"

    // Knowledge Base Configuration
    const val KNOWLEDGE_SEARCH_LIMIT = 5
    const val KNOWLEDGE_SIMILARITY_THRESHOLD = 0.6f
    const val KNOWLEDGE_QUERY_MIN_LENGTH = 3
    const val KNOWLEDGE_QUERY_MAX_LENGTH = 500

    // Database Configuration
    const val DB_NAME = "crisis_coach_db"
    const val EMBEDDING_DIMENSIONS = 512
    const val VECTOR_SEARCH_TOP_K = 10

    // Permissions
    val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // Error Messages
    object ErrorMessages {
        const val MODEL_NOT_READY = "AI model is not ready. Please wait for initialization to complete."
        const val CAMERA_PERMISSION_DENIED = "Camera permission is required for image analysis."
        const val MICROPHONE_PERMISSION_DENIED = "Microphone permission is required for voice input."
        const val STORAGE_PERMISSION_DENIED = "Storage permission is required to save results."
        const val INSUFFICIENT_STORAGE = "Insufficient storage space for model files."
        const val INSUFFICIENT_RAM = "Device does not meet minimum RAM requirements."
        const val MODEL_LOAD_FAILED = "Failed to load AI model. Please restart the app."
        const val NETWORK_UNAVAILABLE = "This app works offline. No internet connection required."
        const val UNKNOWN_ERROR = "An unexpected error occurred. Please try again."
    }

    // Navigation Routes
    object Routes {
        const val DASHBOARD = "dashboard"
        const val TRANSLATE = "translate"
        const val IMAGE_TRIAGE = "image_triage"
        const val KNOWLEDGE = "knowledge"
        const val SETTINGS = "settings"
    }

    // Navigation Labels
    object NavigationLabels {
        const val DASHBOARD = "Dashboard"
        const val TRANSLATE = "Translate"
        const val IMAGE_TRIAGE = "Image Analysis"
        const val KNOWLEDGE = "Emergency Guide"
        const val SETTINGS = "Settings"
    }

    // Shared Preferences Keys
    object PreferenceKeys {
        const val SELECTED_MODEL_VARIANT = "selected_model_variant"
        const val FIRST_LAUNCH = "first_launch"
        const val SOURCE_LANGUAGE = "source_language"
        const val TARGET_LANGUAGE = "target_language"
        const val VOICE_INPUT_ENABLED = "voice_input_enabled"
        const val TTS_ENABLED = "tts_enabled"
        const val SHOW_PRONUNCIATION_GUIDE = "show_pronunciation_guide"
        const val THEME_MODE = "theme_mode"
    }

    // Analysis Types
    object AnalysisTypes {
        const val MEDICAL = "medical"
        const val STRUCTURAL = "structural"
        const val GENERAL = "general"
    }

    // Priority Levels (matching EmergencyInfo priorities)
    object Priorities {
        const val CRITICAL = 1
        const val HIGH = 2
        const val MEDIUM = 3
        const val LOW = 4
        const val INFORMATIONAL = 5
    }

    // Log Tags
    object LogTags {
        const val MAIN_ACTIVITY = "MainActivity"
        const val NAVIGATION = "AppNavigation"
        const val TRANSLATE_VM = "TranslateViewModel"
        const val IMAGE_TRIAGE_VM = "ImageTriageViewModel"
        const val KNOWLEDGE_VM = "KnowledgeViewModel"
        const val DEVICE_CHECKER = "DeviceCapabilityChecker"
        const val PERMISSION_MANAGER = "PermissionManager"
    }

    // Model File Names (matching ModelVariant enum)
    object ModelFiles {
        const val GEMMA_E2B = "gemma-3n-e2b-it.task"
        const val GEMMA_E4B = "gemma-3n-e4b-it.task"
        const val TEXT_EMBEDDER = "text_embedder.tflite"
    }

    // Sample Queries for Knowledge Base
    val SAMPLE_KNOWLEDGE_QUERIES = listOf(
        "How to perform CPR?",
        "How to treat severe bleeding?",
        "What to do for a broken bone?",
        "How to treat burns?",
        "Signs of shock and treatment",
        "How to clear airway obstruction?",
        "First aid for poisoning",
        "How to splint a fracture?",
        "Treating hypothermia",
        "Emergency childbirth assistance"
    )

    // Supported Languages for Translation
    val COMMON_LANGUAGES = listOf(
        "en-US" to "English (US)",
        "en-GB" to "English (UK)",
        "es-ES" to "Spanish",
        "fr-FR" to "French",
        "de-DE" to "German",
        "it-IT" to "Italian",
        "pt-BR" to "Portuguese",
        "ar" to "Arabic",
        "zh-CN" to "Chinese",
        "ja-JP" to "Japanese",
        "ko-KR" to "Korean",
        "ru-RU" to "Russian",
        "hi-IN" to "Hindi"
    )

    // Timeout Configurations
    object Timeouts {
        const val MODEL_INIT_MS = 30000L
        const val TRANSLATION_MS = 15000L
        const val IMAGE_ANALYSIS_MS = 20000L
        const val KNOWLEDGE_SEARCH_MS = 5000L
        const val SPEECH_RECOGNITION_MS = 30000L
        const val TTS_PLAYBACK_MS = 10000L
    }

    // UI State Keys
    object UIStateKeys {
        const val LOADING = "loading"
        const val ERROR = "error"
        const val SUCCESS = "success"
        const val IDLE = "idle"
    }
}