package com.cautious5.crisis_coach.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cautious5.crisis_coach.ui.theme.CrisisCoachTheme
import com.cautious5.crisis_coach.utils.HuggingFaceAuthManager

class AuthWebViewActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AuthWebViewActivity"
        const val EXTRA_MODEL_URL = "model_url"
        const val EXTRA_MODEL_NAME = "model_name"
        const val RESULT_AUTH_SUCCESS = 1001
        const val RESULT_AUTH_CANCELLED = 1002

        fun start(context: Context, modelUrl: String, modelName: String) {
            val intent = Intent(context, AuthWebViewActivity::class.java).apply {
                putExtra(EXTRA_MODEL_URL, modelUrl)
                putExtra(EXTRA_MODEL_NAME, modelName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun newIntent(context: Context, modelUrl: String, modelName: String): Intent {
            return Intent(context, AuthWebViewActivity::class.java).apply {
                putExtra(EXTRA_MODEL_URL, modelUrl)
                putExtra(EXTRA_MODEL_NAME, modelName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelUrl = intent.getStringExtra(EXTRA_MODEL_URL) ?: ""
        val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: ""

        setContent {
            CrisisCoachTheme {
                AuthWebViewScreen(
                    modelUrl = modelUrl,
                    modelName = modelName,
                    onAuthSuccess = {
                        setResult(RESULT_AUTH_SUCCESS)
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthWebViewScreen(
    modelUrl: String,
    modelName: String,
    onAuthSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasAcceptedLicense by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val authManager = remember { HuggingFaceAuthManager(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (hasAcceptedLicense) "Authentication Complete" else "Sign in to Hugging Face"
                )
            },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            },
            actions = {
                if (hasAcceptedLicense) {
                    TextButton(onClick = onAuthSuccess) {
                        Text("Continue")
                    }
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false

                                // Check if user can see the model page (meaning they are logged in and have accepted terms)
                                // This is the key logic change.
                                if (url != null && !url.contains("login") && !url.contains("join") && url.contains("google/gemma")) {
                                    Log.d("AuthWebView", "Login successful, now on model page: $url")
                                    // Extract and save cookies for future downloads
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    if (!cookies.isNullOrEmpty()) {
                                        authManager.saveCookies(cookies)
                                        Log.d("AuthWebView", "Cookies saved for download")
                                        hasAcceptedLicense = true
                                    }
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        loadUrl(modelUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Loading Hugging Face...")
                    }
                }
            }
        }
    }
}