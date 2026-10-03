// 역할: 접근성 권한, 배터리 최적화 예외, 키보드 및 테마 설정 상태를 확인하고 변경하는 설정 팝업을 표시합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.ui.theme.AppConfirmDialogContent
import com.example.gemgemgen.ui.theme.AppDialogHostShell
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.AppThemeMode
import com.example.gemgemgen.ui.theme.AppThemePalette

enum class SettingsDialogStage {
    ACCESSIBILITY_PROMPT,
    SETTINGS
}

@Composable
internal fun SettingsDialogHost(
    uiState: AutomationUiState,
    actions: AutomationScreenActions,
    modifier: Modifier = Modifier
) {
    val activeStage = when {
        uiState.showAccessibilityPrompt -> SettingsDialogStage.ACCESSIBILITY_PROMPT
        uiState.showSettings -> SettingsDialogStage.SETTINGS
        else -> null
    } ?: return

    val onDismissRequest: () -> Unit = {
        when (activeStage) {
            SettingsDialogStage.ACCESSIBILITY_PROMPT -> actions.dismissAccessibilityPromptToSettings()
            SettingsDialogStage.SETTINGS -> actions.hideSettings()
        }
    }

    AppDialogHostShell(
        activeDialog = activeStage,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        contentKey = { it },
        label = "SettingsDialogHostCrossfade"
    ) { stage ->
        when (stage) {
            SettingsDialogStage.ACCESSIBILITY_PROMPT -> {
                AppConfirmDialogContent(
                    title = AutomationUiText.ACCESSIBILITY_PROMPT_TITLE,
                    message = AutomationUiText.ACCESSIBILITY_PROMPT_MESSAGE,
                    confirmLabel = "이동",
                    dismissLabel = "취소",
                    useFilledConfirmButton = true,
                    onConfirm = { actions.onConfirmAccessibilityPrompt() },
                    onDismiss = { actions.dismissAccessibilityPromptToSettings() }
                )
            }
            SettingsDialogStage.SETTINGS -> {
                StatusSettingsDialogContent(
                    uiState = uiState,
                    actions = actions
                )
            }
        }
    }
}

