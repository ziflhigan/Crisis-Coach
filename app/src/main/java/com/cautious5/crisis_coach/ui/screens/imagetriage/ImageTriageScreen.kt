package com.cautious5.crisis_coach.ui.screens.imagetriage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.ui.components.*
import com.cautious5.crisis_coach.ui.theme.SemanticColors
import com.cautious5.crisis_coach.ui.theme.SurfaceTints
import com.cautious5.crisis_coach.utils.LocalPermissionManager
import com.cautious5.crisis_coach.utils.ResponseParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modernized Image Triage screen.
 * Guides user through selecting, analyzing, and reviewing image-based assessments.
 */
@Composable
fun ImageTriageScreen(
    viewModel: ImageTriageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionManager = LocalPermissionManager.current
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.loadImageFromUri(it) }
        viewModel.hideImagePicker()
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { viewModel.loadImageFromUri(it) }
        }
        viewModel.hideImagePicker()
    }

    fun createCameraImageUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFile = File(context.cacheDir, "JPEG_${timeStamp}_.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }

    ImageTriageScreenContent(
        uiState = uiState,
        onAnalysisTypeSelected = viewModel::setAnalysisType,
        onSelectImage = {
            if (!permissionManager.hasAllRequiredPermissions()) {
                permissionManager.requestAllPermissions()
            } else {
                viewModel.showImagePicker()
            }
        },
        onClearImage = viewModel::clearImage,
        onQuestionChanged = viewModel::updateCustomQuestion,
        onAnalyzeImage = viewModel::analyzeImage,
        onClearError = viewModel::clearError,
        onCameraSelected = {
            cameraImageUri = createCameraImageUri()
            cameraLauncher.launch(cameraImageUri!!)
        },
        onGallerySelected = {
            galleryLauncher.launch("image/*")
        },
        onDismissImagePicker = viewModel::hideImagePicker
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
    onClearError: () -> Unit,
    onCameraSelected: () -> Unit,
    onGallerySelected: () -> Unit,
    onDismissImagePicker: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Step 1: Analysis Type
        AnalysisTypeSection(
            selectedType = uiState.analysisType,
            onTypeSelected = onAnalysisTypeSelected
        )

        // Step 2: Image Input
        ImageInputSection(
            selectedImage = uiState.selectedImage,
            onSelectImage = onSelectImage,
            onClearImage = onClearImage
        )

        // Step 3: Optional Question & Analyze Button
        AnimatedVisibility(visible = uiState.hasImage && !uiState.isAnalyzing) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CustomQuestionSection(
                    question = uiState.customQuestion,
                    onQuestionChanged = onQuestionChanged,
                    analysisType = uiState.analysisType
                )
                AnalyzeButton(
                    onClick = onAnalyzeImage,
                    analysisType = uiState.analysisType
                )
            }
        }

        // Analysis Progress / Results
        if (uiState.isAnalyzing) {
            LoadingIndicator("Analyzing image...")
        } else if (uiState.error != null) {
            ErrorCard(title = "Analysis Error") {
                Column {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        CardActionButton(text = "Dismiss", onClick = onClearError)
                    }
                }
            }
        } else if (uiState.analysisResult != null) {
            AnalysisResultSection(result = uiState.analysisResult)
        }

        // Disclaimer
        DisclaimerSection()
    }

    if (uiState.showImagePicker) {
        ImagePickerDialog(
            onCameraSelected = onCameraSelected,
            onGallerySelected = onGallerySelected,
            onDismiss = onDismissImagePicker
        )
    }
}

