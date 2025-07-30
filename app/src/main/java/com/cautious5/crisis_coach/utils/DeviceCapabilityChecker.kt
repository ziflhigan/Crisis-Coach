package com.cautious5.crisis_coach.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.cautious5.crisis_coach.model.ai.ModelVariant
import com.cautious5.crisis_coach.model.ai.HardwarePreference
import com.cautious5.crisis_coach.utils.Constants.LogTags
import com.cautious5.crisis_coach.utils.Constants.MIN_RAM_FOR_E4B_MB
import com.cautious5.crisis_coach.utils.Constants.MIN_RAM_FOR_APP_MB
import com.cautious5.crisis_coach.utils.Constants.MIN_STORAGE_FOR_MODEL_MB

/**
 * Utility class for detecting device capabilities and recommending optimal configurations
 * for the Crisis Coach app based on available hardware resources
 */
object DeviceCapabilityChecker {

    private const val TAG = LogTags.DEVICE_CHECKER

    /**
     * Device capability assessment result
     */
    data class DeviceCapability(
        val canRunApp: Boolean,
        val recommendedModelVariant: ModelVariant,
        val recommendedHardwarePreference: HardwarePreference,
        val availableRamMB: Long,
        val availableStorageMB: Long,
        val hasGpuAcceleration: Boolean,
        val hasNeuralProcessing: Boolean,
        val deviceInfo: DeviceInfo,
        val limitations: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    /**
     * Basic device information
     */
    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val apiLevel: Int,
        val architecture: String,
        val screenDensity: String,
        val totalRamMB: Long
    )

    /**
     * Comprehensive device capability assessment
     */
    fun assessDeviceCapability(context: Context): DeviceCapability {
        Log.d(TAG, "Starting device capability assessment")

        val deviceInfo = getDeviceInfo(context)
        val availableRam = getAvailableRamMB(context)
        val totalRam = getTotalRamMB(context)
        val availableStorage = getAvailableStorageMB()
        val hasGpuAcceleration = checkGpuAcceleration(context)
        val hasNeuralProcessing = checkNeuralProcessingSupport(context)

        val limitations = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check minimum requirements
        val canRunApp = availableRam >= MIN_RAM_FOR_APP_MB &&
                availableStorage >= MIN_STORAGE_FOR_MODEL_MB

        if (availableRam < MIN_RAM_FOR_APP_MB) {
            limitations.add("Insufficient RAM: ${availableRam}MB available, ${MIN_RAM_FOR_APP_MB}MB required")
        }

        if (availableStorage < MIN_STORAGE_FOR_MODEL_MB) {
            limitations.add("Insufficient storage: ${availableStorage}MB available, ${MIN_STORAGE_FOR_MODEL_MB}MB required")
        }

        // Determine recommended model variant
        val recommendedModelVariant = when {
            totalRam >= MIN_RAM_FOR_E4B_MB && hasGpuAcceleration -> {
                Log.d(TAG, "High-end device detected, recommending E4B model")
                ModelVariant.GEMMA_3N_E4B
            }
            totalRam >= MIN_RAM_FOR_E4B_MB -> {
                warnings.add("E4B model possible but may be slow without GPU acceleration")
                ModelVariant.GEMMA_3N_E4B
            }
            else -> {
                Log.d(TAG, "Mid-range device detected, recommending E2B model")
                if (totalRam < 4096) {
                    warnings.add("Limited RAM may affect performance")
                }
                ModelVariant.GEMMA_3N_E2B
            }
        }

        // Determine hardware preference
        val recommendedHardwarePreference = when {
            hasNeuralProcessing -> {
                Log.d(TAG, "Neural processing unit detected, using NNAPI")
                HardwarePreference.NNAPI
            }
            hasGpuAcceleration -> {
                Log.d(TAG, "GPU acceleration available, using GPU preference")
                HardwarePreference.GPU_PREFERRED
            }
            else -> {
                Log.d(TAG, "No hardware acceleration detected, using CPU only")
                HardwarePreference.CPU_ONLY
            }
        }

        // Additional warnings based on device characteristics
        if (deviceInfo.apiLevel < 30) {
            warnings.add("Android API level ${deviceInfo.apiLevel} may have limited functionality")
        }

        if (availableStorage < 8192) { // Less than 8GB
            warnings.add("Low storage space may affect app performance")
        }

        val capability = DeviceCapability(
            canRunApp = canRunApp,
            recommendedModelVariant = recommendedModelVariant,
            recommendedHardwarePreference = recommendedHardwarePreference,
            availableRamMB = availableRam,
            availableStorageMB = availableStorage,
            hasGpuAcceleration = hasGpuAcceleration,
            hasNeuralProcessing = hasNeuralProcessing,
            deviceInfo = deviceInfo,
            limitations = limitations,
            warnings = warnings
        )

        logCapabilityAssessment(capability)
        return capability
    }

