// 역할: Google Gemini 계정 추가 또는 재로그인을 위해 독립 쿠키 세션 WebView를 띄우고 계정 별칭 및 이메일을 저장하는 화면입니다.
package com.example.gemgemgen.usage.ui

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuPillChip
import com.example.gemgemgen.ui.theme.appTextFieldColors
import com.example.gemgemgen.usage.android.GeminiAccountLoginSessionHandler
import com.example.gemgemgen.usage.android.MultiProfileGeminiUsageFetcher
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiUsageParser
import java.util.concurrent.atomic.AtomicBoolean

internal val PRESET_ACCOUNT_ALIASES = listOf(
    "개인 본계정",
    "작업용 1",
    "작업용 2",
    "작업용 3",
    "서브 계정 4",
    "서브 계정 5"
)

/**
 * 계정별 독립 세션 WebView로 구글 로그인을 진행하고 별칭/이메일을 저장하는 모달 내부 화면.
 *
 * - Single Dialog Host 컨테이너 내에서 크로스페이드로 전환되며, 단독 팝업 창을 띄우지 않아
 *   배경 딤 깜빡임(Dim-Flicker)을 방지합니다.
 * - [GeminiAccountLoginSessionHandler]를 통해 전역 싱글톤 직접 호출 없이 쿠키 세션 격리 및 복구를 위임합니다.
 */
@Composable
fun AccountIsolatedLoginContent(
    targetProfile: GeminiAccountProfile,
    isNewAccount: Boolean,
    onSaveAndFinish: (alias: String, email: String) -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
    sessionHandler: GeminiAccountLoginSessionHandler = MultiProfileGeminiUsageFetcher.loginSessionHandler()
) {
    var aliasInput by remember(targetProfile.id) { mutableStateOf(targetProfile.alias) }
    var emailInput by remember(targetProfile.id) { mutableStateOf(targetProfile.email) }
    var sawAuthRedirect by remember(targetProfile.id) { mutableStateOf(false) }
    var recoveredCookieMismatch by remember(targetProfile.id) { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val saved = remember(targetProfile.id) { AtomicBoolean(false) }

    /** CookieMismatch 복구는 무한 반복을 막기 위해 계정당 1회만 수행합니다. 수행했으면 true. */
    fun recoverCookieMismatch(view: WebView): Boolean {
        if (recoveredCookieMismatch) return false
        recoveredCookieMismatch = true
        sawAuthRedirect = true
        sessionHandler.recoverFromCookieMismatch(targetProfile, view)
        return true
    }

    val completeAndCommit = {
        saved.set(true)
        // 저장 직후 이어지는 새로고침이 공유 CookieManager를 교체하기 전에 로그인 WebView부터 정리합니다.
        webViewRef?.let { sessionHandler.releaseLoginWebView(it, targetProfile, commitCookies = true) }
        webViewRef = null
        val finalAlias = aliasInput.trim().ifEmpty { targetProfile.displayAlias }
        onSaveAndFinish(finalAlias, emailInput.trim())
    }

    DisposableEffect(targetProfile.id) {
        onDispose {
            webViewRef?.let {
                sessionHandler.releaseLoginWebView(it, targetProfile, commitCookies = !isNewAccount)
            }
            webViewRef = null
            if (isNewAccount && !saved.get()) {
                sessionHandler.discardLoginSession(targetProfile)
            }
        }
    }


    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 상단 바: 뒤로가기 + 제목 + [완료 및 저장] 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = AppTheme.colors.insetBed,
                    border = BorderStroke(1.dp, AppTheme.colors.insetBorder),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBackToList)
                        .semantics { contentDescription = "계정 목록으로 돌아가기" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = AppTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = if (isNewAccount) "새 Google 계정 추가 (독립 세션)" else "계정 수정 및 세션 재로그인",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "독립 쿠키 금고(${targetProfile.webViewProfileName})에 쿠키가 격리 저장됩니다",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            NeuButton(
                onClick = completeAndCommit,
                isPrimary = true,
                shape = RoundedCornerShape(10.dp),
                contentModifier = Modifier.padding(PaddingValues(0.dp)),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = "완료 및 저장",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 빠른 별칭 추천 칩
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PRESET_ACCOUNT_ALIASES.forEach { preset ->
                NeuPillChip(
                    text = preset,
                    selected = aliasInput.trim() == preset,
                    onClick = { aliasInput = preset },
                    height = 28.dp,
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 계정 별칭 및 구글 이메일 입력란
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = aliasInput,
                onValueChange = { aliasInput = it },
                label = { Text("계정 별칭 (예: 작업용 1)", fontSize = 11.sp) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = appTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(0.45f)
            )

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("구글 이메일 (선택)", fontSize = 11.sp) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = appTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(0.55f)
            )
        }

        // 해당 계정 전용 독립 세션 WebView
        NeuInsetBed(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp)),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // 중요: loadUrl 호출 전에 동기적으로 WebView 설정 및 쿠키 금고 복원/정제를 완료합니다.
                        sessionHandler.prepareLoginWebView(
                            webView = this,
                            profile = targetProfile,
                            clearExistingCookies = isNewAccount
                        )

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val nextUrl = request?.url?.toString()
                                if (GeminiUsageParser.isCookieMismatchUrl(nextUrl) && view != null && recoverCookieMismatch(view)) {
                                    return true
                                }
                                if (GeminiUsageParser.isAuthRedirectUrl(nextUrl)) {
                                    sawAuthRedirect = true
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (GeminiUsageParser.isCookieMismatchUrl(url) && view != null && recoverCookieMismatch(view)) {
                                    return
                                }
                                if (GeminiUsageParser.isAuthRedirectUrl(url)) {
                                    sawAuthRedirect = true
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (GeminiUsageParser.isCookieMismatchUrl(url) && view != null && recoverCookieMismatch(view)) {
                                    return
                                }

                                // 페이지 본문에 쿠키 설정 오류 안내가 표시된 경우 자동 정화 후 로그인 화면으로 복구
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        var text = (document.body && document.body.innerText) ? document.body.innerText : '';
                                        if (text.indexOf('쿠키 설정에 문제가 있음을 발견했습니다') !== -1 ||
                                            text.toLowerCase().indexOf('problem with your cookie settings') !== -1) {
                                            return '__COOKIE_MISMATCH__';
                                        }
                                        var m = text.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
                                        return m ? m[0] : '';
                                    })();
                                    """.trimIndent()
                                ) { rawJsResult ->
                                    val unquoted = rawJsResult?.trim()?.trim('"').orEmpty()
                                    if (unquoted == "__COOKIE_MISMATCH__" && view != null && recoverCookieMismatch(view)) {
                                        return@evaluateJavascript
                                    }
                                    val detected = GeminiUsageParser.extractAccountEmail(unquoted)
                                    if (!detected.isNullOrBlank() && emailInput.isBlank()) {
                                        emailInput = detected
                                    }
                                }

                                sessionHandler.commitLoginSession(targetProfile)

                                if (sawAuthRedirect &&
                                    !url.isNullOrBlank() &&
                                    url.startsWith("https://gemini.google.com/usage")
                                ) {
                                    sawAuthRedirect = false
                                    completeAndCommit()
                                }
                            }
                        }

                        loadUrl(
                            if (isNewAccount) {
                                GeminiUsageParser.LOGIN_URL
                            } else {
                                GeminiUsageParser.USAGE_URL
                            }
                        )
                        webViewRef = this
                    }
                }
            )
        }
    }
}
