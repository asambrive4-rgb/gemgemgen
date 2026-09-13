// 역할: 반복 실행 횟수를 숫자로 직접 입력하거나 증감 버튼으로 조절하는 UI를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuInsetBed

@Composable
internal fun RepeatCountStepper(
    repeatCountText: String,
    onRepeatCountChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVal = RepeatCountParser.parse(repeatCountText)
    val buttonShape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NeuInsetBed(
            shape = RoundedCornerShape(14.dp),
            backgroundColor = AppTheme.colors.insetBed,
            borderColor = AppTheme.colors.insetBorder
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(enabled = currentVal > 1) {
                            onRepeatCountChange((currentVal - 1).toString())
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .then(
                                if (currentVal > 1) {
                                    Modifier.shadow(
                                        elevation = 2.dp,
                                        shape = buttonShape,
                                        ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.4f),
                                        spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.3f)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clip(buttonShape)
                            .background(
                                color = if (currentVal > 1) {
                                    AppTheme.colors.card
                                } else {
                                    AppTheme.colors.card.copy(alpha = 0.4f)
                                }
                            )
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    if (currentVal > 1) AppTheme.colors.cardBorder else AppTheme.colors.cardBorder.copy(alpha = 0.4f)
                                ),
                                buttonShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "—",
                            color = if (currentVal > 1) {
                                AppTheme.colors.primary
                            } else {
                                AppTheme.colors.textSecondary.copy(alpha = 0.4f)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Text(
                    text = repeatCountText,
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .padding(horizontal = 4.dp),
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(enabled = currentVal < 999) {
                            onRepeatCountChange((currentVal + 1).toString())
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .then(
                                if (currentVal < 999) {
                                    Modifier.shadow(
                                         elevation = 2.dp,
                                         shape = buttonShape,
                                         ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.4f),
                                         spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.3f)
                                     )
                                 } else {
                                    Modifier
                                }
                            )
                            .clip(buttonShape)
                            .background(
                                color = if (currentVal < 999) {
                                    AppTheme.colors.card
                                } else {
                                    AppTheme.colors.card.copy(alpha = 0.4f)
                                }
                            )
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    if (currentVal < 999) AppTheme.colors.cardBorder else AppTheme.colors.cardBorder.copy(alpha = 0.4f)
                                ),
                                buttonShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "＋",
                            color = if (currentVal < 999) {
                                AppTheme.colors.primary
                            } else {
                                AppTheme.colors.textSecondary.copy(alpha = 0.4f)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
