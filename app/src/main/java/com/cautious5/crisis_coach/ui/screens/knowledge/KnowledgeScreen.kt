package com.cautious5.crisis_coach.ui.screens.knowledge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cautious5.crisis_coach.model.database.SearchEntry
import com.cautious5.crisis_coach.model.database.entities.EmergencyInfo
import com.cautious5.crisis_coach.ui.components.ResultCard
import com.cautious5.crisis_coach.ui.theme.*
import com.cautious5.crisis_coach.utils.LocalPermissionManager

/**
 * Knowledge screen for Crisis Coach app
 * Provides RAG-powered emergency knowledge search and Q&A functionality
 */

@Composable
fun KnowledgeScreen(
    viewModel: KnowledgeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Permission handling
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
        canUseVoice = viewModel.canUseVoiceInput(),
        onClearQuery = viewModel::clearQuery,
        onCategorySelected = viewModel::selectCategory,
        availableCategories = viewModel.getAvailableCategories(),
        onQuerySelected = viewModel::useSuggestedQuery,
        onClearError = viewModel::clearError,
        onGetConfidenceDescription = viewModel::getConfidenceDescription,
        onGetConfidenceColor = viewModel::getConfidenceColor,
        onFormatRelevanceScore = viewModel::formatRelevanceScore
    )
}

@Composable
private fun KnowledgeScreenContent(
    uiState: KnowledgeViewModel.KnowledgeUiState,
    onQueryChanged: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    canUseVoice: Boolean,
    onClearQuery: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    availableCategories: List<Pair<String?, String>>,
    onQuerySelected: (String) -> Unit,
    onClearError: () -> Unit,
    onGetConfidenceDescription: (Float) -> String,
    onGetConfidenceColor: (Float) -> Color,
    onFormatRelevanceScore: (Float) -> String
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
        KnowledgeHeader()

        // Search Section
        SearchSection(
            query = uiState.query,
            onQueryChanged = onQueryChanged,
            isSearching = uiState.isSearching,
            isListening = uiState.isListening,
            onStartVoiceInput = onStartVoiceInput,
            onStopVoiceInput = onStopVoiceInput,
            canUseVoice = canUseVoice,
            onClearQuery = onClearQuery
        )

        // Category Filters
        CategoryFilterSection(
            selectedCategory = uiState.selectedCategory,
            onCategorySelected = onCategorySelected,
            availableCategories = availableCategories
        )

        // Suggested Queries - Only show if no query and no results
        if (uiState.query.isBlank() && uiState.searchResults.isEmpty()) {
            SuggestedQueriesSection(
                suggestedQueries = uiState.suggestedQueries,
                onQuerySelected = onQuerySelected,
                searchHistory = uiState.searchHistory
            )
        }

        // Search Status
        if (uiState.isSearching || uiState.isGeneratingAnswer) {
            SearchStatusCard(
                isSearching = uiState.isSearching,
                isGeneratingAnswer = uiState.isGeneratingAnswer,
                searchTime = uiState.totalSearchTime
            )
        }

        // RAG Answer
        if (uiState.ragAnswer.isNotBlank()) {
            RAGAnswerSection(
                answer = uiState.ragAnswer,
                confidenceScore = uiState.confidenceScore,
                sourcesUsed = uiState.sourcesUsed,
                searchTime = uiState.totalSearchTime,
                onGetConfidenceDescription = onGetConfidenceDescription,
                onGetConfidenceColor = onGetConfidenceColor
            )
        }

        // Search Results
        if (uiState.searchResults.isNotEmpty()) {
            SearchResultsSection(
                searchResults = uiState.searchResults,
                onFormatRelevanceScore = onFormatRelevanceScore
            )
        }

        // Error Display
        uiState.error?.let { error ->
            ErrorCard(
                error = error,
                onDismiss = onClearError
            )
        }
    }
}