@Composable
private fun AnalysisTypeSection(
    selectedType: ImageTriageViewModel.AnalysisTypeOption,
    onTypeSelected: (ImageTriageViewModel.AnalysisTypeOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("1. Select Analysis Type")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ImageTriageViewModel.AnalysisTypeOption.entries.forEachIndexed { index, option ->
                val (icon, color) = when (option) {
                    ImageTriageViewModel.AnalysisTypeOption.MEDICAL -> Icons.Default.LocalHospital to SemanticColors.Error
                    ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL -> Icons.Default.Engineering to SemanticColors.Warning
                    ImageTriageViewModel.AnalysisTypeOption.GENERAL -> Icons.Default.Visibility to SemanticColors.Info
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ImageTriageViewModel.AnalysisTypeOption.entries.size),
                    onClick = { onTypeSelected(option) },
                    selected = selectedType == option,
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = color,
                        activeContentColor = Color.White,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(option.displayName)
                }
            }
        }
    }
}

@Composable
private fun ImageInputSection(
    selectedImage: android.graphics.Bitmap?,
    onSelectImage: () -> Unit,
    onClearImage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("2. Provide an Image")
        if (selectedImage != null) {
            Box(contentAlignment = Alignment.TopEnd) {
                Image(
                    bitmap = selectedImage.asImageBitmap(),
                    contentDescription = "Selected image for analysis",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onClearImage,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Image", tint = Color.White)
                }
            }
        } else {
            Surface(
                onClick = onSelectImage,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                EmptyState(
                    icon = Icons.Default.AddAPhoto,
                    title = "Add an Image",
                    subtitle = "Tap to use your camera or select a photo from your gallery.",
                    modifier = Modifier.padding(vertical = 24.dp)
                )
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("3. Add Details (Optional)")
        val placeholder = when (analysisType) {
            ImageTriageViewModel.AnalysisTypeOption.MEDICAL -> "e.g., Patient is conscious, complaining of leg pain."
            ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL -> "e.g., I heard a loud crack from this support beam."
            ImageTriageViewModel.AnalysisTypeOption.GENERAL -> "e.g., What are the most important things to note here?"
        }
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Describe the situation or ask a question") },
            placeholder = { Text(placeholder) },
            maxLines = 4
        )
    }
}

@Composable
private fun AnalyzeButton(
    onClick: () -> Unit,
    analysisType: ImageTriageViewModel.AnalysisTypeOption
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Analyze ${analysisType.displayName}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AnalysisResultSection(result: ImageTriageViewModel.AnalysisResult) {
    val isDark = isSystemInDarkTheme()
    val (icon, title, surfaceTint) = when (result) {
        is ImageTriageViewModel.AnalysisResult.Medical -> Triple(
            Icons.Default.LocalHospital,
            "Medical Analysis Result",
            if (isDark) SurfaceTints.RedDark else SurfaceTints.Red
        )
        is ImageTriageViewModel.AnalysisResult.Structural -> Triple(
            Icons.Default.Engineering,
            "Structural Analysis Result",
            if (isDark) SurfaceTints.OrangeDark else SurfaceTints.Orange
        )
        is ImageTriageViewModel.AnalysisResult.General -> Triple(
            Icons.Default.Visibility,
            "General Analysis Result",
            if (isDark) SurfaceTints.BlueDark else SurfaceTints.Blue
        )
    }

    ResultCard(title = title, icon = icon, surfaceTint = surfaceTint) {
        when (result) {
            is ImageTriageViewModel.AnalysisResult.Medical -> MedicalResultContent(result)
            is ImageTriageViewModel.AnalysisResult.Structural -> StructuralResultContent(result)
            is ImageTriageViewModel.AnalysisResult.General -> GeneralResultContent(result)
        }
    }
}

@Composable
private fun MedicalResultContent(result: ImageTriageViewModel.AnalysisResult.Medical) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusBadge(
            level = result.urgencyLevel,
            text = result.urgencyLevel.name.replace('_', ' '),
            icon = when (result.urgencyLevel) {
                ResponseParser.UrgencyLevel.CRITICAL -> Icons.Default.Warning
                else -> Icons.Default.Info
            }
        )
        Text(result.assessment, style = MaterialTheme.typography.bodyLarge)
        if (result.recommendations.isNotEmpty()) {
            ResultList(title = "Recommended Actions", items = result.recommendations)
        }
        if (result.requiresProfessionalCare) {
            ResultCard(title = "Professional Care Required", icon = Icons.Default.PriorityHigh, iconTint = SemanticColors.Error) {
                Text("This assessment indicates that immediate professional medical attention is necessary.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        ResultMetadata(confidence = result.confidenceLevel, timeMs = result.analysisTimeMs)
    }
}

@Composable
private fun StructuralResultContent(result: ImageTriageViewModel.AnalysisResult.Structural) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusBadge(
            level = result.safetyStatus,
            text = result.safetyStatus.name.replace('_', ' '),
            icon = when (result.safetyStatus) {
                ResponseParser.SafetyStatus.CRITICAL, ResponseParser.SafetyStatus.UNSAFE -> Icons.Default.Dangerous
                else -> Icons.Default.CheckCircle
            }
        )
        Text(result.assessment, style = MaterialTheme.typography.bodyLarge)
        if (result.identifiedIssues.isNotEmpty()) {
            ResultList("Identified Issues", result.identifiedIssues, Icons.Default.Report, SemanticColors.Error)
        }
        if (result.immediateActions.isNotEmpty()) {
            ResultList("Immediate Actions", result.immediateActions)
        }
        ResultMetadata(confidence = result.confidenceLevel, timeMs = result.analysisTimeMs)
    }
}

