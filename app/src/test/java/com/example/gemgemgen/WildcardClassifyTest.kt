package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardClassifyFileName
import com.example.gemgemgen.wildcard.domain.WildcardClassifyGroup
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResultPolicy
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResponseParser
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry
import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import com.example.gemgemgen.wildcard.usecase.SaveWildcardClassifyResultUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardClassifySaveResult
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WildcardClassifyTest {
    @Test
    fun fileName_stripsWhitespaceAndDangerousChars() {
        assertEquals("캐주얼톤.txt", WildcardClassifyFileName.fromGroupName("캐주얼 톤"))
        assertEquals("a_b.txt", WildcardClassifyFileName.fromGroupName("a/b"))
        assertEquals(null, WildcardClassifyFileName.fromGroupName("   "))
        assertEquals("custom.txt", WildcardClassifyFileName.normalizeUserInput("custom.txt"))
        assertEquals("custom.txt", WildcardClassifyFileName.normalizeUserInput("custom"))
    }

    @Test
    fun parseGroups_readsNameAndItems() {
        val groups = WildcardClassifyResponseParser.parseGroups(
            """
            {
              "groups": [
                { "name": "캐주얼", "items": ["red tee", "blue jeans"] },
                { "name": "포멀", "items": ["black suit"] }
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, groups.size)
        assertEquals("캐주얼", groups[0].name)
        assertEquals(listOf("red tee", "blue jeans"), groups[0].items)
        assertEquals(listOf("black suit"), groups[1].items)
    }

    @Test
    fun reconcile_dropsUnassignedWithoutUnclassifiedGroup() {
        val source = listOf("a", "b", "c", "a")
        val raw = listOf(
            WildcardClassifyGroup("G1", listOf("a", "invented", "b")),
            WildcardClassifyGroup("G2", listOf("a"))
        )

        val (groups, droppedLines) = WildcardClassifyResultPolicy.reconcile(source, raw)

        assertEquals(
            listOf(
                WildcardClassifyGroup("G1", listOf("a", "b")),
                WildcardClassifyGroup("G2", listOf("a"))
            ),
            groups
        )
        assertEquals(listOf("c"), droppedLines)
        assertTrue(groups.none { it.name == WildcardClassifyFileName.UNCLASSIFIED_GROUP_NAME })
    }

    @Test
    fun buildSaveEntries_suggestsUniqueFileNames() {
        val entries = WildcardClassifyFileName.buildSaveEntries(
            listOf(
                WildcardClassifyGroup("캐주얼", listOf("x")),
                WildcardClassifyGroup("캐 주 얼", listOf("y"))
            )
        )
        assertEquals("캐주얼", entries[0].fileNameInput)
        assertEquals("캐주얼_2", entries[1].fileNameInput)
    }

    @Test
    fun save_usesEditedFileNamesAndReportsConflicts() = runBlocking {
        val repo = FakeRepo("캐주얼.txt" to "old")
        val useCase = SaveWildcardClassifyResultUseCase(
            repository = repo,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )
        val entries = listOf(
            WildcardClassifySaveEntry("캐주얼", listOf("x"), "캐주얼"),
            WildcardClassifySaveEntry("포멀", listOf("y"), "my_formal")
        )

        val conflict = useCase.save(entries, overwrite = false)
        assertTrue(conflict is WildcardClassifySaveResult.FileExists)

        val saved = useCase.save(entries, overwrite = true)
        assertEquals(
            WildcardClassifySaveResult.Success(listOf("캐주얼.txt", "my_formal.txt")),
            saved
        )
        assertEquals("x", repo.contentOf("캐주얼.txt"))
        assertEquals("y", repo.contentOf("my_formal.txt"))
    }

    private class FakeRepo(
        vararg initial: Pair<String, String>
    ) : WildcardFileRepository {
        private val files = linkedMapOf<String, String>()

        init {
            initial.forEach { (name, text) -> files[name] = text }
        }

        fun contentOf(name: String): String = files[name].orEmpty()

        override fun listFiles(): List<WildcardTextFile> =
            files.keys.map { WildcardTextFile(id = it, fileName = it) }

        override fun readFile(file: WildcardTextFile): String =
            files[file.fileName] ?: throw WildcardFileException("없음")

        override fun createFile(fileName: String): WildcardTextFile {
            files[fileName] = ""
            return WildcardTextFile(id = fileName, fileName = fileName)
        }

        override fun writeFile(file: WildcardTextFile, text: String) {
            files[file.fileName] = text
        }

        override fun deleteFile(file: WildcardTextFile) {
            files.remove(file.fileName)
        }

        override fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile {
            val text = files.remove(file.fileName).orEmpty()
            files[newName] = text
            return WildcardTextFile(id = newName, fileName = newName)
        }
    }
}
