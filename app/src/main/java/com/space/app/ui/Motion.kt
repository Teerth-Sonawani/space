package com.space.app.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the platform "remove animations" / "animator duration scale = 0"
 * setting is on. PLAN section 6: motion clarifies state, so when the user has
 * asked for no animation the reveal, transitions and ticks must collapse to
 * their end state rather than play out.
 *
 * Read once per composition from the system setting; it does not change while
 * the screen is up, so caching it in [remember] is safe. Callers pair this with
 * a zero-duration `tween(0)` from `androidx.compose.animation.core` to make an
 * animation jump straight to its target.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Exception) {
            1f
        }
        scale == 0f
    }
}

