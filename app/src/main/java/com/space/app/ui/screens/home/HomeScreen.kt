package com.space.app.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.space.app.R
import com.space.app.ui.rememberReduceMotion

/**
 * Fade-and-rise entrance for one block of the screen. The delay lives inside the
 * animation spec rather than a raw coroutine delay, so the whole stagger
 * collapses when the platform disables animations.
 */
@Composable
private fun RiseIn(
    delayMs: Int,
    content: @Composable () -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = if (reduceMotion) {
                tween(durationMillis = 0)
            } else {
                tween(
                    durationMillis = 440,
                    delayMillis = delayMs,
                    easing = FastOutSlowInEasing
                )
            }
        )
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 16.dp.toPx()
        }
    ) {
        content()
    }
}

@Composable
fun HomeScreen(
    recentPlanTitle: String?,
    savedPlanCount: Int,
    onNewIdea: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenSample: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(64.dp))

        RiseIn(delayMs = 60) {
            Text(
                text = stringResource(R.string.home_title),
                style = typography.displayMedium,
                color = colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(4.dp))
        RiseIn(delayMs = 150) {
            Text(
                text = stringResource(R.string.home_subtitle),
                style = typography.bodyLarge,
                color = colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f))

        if (recentPlanTitle != null) {
            RiseIn(delayMs = 220) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .clickable(onClick = onOpenRecent),
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_recent_label),
                                style = typography.labelSmall,
                                color = colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = recentPlanTitle,
                                style = typography.titleMedium,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            RiseIn(delayMs = 260) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(colorScheme.primary, CircleShape)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.home_empty_title),
                        style = typography.titleMedium,
                        color = colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_empty_body),
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        RiseIn(delayMs = 380) {
            Button(
                onClick = onNewIdea,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_new_idea), style = typography.labelLarge)
            }
        }

        RiseIn(delayMs = 460) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (savedPlanCount > 0) {
                    TextButton(onClick = onOpenLibrary) {
                        Text(
                            stringResource(R.string.home_open_library),
                            style = typography.labelLarge
                        )
                    }
                    Text(
                        text = "·",
                        style = typography.labelLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenSample) {
                    Text(
                        stringResource(R.string.home_open_sample),
                        style = typography.labelLarge
                    )
                }
                Text(
                    text = "·",
                    style = typography.labelLarge,
                    color = colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onOpenSettings) {
                    Text(
                        stringResource(R.string.home_open_settings),
                        style = typography.labelLarge
                    )
                }
            }
        }
    }
}