@Composable
private fun StatusSettingsDialogContent(
    uiState: AutomationUiState,
    actions: AutomationScreenActions,
    modifier: Modifier = Modifier
) {
    val status = uiState.environmentStatus
    val setupInfo = uiState.environmentSetupInfo
    val remoteStatus = uiState.remoteAutomationStatus

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "앱 및 환경 설정",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 화면 테마 모드 (시스템 설정 / 라이트 / 다크)
            Text(
                text = "🌓 화면 테마 모드",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val isModeSelected = mode == uiState.selectedThemeMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { actions.onSelectThemeMode(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isModeSelected) {
                            AppTheme.colors.card
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = if (isModeSelected) {
                            BorderStroke(2.dp, AppTheme.colors.accent)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (mode) {
                                    AppThemeMode.SYSTEM -> "시스템 (기본)"
                                    AppThemeMode.LIGHT -> "라이트"
                                    AppThemeMode.DARK -> "다크"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isModeSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isModeSelected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 테마 색상 팔레트 선택 섹션
            Text(
                text = "🎨 소프트 3D 테마 팔레트",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemePalette.entries.forEach { palette ->
                    val isSelected = palette == uiState.selectedThemePalette
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { actions.onSelectThemePalette(palette) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            AppTheme.colors.card
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = if (isSelected) {
                            BorderStroke(2.dp, palette.previewColor)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(palette.previewColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = palette.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = palette.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { actions.onSelectThemePalette(palette) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 원격 연결 섹션
            Text(
                text = "🌐 원격 연결",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            val isRemoteConnected = when (remoteStatus.mode) {
                AutomationMode.SENDER -> remoteStatus.isPaired && remoteStatus.discoveredDeviceName.isNotBlank()
                AutomationMode.RECEIVER -> remoteStatus.isPaired
                AutomationMode.NORMAL -> false
            }
            val connectedDeviceName = when (remoteStatus.mode) {
                AutomationMode.SENDER -> remoteStatus.discoveredDeviceName.ifBlank { "S25 FE" }
                AutomationMode.RECEIVER -> "태블릿"
                AutomationMode.NORMAL -> ""
            }
            val isRemoteRunning = remoteStatus.automationState is AutomationRunState.Running

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRemoteConnected) connectedDeviceName else "연결된 기기 없음",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRemoteConnected) "원격 연결됨" else "원격 연결 안 됨",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRemoteConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(isReady = isRemoteConnected)
                    }

                    if (isRemoteRunning) {
                        Text(
                            text = "원격 자동화를 중지한 뒤 연결을 끊어주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (uiState.remoteDisconnectMessage.isNotBlank()) {
                        Text(
                            text = uiState.remoteDisconnectMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.remoteDisconnectMessage == "원격 연결을 끊었습니다.") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = { actions.disconnectRemoteDevice() },
                        enabled = isRemoteConnected && !isRemoteRunning && !uiState.isDisconnectingRemote,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isDisconnectingRemote) "연결 끊는 중…" else "연결 끊기")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 자동화 편의 설정 (시작 시 클립보드 복사 등)
            Text(
                text = "📋 자동화 편의 설정",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "시작 시 프롬프트 클립보드 복사",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "자동화 시작 시 원본 프롬프트 템플릿을 클립보드에 보관합니다. (삼성 기기간 클립보드 공유 스낵바가 1회 발생할 수 있습니다.)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.copyPromptOnStart,
                        onCheckedChange = { actions.onToggleCopyPromptOnStart(it) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 시스템 환경 상태 섹션
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "환경 점검",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(onClick = { actions.refreshStatus() }) {
                    Text("새로고침")
                }
            }

            StatusRow("Gemini 앱", status.isGeminiInstalled)
            StatusRow("ChatGPT 앱", status.isChatGptInstalled)
            StatusRow("접근성 서비스", status.isAccessibilityServiceEnabled)
            StatusRow("WRITE_SECURE_SETTINGS", status.hasWriteSecureSettingsPermission)
            StatusRow("wildcard 폴더", status.isWildcardDirectoryAccessible)
            StatusRow("wildcard 편집 권한", status.isWildcardDirectoryWritable)
            StatusRow("프롬프트", uiState.hasPromptTemplate)

            if (!status.isWildcardDirectoryAccessible) {
                Text(
                    text = "Android 14 이상에서는 시스템 파일 선택기가 Download/Documents 하위 폴더를 차단할 수 있습니다. 기존 경로를 사용하려면 공유 저장소 접근을 허용하세요.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { actions.onOpenWildcardStorageSettings() }) {
                    Text("공유 저장소 접근 설정")
                }
            }

            if (!status.isAccessibilityServiceEnabled) {
                Button(onClick = { actions.onOpenAccessibilitySettings() }) {
                    Text("접근성 설정 열기")
                }
            }

            OutlinedButton(onClick = { actions.onSelectWildcardFolder() }) {
                Text("wildcard 폴더 선택")
            }
            TextButton(onClick = { actions.onSelectSafWildcardFolder() }) {
                Text("SAF로 허용된 다른 폴더 선택")
            }

            if (!status.hasWriteSecureSettingsPermission && setupInfo.adbGrantCommand.isNotBlank()) {
                Text(
                    text = "ADB 권한 명령어:\n${setupInfo.adbGrantCommand}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "wildcard 폴더: ${setupInfo.wildcardDirectoryPath.ifBlank { "선택 안 됨" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Null Keyboard 전환 대상: ${setupInfo.nullKeyboardTargetImeId}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { actions.hideSettings() }) {
                Text("닫기")
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, isReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        StatusBadge(isReady = isReady)
    }
}

@Composable
private fun StatusBadge(isReady: Boolean) {
    val containerColor = if (isReady) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val textColor = if (isReady) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = textColor
    ) {
        Text(
            text = if (isReady) "정상" else "필요",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
