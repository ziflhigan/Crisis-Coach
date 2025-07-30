package com.cautious5.crisis_coach.ui.screens.imagetriage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.ui.components.ResultCard
import com.cautious5.crisis_coach.ui.theme.EmergencyTextStyles
import com.cautious5.crisis_coach.ui.theme.emergencyHigh
import com.cautious5.crisis_coach.ui.theme.emergencyMedium
import com.cautious5.crisis_coach.ui.theme.generalSurface
import com.cautious5.crisis_coach.ui.theme.medicalSurface
import com.cautious5.crisis_coach.ui.theme.structuralSurface
import com.cautious5.crisis_coach.ui.theme.warningButton
import com.cautious5.crisis_coach.ui.theme.warningCardBackground
import com.cautious5.crisis_coach.utils.PermissionManager
import com.cautious5.crisis_coach.utils.ResponseParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Image Triage screen for Crisis Coach app
 * Provides image capture/selection and AI-powered analysis for medical and structural assessment
 */

@Composable
fun ImageTriageScreen(
    viewModel: ImageTriageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Permission handling
    val permissionManager = remember { PermissionManager(context as androidx.activity.ComponentActivity) }

    // Camera URI state
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Image selection launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadImageFromUri(it) }
        viewModel.hideImagePicker()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            // Handle camera result - load the captured image
            viewModel.loadImageFromUri(cameraImageUri!!)
        }
        viewModel.hideCameraCapture()
    }

    fun createCameraImageUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "CRISIS_COACH_$timeStamp.jpg"
        val storageDir = File(context.cacheDir, "images")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val imageFile = File(storageDir, imageFileName)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        ImageTriageHeader()

        // Analysis Type Selector
        AnalysisTypeSelector(
            selectedType = uiState.analysisType,
            onTypeSelected = viewModel::setAnalysisType
        )

        // Image Input Section
        ImageInputSection(
            selectedImage = uiState.selectedImage,
            hasImage = uiState.hasImage,
            isAnalyzing = uiState.isAnalyzing,
            onSelectImage = {
                if (permissionManager.hasCameraPermissions() && permissionManager.hasStoragePermissions()) {
                    viewModel.showImagePicker()
                } else {
                    // Request permissions
                    permissionManager.requestAllPermissions()
                }
            },
            onClearImage = viewModel::clearImage
        )

        // Custom Question Input
        if (uiState.hasImage) {
            CustomQuestionSection(
                question = uiState.customQuestion,
                onQuestionChanged = viewModel::updateCustomQuestion,
                analysisType = uiState.analysisType
            )
        }

        // Analysis Button
        if (uiState.hasImage && !uiState.isAnalyzing) {
            AnalyzeButton(
                onClick = viewModel::analyzeImage,
                analysisType = uiState.analysisType
            )
        }

        // Analysis Progress
        if (uiState.isAnalyzing) {
            AnalysisProgressCard(
                analysisType = uiState.analysisType,
                progress = uiState.analysisProgress,
                onCancel = viewModel::cancelAnalysis
            )
        }

        // Analysis Results
        uiState.analysisResult?.let { result ->
            AnalysisResultSection(
                result = result,
                analysisType = uiState.analysisType,
                onGetUrgencyColor = viewModel::getUrgencyColor,
                onGetSafetyColor = viewModel::getSafetyColor,
                onGetLevelDisplayText = viewModel::getLevelDisplayText
            )
        }

        // Error Display
        uiState.error?.let { error ->
            ErrorCard(
                error = error,
                onDismiss = viewModel::clearError
            )
        }

        // Disclaimer
        DisclaimerCard()
    }

    // Image Picker Dialog
    if (uiState.showImagePicker) {
        ImagePickerDialog(
            onCameraSelected = {
                cameraImageUri = createCameraImageUri()
                cameraLauncher.launch(cameraImageUri!!)
                viewModel.hideImagePicker()
            },
            onGallerySelected = {
                galleryLauncher.launch("image/*")
            },
            onDismiss = viewModel::hideImagePicker
        )
    }
}

