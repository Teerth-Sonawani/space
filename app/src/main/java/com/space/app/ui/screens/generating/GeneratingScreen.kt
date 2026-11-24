package com.space.app.ui.screens.generating

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.space.app.R
import com.space.app.ui.GenerationState
import com.space.app.ui.rememberReduceMotion
import kotlinx.coroutines.delay

private val steps = listOf(
    R.string.generating_step_reading,
    R.string.generating_step_thinking,
    R.string.generating_step_shaping,
    R.string.generating_step_drawing
)

/** How long each line of the "working on it" list takes to appear. */
private const val STEP_MS = 220L

@Composable
fun GeneratingScreen(
    state: GenerationState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onReady: () -> Unit,
    onRetry: () -> Unit,
    onUseStarter: () -> Unit = {}
) {
    var step by remember { mutableIntStateOf(0) }
    var tickerDone by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()

    LaunchedEffect(Unit) { onStart() }

    // The list is presentation-only pacing; the real signal is `state`. With
    // reduced motion the whole list is shown at once instead of ticking in.
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            step = steps.lastIndex
            tickerDone = true
            return@LaunchedEffect
        }
        steps.indices.forEach { i ->
            step = i
            delay(STEP_MS)
        }
        step = steps.lastIndex
        tickerDone = true
    }

    // Only leave once generation has actually succeeded and the list has played.
    LaunchedEffect(state, tickerDone) {
        if (state is GenerationState.Ready && tickerDone) onReady()
    }

    val failed = state as? GenerationState.Failed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(96.dp))
        Text(
            text = stringResource(R.string.generating_title),
            style = typography.titleLarge,
            color = colorScheme.onBackground
        )
        Spacer(Modifier.height(32.dp))

        val activeStepLabel = stringResource(steps[step.coerceIn(0, steps.lastIndex)])
        Column(
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = activeStepLabel
            }
        ) {
            steps.forEachIndexed { i, labelRes ->
                StepRow(
                    label = stringResource(labelRes),
                    state = when {
                        i < step -> StepState.DONE
                        i == step -> StepState.ACTIVE
                        else -> StepState.WAITING
                    }
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        if (failed != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.generating_failed_title),
                        style = typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = failed.reason,
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (failed != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.generating_retry), style = typography.labelLarge)
            }
            if (failed.canUseStarter) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onUseStarter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        stringResource(R.string.generating_use_starter),
                        style = typography.labelLarge
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.generating_cancel),
                style = typography.labelLarge,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class StepState { DONE, ACTIVE, WAITING }

@Composable
private fun StepRow(label: String, state: StepState) {
    val targetAlpha = if (state == StepState.WAITING) 0.35f else 1f
    val alpha by animateFloatAsState(targetAlpha, tween(300), label = "step-alpha")
    val dotColor by animateColorAsState(
        if (state == StepState.WAITING) colorScheme.outline else colorScheme.primary,
        tween(300),
        label = "step-dot"
    )

    val pulse = if (state == StepState.ACTIVE && !rememberReduceMotion()) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "pulse-value"
        ).value
    } else 1f

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(alpha)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .alpha(pulse)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = when (state) {
                StepState.DONE -> stringResource(R.string.generating_step_done, label)
                StepState.ACTIVE -> stringResource(R.string.generating_step_active, label)
                StepState.WAITING -> label
            },
            style = typography.bodyLarge,
            color = colorScheme.onBackground
        )
    }
}
