package com.cautious5.crisis_coach.ui.screens.knowledge

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.cautious5.crisis_coach.ui.components.ResultCard
import com.cautious5.crisis_coach.ui.components.ResultMetadata
import com.cautious5.crisis_coach.ui.components.SectionHeader
import com.cautious5.crisis_coach.ui.theme.SemanticColors
import com.cautious5.crisis_coach.ui.theme.SurfaceTints
import com.cautious5.crisis_coach.utils.LocalPermissionManager

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
        onClearError = viewModel::clearError
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
    onClearError: () -> Unit
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
            query = uiState.query,
            isListening = uiState.isListening,
            onQueryChanged = onQueryChanged,
            onStartVoiceInput = onStartVoiceInput,
            onStopVoiceInput = onStopVoiceInput,
            onClearQuery = onClearQuery
        )

        // Category Filters
        CategoryFilterSection(
            selectedCategory = uiState.selectedCategory,
            availableCategories = availableCategories,
            onCategorySelected = onCategorySelected
        )

        // Animated content area for results, loading, idle, and error states
        AnimatedContent(
            targetState = uiState,
            label = "knowledge_content_animation"
        ) { state ->
            when {
                state.isSearching || state.isGeneratingAnswer -> {
                    LoadingIndicator(
                        message = if (state.isGeneratingAnswer) "Generating answer..." else "Searching knowledge base..."
                    )
                }
                state.error != null -> {
                    ErrorCard(title = "Search Error") {
                        Column {
                            Text(
                                text = state.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                CardActionButton(text = "Dismiss", onClick = onClearError)
                            }
                        }
                    }
                }
                state.ragAnswer.isNotBlank() -> {
                    RAGAnswerSection(
                        answer = state.ragAnswer,
                        sourcesUsed = state.sourcesUsed,
                        confidence = state.confidenceScore,
                        timeMs = state.totalSearchTime
                    )
                }
                state.searchResults.isNotEmpty() -> {
                    SearchResultsSection(
                        results = state.searchResults,
                        onResultClick = { onQuerySelected(it.info.title) }
                    )
                }
                else -> {
                    KnowledgeIdleContent(
                        suggestedQueries = state.suggestedQueries,
                        onQuerySelected = onQuerySelected
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchInputSection(
    query: String,
    isListening: Boolean,
    onQueryChanged: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onClearQuery: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Ask a question or search...") },
        placeholder = { Text("e.g., How to splint a fracture?") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
            Row {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearQuery) {
                        Icon(Icons.Default.Close, contentDescription = "Clear query")
                    }
                }
                CompactVoiceButton(
                    isListening = isListening,
                    onStartListening = onStartVoiceInput,
                    onStopListening = onStopVoiceInput
                )
            }
        },
        singleLine = true
    )
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
    sourcesUsed: List<String>,
    confidence: Float,
    timeMs: Long
) {
    val isDark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ResultCard(
            title = "AI-Generated Answer",
            icon = Icons.Default.Psychology,
            surfaceTint = if (isDark) SurfaceTints.TealDark else SurfaceTints.Teal
        ) {
            Text(text = answer, style = MaterialTheme.typography.bodyLarge)
            if (sourcesUsed.isNotEmpty()) {
                SourcesUsedSection(sources = sourcesUsed)
            }
            ResultMetadata(confidence = confidence, timeMs = timeMs)
        }

        ResultCard(
            title = "Disclaimer",
            icon = Icons.Default.Security,
            iconTint = SemanticColors.Info,
            surfaceTint = if (isSystemInDarkTheme()) SurfaceTints.BlueDark else SurfaceTints.Blue
        ) {
            Text(
                text = "This AI-generated answer is for informational purposes only. Always refer to source documents and professional guidance.",
                style = MaterialTheme.typography.bodySmall
            )
        }
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