package com.fvlaenix.translation.table

/**
 * Base type for a row of translation data.
 *
 * Implementations expose the source text to translate and hold the translated result
 * in a mutable backing list representing XLSX row cells.
 *
 * @property data Mutable list of cell values representing the row.
 * @property toTranslate Source text to be translated.
 * @property translate Target translation value; `null` when not yet translated.
 */
sealed class TranslationData {
  abstract val data: MutableList<String>
  abstract val toTranslate: String
  abstract var translate: String?

  /**
   * Row backed by two columns: a source column and a target column.
   *
   * The getter reads [toTranslate] from [sourceColumn]. The setter for [translate] ensures the
   * backing list grows as needed and writes an empty string when `null` is assigned.
   *
   * @param data Backing row storage.
   * @param sourceColumn Index of the source text column.
   * @param targetColumn Index of the target translation column.
   */
  data class TranslationSimpleData(
    override val data: MutableList<String>,
    private val sourceColumn: Int,
    private val targetColumn: Int
  ) : TranslationData() {
    override val toTranslate: String
      get() = data[sourceColumn]
    override var translate: String?
      get() = data.getOrNull(targetColumn)?.ifBlank { null }
      set(value) {
        while (data.size <= targetColumn) data.add("")
        data[targetColumn] = value ?: ""
      }
  }

  /**
   * Row backed by three columns: a name column, a source column, and a target column.
   *
   * Exposes [name] and [toTranslate] from their respective columns. The [translate] setter grows
   * the backing list as needed and writes an empty string when `null` is assigned.
   *
   * @param data Backing row storage.
   * @param nameColumn Index of the name column.
   * @param sourceColumn Index of the source text column.
   * @param targetColumn Index of the target translation column.
   */
  data class TranslationDataWithNameData(
    override val data: MutableList<String>,
    private val nameColumn: Int,
    private val sourceColumn: Int,
    private val targetColumn: Int
  ) : TranslationData() {
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