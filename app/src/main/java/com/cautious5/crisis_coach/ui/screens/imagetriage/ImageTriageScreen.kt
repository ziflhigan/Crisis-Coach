package com.cautious5.crisis_coach.ui.screens.imagetriage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.ui.components.CardActionButton
import com.cautious5.crisis_coach.ui.components.ErrorCard
import com.cautious5.crisis_coach.ui.components.MarkdownTextWithCodeBlocks
import com.cautious5.crisis_coach.ui.components.ResultCard
import com.cautious5.crisis_coach.ui.components.SectionHeader
import com.cautious5.crisis_coach.ui.dialogs.ImageProcessingDialog
import com.cautious5.crisis_coach.ui.theme.SemanticColors
import com.cautious5.crisis_coach.ui.theme.SurfaceTints
import com.cautious5.crisis_coach.utils.LocalPermissionManager
import com.cautious5.crisis_coach.utils.ResponseParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    ImageTriageContent(
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
        onCancelAnalysis = viewModel::cancelAnalysis,
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
private fun ImageTriageContent(
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
        // Model readiness indicator
        AnimatedVisibility(
            visible = !uiState.isModelReady,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ModelStatusCard(isReady = uiState.isModelReady)
        }

        // Analysis Type Selection
        AnalysisTypeSection(
            selectedType = uiState.analysisType,
            onTypeSelected = onAnalysisTypeSelected,
            enabled = !uiState.isBusy
        )

        // Image Input Section
        ImageInputSection(
            selectedImage = uiState.selectedImage,
            onSelectImage = onSelectImage,
            onClearImage = onClearImage,
            isLoading = uiState.progressMessage.contains("Loading image"),
            enabled = !uiState.isBusy
        )

        // Question Section (only show if image is selected)
        AnimatedVisibility(
            visible = uiState.hasImage && !uiState.isBusy,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            QuestionSection(
                question = uiState.customQuestion,
                onQuestionChanged = onQuestionChanged,
                analysisType = uiState.analysisType,
                onAnalyzeImage = onAnalyzeImage,
                canAnalyze = uiState.canAnalyze
            )
        }

        // Analysis Content Area
        AnalysisContentArea(
            uiState = uiState,
            onCancelAnalysis = onCancelAnalysis,
            onClearError = onClearError
        )

        DisclaimerSection()
    }

    // Image Picker Dialog
    if (uiState.showImagePicker) {
        ImagePickerDialog(
            onCameraSelected = onCameraSelected,
            onGallerySelected = onGallerySelected,
            onDismiss = onDismissImagePicker
        )
    }

    if (uiState.showProcessingDialog) {
        ImageProcessingDialog()
    }
}

@Composable
private fun ModelStatusCard(isReady: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isReady)
                SemanticColors.Success.copy(alpha = 0.1f)
            else
                SemanticColors.Warning.copy(alpha = 0.1f)
        ),
        border = BorderStroke(
            1.dp,
            if (isReady) SemanticColors.Success else SemanticColors.Warning
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isReady) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = SemanticColors.Warning
                )
            } else {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SemanticColors.Success,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = if (isReady) "AI Model Ready" else "Loading AI Model...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QuestionSection(
    question: String,
    onQuestionChanged: (String) -> Unit,
    analysisType: ImageTriageViewModel.AnalysisTypeOption,
    onAnalyzeImage: () -> Unit,
    canAnalyze: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("3. Add Context (Optional)")

        val placeholder = when (analysisType) {
            ImageTriageViewModel.AnalysisTypeOption.MEDICAL -> "e.g., Patient is conscious, complaining of leg pain..."
            ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL -> "e.g., I heard a loud crack from this support beam..."
            ImageTriageViewModel.AnalysisTypeOption.GENERAL -> "e.g., What are the most important safety concerns here?"
        }

        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Describe the situation or ask a specific question") },
            placeholder = { Text(placeholder) },
            maxLines = 4,
            supportingText = {
                Text("Optional: Add context to get more accurate analysis")
            }
        )

        AnalyzeButton(
            onClick = onAnalyzeImage,
            analysisType = analysisType,
            enabled = canAnalyze
        )
    }
}

