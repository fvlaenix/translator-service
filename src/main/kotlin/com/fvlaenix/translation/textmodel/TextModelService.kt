package com.fvlaenix.translation.textmodel

interface TextModelService {
  suspend fun fractionOfTokenLimit(text: String): Float

  /**
   * Sends a request to text model for processing
   * @param prompt The prompt to send text model
   * @param systemMessage Optional system message to set context
   * @return The response from text model
   */
  suspend fun sendRequest(prompt: String, systemMessage: String? = null): String

  /**
   * Sends a batch of requests to text model for processing
   * @param prompts List of prompts to send to text model
   * @param systemMessage Optional system message to set context
   * @return List of responses from text model
   */
  suspend fun sendBatchRequest(prompts: List<String>, systemMessage: String? = null): List<String>
}