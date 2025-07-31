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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.ui.components.ResultCard
import com.cautious5.crisis_coach.ui.theme.CrisisCoachTheme
import com.cautious5.crisis_coach.ui.theme.EmergencyColors
import com.cautious5.crisis_coach.ui.theme.EmergencyTextStyles
import com.cautious5.crisis_coach.ui.theme.emergencyHigh
import com.cautious5.crisis_coach.ui.theme.emergencyMedium
import com.cautious5.crisis_coach.ui.theme.generalSurface
import com.cautious5.crisis_coach.ui.theme.medicalSurface
import com.cautious5.crisis_coach.ui.theme.structuralSurface
import com.cautious5.crisis_coach.ui.theme.warningButton
import com.cautious5.crisis_coach.ui.theme.warningCardBackground
import com.cautious5.crisis_coach.utils.LocalPermissionManager
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

    // Permission handling
    val permissionManager = LocalPermissionManager.current

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

    ImageTriageScreenContent(
        uiState = uiState,
        onAnalysisTypeSelected = viewModel::setAnalysisType,
        onSelectImage = {
            if (permissionManager.hasCameraPermissions() && permissionManager.hasStoragePermissions()) {
                viewModel.showImagePicker()
            } else {
                permissionManager.requestAllPermissions()
            }
        },
        onClearImage = viewModel::clearImage,
        onQuestionChanged = viewModel::updateCustomQuestion,
        onAnalyzeImage = viewModel::analyzeImage,
        onCancelAnalysis = viewModel::cancelAnalysis,
        onClearError = viewModel::clearError,
        onCameraSelected = {
            cameraImageUri = createCameraImageUri()
            cameraLauncher.launch(cameraImageUri!!)
            viewModel.hideImagePicker()
        },
        onGallerySelected = {
            galleryLauncher.launch("image/*")
        },
        onDismissImagePicker = viewModel::hideImagePicker,
        onGetUrgencyColor = viewModel::getUrgencyColor,
        onGetSafetyColor = viewModel::getSafetyColor,
        onGetLevelDisplayText = viewModel::getLevelDisplayText
    )
}

@Composable
private fun ImageTriageScreenContent(
    uiState: ImageTriageViewModel.ImageTriageUiState,
    onAnalysisTypeSelected: (ImageTriageViewModel.AnalysisTypeOption) -> Unit,
    onSelectImage: () -> Unit,
    onClearImage: () -> Unit,
    onQuestionChanged: (String) -> Unit,
    onAnalyzeImage: () -> Unit,
    onCancelAnalysis: () -> Unit,
    onClearError: () -> Unit,
    onCameraSelected: () -> Unit,
    onGallerySelected: () -> Unit,
    onDismissImagePicker: () -> Unit,
    onGetUrgencyColor: (ResponseParser.UrgencyLevel) -> Color,
    onGetSafetyColor: (ResponseParser.SafetyStatus) -> Color,
    onGetLevelDisplayText: (Any) -> String
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        ImageTriageHeader()

        // Analysis Type Selector
        AnalysisTypeSelector(
            selectedType = uiState.analysisType,
            onTypeSelected = onAnalysisTypeSelected
        )

        // Image Input Section
        ImageInputSection(
            selectedImage = uiState.selectedImage,
            hasImage = uiState.hasImage,
            isAnalyzing = uiState.isAnalyzing,
            onSelectImage = onSelectImage,
            onClearImage = onClearImage
        )

        // Custom Question Input - Only show if image is selected
        if (uiState.hasImage) {
            CustomQuestionSection(
                question = uiState.customQuestion,
                onQuestionChanged = onQuestionChanged,
                analysisType = uiState.analysisType
            )

            // Analysis Button
            if (!uiState.isAnalyzing) {
                AnalyzeButton(
                    onClick = onAnalyzeImage,
                    analysisType = uiState.analysisType
                )
            }
        }

        // Analysis Progress
        if (uiState.isAnalyzing) {
            AnalysisProgressCard(
                analysisType = uiState.analysisType,
                progress = uiState.analysisProgress,
                onCancel = onCancelAnalysis
            )
        }

        // Analysis Results
        uiState.analysisResult?.let { result ->
            AnalysisResultSection(
                result = result,
                analysisType = uiState.analysisType,
                onGetUrgencyColor = onGetUrgencyColor,
                onGetSafetyColor = onGetSafetyColor,
                onGetLevelDisplayText = onGetLevelDisplayText
            )
        }

        // Error Display
        uiState.error?.let { error ->
            ErrorCard(
                error = error,
                onDismiss = onClearError
            )
        }

        // Disclaimer
        DisclaimerCard()
    }

    // Image Picker Dialog
    if (uiState.showImagePicker) {
        ImagePickerDialog(
            onCameraSelected = onCameraSelected,
            onGallerySelected = onGallerySelected,
            onDismiss = onDismissImagePicker
        )
    }
}

