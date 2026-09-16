// 역할: Gemini 및 Grok 프로바이더와 지원 모델 목록을 칩 형태로 선택하는 공통 UI 컴포넌트입니다.
package com.example.gemgemgen.analysis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_5_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_6_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_7_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_8_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
import com.example.gemgemgen.ui.theme.NeuPillChip

private data class ModelChipItem(
    val modelId: String,
    val label: String
)

private val GEMINI_MODELS = listOf(
    ModelChipItem(MODEL_GEMINI_3_5_FLASH_LITE, "3.5 Lite"),
    ModelChipItem(MODEL_GEMINI_3_6_FLASH, "3.6 Flash"),
    ModelChipItem(MODEL_GEMINI_3_7_FLASH, "3.7 Flash"),
    ModelChipItem(MODEL_GEMINI_3_8_FLASH, "3.8 Flash")
)

private val GROK_MODELS = listOf(
    ModelChipItem(MODEL_GROK_4_5, "Grok 4.5")
)

/**
 * Gemini 및 Grok 프로바이더와 지원 모델 목록을 FlowRow 칩 형태로 표시하고 선택할 수 있는 공통 Composable입니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelSelectorChips(
    selectedProvider: AnalysisProvider,
    selectedModelId: String,
    onSelectProvider: (AnalysisProvider) -> Unit,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp)
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        NeuPillChip(
            text = "Gemini",
            selected = selectedProvider == AnalysisProvider.GEMINI,
            onClick = { onSelectProvider(AnalysisProvider.GEMINI) }
        )
        NeuPillChip(
            text = "Grok",
            selected = selectedProvider == AnalysisProvider.GROK,
            onClick = { onSelectProvider(AnalysisProvider.GROK) }
        )
        val models = when (selectedProvider) {
            AnalysisProvider.GEMINI -> GEMINI_MODELS
            AnalysisProvider.GROK -> GROK_MODELS
        }
        models.forEach { item ->
            NeuPillChip(
                text = item.label,
                selected = selectedModelId == item.modelId,
                onClick = { onSelectModel(item.modelId) }
            )
        }
    }
}
