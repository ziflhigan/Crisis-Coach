package com.cautious5.crisis_coach.ui.screens.knowledge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.model.database.SearchEntry
import com.cautious5.crisis_coach.ui.components.CardActionButton
import com.cautious5.crisis_coach.ui.components.CompactVoiceButton
import com.cautious5.crisis_coach.ui.components.EmptyState
import com.cautious5.crisis_coach.ui.components.ErrorCard
import com.cautious5.crisis_coach.ui.components.LoadingIndicator
import com.cautious5.crisis_coach.ui.components.MarkdownTextWithCodeBlocks
import com.cautious5.crisis_coach.ui.components.ResultCard
import com.cautious5.crisis_coach.ui.components.ResultMetadata
import com.cautious5.crisis_coach.ui.components.SectionHeader
import com.cautious5.crisis_coach.ui.theme.SemanticColors
import com.cautious5.crisis_coach.ui.theme.SurfaceTints
import com.cautious5.crisis_coach.utils.LocalPermissionManager
import kotlinx.coroutines.delay

/**
 * Modernized Knowledge screen for Crisis Coach.
 * Features a streamlined UI for searching the emergency knowledge base with RAG support.
 */
@Composable
fun KnowledgeScreen(
    viewModel: KnowledgeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionManager = LocalPermissionManager.current

    KnowledgeScreenContent(
        uiState = uiState,
        onQueryChanged = viewModel::updateQuery,
        onStartVoiceInput = {
            if (permissionManager.hasMicrophonePermissions()) {
                viewModel.startVoiceInput()
            } else {
                permissionManager.requestMicrophonePermissions()
            }
        },
        onStopVoiceInput = viewModel::stopVoiceInput,
        onClearQuery = viewModel::clearQuery,
        onCategorySelected = viewModel::selectCategory,
        availableCategories = viewModel.getAvailableCategories(),
        onQuerySelected = viewModel::useSuggestedQuery,
        onClearError = viewModel::clearError,
        onTriggerSearch = viewModel::triggerSearch,
        onCancelSearch = viewModel::cancelSearchAndReset
    )
}

@Composable
private fun KnowledgeScreenContent(
    uiState: KnowledgeViewModel.KnowledgeUiState,
    onQueryChanged: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onClearQuery: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    availableCategories: List<Pair<String?, String>>,
    onQuerySelected: (String) -> Unit,
    onClearError: () -> Unit,
    onTriggerSearch: (String) -> Unit,
    onCancelSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Search Input Section
        SearchInputSection(
            uiState = uiState,
            onQueryChanged = onQueryChanged,
            onStartVoiceInput = onStartVoiceInput,
            onStopVoiceInput = onStopVoiceInput,
            onClearQuery = onClearQuery,
            onTriggerSearch = onTriggerSearch,
            onCancelSearch = onCancelSearch
        )

        // Category Filters
        CategoryFilterSection(
            selectedCategory = uiState.selectedCategory,
            availableCategories = availableCategories,
            onCategorySelected = onCategorySelected
        )

        // Content area with explicit state handling
        KnowledgeContentArea(
            uiState = uiState,
            onQuerySelected = onQuerySelected,
            onClearError = onClearError
        )
    }
}