@Composable
private fun AnalysisContentArea(
    uiState: ImageTriageViewModel.ImageTriageUiState,
    onCancelAnalysis: () -> Unit,
    onClearError: () -> Unit
) {
    when {
        // Error state
        uiState.error != null -> {
            ErrorCard(title = "Analysis Error") {
                Column {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CardActionButton(text = "Dismiss", onClick = onClearError)
                    }
                }
            }
        }

        // Currently analyzing
        uiState.isAnalyzing -> {
            when {
                // Show streaming content if available
                uiState.hasStreamingContent -> {
                    StreamingAnalysisCard(
                        streamingText = uiState.streamingAnalysis,
                        analysisType = uiState.analysisType,
                        progress = uiState.analysisProgress,
                        onCancel = onCancelAnalysis
                    )
                }
                // Show detailed progress if no streaming yet
                else -> {
                    DetailedProgressCard(
                        progress = uiState.analysisProgress,
                        analysisType = uiState.analysisType,
                        progressMessage = uiState.progressMessage,
                        onCancel = onCancelAnalysis
                    )
                }
            }
        }

        // Has final results
        uiState.hasFinalResult -> {
            AnalysisResultSection(
                result = uiState.analysisResult!!,
                confidence = uiState.confidence,
                analysisTime = uiState.analysisTimeMs
            )
        }
    }
}

@Composable
private fun DetailedProgressCard(
    progress: ImageTriageViewModel.AnalysisProgress,
    analysisType: ImageTriageViewModel.AnalysisTypeOption,
    progressMessage: String,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "progress")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress_animation"
    )

    val isDark = isSystemInDarkTheme()
    val (icon, surfaceTint) = when (analysisType) {
        ImageTriageViewModel.AnalysisTypeOption.MEDICAL ->
            Icons.Default.LocalHospital to (if (isDark) SurfaceTints.RedDark else SurfaceTints.Red)
        ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL ->
            Icons.Default.Engineering to (if (isDark) SurfaceTints.OrangeDark else SurfaceTints.Orange)
        ImageTriageViewModel.AnalysisTypeOption.GENERAL ->
            Icons.Default.Visibility to (if (isDark) SurfaceTints.BlueDark else SurfaceTints.Blue)
    }

    ResultCard(
        title = "Analyzing ${analysisType.displayName}",
        icon = icon,
        surfaceTint = surfaceTint.copy(alpha = 0.3f)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Animated progress indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${(progress.value * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Progress message with animation
            AnimatedContent(
                targetState = progressMessage,
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                            (slideOutVertically { height -> -height } + fadeOut())
                },
                label = "progress_message"
            ) { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Progress steps visualization
            ProgressStepsIndicator(currentProgress = progress)

            // Cancel button
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SemanticColors.Error
                ),
                border = BorderStroke(1.dp, SemanticColors.Error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel Analysis")
            }
        }
    }
}

