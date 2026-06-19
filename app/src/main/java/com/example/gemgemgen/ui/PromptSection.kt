package com.example.gemgemgen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationTargetApp

@Composable
internal fun PromptSection(
    promptTemplate: String,
    selectedTargetApp: AutomationTargetApp,
    isTargetSelectionEnabled: Boolean,
    onTargetAppSelected: (AutomationTargetApp) -> Unit,
    onPromptTemplateChange: (String) -> Unit,
    onImportFromClipboard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "프롬프트 템플릿",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AutomationTargetApp.entries.forEach { targetApp ->
                    TargetAppButton(
                        targetApp = targetApp,
                        selected = selectedTargetApp == targetApp,
                        enabled = isTargetSelectionEnabled,
                        onClick = { onTargetAppSelected(targetApp) }
                    )
                }
            }
            OutlinedButton(
                onClick = onImportFromClipboard,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "가져오기",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        AppMultilineTextField(
            value = promptTemplate,
            onValueChange = onPromptTemplateChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6
        )
    }
}

@Composable
private fun TargetAppButton(
    targetApp: AutomationTargetApp,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .height(28.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = targetApp.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

