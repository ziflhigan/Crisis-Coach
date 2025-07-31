package com.cautious5.crisis_coach.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cautious5.crisis_coach.ui.AuthWebViewActivity

class HuggingFaceAuthManager(private val context: Context) {
    companion object {
        private const val TAG = "HFAuthManager"
        private const val PREFS_NAME = "hf_auth"
        private const val KEY_COOKIES = "hf_cookies"
        private const val KEY_HF_TOKEN = "hf_token"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStoredCookies(): String? {
        return prefs.getString(KEY_COOKIES, null)
    }

    fun saveCookies(cookies: String) {
        prefs.edit().putString(KEY_COOKIES, cookies).apply()
        Log.d(TAG, "Authentication cookies saved.")

        cookies.split(";").firstOrNull() {
            it.trim().startsWith("token=")
        }?.let { pair ->
            val token = pair.substringAfter("token=").trim()
            if (token.isNotBlank()){
                saveToken(token)
            }
        }
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_HF_TOKEN, token).apply()
        Log.d(TAG, "Hugging Face token saved.")
    }

    fun getToken(): String? {
        return prefs.getString(KEY_HF_TOKEN, null)
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_COOKIES)
            .remove(KEY_HF_TOKEN)
            .apply()
    }

    fun isAuthenticated(): Boolean = !getToken().isNullOrBlank()

    fun launchAuthFlow(modelVariant: com.cautious5.crisis_coach.model.ai.ModelVariant) {
        val modelRepo = modelVariant.huggingFaceRepo
        val loginUrl = "https://huggingface.co/login?next=${Uri.encode("/${modelRepo}")}"

        Log.d(TAG, "Launching auth flow with URL: $loginUrl")
        AuthWebViewActivity.start(context, loginUrl, modelVariant.displayName)
    }
}