package com.fvlaenix.translation.table

sealed class TranslationData {
  abstract val data: MutableList<String>
  abstract val toTranslate: String
  abstract var translate: String?
  
  data class TranslationSimpleData(override val data: MutableList<String>, private val sourceColumn: Int, private val targetColumn: Int): TranslationData() {
    override val toTranslate: String
      get() = data[sourceColumn]
    override var translate: String?
      get() = data.getOrNull(targetColumn)?.ifBlank { null }
      set(value) {
        while (data.size <= targetColumn) data.add("")
        data[targetColumn] = value ?: ""
      }
  }
  
  data class TranslationDataWithNameData(
    override val data: MutableList<String>,
    private val nameColumn: Int,
    private val sourceColumn: Int,
    private val targetColumn: Int
  ): TranslationData() {
    val name: String
      get() = data.getOrNull(nameColumn) ?: ""
    override val toTranslate: String
      get() = data[sourceColumn]
    override var translate: String?
      get() = data.getOrNull(targetColumn)?.ifBlank { null }
      set(value) {
        while (data.size <= targetColumn) data.add("")
        data[targetColumn] = value ?: ""
      }
  }
}