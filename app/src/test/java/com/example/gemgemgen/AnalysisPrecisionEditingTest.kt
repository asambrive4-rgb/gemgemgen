package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisCascadingTrace
import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisCategoryRules
import com.example.gemgemgen.analysis.domain.AnalysisEditPolicy
import com.example.gemgemgen.analysis.domain.AnalysisParseException
import com.example.gemgemgen.analysis.domain.AnalysisPromptBuilder
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisResponseParser
import com.example.gemgemgen.analysis.domain.AnalysisSourceRange
import com.example.gemgemgen.analysis.domain.AnalysisSourceLocator
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.analysis.domain.AnalysisTargetSource
import com.example.gemgemgen.analysis.domain.AnalysisTextEdit
import com.example.gemgemgen.analysis.domain.AnalysisVisualContext
import com.example.gemgemgen.analysis.domain.AnalysisVisualRules
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPrecisionEditingTest {
    private val source = "System Instruction\r\nKeep identity.\r\n\r\n상반신 촬영.\r\n파란 옷과 헤어 유지.\r\n발과 바닥은 보이지 않는다."
    private fun range(text: String) = AnalysisSourceRange(source.indexOf(text), source.indexOf(text) + text.length, text)
    private val primary = range("상반신 촬영.")
    private val conflict = range("발과 바닥은 보이지 않는다.")
    private val protected = range("System Instruction\r\nKeep identity.\r\n\r\n")
    private val target = AnalysisTargetSegment(primary.exactText, primary.startIndex, primary.endIndex,
        AnalysisTargetSource.AUTO, AnalysisCategory.FREE_EDIT)
    private val report = AnalysisReport(
        visualContext = AnalysisVisualContext(visibleScope = "상반신"),
        targetVisualContext = AnalysisVisualContext(visibleScope = "전신"),
        cascadingTrace = AnalysisCascadingTrace(
            newlyVisible = listOf("발, 지지면"), disappearing = listOf("허리 잘림"),
            undefinedAreas = listOf("신발"), requiredAdjustments = listOf("지지 관계"),
            conflictingSegments = listOf(conflict)
        ),
        preservedSegments = listOf(protected)
    )

    @Test fun scatteredEdits_preserveInterveningTextAndCrLf() {
        val envelope = AnalysisEditPolicy.envelope(source, target, report)
        val candidate = AnalysisEditPolicy.assemble(source, envelope, listOf(
            AnalysisTextEdit(conflict, "발과 지지면이 보인다."),
            AnalysisTextEdit(primary, "전신 촬영.")
        ), report)
        assertEquals("전신 촬영.\r\n파란 옷과 헤어 유지.\r\n발과 지지면이 보인다.", candidate)
        assertEquals(protected.exactText + candidate,
            AnalysisTargetSegmentPolicy.replaceSegmentWithText(source, envelope, candidate))
    }

    @Test fun tightening_deletesObsoleteFootwearInstruction() {
        val envelope = AnalysisEditPolicy.envelope(source, target, report)
        val result = AnalysisEditPolicy.assemble(source, envelope, listOf(
            AnalysisTextEdit(primary, "얼굴 중심 촬영."), AnalysisTextEdit(conflict, "")
        ), report)
        assertEquals("얼굴 중심 촬영.\r\n파란 옷과 헤어 유지.\r\n", result)
    }

    @Test fun insertionAtEnd_preservesOriginalCharacters() {
        val atEnd = AnalysisSourceRange(primary.endIndex, primary.endIndex, "")
        assertEquals(primary.exactText + " 시선은 정면.", AnalysisEditPolicy.assemble(source, target,
            listOf(AnalysisTextEdit(atEnd, " 시선은 정면.")), AnalysisReport()))
    }

    @Test fun overlappingAnalysisRanges_mergeWithoutIncludingUnrelatedGaps() {
        val overlapping = AnalysisSourceRange(primary.startIndex + 2, primary.endIndex,
            source.substring(primary.startIndex + 2, primary.endIndex))
        val nextReport = report.copy(cascadingTrace = AnalysisCascadingTrace(conflictingSegments = listOf(overlapping)))
        val envelope = AnalysisEditPolicy.envelope(source, target, nextReport)
        assertEquals(listOf(primary), envelope.editableRanges)
        assertEquals("전신 촬영.", AnalysisEditPolicy.assemble(source, envelope,
            listOf(AnalysisTextEdit(primary, "전신 촬영.")), nextReport))
    }

    @Test fun partialConflictEdit_doesNotRequireWholeSentenceReplacement() {
        val result = AnalysisEditPolicy.assemble(source, AnalysisEditPolicy.envelope(source, target, report),
            listOf(AnalysisTextEdit(primary, "전신 촬영."),
                AnalysisTextEdit(range("보이지 않는다"), "보인다")), report)
        assertEquals("전신 촬영.\r\n파란 옷과 헤어 유지.\r\n발과 바닥은 보인다.", result)
    }

    @Test fun refiningSentenceBoundary_doesNotRequireExactAnalysisRange() {
        val envelope = AnalysisEditPolicy.envelope(source, target, report)
        val result = AnalysisEditPolicy.assemble(source, envelope, listOf(
            AnalysisTextEdit(range("상반신 촬영.\r\n"), "전신 촬영.\r\n"),
            AnalysisTextEdit(conflict, "발이 보인다.")
        ), report)
        assertEquals("전신 촬영.\r\n파란 옷과 헤어 유지.\r\n발이 보인다.", result)
    }

    @Test fun duplicateIdenticalEdits_areAppliedOnce() {
        val edit = AnalysisTextEdit(primary.copy(startIndex = -1, endIndex = -1), "전신 촬영.")
        assertEquals("전신 촬영.", AnalysisEditPolicy.assemble(source, target,
            listOf(edit, edit), AnalysisReport()))
    }

    @Test fun protectedSection_cannotBeEditedEvenInsideTarget() {
        val full = target.copy(text = source, startIndex = 0, endIndex = source.length)
        assertThrows(IllegalArgumentException::class.java) {
            AnalysisEditPolicy.assemble(source, full,
                listOf(AnalysisTextEdit(protected, "")), report.copy(cascadingTrace = AnalysisCascadingTrace()))
        }
    }

    @Test fun overlappingOrWrongSourceEdits_areRejected() {
        listOf(
            listOf(AnalysisTextEdit(primary, "전신"), AnalysisTextEdit(primary, "얼굴")),
            listOf(AnalysisTextEdit(primary.copy(exactText = "잘못된 원문"), "전신"))
        ).forEach { edits ->
            assertThrows(IllegalArgumentException::class.java) {
                AnalysisEditPolicy.assemble(source, target, edits, AnalysisReport())
            }
        }
    }

    @Test fun otherCategories_doNotSilentlyIgnoreOutsideConflicts() {
        assertThrows(IllegalArgumentException::class.java) {
            AnalysisEditPolicy.envelope(source, target.copy(category = AnalysisCategory.COMPOSITION), report)
        }
    }

    @Test fun parser_preservesEditWhitespaceAndRejectsMissingReplacement() {
        val edits = AnalysisResponseParser.parseEditCandidates(
            """[{"edits":[{"startIndex":0,"endIndex":3,"exactText":" a ","replacement":" b "}]}]"""
        )
        assertEquals(" a ", edits.single().single().range.exactText)
        assertEquals(" b ", edits.single().single().replacement)
        assertThrows(AnalysisParseException::class.java) {
            AnalysisResponseParser.parseEditCandidates(
                """[{"edits":[{"startIndex":0,"endIndex":1,"exactText":"a"}]}]"""
            )
        }
    }

    @Test fun reportParser_carriesAllFiveChecksAndTargetScope() {
        val parsed = AnalysisResponseParser.parseReport("""{
            "visualContext":{"visibleScope":"상반신"},
            "targetVisualContext":{"visibleScope":"전신"},
            "cascadingTrace":{
              "newlyVisible":["발"],"disappearing":["허리 잘림"],
              "undefinedAreas":["신발"],"requiredAdjustments":["지지 관계"],
              "conflictingSegments":[{"startIndex":0,"endIndex":1,"exactText":"a"}]
            },
            "preservedSegments":[],"clarificationQuestion":"신발을 유지할까요?"
        }""", "a")
        assertEquals("상반신", parsed.visualContext.visibleScope)
        assertEquals("전신", parsed.targetVisualContext.visibleScope)
        assertEquals(listOf("발"), parsed.cascadingTrace.newlyVisible)
        assertEquals(listOf("허리 잘림"), parsed.cascadingTrace.disappearing)
        assertEquals(listOf("신발"), parsed.cascadingTrace.undefinedAreas)
        assertEquals(listOf("지지 관계"), parsed.cascadingTrace.requiredAdjustments)
        assertEquals("a", parsed.cascadingTrace.conflictingSegments.single().exactText)
        assertEquals("신발을 유지할까요?", parsed.clarificationQuestion)
    }

    @Test fun repeatedSourceText_usesReportedOccurrence() {
        val parsed = AnalysisResponseParser.parseReport("""{
            "targetSegment":{"exactText":"a","occurrence":2}
        }""", "a a")
        assertEquals(2, parsed.targetSegment?.startIndex)
    }

    @Test fun wrongOrMissingIndices_areResolvedFromTextInAllResponseRanges() {
        val original = "😀 처음\r\n상반신  촬영.\r\n발은 숨김."
        val parsed = AnalysisResponseParser.parseReport("""{
            "targetSegment":{"exactText":"상반신 촬영.","startIndex":999,"endIndex":1000},
            "cascadingTrace":{"conflictingSegments":[{"exactText":"발은 숨김."}]},
            "preservedSegments":[{"exactText":"😀 처음","startIndex":3,"endIndex":99}]
        }""", original)
        assertEquals(original.indexOf("상반신"), parsed.targetSegment?.startIndex)
        assertEquals("상반신  촬영.", parsed.targetSegment?.exactText)
        assertEquals(original.indexOf("발은"), parsed.cascadingTrace.conflictingSegments.single().startIndex)
        assertEquals(0, parsed.preservedSegments.single().startIndex)
        val detected = AnalysisTargetSegmentPolicy.fromAutoReport(parsed, AnalysisCategory.FREE_EDIT)!!
        val envelope = AnalysisEditPolicy.envelope(original, detected, parsed)
        val edits = AnalysisResponseParser.parseEditCandidates("""[{"edits":[
            {"exactText":"상반신 촬영.","startIndex":9,"endIndex":10,"replacement":"전신 촬영."},
            {"exactText":"발은 숨김.","replacement":"발과 지지면이 보임."}
        ]}]""").single()
        val candidate = AnalysisEditPolicy.assemble(original, envelope, edits, parsed)
        assertEquals("😀 처음\r\n전신 촬영.\r\n발과 지지면이 보임.",
            AnalysisTargetSegmentPolicy.replaceSegmentWithText(original, envelope, candidate))
    }

    @Test fun whitespaceDifferences_resolveToOriginalTextWithoutNormalizingSource() {
        val original = "앞\r\n상반신\t  촬영.\u00a0표정 유지.\r\n뒤"
        val actual = AnalysisSourceLocator.resolve(original,
            AnalysisSourceRange(-1, -1, "상반신 촬영.\n표정 유지."))
        assertEquals("상반신\t  촬영.\u00a0표정 유지.", actual.exactText)
        assertEquals(original.indexOf("상반신"), actual.startIndex)
        assertEquals(original.indexOf("\r\n뒤"), actual.endIndex)
    }

    @Test fun ambiguousPhrase_requiresContextOrOccurrence() {
        val repeated = "정면 촬영. 그리고 정면 촬영."
        val request = AnalysisSourceRange(-1, -1, "정면 촬영.")
        assertThrows(IllegalArgumentException::class.java) { AnalysisSourceLocator.resolve(repeated, request) }
        assertEquals(repeated.lastIndexOf("정면"),
            AnalysisSourceLocator.resolve(repeated, request.copy(occurrence = 2)).startIndex)
    }

    @Test fun insertionUsesQuotedAnchorWithoutCharacterCounting() {
        val edits = AnalysisResponseParser.parseEditCandidates("""[{"edits":[{
            "exactText":"상반신 촬영.","replacement":"상반신 촬영. 시선은 정면."
        }]}]""").single()
        assertEquals("상반신 촬영. 시선은 정면.",
            AnalysisEditPolicy.assemble(source, target, edits, AnalysisReport()))
    }

    @Test fun bothStages_receiveSharedAndCategoryRules_withoutSummaryDependency() {
        listOf(AnalysisCategory.FREE_EDIT, AnalysisCategory.COMPOSITION, AnalysisCategory.CAMERA_TEXTURE,
            AnalysisCategory.WOMEN_POSE, AnalysisCategory.WOMEN_EXPRESSION).forEach { category ->
            val analysis = AnalysisPromptBuilder.buildAnalysisPrompt(source, category)
            val generation = AnalysisPromptBuilder.buildTxtPrompt(source, category, target.copy(category = category),
                report, 2, emptyList())
            listOf(analysis.systemInstruction, generation.systemInstruction).forEach { text ->
                assertTrue(text.contains(AnalysisVisualRules.instructions))
                val rule = AnalysisCategoryRules.ruleFor(category)
                assertTrue(text.contains(rule.required))
                assertTrue(text.contains(rule.avoid))
            }
            assertTrue(generation.systemInstruction.contains("Target visual context:"))
            val fields = generation.responseSchema["items"]!!.jsonObject["properties"]!!.jsonObject
            assertEquals(category == AnalysisCategory.FREE_EDIT, fields.containsKey("edits"))
            assertEquals(category != AnalysisCategory.FREE_EDIT, fields.containsKey("text"))
        }
    }
}
