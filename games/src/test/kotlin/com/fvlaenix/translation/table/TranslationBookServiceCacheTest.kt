package com.fvlaenix.translation.table

import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.TestConstants
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.textmodel.TestTextModelService
import com.fvlaenix.translation.translator.TextModelTranslator
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@DisplayName("TranslationBookService Cache Tests")
class TranslationBookServiceCacheTest {

  @TempDir
  lateinit var tempDir: Path

  private lateinit var testTextModelService: TestTextModelService
  private lateinit var namesService: NamesService

  @BeforeEach
  fun setUp() {
    testTextModelService = TestTextModelService()
    testTextModelService.reset()

    setupCacheTestResponses()

    namesService = NamesService(
      mapOf(
        "TestCharacter" to "Тестовый персонаж"
      )
    )
  }

  private fun createTranslationService(path: Path, gameId: String): TranslationBookService {
    return TranslationBookService(
      path = path,
      language = "RU",
      gameId = gameId,
      namesService = namesService,
      dialogProvider = ProvidersCollection(emptyList()),
      translator = TextModelTranslator(testTextModelService, textPrompt = TestConstants.TEST_PROMPT)
    )
  }

  private fun setupCacheTestResponses() {
    testTextModelService.apply {
      setTestResponse("New text 1", "Новый текст 1")
      setTestResponse("New text 2", "Новый текст 2")
      setTestResponse("New text 3", "Новый текст 3")
      setTestResponse("Conflicting text", "Конфликтный текст (новый)")
      setTestResponse("Batch text A", "Пакетный текст А")
      setTestResponse("Batch text B", "Пакетный текст Б")

      setTestBatchResponse(
        listOf("New text 1", "New text 2"),
        listOf("Новый текст 1", "Новый текст 2")
      )
      setTestBatchResponse(
        listOf("New text 1", "New text 2", "New text 3"),
        listOf("Новый текст 1", "Новый текст 2", "Новый текст 3")
      )
      setTestBatchResponse(
        listOf("Persistent text 1", "Persistent text 2", "Persistent text 3"),
        listOf("Постоянный текст 1", "Постоянный текст 2", "Постоянный текст 3")
      )

      setTestResponse("Persistent text 1", "Постоянный текст 1")
      setTestResponse("Persistent text 2", "Постоянный текст 2")
      setTestResponse("Persistent text 3", "Постоянный текст 3")
    }
  }

  @Test
  @DisplayName("Cache merging with overlapping keys - source cache should override target")
  fun testCacheMergingOverlappingKeysSourceWins() = runTest {
    val firstDir = tempDir.resolve("first")
    firstDir.createDirectories()

    val firstFile = firstDir.resolve("translations.xlsx")
    createTestFile(
      firstFile, listOf(
        "Overlapping text 1" to "Перекрывающийся текст 1 (первый)",
        "Overlapping text 2" to "Перекрывающийся текст 2 (первый)",
        "Unique to first" to "Уникальный для первого"
      )
    )

    val firstService = createTranslationService(firstDir, "cache-test-1")

    val secondDir = tempDir.resolve("second")
    secondDir.createDirectories()

    val secondFile = secondDir.resolve("translations.xlsx")
    createTestFile(
      secondFile, listOf(
        "Overlapping text 1" to "Перекрывающийся текст 1 (второй)",
        "Overlapping text 2" to "Перекрывающийся текст 2 (второй)",
        "Unique to second" to "Уникальный для второго"
      )
    )

    val secondService = createTranslationService(secondDir, "cache-test-2")

    secondService.addToCache(firstService)

    val mergedCache = secondService.cache

    assertThat(mergedCache["Overlapping text 1"]).isEqualTo("Перекрывающийся текст 1 (первый)")
    assertThat(mergedCache["Overlapping text 2"]).isEqualTo("Перекрывающийся текст 2 (первый)")

    assertThat(mergedCache["Unique to first"]).isEqualTo("Уникальный для первого")
    assertThat(mergedCache["Unique to second"]).isEqualTo("Уникальный для второго")

    assertThat(mergedCache).hasSize(4)
  }

