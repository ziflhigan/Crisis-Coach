package com.cautious5.crisis_coach

import android.app.Application
import android.util.Log
import com.cautious5.crisis_coach.model.ai.GemmaModelManager
import com.cautious5.crisis_coach.model.database.KnowledgeBase
import com.cautious5.crisis_coach.model.database.entities.MyObjectBox
import com.cautious5.crisis_coach.model.embedding.TextEmbedder
import com.cautious5.crisis_coach.model.services.ImageAnalysisService
import com.cautious5.crisis_coach.model.services.SpeechService
import com.cautious5.crisis_coach.model.services.TextToSpeechService
import com.cautious5.crisis_coach.model.services.TranslationService
import com.cautious5.crisis_coach.utils.Constants.LogTags
import io.objectbox.BoxStore

class CrisisCoachApplication : Application() {

    companion object {
        private const val TAG = LogTags.MAIN_ACTIVITY
    }

    val boxStore: BoxStore by lazy {
        Log.d(TAG, "Initializing ObjectBox database")
        MyObjectBox.builder()
            .androidContext(this)
            .name("crisis_coach_db")
            .build()
    }

    val textEmbedder: TextEmbedder by lazy {
        Log.d(TAG, "Initializing TextEmbedder singleton")
        TextEmbedder.getInstance(this)
    }

    val gemmaModelManager: GemmaModelManager by lazy {
        Log.d(TAG, "Initializing GemmaModelManager singleton")
        GemmaModelManager.getInstance(this)
    }

    val knowledgeBase: KnowledgeBase by lazy {
        Log.d(TAG, "Initializing KnowledgeBase singleton")
        KnowledgeBase(boxStore, textEmbedder)
    }

    val speechService: SpeechService by lazy {
        Log.d(TAG, "Initializing SpeechService singleton")
        SpeechService(this)
    }

    val textToSpeechService: TextToSpeechService by lazy {
        Log.d(TAG, "Initializing TextToSpeechService singleton")
        TextToSpeechService(this)
    }

    val translationService: TranslationService by lazy {
        Log.d(TAG, "Initializing TranslationService singleton")
        TranslationService(this, gemmaModelManager, speechService, textToSpeechService)
    }

    val imageAnalysisService: ImageAnalysisService by lazy {
        Log.d(TAG, "Initializing ImageAnalysisService singleton")
        ImageAnalysisService(this, gemmaModelManager)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CrisisCoachApplication onCreate")
        // Triggering the lazy initialization of the database here is a good practice.
        // Other services will be initialized when they are first accessed.
        boxStore
    }
}