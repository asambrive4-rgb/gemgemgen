// 역할: PromptSnippetStore의 Preferences DataStore 기반 상용구·카테고리 저장, 복원 및 JSON 직렬화 동작을 검증합니다.
package com.example.gemgemgen.automation.android

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.gemgemgen.automation.domain.PromptSnippet
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PromptSnippetStoreTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var testScope: CoroutineScope
    private lateinit var store: PromptSnippetStore
    private lateinit var testFile: File

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        testFile = temporaryFolder.newFile("test_prompt_snippets.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        store = PromptSnippetStore(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun load_initial_returnsEmptyList() {
        val snippets = store.load()
        assertTrue("초기 상용구 목록은 비어 있어야 합니다.", snippets.isEmpty())
    }

    @Test
    fun save_and_load_restoresAllFields() {
        val snippet = PromptSnippet(
            id = "snip-001",
            shortcut = "품질",
            content = "masterpiece, best quality, ultra-detailed",
            category = "스타일",
            createdAtMillis = 1700000000000L
        )
        store.save(listOf(snippet))

        val loaded = store.load()
        assertEquals(1, loaded.size)
        val item = loaded[0]
        assertEquals("snip-001", item.id)
        assertEquals("품질", item.shortcut)
        assertEquals("masterpiece, best quality, ultra-detailed", item.content)
        assertEquals("스타일", item.category)
        assertEquals(1700000000000L, item.createdAtMillis)
    }

    @Test
    fun saveCategories_and_loadCategories_preservesOrderAndDeduplicatesCaseInsensitively() {
        assertTrue(store.loadCategories().isEmpty())

        store.saveCategories(listOf("스타일", "조명", "카메라", "스타일", "  "))
        val loaded = store.loadCategories()
        assertEquals(listOf("스타일", "조명", "카메라"), loaded)
    }

    @Test
    fun save_multipleItems_preservesOrderAndContents() {
        val list = listOf(
            PromptSnippet(id = "1", shortcut = "구도", content = "wide angle, cinematic shot"),
            PromptSnippet(id = "2", shortcut = "조명", content = "golden hour, volumetric lighting"),
            PromptSnippet(id = "3", shortcut = "스타일", content = "oil painting on canvas")
        )
        store.save(list)

        val loaded = store.load()
        assertEquals(3, loaded.size)
        assertEquals("구도", loaded[0].shortcut)
        assertEquals("조명", loaded[1].shortcut)
        assertEquals("스타일", loaded[2].shortcut)
    }

    @Test
    fun saveAsync_and_flowFirst_worksAsynchronously() = runBlocking {
        val snippet = PromptSnippet(
            id = "async-1",
            shortcut = "비동기",
            content = "asynchronous flow test"
        )
        store.saveAsync(listOf(snippet))

        val fromFlow = store.snippets.first()
        assertEquals(1, fromFlow.size)
        assertEquals("비동기", fromFlow[0].shortcut)
        assertEquals("asynchronous flow test", fromFlow[0].content)
    }

    @Test
    fun save_emptyList_clearsSnippets() {
        val snippet = PromptSnippet(shortcut = "임시", content = "temp")
        store.save(listOf(snippet))
        assertEquals(1, store.load().size)

        store.save(emptyList())
        assertTrue(store.load().isEmpty())
    }
}
