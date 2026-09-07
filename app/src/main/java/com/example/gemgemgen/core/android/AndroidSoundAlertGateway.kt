package com.example.gemgemgen.core.android

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.example.gemgemgen.core.SoundAlertGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Android 시스템의 [ToneGenerator]를 사용하여 짧은 비프음을 재생하는 구현체.
 * 미디어 볼륨(STREAM_MUSIC)과 동기화되어 볼륨이 0이면 소리를 내지 않습니다.
 */
class AndroidSoundAlertGateway(
    context: Context,
    private val toneDurationMs: Int = DEFAULT_BEEP_DURATION_MS,
    private val toneType: Int = ToneGenerator.TONE_PROP_BEEP
) : SoundAlertGateway {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun playShortAlert() {
        runCatching {
            val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            if (currentVolume <= 0) return

            scope.launch {
                runCatching {
                    val generator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
                    try {
                        generator.startTone(toneType, toneDurationMs)
                        delay(toneDurationMs + 100L)
                    } finally {
                        generator.release()
                    }
                }
            }
        }
    }

    companion object {
        const val DEFAULT_BEEP_DURATION_MS = 120
    }
}