@Composable
private fun ImageTriageHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.medicalSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Image Analysis",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Image Analysis",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "AI-powered medical and structural assessment",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnalysisTypeSelector(
    selectedType: ImageTriageViewModel.AnalysisTypeOption,
    onTypeSelected: (ImageTriageViewModel.AnalysisTypeOption) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Analysis Type",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImageTriageViewModel.AnalysisTypeOption.entries.forEach { option ->
                    AnalysisTypeChip(
                        option = option,
                        isSelected = option == selectedType,
                        onClick = { onTypeSelected(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisTypeChip(
    option: ImageTriageViewModel.AnalysisTypeOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (option) {
        ImageTriageViewModel.AnalysisTypeOption.MEDICAL ->
            Icons.Default.LocalHospital to MaterialTheme.colorScheme.emergencyMedium
        ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL ->
            Icons.Default.Engineering to MaterialTheme.colorScheme.emergencyHigh
        ImageTriageViewModel.AnalysisTypeOption.GENERAL ->
            Icons.Default.Visibility to MaterialTheme.colorScheme.tertiary
    }

    FilterChip(
        onClick = onClick,
        label = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = option.displayName,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else color
                )
                Text(
                    text = option.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        },
        selected = isSelected,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier.height(72.dp)
    )
}

@Composable
private fun ImageInputSection(
    selectedImage: android.graphics.Bitmap?,
    hasImage: Boolean,
    isAnalyzing: Boolean,
    onSelectImage: () -> Unit,
    onClearImage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Image",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            if (hasImage && selectedImage != null) {
                // Image Display
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = selectedImage.asImageBitmap(),
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Analysis overlay
                    if (isAnalyzing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                        }
                    }

                    // Clear button
                    if (!isAnalyzing) {
                        IconButton(
                            onClick = onClearImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove image",
                                tint = Color.White
                            )
                        }
                    }
                }
            } else {
                // Image Selection Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectImage() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Select image",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap to Capture or Select Image",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Camera • Gallery",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomQuestionSection(
    question: String,
    onQuestionChanged: (String) -> Unit,
    analysisType: ImageTriageViewModel.AnalysisTypeOption
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Specific Question (Optional)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            val placeholder = when (analysisType) {
                ImageTriageViewModel.AnalysisTypeOption.MEDICAL ->
                    "e.g., Is this wound infected? What treatment is needed?"
                ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL ->
                    "e.g., Is this structure safe? What are the main concerns?"
                ImageTriageViewModel.AnalysisTypeOption.GENERAL ->
                    "e.g., What do you see? What should I be concerned about?"
            }

            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ask a specific question") },
                placeholder = { Text(placeholder) },
                maxLines = 3,
                textStyle = EmergencyTextStyles.InputText
            )
        }
    }
}

@Composable
private fun AnalyzeButton(
    onClick: () -> Unit,
    analysisType: ImageTriageViewModel.AnalysisTypeOption
) {
    val (icon, color, text) = when (analysisType) {
        ImageTriageViewModel.AnalysisTypeOption.MEDICAL -> Triple(
            Icons.Default.LocalHospital,
            MaterialTheme.colorScheme.emergencyMedium,
            "Analyze Medical Condition"
        )
        ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL -> Triple(
            Icons.Default.Engineering,
            MaterialTheme.colorScheme.emergencyHigh,
            "Analyze Structural Damage"
        )
        ImageTriageViewModel.AnalysisTypeOption.GENERAL -> Triple(
            Icons.Default.Visibility,
            MaterialTheme.colorScheme.tertiary,
            "Analyze Image"
        )
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = EmergencyTextStyles.ButtonText
        )
    }
}

@Composable
private fun AnalysisProgressCard(
    analysisType: ImageTriageViewModel.AnalysisTypeOption,
    progress: Float,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Analyzing ${analysisType.displayName}...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )

                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Please wait while the AI analyzes your image...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun AnalysisResultSection(
    result: ImageTriageViewModel.AnalysisResult,
    analysisType: ImageTriageViewModel.AnalysisTypeOption,
    onGetUrgencyColor: (ResponseParser.UrgencyLevel) -> Color,
    onGetSafetyColor: (ResponseParser.SafetyStatus) -> Color,
    onGetLevelDisplayText: (Any) -> String
) {
    when (result) {
        is ImageTriageViewModel.AnalysisResult.Medical -> {
            MedicalResultCard(
                result = result,
                onGetUrgencyColor = onGetUrgencyColor,
                onGetLevelDisplayText = onGetLevelDisplayText
            )
        }
        is ImageTriageViewModel.AnalysisResult.Structural -> {
            StructuralResultCard(
                result = result,
                onGetSafetyColor = onGetSafetyColor,
                onGetLevelDisplayText = onGetLevelDisplayText
            )
        }
        is ImageTriageViewModel.AnalysisResult.General -> {
            GeneralResultCard(result = result)
        }
    }
}