@Composable
private fun KnowledgeHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.generalSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = "Emergency Guide",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Emergency Guide",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "AI-powered search through emergency protocols and first aid procedures",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchSection(
    query: String,
    onQueryChanged: (String) -> Unit,
    isSearching: Boolean,
    isListening: Boolean,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    canUseVoice: Boolean,
    onClearQuery: () -> Unit
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
                text = "Search Emergency Information",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            // Search Input with Voice
            SearchInputField(
                query = query,
                onQueryChanged = onQueryChanged,
                isSearching = isSearching,
                isListening = isListening,
                onStartVoiceInput = onStartVoiceInput,
                onStopVoiceInput = onStopVoiceInput,
                canUseVoice = canUseVoice,
                onClearQuery = onClearQuery
            )

            // Voice Status
            if (isListening) {
                VoiceListeningIndicator()
            }
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChanged: (String) -> Unit,
    isSearching: Boolean,
    isListening: Boolean,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    canUseVoice: Boolean,
    onClearQuery: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Ask a question or search...") },
        placeholder = { Text("e.g., How to treat a burn?") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            SearchTrailingIcons(
                canUseVoice = canUseVoice,
                isListening = isListening,
                hasQuery = query.isNotBlank(),
                onStartVoiceInput = onStartVoiceInput,
                onStopVoiceInput = onStopVoiceInput,
                onClearQuery = onClearQuery
            )
        },
        enabled = !isSearching,
        maxLines = 3,
        textStyle = ContentTextStyles.KnowledgeQuery
    )
}

