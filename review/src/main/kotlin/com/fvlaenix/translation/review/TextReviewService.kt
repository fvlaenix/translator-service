package com.fvlaenix.translation.review

import com.fvlaenix.text.TextModelService
import com.fvlaenix.translation.agent.AbstractTextModelAgent
import com.fvlaenix.translation.splitter.TextSplitter
import com.fvlaenix.translation.summarizer.Summarizer
import com.fvlaenix.translation.summarizer.TextModelSummarizer
import com.fvlaenix.translation.translator.TextTranslation
import com.fvlaenix.translation.translator.Translation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Service for processing text in chunks, applying review agents, and generating summaries.
 * Each chunk and its reviews are saved in separate directories.
 *
 * @property textModelService Service for text model operations
 * @property outputDirectory Base directory for output files
 * @property agents List of review agents to apply to each chunk
 * @property summarizer Summarizer for generating context for subsequent chunks
 */
class TextReviewService(
  private val textModelService: TextModelService,
  private val splitTextModelService: TextModelService,
  private val outputDirectory: Path,
  private val agents: List<ReviewAgent>,
  private val summarizer: Summarizer = TextModelSummarizer(textModelService),
  private val fraction: Float = 0.2f
) {

  private val textSplitter = TextSplitter(splitTextModelService, fraction)

  /**
   * Process a text by splitting it into chunks and applying review agents.
   * Progress is printed to standard output.
   *
   * @param text Full text to process
   * @param context Optional initial context
   */
  suspend fun processText(text: String, context: String? = null) = coroutineScope {
    // Initialize output directory
    val currentOutputDirectory =
      outputDirectory.resolve(LocalTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")))
    currentOutputDirectory.createDirectories()

    // Split text into chunks
    val chunks = splitTextIntoChunks(text)
    val totalChunks = chunks.size

    // Process each chunk
    chunks.forEachIndexed { index, chunk ->
      val chunkNumber = index + 1
      val progressPercent = (chunkNumber * 100) / totalChunks

      println("Processing chunk $chunkNumber of $totalChunks ($progressPercent%)")

      // Create directory for this chunk
      val chunkDir = currentOutputDirectory.resolve("chunk_$chunkNumber")
      chunkDir.createDirectories()

      // Save the current summary (context)
      val currentSummary = summarizer.getCurrentSummary()
      chunkDir.resolve("summary.txt").writeText(currentSummary)

      // Save original chunk
      chunkDir.resolve("original.txt").writeText(chunk)

      val additionalContext = """
        Context:
        
        $context
        
        Current summary of story (please use it for analyze):
        
        $currentSummary
      """.trimIndent()

      // Apply each review agent
      coroutineScope {
        agents.forEach { agent ->
          launch {
            val reviewResult = agent.processChunk(chunk, additionalContext)
            chunkDir.resolve("review_${agent.name}.txt").writeText(reviewResult)
          }
        }
      }

      // Update summary with this chunk
      summarizer.updateSummary(chunk)
    }

    // Save final summary
    currentOutputDirectory.resolve("final_summary.txt").writeText(summarizer.getCurrentSummary())

    println("Text processing complete. Output saved to $currentOutputDirectory")
  }

  /**
   * Split text into chunks using TextSplitter.
   *
   * @param text Full text to split
   * @return List of text chunks
   */
  private suspend fun splitTextIntoChunks(text: String): List<String> {
    // Create text translations from the input text
    val translations = text.split("\n\n")
      .filter { it.isNotBlank() }
      .map { TextTranslation(it) }

    // Function to transform translations to a single string for token counting
    val transformer = { translations: List<Translation> ->
      translations.joinToString("\n\n") { it.original }
    }

    // Split translations using TextSplitter
    val (splitTranslations, _) = textSplitter.splitTranslations(translations, transformer)

    // Create batches using TextSplitter
    val batches = textSplitter.createBatches(splitTranslations, transformer)

    // Convert each batch to a single text chunk
    return batches.map { batch ->
      batch.joinToString("\n\n") { it.original }
    }
  }

  /**
   * Interface for review agents that process text chunks
   */
  interface ReviewAgent {
    /**
     * Name of the review agent, used for output file naming
     */
    val name: String

    /**
     * Process a chunk of text
     *
     * @param chunk Text chunk to process
     * @param additionalContext Summary of previous chunks for context
     * @return Review result as a string
     */
    suspend fun processChunk(chunk: String, additionalContext: String): String
  }

  /**
   * Implementation of ReviewAgent that uses AbstractTextModelAgent
   */
  class TextModelReviewAgent(
    override val name: String,
    private val agent: AbstractTextModelAgent
  ) : ReviewAgent {
    override suspend fun processChunk(chunk: String, additionalContext: String): String {
      return agent.process(chunk, additionalContext)
    }
  }
}