  @Test
  @DisplayName("Cache merging preserves non-overlapping entries")
  fun testCacheMergingPreservesNonOverlapping() = runTest {
    val serviceADir = tempDir.resolve("serviceA")
    serviceADir.createDirectories()

    val fileA = serviceADir.resolve("setA.xlsx")
    createTestFile(
      fileA, listOf(
        "Text A1" to "Текст А1",
        "Text A2" to "Текст А2",
        "Text A3" to "Текст А3"
      )
    )

    val serviceA = createTranslationService(serviceADir, "set-A")

    val serviceBDir = tempDir.resolve("serviceB")
    serviceBDir.createDirectories()

    val fileB = serviceBDir.resolve("setB.xlsx")
    createTestFile(
      fileB, listOf(
        "Text B1" to "Текст Б1",
        "Text B2" to "Текст Б2",
        "Text B3" to "Текст Б3"
      )
    )

    val serviceB = createTranslationService(serviceBDir, "set-B")

    serviceA.addToCache(serviceB)

    val mergedCache = serviceA.cache

    assertThat(mergedCache["Text A1"]).isEqualTo("Текст А1")
    assertThat(mergedCache["Text A2"]).isEqualTo("Текст А2")
    assertThat(mergedCache["Text A3"]).isEqualTo("Текст А3")

    assertThat(mergedCache["Text B1"]).isEqualTo("Текст Б1")
    assertThat(mergedCache["Text B2"]).isEqualTo("Текст Б2")
    assertThat(mergedCache["Text B3"]).isEqualTo("Текст Б3")

    assertThat(mergedCache).hasSize(6)
  }

  @Test
  @DisplayName("Cache merging with partial overlaps")
  fun testCacheMergingPartialOverlaps() = runTest {
    val initialDir = tempDir.resolve("initial")
    initialDir.createDirectories()

    val initialFile = initialDir.resolve("initial.xlsx")
    createTestFile(
      initialFile, listOf(
        "Common text 1" to "Общий текст 1 (исходный)",
        "Common text 2" to "Общий текст 2 (исходный)",
        "Initial unique 1" to "Начальный уникальный 1",
        "Initial unique 2" to "Начальный уникальный 2"
      )
    )

    val initialService = createTranslationService(initialDir, "initial")

    val updateDir = tempDir.resolve("update")
    updateDir.createDirectories()

    val updateFile = updateDir.resolve("update.xlsx")
    createTestFile(
      updateFile, listOf(
        "Common text 1" to "Общий текст 1 (обновленный)",
        "Common text 3" to "Общий текст 3 (новый)",
        "Update unique 1" to "Обновленный уникальный 1",
        "Update unique 2" to "Обновленный уникальный 2"
      )
    )

    val updateService = createTranslationService(updateDir, "update")

    initialService.addToCache(updateService)

    val mergedCache = initialService.cache

    assertThat(mergedCache["Common text 1"]).isEqualTo("Общий текст 1 (обновленный)")

    assertThat(mergedCache["Common text 2"]).isEqualTo("Общий текст 2 (исходный)")
    assertThat(mergedCache["Initial unique 1"]).isEqualTo("Начальный уникальный 1")
    assertThat(mergedCache["Initial unique 2"]).isEqualTo("Начальный уникальный 2")

    assertThat(mergedCache["Common text 3"]).isEqualTo("Общий текст 3 (новый)")
    assertThat(mergedCache["Update unique 1"]).isEqualTo("Обновленный уникальный 1")
    assertThat(mergedCache["Update unique 2"]).isEqualTo("Обновленный уникальный 2")

    assertThat(mergedCache).hasSize(7)
  }

  @Test
  @DisplayName("Cache merging affects translation behavior")
  fun testCacheMergingAffectsTranslation() = runTest {
    val oldDir = tempDir.resolve("old")
    oldDir.createDirectories()

    val oldFile = oldDir.resolve("old_translations.xlsx")
    createTestFile(
      oldFile, listOf(
        "Translate me" to "Переведи меня (старый)",
        "Another text" to "Другой текст (старый)"
      )
    )

    val oldService = createTranslationService(oldDir, "old-service")

    val newDir = tempDir.resolve("new")
    newDir.createDirectories()

    val newFile = newDir.resolve("new_translations.xlsx")
    createTestFile(
      newFile, listOf(
        "Translate me" to null,
        "Another text" to null,
        "New text 1" to null,
        "New text 2" to null
      )
    )

    // PHASE 3: Setup AI responses for only the new items (not cached ones)
    testTextModelService.reset()
    testTextModelService.apply {
      setTestResponse("New text 1", "Новый текст 1")
      setTestResponse("New text 2", "Новый текст 2")
      setTestBatchResponse(
        listOf("New text 1", "New text 2"),
        listOf("Новый текст 1", "Новый текст 2")
      )
    }

    val newService = createTranslationService(newDir, "new-service")

    newService.addToCache(oldService)

    newService.translate()

    val finalOutputDir = tempDir.resolve("final_output")
    newService.write(finalOutputDir)

    val finalFile = finalOutputDir.resolve("new_translations.xlsx")
    val resultBook = TranslationBook(finalFile.inputStream(), finalFile.fileName)

    assertThat(resultBook.translationBook[0].toTranslate).isEqualTo("Translate me")
    assertThat(resultBook.translationBook[0].translate).isEqualTo("Переведи меня (старый)")

    assertThat(resultBook.translationBook[1].toTranslate).isEqualTo("Another text")
    assertThat(resultBook.translationBook[1].translate).isEqualTo("Другой текст (старый)")

    assertThat(resultBook.translationBook[2].toTranslate).isEqualTo("New text 1")
    assertThat(resultBook.translationBook[2].translate).isEqualTo("Новый текст 1")

    assertThat(resultBook.translationBook[3].toTranslate).isEqualTo("New text 2")
    assertThat(resultBook.translationBook[3].translate).isEqualTo("Новый текст 2")

  }

