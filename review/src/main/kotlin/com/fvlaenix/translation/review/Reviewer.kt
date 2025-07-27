package com.fvlaenix.translation.review

import com.fvlaenix.translation.translator.Translation
import kotlinx.serialization.Serializable

/**
 * Interface for translation reviewers
 */
interface Reviewer {
  /**
   * Evaluates the quality of text translation
   * @param original Original text
   * @param translation Translated text
   * @param context Translation context (e.g., summary)
   * @return Evaluation result
   */
  suspend fun review(original: String, translation: String, context: String? = null): ReviewResult

  /**
   * Evaluates the quality of translation for a list of texts
   * @param translations List of translations
   * @param context Translation context (e.g., summary)
   * @return List of evaluation results in the same order
   */
  suspend fun reviewBatch(translations: List<Translation>, context: String? = null): List<ReviewResult>
}

/**
 * Translation evaluation result
 */
@Serializable
data class ReviewResult(
  /**
   * Scores for various criteria
   */
  val scores: ReviewScores,

  /**
   * Feedback for the scores
   */
  val feedback: ReviewFeedback
)

/**
 * Scores for various criteria
 */
@Serializable
data class ReviewScores(
  /**
   * Text coherence (logical connections, smooth transitions)
   * Scale: 1-10
   */
  val coherence: Int,

  /**
   * Grammatical correctness of the text
   * Scale: 1-10
   */
  val grammar: Int,

  /**
   * Modernity of the translation language
   * Scale: 1-10
   */
  val modernity: Int
)

/**
 * Feedback for the scores
 */
@Serializable
data class ReviewFeedback(
  /**
   * Feedback on coherence score
   */
  val coherence: String,

  /**
   * Feedback on grammar score
   */
  val grammar: String,

  /**
   * Feedback on modernity score
   */
  val modernity: String
)
