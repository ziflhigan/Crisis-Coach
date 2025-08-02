package com.cautious5.crisis_coach.model.database

import android.content.Context
import android.util.Log
import com.cautious5.crisis_coach.model.database.entities.EmergencyInfo
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Flexible document processor for importing various document formats
 * into the knowledge base for RAG functionality
 */
class DocumentProcessor(
    private val context: Context,
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "DocumentProcessor"
        private const val DEFAULT_CHUNK_SIZE = 500 // characters
        private const val CHUNK_OVERLAP = 50 // characters
    }

    /**
     * Supported document formats
     */
    enum class DocumentFormat {
        JSON,
        TXT,
        MARKDOWN,
        CSV,
        XML,
        PDF // Future support
    }

    /**
     * Document metadata for processing
     */
    data class DocumentMetadata(
        val source: String,
        val format: DocumentFormat,
        val category: String = "general",
        val priority: Int = 3,
        val languageCode: String = "en",
        val chunkingStrategy: ChunkingStrategy = ChunkingStrategy.FIXED_SIZE
    )

    /**
     * Chunking strategies for large documents
     */
    enum class ChunkingStrategy {
        FIXED_SIZE,      // Fixed character count
        SENTENCE_BASED,  // Split by sentences
        PARAGRAPH_BASED, // Split by paragraphs
        SEMANTIC        // Future: semantic chunking using embeddings
    }

    /**
     * Processes a document from various sources
     */
    suspend fun processDocument(
        inputStream: InputStream,
        metadata: DocumentMetadata
    ): DocumentProcessResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Processing document: ${metadata.source} (${metadata.format})")

        try {
            val entries = when (metadata.format) {
                DocumentFormat.JSON -> processJsonDocument(inputStream, metadata)
                DocumentFormat.TXT -> processTextDocument(inputStream, metadata)
                DocumentFormat.MARKDOWN -> processMarkdownDocument(inputStream, metadata)
                DocumentFormat.CSV -> processCsvDocument(inputStream, metadata)
                DocumentFormat.XML -> processXmlDocument(inputStream, metadata)
                DocumentFormat.PDF -> processPdfDocument(inputStream, metadata)
            }

            if (entries.isEmpty()) {
                DocumentProcessResult.Error("No entries extracted from document")
            } else {
                DocumentProcessResult.Success(
                    entries = entries,
                    totalChunks = entries.size,
                    source = metadata.source
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Document processing failed: ${e.message}", e)
            DocumentProcessResult.Error("Processing failed: ${e.message}", e)
        }
    }

    /**
     * Processes multiple documents in batch
     */
    suspend fun processBatch(
        documents: List<Pair<InputStream, DocumentMetadata>>
    ): BatchProcessResult = withContext(Dispatchers.IO) {

        val allEntries = mutableListOf<EmergencyInfo>()
        val errors = mutableListOf<String>()

        documents.forEach { (stream, metadata) ->
            when (val result = processDocument(stream, metadata)) {
                is DocumentProcessResult.Success -> {
                    allEntries.addAll(result.entries)
                }
                is DocumentProcessResult.Error -> {
                    errors.add("${metadata.source}: ${result.message}")
                }
            }
        }

        BatchProcessResult(
            totalEntries = allEntries,
            successCount = allEntries.size,
            errors = errors
        )
    }

    // Format-specific processors

    private fun processJsonDocument(
        inputStream: InputStream,
        metadata: DocumentMetadata
    ): List<EmergencyInfo> {
        val reader = InputStreamReader(inputStream)
        val jsonElement = JsonParser.parseReader(reader)

        return if (jsonElement.isJsonArray) {
            // Process array of entries
            jsonElement.asJsonArray.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    EmergencyInfo(
                        title = obj.get("title")?.asString ?: "Untitled",
                        text = obj.get("text")?.asString ?: "",
                        category = obj.get("category")?.asString ?: metadata.category,
                        priority = obj.get("priority")?.asInt ?: metadata.priority,
                        keywords = obj.get("keywords")?.asString ?: "",
                        source = metadata.source,
                        languageCode = metadata.languageCode
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse JSON entry: ${e.message}")
                    null
                }
            }
        } else {
            // Single entry
            listOf(createEntryFromText(
                jsonElement.toString(),
                "JSON Document",
                metadata
            ))
        }
    }

    private fun processTextDocument(
        inputStream: InputStream,
        metadata: DocumentMetadata
    ): List<EmergencyInfo> {
        val text = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }

        return when (metadata.chunkingStrategy) {
            ChunkingStrategy.FIXED_SIZE -> chunkBySize(text, metadata)
            ChunkingStrategy.SENTENCE_BASED -> chunkBySentences(text, metadata)
            ChunkingStrategy.PARAGRAPH_BASED -> chunkByParagraphs(text, metadata)
            ChunkingStrategy.SEMANTIC -> chunkBySize(text, metadata) // Fallback for now
        }
    }

    private fun processMarkdownDocument(
        inputStream: InputStream,
        metadata: DocumentMetadata
    ): List<EmergencyInfo> {
        val text = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        val sections = parseMarkdownSections(text)

        return sections.map { section ->
            EmergencyInfo(
                title = section.title,
                text = section.content,
                category = metadata.category,
                priority = metadata.priority,
                keywords = extractKeywords(section.content),
                source = metadata.source,
                languageCode = metadata.languageCode
            )
        }
    }

    private fun processCsvDocument(
        inputStream: InputStream,
        metadata: DocumentMetadata
    ): List<EmergencyInfo> {
        val entries = mutableListOf<EmergencyInfo>()
        val reader = BufferedReader(InputStreamReader(inputStream))

        // Simple CSV parsing (consider using a proper CSV library for production)
        val headers = reader.readLine()?.split(",")?.map { it.trim() } ?: return emptyList()

        val titleIndex = headers.indexOf("title")
        val textIndex = headers.indexOf("text")
        val categoryIndex = headers.indexOf("category")
        val priorityIndex = headers.indexOf("priority")

        if (textIndex == -1) {
            Log.e(TAG, "CSV must have a 'text' column")
            return emptyList()
        }

        reader.forEachLine { line ->
            val values = line.split(",").map { it.trim() }
            if (values.size > textIndex) {
                entries.add(EmergencyInfo(
                    title = if (titleIndex >= 0) values.getOrNull(titleIndex) ?: "Entry ${entries.size + 1}"
                    else "Entry ${entries.size + 1}",
                    text = values[textIndex],
                    category = if (categoryIndex >= 0) values.getOrNull(categoryIndex) ?: metadata.category
                    else metadata.category,
                    priority = if (priorityIndex >= 0) values.getOrNull(priorityIndex)?.toIntOrNull() ?: metadata.priority
                    else metadata.priority,
                    source = metadata.source,
                    languageCode = metadata.languageCode
                ))
            }
        }

        return entries
    }

    private fun processXmlDocument(
        inputStream: InputStream,
        metadata: DocumentMetadata
    ): List<EmergencyInfo> {
        // Simple XML parsing - in production, use a proper XML parser
        val text = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        val entries = mutableListOf<EmergencyInfo>()

        // Extract content between <entry> tags
        val entryPattern = "<entry>(.*?)</entry>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matches = entryPattern.findAll(text)

        matches.forEach { match ->
            val entryContent = match.groupValues[1]
            val title = extractXmlTag(entryContent, "title") ?: "Untitled"
            val content = extractXmlTag(entryContent, "content") ?: ""

            if (content.isNotBlank()) {
                entries.add(createEntryFromText(content, title, metadata))
            }
        }

        return entries
    }

    private fun processPdfDocument(
        inputStream: InputStream,
        metadata: DocumentMetadata
    ): List<EmergencyInfo> {
        Log.d(TAG, "=== Processing PDF Document: ${metadata.source} ===")

        val entries = mutableListOf<EmergencyInfo>()
        var pdfReader: PdfReader? = null
        var pdfDocument: PdfDocument? = null

        try {
            // Initialize PDF reader
            pdfReader = PdfReader(inputStream)
            pdfDocument = PdfDocument(pdfReader)
            val pageCount = pdfDocument.numberOfPages

            Log.d(TAG, "PDF Details:")
            Log.d(TAG, "  - Source: ${metadata.source}")
            Log.d(TAG, "  - Pages: $pageCount")
            Log.d(TAG, "  - Chunking: ${metadata.chunkingStrategy}")

            // Extract text from all pages
            val extractedText = StringBuilder()
            for (pageNum in 1..pageCount) {
                try {
                    val page = pdfDocument.getPage(pageNum)
                    val pageText = PdfTextExtractor.getTextFromPage(page, SimpleTextExtractionStrategy())

                    Log.d(TAG, "Page $pageNum raw text length = ${pageText.length}")

                    if (pageText.isNotBlank()) {
                        extractedText.append(pageText).append("\n\n")
                        Log.d(TAG, "  - Page $pageNum: ${pageText.length} characters extracted")
                    } else {
                        Log.w(TAG, "  - Page $pageNum: No text found")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "  Failed to extract text from page $pageNum: ${e.message}")
                }
            }

            val fullText = extractedText.toString().trim()
            Log.d(TAG, "Total text extracted: ${fullText.length} characters")

            if (fullText.isBlank()) {
                Log.w(TAG, "No text content found in PDF")
                return emptyList()
            }

            // Log a sample of the extracted text for debugging
            val sampleLength = minOf(200, fullText.length)
            Log.d(TAG, "Text sample: ${fullText.take(sampleLength)}...")

            // Apply chunking strategy
            val chunks = when (metadata.chunkingStrategy) {
                ChunkingStrategy.FIXED_SIZE -> {
                    Log.d(TAG, "Using FIXED_SIZE chunking")
                    chunkBySize(fullText, metadata)
                }
                ChunkingStrategy.SENTENCE_BASED -> {
                    Log.d(TAG, "Using SENTENCE_BASED chunking")
                    chunkBySentences(fullText, metadata)
                }
                ChunkingStrategy.PARAGRAPH_BASED -> {
                    Log.d(TAG, "Using PARAGRAPH_BASED chunking")
                    chunkByParagraphs(fullText, metadata)
                }
                ChunkingStrategy.SEMANTIC -> {
                    Log.d(TAG, "Using SEMANTIC chunking (fallback to FIXED_SIZE)")
                    chunkBySize(fullText, metadata)
                }
            }

            Log.i(TAG, "PDF processing complete: Generated ${chunks.size} chunks from ${metadata.source}")
            return chunks

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process PDF document: ${metadata.source}", e)
            return emptyList()
        } finally {
            // Clean up resources
            try {
                pdfDocument?.close()
                pdfReader?.close()
                inputStream.close()
                Log.v(TAG, "PDF resources cleaned up")
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up PDF resources: ${e.message}")
            }
        }
    }

    // Chunking strategies

    private fun chunkBySize(
        text: String,
        metadata: DocumentMetadata,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): List<EmergencyInfo> {
        val chunks = mutableListOf<EmergencyInfo>()
        var startIndex = 0
        var chunkNumber = 1

        while (startIndex < text.length) {
            val endIndex = minOf(startIndex + chunkSize, text.length)
            val chunkText = text.substring(startIndex, endIndex)

            // Add overlap from previous chunk if not the first chunk
            val finalText = if (startIndex > 0 && startIndex >= CHUNK_OVERLAP) {
                text.substring(startIndex - CHUNK_OVERLAP, endIndex)
            } else {
                chunkText
            }

            chunks.add(createEntryFromText(
                finalText,
                "${metadata.source} - Chunk $chunkNumber",
                metadata
            ))

            startIndex = endIndex
            chunkNumber++
        }

        return chunks
    }

    private fun chunkBySentences(
        text: String,
        metadata: DocumentMetadata,
        sentencesPerChunk: Int = 5
    ): List<EmergencyInfo> {
        val sentences = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }
        val chunks = mutableListOf<EmergencyInfo>()

        sentences.chunked(sentencesPerChunk).forEachIndexed { index, sentenceGroup ->
            val chunkText = sentenceGroup.joinToString(". ") + "."
            chunks.add(createEntryFromText(
                chunkText,
                "${metadata.source} - Section ${index + 1}",
                metadata
            ))
        }

        return chunks
    }

    private fun chunkByParagraphs(
        text: String,
        metadata: DocumentMetadata
    ): List<EmergencyInfo> {
        val paragraphs = text.split(Regex("\\n\\n+")).filter { it.isNotBlank() }

        return paragraphs.mapIndexed { index, paragraph ->
            createEntryFromText(
                paragraph.trim(),
                "${metadata.source} - Paragraph ${index + 1}",
                metadata
            )
        }
    }

    // Helper methods

    private fun createEntryFromText(
        text: String,
        title: String,
        metadata: DocumentMetadata
    ): EmergencyInfo {
        return EmergencyInfo(
            title = title,
            text = text,
            category = metadata.category,
            priority = metadata.priority,
            keywords = extractKeywords(text),
            source = metadata.source,
            languageCode = metadata.languageCode,
            fieldSuitable = true
        )
    }

    private fun parseMarkdownSections(markdown: String): List<MarkdownSection> {
        val sections = mutableListOf<MarkdownSection>()
        val lines = markdown.lines()

        var currentTitle = "Document"
        val currentContent = StringBuilder()

        for (line in lines) {
            when {
                line.startsWith("# ") -> {
                    if (currentContent.isNotEmpty()) {
                        sections.add(MarkdownSection(currentTitle, currentContent.toString().trim()))
                        currentContent.clear()
                    }
                    currentTitle = line.removePrefix("# ").trim()
                }
                line.startsWith("## ") -> {
                    if (currentContent.isNotEmpty()) {
                        sections.add(MarkdownSection(currentTitle, currentContent.toString().trim()))
                        currentContent.clear()
                    }
                    currentTitle = line.removePrefix("## ").trim()
                }
                else -> {
                    currentContent.appendLine(line)
                }
            }
        }

        if (currentContent.isNotEmpty()) {
            sections.add(MarkdownSection(currentTitle, currentContent.toString().trim()))
        }

        return sections
    }

    private fun extractKeywords(text: String): String {
        // Simple keyword extraction - in production, use NLP techniques
        val words = text.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length > 3 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }

        return words.joinToString(" ")
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val pattern = "<$tag>(.*?)</$tag>".toRegex(RegexOption.DOT_MATCHES_ALL)
        return pattern.find(xml)?.groupValues?.get(1)?.trim()
    }

    private data class MarkdownSection(
        val title: String,
        val content: String
    )
}

/**
 * Result classes for document processing
 */
sealed class DocumentProcessResult {
    data class Success(
        val entries: List<EmergencyInfo>,
        val totalChunks: Int,
        val source: String
    ) : DocumentProcessResult()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : DocumentProcessResult()
}

data class BatchProcessResult(
    val totalEntries: List<EmergencyInfo>,
    val successCount: Int,
    val errors: List<String>
)