  @Test
  @DisplayName("Cache persistence across service restarts")
  fun testCachePersistenceAcrossRestarts() = runTest {
    val workingDir = tempDir.resolve("persistent")
    workingDir.createDirectories()

    val initialFile = workingDir.resolve("persistent.xlsx")
    createTestFile(
      initialFile, listOf(
        "Persistent text 1" to null,
        "Persistent text 2" to null,
        "Persistent text 3" to null
      )
    )

    // Setup responses for initial translation
    testTextModelService.apply {
      setTestResponse("Persistent text 1", "Постоянный текст 1")
      setTestResponse("Persistent text 2", "Постоянный текст 2")
      setTestResponse("Persistent text 3", "Постоянный текст 3")
      setTestBatchResponse(
        listOf("Persistent text 1", "Persistent text 2", "Persistent text 3"),
        listOf("Постоянный текст 1", "Постоянный текст 2", "Постоянный текст 3")
      )
    }

    val firstSession = createTranslationService(workingDir, "persistent")

    firstSession.translate()
    firstSession.write(workingDir)

    testTextModelService.reset()

    val secondSession = createTranslationService(workingDir, "persistent")

    val cache = secondSession.cache
    assertThat(cache["Persistent text 1"]).isEqualTo("Постоянный текст 1")
    assertThat(cache["Persistent text 2"]).isEqualTo("Постоянный текст 2")
    assertThat(cache["Persistent text 3"]).isEqualTo("Постоянный текст 3")

    secondSession.translate()
  }

  @Test
  @DisplayName("Large cache merging performance")
  fun testLargeCacheMergingPerformance() = runTest {
    val largeCacheSize = 1000

    val largeDir = tempDir.resolve("large_cache")
    largeDir.createDirectories()

    val largeFile = largeDir.resolve("large.xlsx")
    val largeData = (1..largeCacheSize).map { i ->
      "Large cache text $i" to "Большой кэш текст $i"
    }
    createTestFile(largeFile, largeData)

    val largeService = createTranslationService(largeDir, "large-cache")

    val smallDir = tempDir.resolve("small_cache")
    smallDir.createDirectories()

    val smallFile = smallDir.resolve("small.xlsx")
    val smallData = (1..100).map { i ->
      "Small cache text $i" to "Маленький кэш текст $i"
    }
    createTestFile(smallFile, smallData)

    val smallService = createTranslationService(smallDir, "small-cache")

    val mergeTime = kotlin.system.measureTimeMillis {
      smallService.addToCache(largeService)
    }

    println("Merging cache of size $largeCacheSize took: ${mergeTime}ms")

    val mergedCache = smallService.cache
    assertThat(mergedCache).hasSize(largeCacheSize + 100)

    assertThat(mergedCache["Large cache text 1"]).isEqualTo("Большой кэш текст 1")
    assertThat(mergedCache["Large cache text 500"]).isEqualTo("Большой кэш текст 500")
    assertThat(mergedCache["Small cache text 1"]).isEqualTo("Маленький кэш текст 1")
    assertThat(mergedCache["Small cache text 50"]).isEqualTo("Маленький кэш текст 50")

    assertThat(mergeTime).isLessThan(5000)
  }

  private fun createTestFile(file: Path, data: List<Pair<String, String?>>) {
    file.createFile()
    val workbook = XSSFWorkbook().apply {
      createSheet("Test").apply {
        createRow(0).apply {
          createCell(0).setCellValue("fvlaenix-magic-words")
          createCell(1).setCellValue("SRPG")
          createCell(2).setCellValue("SRPG")
        }
        createRow(1).apply {
          createCell(0).setCellValue("totranslate")
          createCell(1).setCellValue("translated")
        }

        data.forEachIndexed { index, (original, translation) ->
          createRow(index + 2).apply {
            createCell(0).setCellValue(original)
            if (translation != null) {
              createCell(1).setCellValue(translation)
            }
          }
        }
      }
    }
    file.outputStream().use { workbook.write(it) }
  }
}