@Composable
private fun GeneralResultContent(result: ImageTriageViewModel.AnalysisResult.General) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(result.description, style = MaterialTheme.typography.bodyLarge)
        if (result.keyObservations.isNotEmpty()) {
            ResultList("Key Observations", result.keyObservations, Icons.Default.FindInPage, SemanticColors.Info)
        }
        if (result.suggestedActions.isNotEmpty()) {
            ResultList("Suggested Actions", result.suggestedActions)
        }
        ResultMetadata(confidence = result.confidence, timeMs = result.analysisTimeMs)
    }
}

@Composable
private fun <T : Enum<T>> StatusBadge(level: T, text: String, icon: ImageVector) {
    val color = when (level) {
        is ResponseParser.UrgencyLevel -> when(level) {
            ResponseParser.UrgencyLevel.CRITICAL -> SemanticColors.Critical
            ResponseParser.UrgencyLevel.HIGH -> SemanticColors.High
            ResponseParser.UrgencyLevel.MEDIUM -> SemanticColors.Medium
            ResponseParser.UrgencyLevel.LOW -> SemanticColors.Success
            ResponseParser.UrgencyLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        is ResponseParser.SafetyStatus -> when(level) {
            ResponseParser.SafetyStatus.CRITICAL -> SemanticColors.Critical
            ResponseParser.SafetyStatus.UNSAFE -> SemanticColors.High
            ResponseParser.SafetyStatus.CAUTION -> SemanticColors.Medium
            ResponseParser.SafetyStatus.SAFE -> SemanticColors.Success
            ResponseParser.SafetyStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultList(title: String, items: List<String>, icon: ImageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck, iconTint: Color = MaterialTheme.colorScheme.primary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ResultMetadata(confidence: Float, timeMs: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Confidence: ${(confidence * 100).toInt()}% • Time: ${timeMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DisclaimerSection() {
    ResultCard(
        title = "Important Disclaimer",
        icon = Icons.Default.Warning,
        iconTint = SemanticColors.Warning,
        surfaceTint = if (isSystemInDarkTheme()) SurfaceTints.OrangeDark else SurfaceTints.Orange
    ) {
        Text(
            text = "This AI assessment is for informational purposes only and is NOT a substitute for professional medical or structural evaluation. Always prioritize safety and seek help from qualified experts.",
            style = MaterialTheme.typography.bodySmall
        )
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
        title = { Text("Choose Image Source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ImageSourceOption(icon = Icons.Default.CameraAlt, text = "Use Camera", onClick = onCameraSelected)
                ImageSourceOption(icon = Icons.Default.PhotoLibrary, text = "From Gallery", onClick = onGallerySelected)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ImageSourceOption(icon: ImageVector, text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
}