@Composable
private fun MedicalResultCard(
    result: ImageTriageViewModel.AnalysisResult.Medical,
    onGetUrgencyColor: (ResponseParser.UrgencyLevel) -> Color,
    onGetLevelDisplayText: (Any) -> String
) {
    ResultCard(
        title = "Medical Analysis",
        icon = Icons.Default.LocalHospital,
        containerColor = MaterialTheme.colorScheme.medicalSurface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Urgency Level Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = onGetUrgencyColor(result.urgencyLevel).copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    onGetUrgencyColor(result.urgencyLevel)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (result.urgencyLevel) {
                            ResponseParser.UrgencyLevel.CRITICAL -> Icons.Default.Warning
                            ResponseParser.UrgencyLevel.HIGH -> Icons.Default.PriorityHigh
                            ResponseParser.UrgencyLevel.MEDIUM -> Icons.Default.Info
                            ResponseParser.UrgencyLevel.LOW -> Icons.Default.CheckCircle
                            ResponseParser.UrgencyLevel.UNKNOWN -> Icons.AutoMirrored.Filled.Help
                        },
                        contentDescription = "Urgency level",
                        modifier = Modifier.size(16.dp),
                        tint = onGetUrgencyColor(result.urgencyLevel)
                    )
                    Text(
                        text = "${onGetLevelDisplayText(result.urgencyLevel)} Urgency",
                        style = EmergencyTextStyles.UrgencyLevel,
                        color = onGetUrgencyColor(result.urgencyLevel),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Assessment
            Text(
                text = result.assessment,
                style = EmergencyTextStyles.AnalysisResult,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Recommendations
            if (result.recommendations.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Recommended Actions:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    result.recommendations.forEach { recommendation ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowRight,
                                contentDescription = "Recommendation",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = recommendation,
                                style = EmergencyTextStyles.RecommendationText,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Professional Care Warning
            if (result.requiresProfessionalCare) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.warningCardBackground
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.warningButton,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Professional medical care required",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Confidence and Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Confidence: ${(result.confidenceLevel * 100).toInt()}%",
                    style = EmergencyTextStyles.ConfidenceText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Analysis: ${result.analysisTimeMs}ms",
                    style = EmergencyTextStyles.TimeText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StructuralResultCard(
    result: ImageTriageViewModel.AnalysisResult.Structural,
    onGetSafetyColor: (ResponseParser.SafetyStatus) -> Color,
    onGetLevelDisplayText: (Any) -> String
) {
    ResultCard(
        title = "Structural Analysis",
        icon = Icons.Default.Engineering,
        containerColor = MaterialTheme.colorScheme.structuralSurface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Safety Status Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = onGetSafetyColor(result.safetyStatus).copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    onGetSafetyColor(result.safetyStatus)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (result.safetyStatus) {
                            ResponseParser.SafetyStatus.CRITICAL -> Icons.Default.Dangerous
                            ResponseParser.SafetyStatus.UNSAFE -> Icons.Default.Warning
                            ResponseParser.SafetyStatus.CAUTION -> Icons.Default.Info
                            ResponseParser.SafetyStatus.SAFE -> Icons.Default.CheckCircle
                            ResponseParser.SafetyStatus.UNKNOWN -> Icons.AutoMirrored.Filled.Help
                        },
                        contentDescription = "Safety status",
                        modifier = Modifier.size(16.dp),
                        tint = onGetSafetyColor(result.safetyStatus)
                    )
                    Text(
                        text = onGetLevelDisplayText(result.safetyStatus),
                        style = EmergencyTextStyles.UrgencyLevel,
                        color = onGetSafetyColor(result.safetyStatus),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Assessment
            Text(
                text = result.assessment,
                style = EmergencyTextStyles.AnalysisResult,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Issues
            if (result.identifiedIssues.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Identified Issues:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    result.identifiedIssues.forEach { issue ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Issue",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = issue,
                                style = EmergencyTextStyles.RecommendationText,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Immediate Actions
            if (result.immediateActions.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Immediate Actions:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    result.immediateActions.forEach { action ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowRight,
                                contentDescription = "Action",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = action,
                                style = EmergencyTextStyles.RecommendationText,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Confidence and Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Confidence: ${(result.confidenceLevel * 100).toInt()}%",
                    style = EmergencyTextStyles.ConfidenceText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Analysis: ${result.analysisTimeMs}ms",
                    style = EmergencyTextStyles.TimeText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GeneralResultCard(
    result: ImageTriageViewModel.AnalysisResult.General
) {
    ResultCard(
        title = "General Analysis",
        icon = Icons.Default.Visibility,
        containerColor = MaterialTheme.colorScheme.generalSurface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Display the main description
            Text(
                text = result.description,
                style = EmergencyTextStyles.AnalysisResult,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Display Key Observations
            if (result.keyObservations.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Key Observations:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    result.keyObservations.forEach { observation ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Observation",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = observation,
                                style = EmergencyTextStyles.RecommendationText,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Display Suggested Actions
            if (result.suggestedActions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Suggested Actions:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    result.suggestedActions.forEach { action ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowRight,
                                contentDescription = "Action",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = action,
                                style = EmergencyTextStyles.RecommendationText,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }


            // Display confidence and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Confidence: ${(result.confidence * 100).toInt()}%",
                    style = EmergencyTextStyles.ConfidenceText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Analysis: ${result.analysisTimeMs}ms",
                    style = EmergencyTextStyles.TimeText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    error: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = error,
                style = EmergencyTextStyles.ErrorText,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.warningCardBackground
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.warningButton,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Important: This AI assessment is not a substitute for professional medical or structural evaluation. Always seek qualified professional assistance when available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ImagePickerDialog(
    onCameraSelected: () -> Unit,
    onGallerySelected: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Image Source",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Camera Option
                Card(
                    onClick = onCameraSelected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Camera",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Take a new photo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Gallery Option
                Card(
                    onClick = onGallerySelected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Gallery",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Choose from existing photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}