@Composable
private fun ProgressStepsIndicator(
    currentProgress: ImageTriageViewModel.AnalysisProgress
) {
    val steps = listOf(
        ImageTriageViewModel.AnalysisProgress.InitializingAnalysis,
        ImageTriageViewModel.AnalysisProgress.PreprocessingImage,
        ImageTriageViewModel.AnalysisProgress.PreparingPrompt,
        ImageTriageViewModel.AnalysisProgress.ConnectingToModel,
        ImageTriageViewModel.AnalysisProgress.WaitingForResponse,
        ImageTriageViewModel.AnalysisProgress.StreamingResponse
    )

    val currentIndex = steps.indexOf(currentProgress).coerceAtLeast(0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, _ ->
            val isActive = index <= currentIndex
            val isCurrentStep = index == currentIndex

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isCurrentStep) 12.dp else 8.dp)
                    .background(
                        color = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            ) {
                if (isCurrentStep) {
                    // Pulsing animation for current step
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                }
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(24.dp)
                        .background(
                            color = if (index < currentIndex)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun StreamingAnalysisCard(
    streamingText: String,
    analysisType: ImageTriageViewModel.AnalysisTypeOption,
    progress: ImageTriageViewModel.AnalysisProgress,
    onCancel: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val (icon, surfaceTint) = when (analysisType) {
        ImageTriageViewModel.AnalysisTypeOption.MEDICAL ->
            Icons.Default.LocalHospital to (if (isDark) SurfaceTints.RedDark else SurfaceTints.Red)
        ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL ->
            Icons.Default.Engineering to (if (isDark) SurfaceTints.OrangeDark else SurfaceTints.Orange)
        ImageTriageViewModel.AnalysisTypeOption.GENERAL ->
            Icons.Default.Visibility to (if (isDark) SurfaceTints.BlueDark else SurfaceTints.Blue)
    }

    ResultCard(
        title = "AI Analysis",
        icon = icon,
        surfaceTint = surfaceTint
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Streaming indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamingIndicator()
                Text(
                    text = "Analyzing...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Streaming text with smooth animation
            MarkdownTextWithCodeBlocks(
                markdown = streamingText,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
            )

            // Cancel button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SemanticColors.Error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun StreamingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val delay = index * 100
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun AnalysisTypeSection(
    selectedType: ImageTriageViewModel.AnalysisTypeOption,
    onTypeSelected: (ImageTriageViewModel.AnalysisTypeOption) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("1. Choose Analysis Type")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImageTriageViewModel.AnalysisTypeOption.entries.forEach { option ->
                val (icon, color) = when (option) {
                    ImageTriageViewModel.AnalysisTypeOption.MEDICAL -> Icons.Default.LocalHospital to SemanticColors.Error
                    ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL -> Icons.Default.Engineering to SemanticColors.Warning
                    ImageTriageViewModel.AnalysisTypeOption.GENERAL -> Icons.Default.Visibility to SemanticColors.Info
                }

                AnalysisTypeCard(
                    option = option,
                    icon = icon,
                    color = color,
                    isSelected = selectedType == option,
                    onSelected = { onTypeSelected(option) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Show description for selected type
        AnimatedVisibility(visible = true) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = selectedType.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Examples: ${selectedType.examples.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisTypeCard(
    option: ImageTriageViewModel.AnalysisTypeOption,
    icon: ImageVector,
    color: Color,
    isSelected: Boolean,
    onSelected: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelected,
        modifier = modifier.height(120.dp),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, color) else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = option.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ImageInputSection(
    selectedImage: android.graphics.Bitmap?,
    onSelectImage: () -> Unit,
    onClearImage: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("2. Add an Image")

        if (selectedImage != null) {
            Box(contentAlignment = Alignment.TopEnd) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Image(
                        bitmap = selectedImage.asImageBitmap(),
                        contentDescription = "Selected image for analysis",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // Clear button
                IconButton(
                    onClick = onClearImage,
                    enabled = enabled,
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Image", tint = Color.White)
                }

                // Show loading overlay if processing
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Text(
                                text = "Processing image...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        } else {
            ImagePickerCard(
                onSelectImage = onSelectImage,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun ImagePickerCard(
    onSelectImage: () -> Unit,
    enabled: Boolean
) {
    Card(
        onClick = onSelectImage,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tap to Add Image",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Use your camera or select from gallery",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnalyzeButton(
    onClick: () -> Unit,
    analysisType: ImageTriageViewModel.AnalysisTypeOption,
    enabled: Boolean
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = when (analysisType) {
                ImageTriageViewModel.AnalysisTypeOption.MEDICAL -> SemanticColors.Error
                ImageTriageViewModel.AnalysisTypeOption.STRUCTURAL -> SemanticColors.Warning
                ImageTriageViewModel.AnalysisTypeOption.GENERAL -> SemanticColors.Info
            }
        )
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Analyze ${analysisType.displayName}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AnalysisResultSection(
    result: ImageTriageViewModel.AnalysisResult,
    confidence: Float,
    analysisTime: Long
) {
    when (result) {
        is ImageTriageViewModel.AnalysisResult.Medical -> {
            MedicalResultContent(result, confidence, analysisTime)
        }
        is ImageTriageViewModel.AnalysisResult.Structural -> {
            StructuralResultContent(result, confidence, analysisTime)
        }
        is ImageTriageViewModel.AnalysisResult.General -> {
            GeneralResultContent(result, confidence, analysisTime)
        }
    }
}

@Composable
private fun MedicalResultContent(
    result: ImageTriageViewModel.AnalysisResult.Medical,
    confidence: Float,
    analysisTime: Long
) {
    val isDark = isSystemInDarkTheme()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Main result card
        ResultCard(
            title = "Medical Analysis Result",
            icon = Icons.Default.LocalHospital,
            surfaceTint = if (isDark) SurfaceTints.RedDark else SurfaceTints.Red
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Urgency badge
                StatusBadge(
                    level = result.urgencyLevel,
                    text = result.urgencyLevel.name.replace('_', ' '),
                    icon = when (result.urgencyLevel) {
                        ResponseParser.UrgencyLevel.CRITICAL -> Icons.Default.Warning
                        ResponseParser.UrgencyLevel.HIGH -> Icons.Default.PriorityHigh
                        ResponseParser.UrgencyLevel.MEDIUM -> Icons.Default.Info
                        ResponseParser.UrgencyLevel.LOW -> Icons.Default.CheckCircle
                        else -> Icons.AutoMirrored.Filled.Help
                    }
                )

                // Assessment with markdown support
                MarkdownTextWithCodeBlocks(
                    markdown = result.assessment,
                    modifier = Modifier.fillMaxWidth()
                )

                // Key findings if available
                if (result.keyFindings.isNotEmpty()) {
                    ResultList(
                        title = "Key Findings",
                        items = result.keyFindings,
                        icon = Icons.Default.FindInPage,
                        iconTint = SemanticColors.Info
                    )
                }

                // Recommendations
                if (result.recommendations.isNotEmpty()) {
                    ResultList(
                        title = "Recommended Actions",
                        items = result.recommendations,
                        icon = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                        iconTint = SemanticColors.Success
                    )
                }

                // Professional care warning
                if (result.requiresProfessionalCare) {
                    val (careColor, careIcon) = when (result.urgencyLevel) {
                        ResponseParser.UrgencyLevel.CRITICAL -> SemanticColors.Critical to Icons.Default.Warning
                        ResponseParser.UrgencyLevel.HIGH -> SemanticColors.High to Icons.Default.PriorityHigh
                        else -> SemanticColors.Medium to Icons.Default.Info // Fallback
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = careColor.copy(alpha = 0.1f),
                            contentColor = careColor
                        ),
                        border = BorderStroke(1.dp, careColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = careIcon,
                                contentDescription = null,
                                tint = careColor
                            )
                            Column {
                                Text(
                                    text = "Professional Care Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "This assessment indicates immediate professional medical attention is necessary.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }


                // Metadata
                ResultMetadata(
                    confidence = confidence,
                    timeMs = analysisTime,
                    additionalInfo = "Medical Analysis"
                )
            }
        }
    }
}

@Composable
private fun StructuralResultContent(
    result: ImageTriageViewModel.AnalysisResult.Structural,
    confidence: Float,
    analysisTime: Long
) {
    val isDark = isSystemInDarkTheme()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ResultCard(
            title = "Structural Analysis Result",
            icon = Icons.Default.Engineering,
            surfaceTint = if (isDark) SurfaceTints.OrangeDark else SurfaceTints.Orange
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Safety status badge
                StatusBadge(
                    level = result.safetyStatus,
                    text = result.safetyStatus.name.replace('_', ' '),
                    icon = when (result.safetyStatus) {
                        ResponseParser.SafetyStatus.CRITICAL -> Icons.Default.Dangerous
                        ResponseParser.SafetyStatus.UNSAFE -> Icons.Default.Warning
                        ResponseParser.SafetyStatus.CAUTION -> Icons.Default.Info
                        ResponseParser.SafetyStatus.SAFE -> Icons.Default.CheckCircle
                        else -> Icons.AutoMirrored.Filled.Help
                    }
                )

                // Structure info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoChip(
                        label = "Type",
                        value = result.structureType,
                        modifier = Modifier.weight(1f)
                    )
                    InfoChip(
                        label = "Damage",
                        value = result.damageLevel,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Assessment with markdown
                MarkdownTextWithCodeBlocks(
                    markdown = result.assessment,
                    modifier = Modifier.fillMaxWidth()
                )

                // Identified issues
                if (result.identifiedIssues.isNotEmpty()) {
                    ResultList(
                        title = "Identified Issues",
                        items = result.identifiedIssues,
                        icon = Icons.Default.Report,
                        iconTint = SemanticColors.Error
                    )
                }

                // Immediate actions
                if (result.immediateActions.isNotEmpty()) {
                    ResultList(
                        title = "Immediate Actions",
                        items = result.immediateActions,
                        icon = Icons.Default.Emergency,
                        iconTint = SemanticColors.Warning
                    )
                }

                // Metadata
                ResultMetadata(
                    confidence = confidence,
                    timeMs = analysisTime,
                    additionalInfo = "Structural Analysis"
                )
            }
        }
    }
}

@Composable
private fun GeneralResultContent(
    result: ImageTriageViewModel.AnalysisResult.General,
    confidence: Float,
    analysisTime: Long
) {
    val isDark = isSystemInDarkTheme()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ResultCard(
            title = "General Analysis Result",
            icon = Icons.Default.Visibility,
            surfaceTint = if (isDark) SurfaceTints.BlueDark else SurfaceTints.Blue
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Risk level info
                if (result.riskLevel != "Unknown") {
                    InfoChip(
                        label = "Risk Level",
                        value = result.riskLevel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Description with markdown
                MarkdownTextWithCodeBlocks(
                    markdown = result.description,
                    modifier = Modifier.fillMaxWidth()
                )

                // Key observations
                if (result.keyObservations.isNotEmpty()) {
                    ResultList(
                        title = "Key Observations",
                        items = result.keyObservations,
                        icon = Icons.Default.FindInPage,
                        iconTint = SemanticColors.Info
                    )
                }

                // Suggested actions
                if (result.suggestedActions.isNotEmpty()) {
                    ResultList(
                        title = "Suggested Actions",
                        items = result.suggestedActions,
                        icon = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                        iconTint = SemanticColors.Success
                    )
                }

                // Safety recommendations
                if (result.safetyRecommendations.isNotEmpty()) {
                    ResultList(
                        title = "Safety Recommendations",
                        items = result.safetyRecommendations,
                        icon = Icons.Default.Security,
                        iconTint = SemanticColors.Warning
                    )
                }

                // Metadata
                ResultMetadata(
                    confidence = confidence,
                    timeMs = analysisTime,
                    additionalInfo = "General Analysis"
                )
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> StatusBadge(
    level: T,
    text: String,
    icon: ImageVector
) {
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
        border = BorderStroke(1.5.dp, color),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ResultList(
    title: String,
    items: List<String>,
    icon: ImageVector,
    iconTint: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(iconTint, CircleShape)
                            .offset(y = 8.dp)
                    )
                    MarkdownTextWithCodeBlocks(
                        markdown = item,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ResultMetadata(
    confidence: Float,
    timeMs: Long,
    additionalInfo: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Confidence: ${(confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = additionalInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Text(
                text = "Analysis: ${timeMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "This AI assessment is for informational purposes only and is NOT a substitute for professional evaluation.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Always prioritize safety and seek help from qualified experts for medical or structural concerns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        icon = {
            Icon(
                Icons.Default.AddAPhoto,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Choose Image Source",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ImageSourceOption(
                    icon = Icons.Default.CameraAlt,
                    title = "Take Photo",
                    subtitle = "Use your camera to capture an image",
                    onClick = onCameraSelected
                )
                ImageSourceOption(
                    icon = Icons.Default.PhotoLibrary,
                    title = "From Gallery",
                    subtitle = "Select an existing image from your device",
                    onClick = onGallerySelected
                )
            }
        },
        confirmButton = {
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}