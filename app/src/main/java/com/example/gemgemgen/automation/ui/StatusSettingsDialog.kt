// 역할: 자동화 편의 설정(클립보드·사이드바·메모리 정리), 원격 연결·재시도, 화면 테마 및 표준 회색 알약 버튼 기반 시스템 환경 점검 상태를 확인하고 변경하는 설정 팝업을 표시합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.automation.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.ui.theme.AppConfirmDialogContent
import com.example.gemgemgen.ui.theme.AppDialogHostShell
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuPillButton
import com.example.gemgemgen.ui.theme.AppThemeMode
import com.example.gemgemgen.ui.theme.AppThemePalette
import com.example.gemgemgen.ui.theme.LocalAppThemeMode
import com.example.gemgemgen.ui.theme.LocalAppThemePalette

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
            color = AppTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 자동화 편의 설정 (시작 시 클립보드 복사 등)
            Text(
                text = "📋 자동화 편의 설정",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AppTheme.colors.insetBed
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
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "자동화 시작 시 원본 프롬프트 템플릿을 클립보드에 보관합니다. (삼성 기기간 클립보드 공유 스낵바가 1회 발생할 수 있습니다.)",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                    Switch(
                        checked = uiState.copyPromptOnStart,
                        onCheckedChange = { actions.onToggleCopyPromptOnStart(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppTheme.colors.card,
                            checkedTrackColor = AppTheme.colors.primary,
                            uncheckedThumbColor = AppTheme.colors.card,
                            uncheckedTrackColor = AppTheme.colors.cardBorder,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AppTheme.colors.insetBed
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
                            text = "사이드바 열림 상태 자동화 (Gemini)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "태블릿 가로 화면 등 사이드바가 항상 열린 상태에서 사용할 때 켜주세요. 사이드바 열기 단계를 건너뛰고 바로 사이드바 내 새 채팅을 누릅니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                    Switch(
                        checked = uiState.geminiSidebarOpenMode,
                        onCheckedChange = { actions.onToggleGeminiSidebarOpenMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppTheme.colors.card,
                            checkedTrackColor = AppTheme.colors.primary,
                            uncheckedThumbColor = AppTheme.colors.card,
                            uncheckedTrackColor = AppTheme.colors.cardBorder,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AppTheme.colors.insetBed
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "시작 전 자동 메모리 정리 (Gemini)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "누적 생성 횟수가 임계치 이상이면 다음 Gemini 자동화 시작 직전에 Google 앱 메모리를 정리합니다. (송신 모드에서는 수신 기기가 수행)",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = uiState.autoMemoryCleanupEnabled,
                            onCheckedChange = { actions.onToggleAutoMemoryCleanup(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AppTheme.colors.card,
                                checkedTrackColor = AppTheme.colors.primary,
                                uncheckedThumbColor = AppTheme.colors.card,
                                uncheckedTrackColor = AppTheme.colors.cardBorder,
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }

                    if (uiState.autoMemoryCleanupEnabled) {
                        HorizontalDivider(color = AppTheme.colors.insetBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Text(
                                    text = "누적 임계 횟수",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = "현재 누적 생성: ${uiState.cumulativeGeminiGenerationCount}회",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                            RepeatCountStepper(
                                repeatCountText = uiState.autoMemoryCleanupThresholdText,
                                onRepeatCountChange = { actions.onAutoMemoryCleanupThresholdChange(it) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = AppTheme.colors.cardBorder.copy(alpha = 0.6f)
            )

            // 원격 연결 섹션
            Text(
                text = "🌐 원격 연결",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary
            )

            val mode = remoteStatus.mode
            val isRemoteConnected = when (mode) {
                AutomationMode.SENDER -> remoteStatus.isPaired && remoteStatus.discoveredDeviceName.isNotBlank()
                AutomationMode.RECEIVER -> remoteStatus.isPaired
                AutomationMode.NORMAL -> false
            }
            val connectedDeviceName = when (mode) {
                AutomationMode.SENDER -> remoteStatus.discoveredDeviceName.ifBlank { "S25 FE" }
                AutomationMode.RECEIVER -> "태블릿"
                AutomationMode.NORMAL -> ""
            }
            val isRemoteRunning = remoteStatus.automationState is AutomationRunState.Running

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AppTheme.colors.insetBed
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
                            val modeLabel = when (mode) {
                                AutomationMode.SENDER -> "송신 모드 (컨트롤러)"
                                AutomationMode.RECEIVER -> "수신 모드 (실행 기기)"
                                AutomationMode.NORMAL -> "일반 모드 (단독 실행)"
                            }
                            Text(
                                text = modeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            val subStatusText = when (mode) {
                                AutomationMode.SENDER -> {
                                    if (isRemoteConnected) {
                                        "${connectedDeviceName} 연결됨"
                                    } else {
                                        remoteStatus.connectionMessage.ifBlank { "수신 기기를 찾는 중입니다." }
                                    }
                                }
                                AutomationMode.RECEIVER -> {
                                    if (isRemoteConnected) {
                                        "송신 기기 연결됨"
                                    } else if (remoteStatus.receiverPairingCode.isNotBlank()) {
                                        "수신 대기 중 · 연결 번호 ${remoteStatus.receiverPairingCode}"
                                    } else {
                                        remoteStatus.message.ifBlank { "수신 대기 중" }
                                    }
                                }
                                AutomationMode.NORMAL -> "원격 연결 안 됨 (일반 단독 모드)"
                            }
                            Text(
                                text = subStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRemoteConnected) AppTheme.colors.primary else AppTheme.colors.textSecondary
                            )
                        }
                        StatusBadge(isReady = isRemoteConnected)
                    }

                    if (isRemoteRunning) {
                        Text(
                            text = "원격 자동화를 중지한 뒤 연결을 끊어주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.error
                        )
                    }

                    if (uiState.remoteDisconnectMessage.isNotBlank()) {
                        Text(
                            text = uiState.remoteDisconnectMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.remoteDisconnectMessage == "원격 연결을 끊었습니다.") {
                                AppTheme.colors.primary
                            } else {
                                AppTheme.colors.error
                            }
                        )
                    }

                    when (mode) {
                        AutomationMode.SENDER -> {
                            if (isRemoteConnected) {
                                OutlinedButton(
                                    onClick = { actions.disconnectRemoteDevice() },
                                    enabled = !isRemoteRunning && !uiState.isDisconnectingRemote,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = AppTheme.colors.error,
                                        disabledContentColor = AppTheme.colors.textSecondary.copy(alpha = 0.38f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (!isRemoteRunning && !uiState.isDisconnectingRemote) {
                                            AppTheme.colors.error.copy(alpha = 0.5f)
                                        } else {
                                            AppTheme.colors.cardBorder
                                        }
                                    )
                                ) {
                                    Text(if (uiState.isDisconnectingRemote) "연결 끊는 중…" else "원격 연결 끊기")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { actions.retryRemoteConnection() },
                                    enabled = !uiState.isRetryingRemote,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = AppTheme.colors.primary,
                                        disabledContentColor = AppTheme.colors.textSecondary.copy(alpha = 0.38f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (!uiState.isRetryingRemote) {
                                            AppTheme.colors.primary.copy(alpha = 0.5f)
                                        } else {
                                            AppTheme.colors.cardBorder
                                        }
                                    )
                                ) {
                                    Text(if (uiState.isRetryingRemote) "재검색 중…" else "기기 재검색 / 연결 재시도")
                                }
                            }
                        }
                        AutomationMode.RECEIVER -> {
                            if (isRemoteConnected) {
                                OutlinedButton(
                                    onClick = { actions.disconnectRemoteDevice() },
                                    enabled = !uiState.isDisconnectingRemote,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = AppTheme.colors.error,
                                        disabledContentColor = AppTheme.colors.textSecondary.copy(alpha = 0.38f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (!uiState.isDisconnectingRemote) {
                                            AppTheme.colors.error.copy(alpha = 0.5f)
                                        } else {
                                            AppTheme.colors.cardBorder
                                        }
                                    )
                                ) {
                                    Text(if (uiState.isDisconnectingRemote) "연결 끊는 중…" else "원격 연결 끊기")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { actions.retryRemoteConnection() },
                                    enabled = !uiState.isRetryingRemote,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = AppTheme.colors.primary,
                                        disabledContentColor = AppTheme.colors.textSecondary.copy(alpha = 0.38f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (!uiState.isRetryingRemote) {
                                            AppTheme.colors.primary.copy(alpha = 0.5f)
                                        } else {
                                            AppTheme.colors.cardBorder
                                        }
                                    )
                                ) {
                                    Text(if (uiState.isRetryingRemote) "신호 갱신 중…" else "수신 재등록 / 신호 갱신")
                                }
                            }
                        }
                        AutomationMode.NORMAL -> {
                            // 일반 모드에서는 추가 제어 버튼 불필요
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = AppTheme.colors.cardBorder.copy(alpha = 0.6f)
            )

            // 화면 테마 모드 (시스템 설정 / 라이트 / 다크)
            Text(
                text = "🌓 화면 테마 모드",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val isModeSelected = mode == LocalAppThemeMode.current
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { actions.onSelectThemeMode(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isModeSelected) {
                            AppTheme.colors.accent.copy(alpha = 0.14f)
                        } else {
                            AppTheme.colors.insetBed
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
                                color = if (isModeSelected) AppTheme.colors.accent else AppTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 테마 색상 팔레트 선택 섹션
            Text(
                text = "🎨 토스 스타일 테마 팔레트",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemePalette.entries.forEach { palette ->
                    val isSelected = palette == LocalAppThemePalette.current
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { actions.onSelectThemePalette(palette) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            palette.previewColor.copy(alpha = 0.14f)
                        } else {
                            AppTheme.colors.insetBed
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
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) palette.previewColor else AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = palette.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { actions.onSelectThemePalette(palette) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = palette.previewColor,
                                    unselectedColor = AppTheme.colors.textSecondary.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = AppTheme.colors.cardBorder.copy(alpha = 0.6f)
            )

            // 시스템 환경 상태 섹션
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "환경 점검",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary
                )
                OutlinedButton(
                    onClick = { actions.refreshStatus() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.primary),
                    border = BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.5f))
                ) {
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
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary
                )
                Button(
                    onClick = { actions.onOpenWildcardStorageSettings() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.primary,
                        contentColor = AppTheme.colors.onPrimary
                    )
                ) {
                    Text("공유 저장소 접근 설정")
                }
            }

            if (!status.isAccessibilityServiceEnabled) {
                Button(
                    onClick = { actions.onOpenAccessibilitySettings() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.primary,
                        contentColor = AppTheme.colors.onPrimary
                    )
                ) {
                    Text("접근성 설정 열기")
                }
            }

            OutlinedButton(
                onClick = { actions.onSelectWildcardFolder() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.primary),
                border = BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.5f))
            ) {
                Text("wildcard 폴더 선택")
            }
            NeuPillButton(
                onClick = { actions.onSelectSafWildcardFolder() },
                contentColor = AppTheme.colors.primary,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("SAF로 허용된 다른 폴더 선택")
            }

            if (!status.hasWriteSecureSettingsPermission && setupInfo.adbGrantCommand.isNotBlank()) {
                Text(
                    text = "ADB 권한 명령어:\n${setupInfo.adbGrantCommand}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary
                )
            }

            Text(
                text = "wildcard 폴더: ${setupInfo.wildcardDirectoryPath.ifBlank { "선택 안 됨" }}",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary
            )
            Text(
                text = "Null Keyboard 전환 대상: ${setupInfo.nullKeyboardTargetImeId}",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            NeuPillButton(
                onClick = { actions.hideSettings() },
                contentColor = AppTheme.colors.primary,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("닫기", fontWeight = FontWeight.SemiBold)
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary
        )
        StatusBadge(isReady = isReady)
    }
}

@Composable
private fun StatusBadge(isReady: Boolean) {
    val containerColor = if (isReady) {
        AppTheme.colors.primary.copy(alpha = 0.14f)
    } else {
        AppTheme.colors.error.copy(alpha = 0.14f)
    }
    val textColor = if (isReady) {
        AppTheme.colors.primary
    } else {
        AppTheme.colors.error
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = textColor
    ) {
        Text(
            text = if (isReady) "정상" else "필요",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