@Composable
private fun KnowledgeContentArea(
    uiState: KnowledgeViewModel.KnowledgeUiState,
    onQuerySelected: (String) -> Unit,
    onClearError: () -> Unit
) {
    when {
        // Error state - highest priority
        uiState.error != null -> {
            ErrorCard(title = "Search Error") {
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
        }

        // Currently generating with streaming content
        uiState.isGeneratingAnswer && uiState.streamingAnswer.isNotEmpty() -> {
            RAGAnswerSection(
                answer = uiState.ragAnswer,
                streamingAnswer = uiState.streamingAnswer,
                isGenerating = true, // Explicitly true
                sourcesUsed = uiState.sourcesUsed,
                confidence = uiState.confidenceScore,
                timeMs = uiState.totalSearchTime
            )

            // Show search results below if available
            if (uiState.searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SearchResultsSection(
                    results = uiState.searchResults,
                    onResultClick = { onQuerySelected(it.info.title) }
                )
            }
        }

        // Has final answer (not generating)
        !uiState.isGeneratingAnswer && uiState.ragAnswer.isNotEmpty() -> {
            RAGAnswerSection(
                answer = uiState.ragAnswer,
                streamingAnswer = uiState.streamingAnswer,
                isGenerating = false, // Explicitly false
                sourcesUsed = uiState.sourcesUsed,
                confidence = uiState.confidenceScore,
                timeMs = uiState.totalSearchTime
            )

            // Show search results below if available
            if (uiState.searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                SearchResultsSection(
                    results = uiState.searchResults,
                    onResultClick = { onQuerySelected(it.info.title) }
                )
            }
        }

        // Currently searching or generating (no content yet)
        uiState.isBusy && uiState.streamingAnswer.isEmpty() && uiState.ragAnswer.isEmpty() -> {
            LoadingIndicator(
                message = when {
                    uiState.isGeneratingAnswer -> "Generating answer..."
                    uiState.isSearching -> "Searching knowledge base..."
                    else -> "Processing..."
                }
            )
        }

        // Search results only (no RAG answer)
        uiState.searchResults.isNotEmpty() -> {
            SearchResultsSection(
                results = uiState.searchResults,
                onResultClick = { onQuerySelected(it.info.title) }
            )
        }

        // Idle state - show suggestions
        else -> {
            KnowledgeIdleContent(
                suggestedQueries = uiState.suggestedQueries,
                onQuerySelected = onQuerySelected
            )
        }
    }
}

@Composable
private fun SearchInputSection(
    uiState: KnowledgeViewModel.KnowledgeUiState,
    onQueryChanged: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onClearQuery: () -> Unit,
    onTriggerSearch: (String) -> Unit,
    onCancelSearch: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var isTextFieldFocused by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isTextFieldFocused = it.isFocused },
            label = { Text("Ask a question or search...") },
            placeholder = {
                Text(
                    text = if (isTextFieldFocused) "e.g., How to splint a fracture?" else "Search emergency protocols...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (uiState.isBusy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (uiState.query.isNotBlank()) {
                        onTriggerSearch(uiState.query)
                        keyboardController?.hide()
                    }
                }
            ),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Show progress indicator when busy
                    if (uiState.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        IconButton(onClick = onCancelSearch) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel search",
                                tint = SemanticColors.Error
                            )
                        }
                    } else {
                        // Clear button
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = onClearQuery) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear query",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Voice input button
                        CompactVoiceButton(
                            isListening = uiState.isListening,
                            onStartListening = onStartVoiceInput,
                            onStopListening = onStopVoiceInput,
                            enabled = !uiState.isBusy
                        )

                        // Search button (only show when there's text and not busy)
                        if (uiState.query.isNotBlank() && !uiState.isBusy) {
                            IconButton(
                                onClick = {
                                    onTriggerSearch(uiState.query)
                                    keyboardController?.hide()
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            singleLine = true,
            enabled = !uiState.isBusy,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary
            )
        )

        // Query hints and shortcuts
        if (isTextFieldFocused && uiState.query.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Try asking:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val quickHints = listOf(
                        "How to treat burns?",
                        "What to do for broken bones?",
                        "Signs of shock in patients",
                        "Emergency CPR procedure"
                    )

                    quickHints.forEach { hint ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onQueryChanged(hint) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Search status indicator
        if (uiState.isBusy) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = when {
                    uiState.isSearching -> "Searching knowledge base..."
                    uiState.isGeneratingAnswer && uiState.streamingAnswer.isEmpty() -> "Thinking..."
                    uiState.isGeneratingAnswer -> "Generating response..."
                    else -> "Processing..."
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterSection(
    selectedCategory: String?,
    availableCategories: List<Pair<String?, String>>,
    onCategorySelected: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Filter by Category")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(availableCategories) { (value, name) ->
                FilterChip(
                    selected = selectedCategory == value,
                    onClick = { onCategorySelected(value) },
                    label = { Text(name) },
                    leadingIcon = if (selectedCategory == value) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun RAGAnswerSection(
    answer: String,
    streamingAnswer: String,
    isGenerating: Boolean,
    sourcesUsed: List<String>,
    confidence: Float,
    timeMs: Long
) {
    val isDark = isSystemInDarkTheme()

    // Debug logging to track state transitions
    LaunchedEffect(isGenerating, streamingAnswer.isNotEmpty(), answer.isNotEmpty()) {
        println("RAG State: isGenerating=$isGenerating, hasStreaming=${streamingAnswer.isNotEmpty()}, hasAnswer=${answer.isNotEmpty()}")
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ResultCard(
            title = when {
                isGenerating -> "AI Assistant (Generating...)"
                answer.isNotEmpty() -> "AI-Generated Answer"
                else -> "AI Assistant"
            },
            icon = Icons.Default.Psychology,
            surfaceTint = if (isDark) SurfaceTints.TealDark else SurfaceTints.Teal
        ) {
            // More explicit state handling
            when {
                // Currently generating with streaming text
                isGenerating && streamingAnswer.isNotEmpty() -> {
                    StreamingAnswerContent(
                        streamingText = streamingAnswer,
                        isGenerating = true
                    )
                }

                // Currently generating but no streaming text yet
                isGenerating && streamingAnswer.isEmpty() -> {
                    GeneratingIndicator()
                }

                // Generation complete with final answer
                !isGenerating && answer.isNotEmpty() -> {
                    FinalAnswerContent(
                        answer = answer,
                        sourcesUsed = sourcesUsed,
                        confidence = confidence,
                        timeMs = timeMs
                    )
                }

                // Fallback state (shouldn't happen)
                else -> {
                    Text(
                        text = "Preparing answer...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Only show disclaimer for final answers
        if (!isGenerating && answer.isNotEmpty()) {
            DisclaimerCard()
        }
    }
}

@Composable
private fun StreamingAnswerContent(
    streamingText: String,
    isGenerating: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Show the streaming text with markdown support
        MarkdownTextWithCodeBlocks(
            markdown = streamingText,
            modifier = Modifier.animateContentSize()
        )

        // Blinking cursor for streaming indication
        AnimatedStreamingCursor(isVisible = isGenerating)
    }
}

@Composable
private fun FinalAnswerContent(
    answer: String,
    sourcesUsed: List<String>,
    confidence: Float,
    timeMs: Long
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Final answer with markdown support
        MarkdownTextWithCodeBlocks(
            markdown = answer,
            modifier = Modifier.fillMaxWidth()
        )

        // Sources (if available)
        if (sourcesUsed.isNotEmpty()) {
            SourcesUsedSection(sources = sourcesUsed)
        }

        // Metadata
        ResultMetadata(confidence = confidence, timeMs = timeMs)
    }
}

@Composable
private fun GeneratingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp))
        Text(
            text = "Thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnimatedStreamingCursor(isVisible: Boolean) {
    var showCursor by remember { mutableStateOf(true) }

    // Only animate when visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            while (isVisible) {
                delay(500)
                showCursor = !showCursor
            }
        } else {
            showCursor = false
        }
    }

    Text(
        text = if (showCursor && isVisible) "|" else " ",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun DisclaimerCard() {
    ResultCard(
        title = "Important Notice",
        icon = Icons.Default.Security,
        iconTint = SemanticColors.Info,
        surfaceTint = if (isSystemInDarkTheme()) SurfaceTints.BlueDark else SurfaceTints.Blue
    ) {
        Text(
            text = "This AI-generated answer is for informational purposes only. Always refer to source documents and professional guidance for critical decisions.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SourcesUsedSection(sources: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Based on the following sources:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        sources.forEach { source ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Source,
                    contentDescription = "Source",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchResultsSection(
    results: List<SearchEntry>,
    onResultClick: (SearchEntry) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader("Found in Knowledge Base", action = {
            Text("${results.size} results", style = MaterialTheme.typography.bodySmall)
        })
        results.forEach { entry ->
            SearchResultItem(entry = entry, onClick = { onResultClick(entry) })
        }
    }
}

@Composable
private fun SearchResultItem(entry: SearchEntry, onClick: () -> Unit) {
    val categoryColor = if (entry.info.category.equals("medical", true)) SemanticColors.Error else SemanticColors.Warning
    ResultCard(
        title = entry.info.title,
        icon = if (entry.info.category.equals("medical", true)) Icons.Default.LocalHospital else Icons.Default.Engineering,
        iconTint = categoryColor,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = entry.info.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Source: ${entry.info.source}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Relevance: ${(entry.relevanceScore * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun KnowledgeIdleContent(
    suggestedQueries: List<String>,
    onQuerySelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            title = "Ask Me Anything",
            subtitle = "Search for emergency protocols, first aid procedures, or ask a question about a situation."
        )

        SectionHeader(title = "Suggested Queries")
        suggestedQueries.forEach { query ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQuerySelected(query) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(query, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = "Select query")
                }
            }
        }
    }
}