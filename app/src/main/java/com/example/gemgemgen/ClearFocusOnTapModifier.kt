package com.example.gemgemgen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal fun Modifier.clearFocusOnOutsideTap(onClearFocus: () -> Unit): Modifier {
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Final)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
            if (up != null && !down.isConsumed && !up.isConsumed) {
                onClearFocus()
            }
        }
    }
}
