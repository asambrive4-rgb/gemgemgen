// 역할: 5시간 단기 한도 및 주간 누적 한도 게이지 바, 리셋 안내, 새로고침 및 인앱 로그인 웹뷰 시트를 제공하는 컴팩트 사용량 카드 UI입니다.
package com.example.gemgemgen.usage.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gemgemgen.automation.ui.GeminiGradientLogo
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.usage.android.GeminiUsageFetcher
import com.example.gemgemgen.usage.domain.GeminiUsageParser
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.domain.GeminiUsageWarningLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val UsageCautionAmber = Color(0xFFE67E22)
private val UsageWarningRed = Color(0xFFD93025)

/**
 * Google AI Pro 계정의 제미나이 사용량 한도(5시간 단기 한도 및 주간 누적 한도)를 표시하는 컴팩트 카드 UI.
 *
 * - 5시간 단기 한도 게이지 바 (%) + 리셋 카운트다운/시각 텍스트
 * - 주간(Weekly) 한도 게이지 바 (%) + 리셋 시각 텍스트
 * - 70% 이상 시 주의 색상(Amber), 90% 이상 시 경고 색상(Red) 적용
 * - 새로고침 버튼 및 세션 만료(AuthRequired) 시 인앱 로그인 WebView 바텀시트 호출 버튼 제공
 */
@Composable
fun GeminiUsageCard(
    usageState: GeminiUsageState,
    cachedSuccess: GeminiUsageState.Success? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedSuccess = (usageState as? GeminiUsageState.Success) ?: cachedSuccess
    val isLoading = usageState is GeminiUsageState.Loading || isRefreshing

    NeuCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GeminiUsageHeaderRow(
                displayedSuccess = displayedSuccess,
                usageState = usageState,
                isLoading = isLoading,
                onRefresh = onRefresh,
                onOpenLogin = onOpenLogin
            )

            when (usageState) {
                is GeminiUsageState.Success -> {
                    GeminiUsageGaugesSection(success = usageState)
                }
                is GeminiUsageState.AuthRequired -> {
                    if (displayedSuccess != null) {
                        GeminiUsageGaugesSection(success = displayedSuccess)
                    }
                    GeminiUsageAuthRequiredBanner(onOpenLogin = onOpenLogin)
                }
                is GeminiUsageState.Error -> {
                    if (displayedSuccess != null) {
                        GeminiUsageGaugesSection(success = displayedSuccess)
                    }
                    GeminiUsageErrorBanner(
                        message = usageState.message,
                        onRefresh = onRefresh,
                        onOpenLogin = onOpenLogin
                    )
                }
                GeminiUsageState.Loading -> {
                    if (displayedSuccess != null) {
                        GeminiUsageGaugesSection(success = displayedSuccess)
                    } else {
                        GeminiUsageLoadingRow()
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiUsageHeaderRow(
    displayedSuccess: GeminiUsageState.Success?,
    usageState: GeminiUsageState,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Image(
                imageVector = GeminiGradientLogo,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Gemini Pro 사용량",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            if (displayedSuccess != null && displayedSuccess.updatedAt > 0L) {
                Text(
                    text = formatUpdatedAtTime(displayedSuccess.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (usageState !is GeminiUsageState.AuthRequired) {
                CompactUsageActionButton(
                    label = "계정 연동",
                    icon = Icons.Default.Login,
                    onClick = onOpenLogin,
                    contentDescriptionText = "구글 계정 로그인 웹뷰 열기"
                )
            }

            Surface(
                shape = CircleShape,
                color = AppTheme.colors.insetBed,
                border = BorderStroke(1.dp, AppTheme.colors.insetBorder),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isLoading, onClick = onRefresh)
                    .semantics { contentDescription = "제미나이 사용량 새로고침" }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AppTheme.colors.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiUsageGaugesSection(
    success: GeminiUsageState.Success
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        UsageGaugeRow(
            title = "5시간 단기",
            percent = success.fiveHourPercent,
            resetText = success.fiveHourResetText,
            warningLevel = success.fiveHourWarningLevel
        )

        UsageGaugeRow(
            title = "주간 누적",
            percent = success.weeklyPercent,
            resetText = success.weeklyResetText,
            warningLevel = success.weeklyWarningLevel
        )
    }
}

@Composable
private fun UsageGaugeRow(
    title: String,
    percent: Int,
    resetText: String,
    warningLevel: GeminiUsageWarningLevel
) {
    val clampedPercent = percent.coerceIn(0, 100)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedPercent / 100f,
        animationSpec = tween(durationMillis = 350),
        label = "UsageGaugeProgress"
    )
    val gaugeColor = resolveWarningColor(warningLevel)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    text = "${clampedPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = gaugeColor
                )
                if (warningLevel != GeminiUsageWarningLevel.NORMAL) {
                    WarningBadge(warningLevel = warningLevel, color = gaugeColor)
                }
            }

            Text(
                text = resetText,
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        NeuInsetBed(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            shape = RoundedCornerShape(999.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(gaugeColor)
            )
        }
    }
}

@Composable
private fun WarningBadge(
    warningLevel: GeminiUsageWarningLevel,
    color: Color
) {
    val label = when (warningLevel) {
        GeminiUsageWarningLevel.CAUTION -> "주의"
        GeminiUsageWarningLevel.WARNING -> "경고"
        GeminiUsageWarningLevel.NORMAL -> return
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun GeminiUsageAuthRequiredBanner(
    onOpenLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colors.insetBed)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "세션이 만료되었거나 Google 로그인이 필요합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

        NeuButton(
            onClick = onOpenLogin,
            isPrimary = true,
            shape = RoundedCornerShape(10.dp),
            contentModifier = Modifier.padding(PaddingValues(0.dp)),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Login,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "로그인",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GeminiUsageErrorBanner(
    message: String,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colors.insetBed)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactUsageActionButton(
                label = "로그인",
                icon = Icons.Default.Login,
                onClick = onOpenLogin,
                contentDescriptionText = "구글 로그인 웹뷰 열기"
            )
            CompactUsageActionButton(
                label = "재시도",
                icon = Icons.Default.Refresh,
                onClick = onRefresh,
                contentDescriptionText = "사용량 다시 조회"
            )
        }
    }
}

@Composable
private fun GeminiUsageLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = AppTheme.colors.primary
        )
        Text(
            text = "제미나이 사용량 한도를 불러오는 중…",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary
        )
    }
}

@Composable
private fun CompactUsageActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescriptionText: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.2.dp, AppTheme.colors.cardBorder),
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDescriptionText }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.colors.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
        }
    }
}

