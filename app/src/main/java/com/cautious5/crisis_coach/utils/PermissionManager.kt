package com.cautious5.crisis_coach.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.cautious5.crisis_coach.utils.Constants.LogTags

/**
 * Centralized permission management for Crisis Coach app
 * Handles camera, microphone, and storage permissions with Compose integration
 */
class PermissionManager(private val activity: ComponentActivity) {

    companion object {
        private const val TAG = LogTags.PERMISSION_MANAGER

        // Required permissions for different features
        val CAMERA_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
        )

        val MICROPHONE_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )

        val STORAGE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        val ALL_PERMISSIONS = CAMERA_PERMISSIONS + MICROPHONE_PERMISSIONS + STORAGE_PERMISSIONS
    }

    /**
     * Permission state for UI binding
     */
    data class PermissionState(
        val hasCameraPermission: Boolean = false,
        val hasMicrophonePermission: Boolean = false,
        val hasStoragePermission: Boolean = false,
        val isRequestingPermissions: Boolean = false,
        val deniedPermissions: List<String> = emptyList(),
        val permanentlyDeniedPermissions: List<String> = emptyList()
    ) {
        val hasAllRequiredPermissions: Boolean
            get() = hasCameraPermission && hasMicrophonePermission && hasStoragePermission

        val canUseCamera: Boolean
            get() = hasCameraPermission

        val canUseMicrophone: Boolean
            get() = hasMicrophonePermission

        val canAccessStorage: Boolean
            get() = hasStoragePermission
    }

    /**
     * Permission request result callback
     */
    interface PermissionCallback {
        fun onPermissionGranted(permission: String)
        fun onPermissionDenied(permission: String, isPermanentlyDenied: Boolean)
        fun onAllPermissionsResult(granted: List<String>, denied: List<String>)
    }

    private var permissionCallback: PermissionCallback? = null
    private var currentPermissionState = mutableStateOf(getCurrentPermissionState())

    // Activity result launcher for multiple permissions
    private val multiplePermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionResults(permissions)
        }

    // Activity result launcher for single permission
    private val singlePermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            // Handle single permission result - this will be called by the multiple permission handler
        }

    /**
     * Gets current permission state
     */
    fun getCurrentPermissionState(): PermissionState {
        return PermissionState(
            hasCameraPermission = hasPermission(Manifest.permission.CAMERA),
            hasMicrophonePermission = hasPermission(Manifest.permission.RECORD_AUDIO),
            hasStoragePermission = hasStoragePermissions(),
            isRequestingPermissions = false,
            deniedPermissions = getDeniedPermissions(),
            permanentlyDeniedPermissions = getPermanentlyDeniedPermissions()
        )
    }

    /**
     * Checks if a specific permission is granted
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if all camera permissions are granted
     */
    fun hasCameraPermissions(): Boolean {
        return CAMERA_PERMISSIONS.all { hasPermission(it) }
    }

    /**
     * Checks if all microphone permissions are granted
     */
    fun hasMicrophonePermissions(): Boolean {
        return MICROPHONE_PERMISSIONS.all { hasPermission(it) }
    }

    /**
     * Checks if all storage permissions are granted
     */
    fun hasStoragePermissions(): Boolean {
        return STORAGE_PERMISSIONS.all { hasPermission(it) }
    }

    /**
     * Checks if all required permissions are granted
     */
    fun hasAllRequiredPermissions(): Boolean {
        return ALL_PERMISSIONS.all { hasPermission(it) }
    }

    /**
     * Gets list of denied permissions
     */
    private fun getDeniedPermissions(): List<String> {
        return ALL_PERMISSIONS.filter { !hasPermission(it) }
    }

    /**
     * Gets list of permanently denied permissions
     */
    private fun getPermanentlyDeniedPermissions(): List<String> {
        return ALL_PERMISSIONS.filter { permission ->
            !hasPermission(permission) && !activity.shouldShowRequestPermissionRationale(permission)
        }
    }

    /**
     * Requests all required permissions
     */
    fun requestAllPermissions(callback: PermissionCallback? = null) {
        Log.d(TAG, "Requesting all required permissions")

        permissionCallback = callback
        currentPermissionState.value = currentPermissionState.value.copy(isRequestingPermissions = true)

        val deniedPermissions = getDeniedPermissions()
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            updatePermissionState()
            callback?.onAllPermissionsResult(ALL_PERMISSIONS.toList(), emptyList())
            return
        }

        Log.d(TAG, "Requesting permissions: ${deniedPermissions.joinToString(", ")}")
        multiplePermissionLauncher.launch(deniedPermissions.toTypedArray())
    }

    /**
     * Requests camera permissions
     */
    fun requestCameraPermissions(callback: PermissionCallback? = null) {
        Log.d(TAG, "Requesting camera permissions")

        permissionCallback = callback

        val deniedPermissions = CAMERA_PERMISSIONS.filter { !hasPermission(it) }
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "Camera permissions already granted")
            callback?.onAllPermissionsResult(CAMERA_PERMISSIONS.toList(), emptyList())
            return
        }

        multiplePermissionLauncher.launch(deniedPermissions.toTypedArray())
    }

    /**
     * Requests microphone permissions
     */
    fun requestMicrophonePermissions(callback: PermissionCallback? = null) {
        Log.d(TAG, "Requesting microphone permissions")

        permissionCallback = callback

        val deniedPermissions = MICROPHONE_PERMISSIONS.filter { !hasPermission(it) }
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "Microphone permissions already granted")
            callback?.onAllPermissionsResult(MICROPHONE_PERMISSIONS.toList(), emptyList())
            return
        }

        multiplePermissionLauncher.launch(deniedPermissions.toTypedArray())
    }

    /**
     * Requests storage permissions
     */
    fun requestStoragePermissions(callback: PermissionCallback? = null) {
        Log.d(TAG, "Requesting storage permissions")

        permissionCallback = callback

        val deniedPermissions = STORAGE_PERMISSIONS.filter { !hasPermission(it) }
        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "Storage permissions already granted")
            callback?.onAllPermissionsResult(STORAGE_PERMISSIONS.toList(), emptyList())
            return
        }

        multiplePermissionLauncher.launch(deniedPermissions.toTypedArray())
    }

    /**
     * Handles permission request results
     */
    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        Log.d(TAG, "Permission results received: $permissions")

        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()

        permissions.forEach { (permission, isGranted) ->
            if (isGranted) {
                granted.add(permission)
                permissionCallback?.onPermissionGranted(permission)
                Log.d(TAG, "Permission granted: $permission")
            } else {
                denied.add(permission)
                val isPermanentlyDenied = !activity.shouldShowRequestPermissionRationale(permission)
                permissionCallback?.onPermissionDenied(permission, isPermanentlyDenied)
                Log.w(TAG, "Permission denied: $permission (permanently: $isPermanentlyDenied)")
            }
        }

        updatePermissionState()
        permissionCallback?.onAllPermissionsResult(granted, denied)
        permissionCallback = null
    }

    /**
     * Updates the current permission state
     */
    private fun updatePermissionState() {
        currentPermissionState.value = getCurrentPermissionState()
    }

    /**
     * Gets permission rationale message for UI
     */
    fun getPermissionRationale(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA ->
                "Camera access is required to capture images for medical and structural analysis. " +
                        "This helps provide accurate AI-powered assessments in emergency situations."

            Manifest.permission.RECORD_AUDIO ->
                "Microphone access is required for voice translation and voice commands. " +
                        "This enables hands-free operation during emergency response."

            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO ->
                "Storage access is required to select images from your gallery for analysis " +
                        "and to save important emergency information."

            else -> "This permission is required for the app to function properly."
        }
    }

    /**
     * Gets user-friendly permission name
     */
    fun getPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO -> "Storage"
            else -> permission.substringAfterLast(".")
        }
    }

    /**
     * Checks if we should show rationale for permission
     */
    fun shouldShowRationale(permission: String): Boolean {
        return activity.shouldShowRequestPermissionRationale(permission)
    }

    /**
     * Checks if permission was permanently denied
     */
    fun isPermanentlyDenied(permission: String): Boolean {
        return !hasPermission(permission) && !shouldShowRationale(permission)
    }

    /**
     * Gets permissions needed for a specific feature
     */
    fun getPermissionsForFeature(feature: String): Array<String> {
        return when (feature.lowercase()) {
            "camera", "image", "photo" -> CAMERA_PERMISSIONS
            "microphone", "voice", "audio", "speech" -> MICROPHONE_PERMISSIONS
            "storage", "gallery", "files" -> STORAGE_PERMISSIONS
            "translate", "translation" -> MICROPHONE_PERMISSIONS + STORAGE_PERMISSIONS
            "image_triage", "image_analysis" -> CAMERA_PERMISSIONS + STORAGE_PERMISSIONS
            else -> ALL_PERMISSIONS
        }
    }

    /**
     * Compose function to observe permission state
     */
    @Composable
    fun rememberPermissionState(): PermissionState {
        var permissionState by remember { mutableStateOf(getCurrentPermissionState()) }

        LaunchedEffect(Unit) {
            permissionState = getCurrentPermissionState()
        }

        return permissionState
    }
}

/**
 * Extension function to check if activity has a specific permission
 */
fun Activity.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Extension function to check if context has a specific permission
 */
fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Simple permission state composable for quick checks
 */
@Composable
fun rememberPermissionState(
    context: Context,
    permission: String
): Boolean {
    var hasPermission by remember { mutableStateOf(context.hasPermission(permission)) }

    LaunchedEffect(permission) {
        hasPermission = context.hasPermission(permission)
    }

    return hasPermission
}