    /**
     * Gets basic device information
     */
    private fun getDeviceInfo(context: Context): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            architecture = getArchitecture(),
            screenDensity = getScreenDensity(context),
            totalRamMB = getTotalRamMB(context)
        )
    }

    /**
     * Gets device architecture
     */
    private fun getArchitecture(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
    }

    /**
     * Gets screen density classification
     */
    private fun getScreenDensity(context: Context): String {
        val density = context.resources.displayMetrics.densityDpi
        return when {
            density <= 120 -> "LDPI"
            density <= 160 -> "MDPI"
            density <= 240 -> "HDPI"
            density <= 320 -> "XHDPI"
            density <= 480 -> "XXHDPI"
            else -> "XXXHDPI"
        }
    }

    /**
     * Gets available RAM in MB
     */
    private fun getAvailableRamMB(context: Context): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.availMem / (1024 * 1024) // Convert to MB
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get available RAM: ${e.message}")
            0L
        }
    }

    /**
     * Gets total RAM in MB
     */
    private fun getTotalRamMB(context: Context): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem / (1024 * 1024) // Convert to MB
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get total RAM: ${e.message}")
            4096L // Default assumption for fallback
        }
    }

    /**
     * Gets available storage in MB
     */
    private fun getAvailableStorageMB(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableBytes = stat.availableBytes
            availableBytes / (1024 * 1024) // Convert to MB
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get available storage: ${e.message}")
            0L
        }
    }

    /**
     * Checks if GPU acceleration is available
     */
    private fun checkGpuAcceleration(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager

            // Check for OpenGL ES 3.0+ support (indicator of decent GPU)
            val hasOpenGLES3 = packageManager.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK) ||
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

            // Check for Vulkan API support (modern GPU acceleration)
            val hasVulkan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) ||
                        packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
            } else {
                false
            }

            // Additional checks for known GPU-capable devices
            val isKnownGpuDevice = checkKnownGpuDevices()

            val hasGpu = hasOpenGLES3 || hasVulkan || isKnownGpuDevice
            Log.d(TAG, "GPU acceleration check: OpenGL ES 3.0=$hasOpenGLES3, Vulkan=$hasVulkan, Known GPU device=$isKnownGpuDevice")

            hasGpu
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check GPU acceleration: ${e.message}")
            false
        }
    }

    /**
     * Checks for known GPU-capable devices based on manufacturer and model
     */
    private fun checkKnownGpuDevices(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()

        return when {
            // Samsung devices with Exynos or Snapdragon
            manufacturer.contains("samsung") && (
                    model.contains("galaxy s") ||
                            model.contains("galaxy note") ||
                            model.contains("galaxy a5") ||
                            model.contains("galaxy a7")
                    ) -> true

            // Google Pixel devices
            manufacturer.contains("google") && model.contains("pixel") -> true

            // OnePlus devices
            manufacturer.contains("oneplus") -> true

            // Xiaomi flagship devices
            manufacturer.contains("xiaomi") && (
                    model.contains("mi ") ||
                            model.contains("redmi note")
                    ) -> true

            // Huawei/Honor devices with Kirin chips
            (manufacturer.contains("huawei") || manufacturer.contains("honor")) &&
                    (model.contains("mate") || model.contains("p30") || model.contains("p40")) -> true

            else -> false
        }
    }

    /**
     * Checks if neural processing (NPU/NNAPI) is supported
     */
    private fun checkNeuralProcessingSupport(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9+ has NNAPI support
                val packageManager = context.packageManager

                // Check for dedicated neural processing features
                val hasNeuralNetworks = try {
                    // This is a best-effort check - NNAPI availability is hard to detect definitively
                    Class.forName("android.hardware.neuralnetworks.V1_0.IDevice") != null
                } catch (e: ClassNotFoundException) {
                    false
                }

                // Check for known NPU-capable devices
                val hasKnownNPU = checkKnownNPUDevices()

                Log.d(TAG, "Neural processing check: NNAPI class found=$hasNeuralNetworks, Known NPU device=$hasKnownNPU")
                hasNeuralNetworks || hasKnownNPU
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check neural processing support: ${e.message}")
            false
        }
    }

    /**
     * Checks for known NPU-capable devices
     */
    private fun checkKnownNPUDevices(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()

        return when {
            // Huawei devices with Kirin NPU
            manufacturer.contains("huawei") && (
                    model.contains("mate 20") ||
                            model.contains("mate 30") ||
                            model.contains("p30") ||
                            model.contains("p40")
                    ) -> true

            // Samsung devices with NPU (Exynos 9820+)
            manufacturer.contains("samsung") && (
                    model.contains("galaxy s10") ||
                            model.contains("galaxy s20") ||
                            model.contains("galaxy s21") ||
                            model.contains("galaxy note 10") ||
                            model.contains("galaxy note 20")
                    ) -> true

            // Google Pixel 4+ with Neural Core/Tensor
            manufacturer.contains("google") && (
                    model.contains("pixel 4") ||
                            model.contains("pixel 5") ||
                            model.contains("pixel 6") ||
                            model.contains("pixel 7")
                    ) -> true

            else -> false
        }
    }

    /**
     * Quick check if device meets minimum requirements
     */
    fun meetsMinimumRequirements(context: Context): Boolean {
        val availableRam = getAvailableRamMB(context)
        val availableStorage = getAvailableStorageMB()

        return availableRam >= MIN_RAM_FOR_APP_MB &&
                availableStorage >= MIN_STORAGE_FOR_MODEL_MB
    }

    /**
     * Gets recommended model variant based on device capabilities
     */
    fun getRecommendedModelVariant(context: Context): ModelVariant {
        val totalRam = getTotalRamMB(context)
        val hasGpu = checkGpuAcceleration(context)

        return if (totalRam >= MIN_RAM_FOR_E4B_MB && hasGpu) {
            ModelVariant.GEMMA_3N_E4B
        } else {
            ModelVariant.GEMMA_3N_E2B
        }
    }

    /**
     * Gets recommended hardware preference
     */
    fun getRecommendedHardwarePreference(context: Context): HardwarePreference {
        return when {
            checkNeuralProcessingSupport(context) -> HardwarePreference.NNAPI
            checkGpuAcceleration(context) -> HardwarePreference.GPU_PREFERRED
            else -> HardwarePreference.CPU_ONLY
        }
    }

    /**
     * Logs detailed capability assessment
     */
    private fun logCapabilityAssessment(capability: DeviceCapability) {
        Log.i(TAG, "=== Device Capability Assessment ===")
        Log.i(TAG, "Device: ${capability.deviceInfo.manufacturer} ${capability.deviceInfo.model}")
        Log.i(TAG, "Android: ${capability.deviceInfo.androidVersion} (API ${capability.deviceInfo.apiLevel})")
        Log.i(TAG, "Architecture: ${capability.deviceInfo.architecture}")
        Log.i(TAG, "Available RAM: ${capability.availableRamMB}MB")
        Log.i(TAG, "Available Storage: ${capability.availableStorageMB}MB")
        Log.i(TAG, "GPU Acceleration: ${capability.hasGpuAcceleration}")
        Log.i(TAG, "Neural Processing: ${capability.hasNeuralProcessing}")
        Log.i(TAG, "Can Run App: ${capability.canRunApp}")
        Log.i(TAG, "Recommended Model: ${capability.recommendedModelVariant.displayName}")
        Log.i(TAG, "Recommended Hardware: ${capability.recommendedHardwarePreference.displayName}")

        if (capability.limitations.isNotEmpty()) {
            Log.w(TAG, "Limitations: ${capability.limitations.joinToString(", ")}")
        }

        if (capability.warnings.isNotEmpty()) {
            Log.w(TAG, "Warnings: ${capability.warnings.joinToString(", ")}")
        }

        Log.i(TAG, "=== End Assessment ===")
    }
}