@Composable
private fun ImageTriageHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.medicalSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Image Analysis",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

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
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Image",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            if (hasImage && selectedImage != null) {
                ImageDisplay(
                    image = selectedImage,
                    isAnalyzing = isAnalyzing,
                    onClearImage = onClearImage
                )
            } else {
                ImageSelectionArea(onSelectImage = onSelectImage)
            }
        }
    }
}

@Composable
private fun ImageDisplay(
    image: android.graphics.Bitmap,
    isAnalyzing: Boolean,
    onClearImage: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            bitmap = image.asImageBitmap(),
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "Analyzing...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
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
}

@Composable
private fun ImageSelectionArea(onSelectImage: () -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Camera • Gallery",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
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
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
            style = EmergencyTextStyles.ButtonText,
            fontWeight = FontWeight.SemiBold
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
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            UrgencyLevelBadge(
                urgencyLevel = result.urgencyLevel,
                onGetUrgencyColor = onGetUrgencyColor,
                onGetLevelDisplayText = onGetLevelDisplayText
            )

            // Assessment
            Text(
                text = result.assessment,
                style = EmergencyTextStyles.AnalysisResult,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Recommendations
            if (result.recommendations.isNotEmpty()) {
                RecommendationsList(
                    title = "Recommended Actions:",
                    items = result.recommendations,
                    icon = Icons.AutoMirrored.Filled.ArrowRight
                )
            }

            // Professional Care Warning
            if (result.requiresProfessionalCare) {
                ProfessionalCareWarning()
            }

            // Metadata
            ResultMetadata(
                confidenceLevel = result.confidenceLevel,
                analysisTimeMs = result.analysisTimeMs
            )
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
            SafetyStatusBadge(
                safetyStatus = result.safetyStatus,
                onGetSafetyColor = onGetSafetyColor,
                onGetLevelDisplayText = onGetLevelDisplayText
            )

            // Assessment
            Text(
                text = result.assessment,
                style = EmergencyTextStyles.AnalysisResult,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Issues
            if (result.identifiedIssues.isNotEmpty()) {
                RecommendationsList(
                    title = "Identified Issues:",
                    items = result.identifiedIssues,
                    icon = Icons.Default.Error,
                    iconTint = MaterialTheme.colorScheme.error
                )
            }

            // Immediate Actions
            if (result.immediateActions.isNotEmpty()) {
                RecommendationsList(
                    title = "Immediate Actions:",
                    items = result.immediateActions,
                    icon = Icons.AutoMirrored.Filled.ArrowRight
                )
            }

            // Metadata
            ResultMetadata(
                confidenceLevel = result.confidenceLevel,
                analysisTimeMs = result.analysisTimeMs
            )
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
            // Description
            Text(
                text = result.description,
                style = EmergencyTextStyles.AnalysisResult,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Key Observations
            if (result.keyObservations.isNotEmpty()) {
                RecommendationsList(
                    title = "Key Observations:",
                    items = result.keyObservations,
                    icon = Icons.Default.Info,
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }

            // Suggested Actions
            if (result.suggestedActions.isNotEmpty()) {
                RecommendationsList(
                    title = "Suggested Actions:",
                    items = result.suggestedActions,
                    icon = Icons.AutoMirrored.Filled.ArrowRight
                )
            }

            // Metadata
            ResultMetadata(
                confidenceLevel = result.confidence,
                analysisTimeMs = result.analysisTimeMs
            )
        }
    }
}

@Composable
private fun UrgencyLevelBadge(
    urgencyLevel: ResponseParser.UrgencyLevel,
    onGetUrgencyColor: (ResponseParser.UrgencyLevel) -> Color,
    onGetLevelDisplayText: (Any) -> String
) {
    val color = onGetUrgencyColor(urgencyLevel)
    val icon = when (urgencyLevel) {
        ResponseParser.UrgencyLevel.CRITICAL -> Icons.Default.Warning
        ResponseParser.UrgencyLevel.HIGH -> Icons.Default.PriorityHigh
        ResponseParser.UrgencyLevel.MEDIUM -> Icons.Default.Info
        ResponseParser.UrgencyLevel.LOW -> Icons.Default.CheckCircle
        ResponseParser.UrgencyLevel.UNKNOWN -> Icons.AutoMirrored.Filled.Help
    }

    StatusBadge(
        label = "${onGetLevelDisplayText(urgencyLevel)} Urgency",
        icon = icon,
        color = color
    )
}

@Composable
private fun SafetyStatusBadge(
    safetyStatus: ResponseParser.SafetyStatus,
    onGetSafetyColor: (ResponseParser.SafetyStatus) -> Color,
    onGetLevelDisplayText: (Any) -> String
) {
    val color = onGetSafetyColor(safetyStatus)
    val icon = when (safetyStatus) {
        ResponseParser.SafetyStatus.CRITICAL -> Icons.Default.Dangerous
        ResponseParser.SafetyStatus.UNSAFE -> Icons.Default.Warning
        ResponseParser.SafetyStatus.CAUTION -> Icons.Default.Info
        ResponseParser.SafetyStatus.SAFE -> Icons.Default.CheckCircle
        ResponseParser.SafetyStatus.UNKNOWN -> Icons.AutoMirrored.Filled.Help
    }

    StatusBadge(
        label = onGetLevelDisplayText(safetyStatus),
        icon = icon,
        color = color
    )
}

@Composable
private fun StatusBadge(
    label: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Text(
                text = label,
                style = EmergencyTextStyles.UrgencyLevel,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RecommendationsList(
    title: String,
    items: List<String>,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = iconTint
                )
                Text(
                    text = item,
                    style = EmergencyTextStyles.RecommendationText,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProfessionalCareWarning() {
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

@Composable
private fun ResultMetadata(
    confidenceLevel: Float,
    analysisTimeMs: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Confidence: ${(confidenceLevel * 100).toInt()}%",
            style = EmergencyTextStyles.ConfidenceText,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Analysis: ${analysisTimeMs}ms",
            style = EmergencyTextStyles.TimeText,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                text = "Important: This AI assessment is not a substitute for professional medical " +
                        "or structural evaluation. " +
                        "Always seek qualified professional assistance when available.",
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
                ImageSourceOption(
                    icon = Icons.Default.CameraAlt,
                    title = "Camera",
                    subtitle = "Take a new photo",
                    onClick = onCameraSelected
                )

                // Gallery Option
                ImageSourceOption(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Gallery",
                    subtitle = "Choose from existing photos",
                    onClick = onGallerySelected
                )
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

@Composable
private fun ImageSourceOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Preview Parameter Provider
class ImageTriageUiStateProvider : PreviewParameterProvider<ImageTriageViewModel.ImageTriageUiState> {
    override val values = sequenceOf(
        // Initial state
        ImageTriageViewModel.ImageTriageUiState(),
        // With image selected
        ImageTriageViewModel.ImageTriageUiState(
            hasImage = true,
            customQuestion = "Is this wound infected?"
        ),
        // Analyzing state
        ImageTriageViewModel.ImageTriageUiState(
            hasImage = true,
            isAnalyzing = true,
            analysisProgress = 0.6f
        ),
        // Error state
        ImageTriageViewModel.ImageTriageUiState(
            hasImage = true,
            error = "Analysis failed. Please try again with a clearer image."
        )
    )
}

// Preview Functions
@Preview(
    name = "Image Triage Screen",
    apiLevel = 34,
    showBackground = true
)
@Composable
private fun ImageTriageScreenPreview(
    @PreviewParameter(ImageTriageUiStateProvider::class) uiState: ImageTriageViewModel.ImageTriageUiState
) {
    CrisisCoachTheme {
        Surface {
            ImageTriageScreenContent(
                uiState = uiState,
                onAnalysisTypeSelected = {},
                onSelectImage = {},
                onClearImage = {},
                onQuestionChanged = {},
                onAnalyzeImage = {},
                onCancelAnalysis = {},
                onClearError = {},
                onCameraSelected = {},
                onGallerySelected = {},
                onDismissImagePicker = {},
                onGetUrgencyColor = { Color.Red },
                onGetSafetyColor = { EmergencyColors.High },
                onGetLevelDisplayText = { "High" }
            )
        }
    }
}

@Preview(
    name = "Image Triage Screen - Dark",
    apiLevel = 34,
    showBackground = true
)
@Composable
private fun ImageTriageScreenDarkPreview() {
    CrisisCoachTheme(darkTheme = true) {
        Surface {
            ImageTriageScreenContent(
                uiState = ImageTriageViewModel.ImageTriageUiState(
                    analysisType = ImageTriageViewModel.AnalysisTypeOption.MEDICAL,
                    hasImage = true,
                    customQuestion = "What treatment is needed for this injury?"
                ),
                onAnalysisTypeSelected = {},
                onSelectImage = {},
                onClearImage = {},
                onQuestionChanged = {},
                onAnalyzeImage = {},
                onCancelAnalysis = {},
                onClearError = {},
                onCameraSelected = {},
                onGallerySelected = {},
                onDismissImagePicker = {},
                onGetUrgencyColor = { Color.Red },
                onGetSafetyColor = { EmergencyColors.High },
                onGetLevelDisplayText = { "Critical" }
            )
        }
    }
}