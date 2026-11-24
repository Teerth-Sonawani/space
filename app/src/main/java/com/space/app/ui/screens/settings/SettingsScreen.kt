package com.space.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.space.app.R
import com.space.app.data.ApiSource
import com.space.app.data.KeyHealth
import com.space.app.data.ProviderKind
import com.space.app.data.displayName

@Composable
fun SettingsScreen(
    sources: List<ApiSource>,
    health: Map<String, KeyHealth>,
    onBack: () -> Unit,
    onAdd: (ProviderKind, String, String) -> Unit,
    onRemove: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit
) {
    var provider by remember { mutableStateOf(ProviderKind.GEMINI) }
    var label by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = typography.titleLarge,
                color = colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_sources_heading),
                style = typography.labelLarge,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_sources_body),
                style = typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (sources.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_empty),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            } else {
                sources.forEachIndexed { index, source ->
                    SourceRow(
                        source = source,
                        health = health[source.id] ?: KeyHealth.Active,
                        canMoveUp = index > 0,
                        canMoveDown = index < sources.lastIndex,
                        onMoveUp = { onMoveUp(source.id) },
                        onMoveDown = { onMoveDown(source.id) },
                        onRemove = { onRemove(source.id) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_add_heading),
                style = typography.labelLarge,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderKind.entries.forEach { kind ->
                    FilterChip(
                        selected = provider == kind,
                        onClick = { provider = kind },
                        label = {
                            Text(kind.displayName(), style = typography.labelMedium)
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_label_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_key_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.medium
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error!!,
                    style = typography.bodySmall,
                    color = colorScheme.error
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (apiKey.isBlank()) {
                        error = "Paste an API key first"
                        return@Button
                    }
                    onAdd(provider, label, apiKey)
                    label = ""
                    apiKey = ""
                    error = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.settings_add), style = typography.labelLarge)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SourceRow(
    source: ApiSource,
    health: KeyHealth,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.label.ifBlank { source.provider.displayName() },
                        style = typography.titleMedium,
                        color = colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${source.provider.displayName()} · ${source.maskedKeyHint}",
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                HealthChip(health)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.settings_move_up)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.settings_move_down)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemove) {
                    Text(
                        stringResource(R.string.settings_remove),
                        style = typography.labelLarge,
                        color = colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthChip(health: KeyHealth) {
    val (label, color) = when (health) {
        KeyHealth.Active -> stringResource(R.string.settings_health_active) to colorScheme.primary
        is KeyHealth.Cooling ->
            stringResource(R.string.settings_health_cooling) to colorScheme.onSurfaceVariant
        KeyHealth.Invalid ->
            stringResource(R.string.settings_health_invalid) to colorScheme.error
    }
    Text(text = label, style = typography.labelSmall, color = color)
}