@Composable
internal fun resolveWarningColor(level: GeminiUsageWarningLevel): Color {
    return when (level) {
        GeminiUsageWarningLevel.NORMAL -> AppTheme.colors.primary
        GeminiUsageWarningLevel.CAUTION -> UsageCautionAmber
        GeminiUsageWarningLevel.WARNING -> UsageWarningRed
    }
}

private fun formatUpdatedAtTime(updatedAt: Long): String {
    return runCatching {
        val formatter = SimpleDateFormat("HH:mm 갱신", Locale.KOREA)
        formatter.format(Date(updatedAt))
    }.getOrDefault("")
}

/**
 * 세션 만료 또는 미로그인 시 Google 계정에 로그인하여 CookieManager 세션을 공유하기 위한 인앱 로그인 WebView 바텀시트.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeminiUsageLoginBottomSheet(
    onLoginCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sawAuthRedirect by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { webView ->
                runCatching {
                    CookieManager.getInstance().flush()
                    webView.stopLoading()
                    webView.loadUrl(GeminiUsageFetcher.BLANK_URL)
                    webView.clearHistory()
                }
            }
            webViewRef = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.colors.card,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(620.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google AI Pro 계정 로그인",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "로그인 후 자동으로 닫히거나 [완료 및 새로고침]을 눌러 동기화하세요.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuButton(
                        onClick = {
                            CookieManager.getInstance().flush()
                            onLoginCompleted()
                        },
                        isPrimary = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "완료 및 새로고침",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = AppTheme.colors.insetBed,
                        border = BorderStroke(1.dp, AppTheme.colors.insetBorder),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss)
                            .semantics { contentDescription = "로그인 시트 닫기" }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = AppTheme.colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = GeminiUsageFetcher.sanitizeWebViewUserAgent(userAgentString)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val nextUrl = request?.url?.toString()
                                if (GeminiUsageParser.isAuthRedirectUrl(nextUrl)) {
                                    sawAuthRedirect = true
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (GeminiUsageParser.isAuthRedirectUrl(url)) {
                                    sawAuthRedirect = true
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                CookieManager.getInstance().flush()
                                if (sawAuthRedirect &&
                                    !url.isNullOrBlank() &&
                                    url.startsWith("https://gemini.google.com/usage")
                                ) {
                                    sawAuthRedirect = false
                                    onLoginCompleted()
                                }
                            }
                        }

                        loadUrl(GeminiUsageParser.USAGE_URL)
                        webViewRef = this
                    }
                }
            )
        }
    }
}
