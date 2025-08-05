package com.cautious5.crisis_coach

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.cautious5.crisis_coach.ui.AuthWebViewActivity
import com.cautious5.crisis_coach.ui.screens.main.MainScreen
import com.cautious5.crisis_coach.ui.theme.CrisisCoachTheme
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.LocalPermissionManager
import com.cautious5.crisis_coach.utils.PermissionManager

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = LogTags.MAIN_ACTIVITY
    }

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        permissionManager = PermissionManager(this)

        splashScreen.setKeepOnScreenCondition {
            mainViewModel.uiState.value.showInitializationProgress
        }

        val authActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AuthWebViewActivity.RESULT_AUTH_SUCCESS) {
                Log.d(TAG, "Auth flow completed successfully. Retrying download.")
                mainViewModel.retryDownloadAfterAuth()
            } else {
                Log.w(TAG, "Auth flow was cancelled or failed.")
            }
        }

        enableEdgeToEdge()

        setContent {
            CrisisCoachTheme {
                CompositionLocalProvider(LocalPermissionManager provides permissionManager) {
                    val modelToAuth by mainViewModel.triggerAuthFlow.collectAsState()

                    LaunchedEffect(modelToAuth) {
                        modelToAuth?.let { variant ->
                            val intent = AuthWebViewActivity.newIntent(
                                context = this@MainActivity,
                                modelUrl = "https://huggingface.co/login?next=" +
                                        Uri.encode("/${variant.huggingFaceRepo}"),
                                modelName = variant.displayName
                            )
                            authActivityLauncher.launch(intent)
                            mainViewModel.onAuthFlowTriggered() // Reset the trigger
                        }
                    }

                    MainScreen(viewModel = mainViewModel)
                }
            }
        }
    }
}