@Composable
private fun SearchTrailingIcons(
    canUseVoice: Boolean,
    isListening: Boolean,
    hasQuery: Boolean,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    onClearQuery: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Voice Input Button
        if (canUseVoice) {
            VoiceInputIconButton(
                isListening = isListening,
                onStartVoiceInput = onStartVoiceInput,
                onStopVoiceInput = onStopVoiceInput
            )
        }

        // Clear Button
        if (hasQuery) {
            IconButton(
                onClick = onClearQuery,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceInputIconButton(
    isListening: Boolean,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit
) {
    IconButton(
        onClick = if (isListening) onStopVoiceInput else onStartVoiceInput,
        modifier = Modifier
            .size(40.dp)
            .background(
                if (isListening) MaterialTheme.colorScheme.voiceListening.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(8.dp)
            )
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isListening) "Stop listening" else "Voice search",
            tint = if (isListening) MaterialTheme.colorScheme.voiceListening
            else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun VoiceListeningIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Listening",
            tint = MaterialTheme.colorScheme.voiceListening,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "Listening for your question...",
            style = EmergencyTextStyles.VoiceStatus,
            color = MaterialTheme.colorScheme.voiceListening
        )
    }
}

@Composable
private fun CategoryFilterSection(
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    availableCategories: List<Pair<String?, String>>
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
                text = "Filter by Category",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(availableCategories) { (categoryValue, categoryName) ->
                    CategoryChip(
                        name = categoryName,
                        isSelected = selectedCategory == categoryValue,
                        onClick = { onCategorySelected(categoryValue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        onClick = onClick,
        label = { Text(name) },
        selected = isSelected,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun SuggestedQueriesSection(
    suggestedQueries: List<String>,
    onQuerySelected: (String) -> Unit,
    searchHistory: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Suggested Queries
            SuggestedQueriesGrid(
                suggestedQueries = suggestedQueries,
                onQuerySelected = onQuerySelected
            )

            // Recent Searches
            if (searchHistory.isNotEmpty()) {
                HorizontalDivider()
                RecentSearchesSection(
                    searchHistory = searchHistory,
                    onQuerySelected = onQuerySelected
                )
            }
        }
    }
}

@Composable
private fun SuggestedQueriesGrid(
    suggestedQueries: List<String>,
    onQuerySelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Suggested Questions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            modifier = Modifier.height(200.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(suggestedQueries) { query ->
                SuggestedQueryItem(
                    query = query,
                    onClick = { onQuerySelected(query) }
                )
            }
        }
    }
}

@Composable
private fun SuggestedQueryItem(
    query: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.QuestionMark,
                contentDescription = "Suggested question",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = query,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Select",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun RecentSearchesSection(
    searchHistory: List<String>,
    onQuerySelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Recent Searches",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        searchHistory.take(3).forEach { query ->
            RecentSearchItem(
                query = query,
                onClick = { onQuerySelected(query) }
            )
        }
    }
}

@Composable
private fun RecentSearchItem(
    query: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "Recent search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = query,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchStatusCard(
    isSearching: Boolean,
    isGeneratingAnswer: Boolean,
    searchTime: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )

            Column {
                Text(
                    text = when {
                        isSearching -> "Searching knowledge base..."
                        isGeneratingAnswer -> "Generating answer..."
                        else -> "Processing..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )

                if (searchTime > 0) {
                    Text(
                        text = "Search completed in ${searchTime}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RAGAnswerSection(
    answer: String,
    confidenceScore: Float,
    sourcesUsed: List<String>,
    searchTime: Long,
    onGetConfidenceDescription: (Float) -> String,
    onGetConfidenceColor: (Float) -> Color
) {
    ResultCard(
        title = "AI Answer",
        icon = Icons.Default.Psychology,
        containerColor = MaterialTheme.colorScheme.infoCardBackground
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Confidence Badge
            if (confidenceScore > 0f) {
                ConfidenceBadge(
                    confidenceScore = confidenceScore,
                    onGetConfidenceDescription = onGetConfidenceDescription,
                    onGetConfidenceColor = onGetConfidenceColor
                )
            }

            // Answer Text
            Text(
                text = answer,
                style = ContentTextStyles.KnowledgeAnswer,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Sources Used
            if (sourcesUsed.isNotEmpty()) {
                SourcesList(sourcesUsed = sourcesUsed)
            }

            // Metadata
            AnswerMetadata(
                confidenceScore = confidenceScore,
                searchTime = searchTime
            )
        }
    }
}

@Composable
private fun ConfidenceBadge(
    confidenceScore: Float,
    onGetConfidenceDescription: (Float) -> String,
    onGetConfidenceColor: (Float) -> Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = onGetConfidenceColor(confidenceScore).copy(alpha = 0.2f),
        border = BorderStroke(
            1.dp,
            onGetConfidenceColor(confidenceScore)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = "Confidence",
                modifier = Modifier.size(16.dp),
                tint = onGetConfidenceColor(confidenceScore)
            )
            Text(
                text = onGetConfidenceDescription(confidenceScore),
                style = EmergencyTextStyles.ConfidenceText,
                color = onGetConfidenceColor(confidenceScore),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SourcesList(sourcesUsed: List<String>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Sources:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        sourcesUsed.forEach { source ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Source,
                    contentDescription = "Source",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = source,
                    style = ContentTextStyles.KnowledgeSource,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AnswerMetadata(
    confidenceScore: Float,
    searchTime: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (confidenceScore > 0f) {
            Text(
                text = "Confidence: ${(confidenceScore * 100).toInt()}%",
                style = EmergencyTextStyles.ConfidenceText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (searchTime > 0) {
            Text(
                text = "Response: ${searchTime}ms",
                style = EmergencyTextStyles.TimeText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchResultsSection(
    searchResults: List<SearchEntry>,
    onFormatRelevanceScore: (Float) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SearchResultsHeader(resultCount = searchResults.size)

            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { searchEntry ->
                    SearchResultCard(
                        searchEntry = searchEntry,
                        onFormatRelevanceScore = onFormatRelevanceScore
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsHeader(resultCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Knowledge Base Results",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "$resultCount results",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchResultCard(
    searchEntry: SearchEntry,
    onFormatRelevanceScore: (Float) -> String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title and Relevance Score
            SearchResultHeader(
                title = searchEntry.info.title,
                relevanceScore = onFormatRelevanceScore(searchEntry.relevanceScore)
            )

            // Content Preview
            Text(
                text = searchEntry.info.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Category and Source
            SearchResultFooter(
                category = searchEntry.info.category,
                source = searchEntry.info.source
            )
        }
    }
}

@Composable
private fun SearchResultHeader(
    title: String,
    relevanceScore: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Text(
                text = relevanceScore,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SearchResultFooter(
    category: String,
    source: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category Badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = category.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Source
        if (source.isNotBlank()) {
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

// Preview Parameter Provider
class KnowledgeUiStateProvider : PreviewParameterProvider<KnowledgeViewModel.KnowledgeUiState> {
    override val values = sequenceOf(
        // Initial state
        KnowledgeViewModel.KnowledgeUiState(),
        // With query
        KnowledgeViewModel.KnowledgeUiState(
            query = "How to treat burns?",
            ragAnswer = "For burns, first remove the person from the heat source. Cool the burn with cool water for 10-15 minutes. Do not use ice. Cover with a clean, dry cloth. Seek medical attention for severe burns.",
            confidenceScore = 0.85f,
            sourcesUsed = listOf("First Aid Manual Chapter 12", "Emergency Medicine Guide"),
            totalSearchTime = 1250,
            searchResults = getSampleSearchResults()
        ),
        // Searching state
        KnowledgeViewModel.KnowledgeUiState(
            query = "CPR procedure",
            isSearching = true
        ),
        // Error state
        KnowledgeViewModel.KnowledgeUiState(
            query = "emergency procedure",
            error = "Search failed. Please check your input and try again."
        )
    )

    private fun getSampleSearchResults() = listOf(
        SearchEntry(
            info = EmergencyInfo().apply {
                title = "Burn Treatment Protocol"
                text =
                    "For minor burns, cool with water for 10-15 minutes. Do not use ice or butter. Cover with sterile gauze."
                category = "medical"
                source = "First Aid Manual"
            },
            relevanceScore = 0.92f,
            similarity = 0.9f
        ),
        SearchEntry(
            info = EmergencyInfo().apply {
                title = "Thermal Injury Management"
                text =
                    "Assess burn severity by depth and area. Second-degree burns require medical attention."
                category = "medical"
                source = "Emergency Medicine Guide"
            },
            relevanceScore = 0.78f,
            similarity = 0.8f
        )
    )
}

// Preview Functions
@Preview(
    name = "Knowledge Screen",
    apiLevel = 34,
    showBackground = true
)
@Composable
private fun KnowledgeScreenPreview(
    @PreviewParameter(KnowledgeUiStateProvider::class) uiState: KnowledgeViewModel.KnowledgeUiState
) {
    CrisisCoachTheme {
        Surface {
            KnowledgeScreenContent(
                uiState = uiState,
                onQueryChanged = {},
                onStartVoiceInput = {},
                onStopVoiceInput = {},
                canUseVoice = true,
                onClearQuery = {},
                onCategorySelected = {},
                availableCategories = listOf(
                    null to "All Categories",
                    "medical" to "Medical",
                    "structural" to "Structural",
                    "general" to "General"
                ),
                onQuerySelected = {},
                onClearError = {},
                onGetConfidenceDescription = { "High Confidence" },
                onGetConfidenceColor = { Color.Green },
                onFormatRelevanceScore = { "${(it * 100).toInt()}%" }
            )
        }
    }
}

@Preview(
    name = "Knowledge Screen - Dark",
    apiLevel = 34,
    showBackground = true
)
@Composable
private fun KnowledgeScreenDarkPreview() {
    CrisisCoachTheme(darkTheme = true) {
        Surface {
            KnowledgeScreenContent(
                uiState = KnowledgeViewModel.KnowledgeUiState(
                    query = "How to perform CPR?",
                    ragAnswer = "CPR should be performed with chest compressions at least 2 inches deep and at a rate of 100-120 compressions per minute. Give 30 compressions followed by 2 rescue breaths.",
                    confidenceScore = 0.92f,
                    sourcesUsed = listOf("CPR Guidelines 2023", "Emergency Response Manual"),
                    selectedCategory = "medical"
                ),
                onQueryChanged = {},
                onStartVoiceInput = {},
                onStopVoiceInput = {},
                canUseVoice = true,
                onClearQuery = {},
                onCategorySelected = {},
                availableCategories = listOf(
                    null to "All Categories",
                    "medical" to "Medical",
                    "structural" to "Structural",
                    "general" to "General"
                ),
                onQuerySelected = {},
                onClearError = {},
                onGetConfidenceDescription = { "Very High Confidence" },
                onGetConfidenceColor = { Color.Green },
                onFormatRelevanceScore = { "${(it * 100).toInt()}%" }
            )
        }
    }
}