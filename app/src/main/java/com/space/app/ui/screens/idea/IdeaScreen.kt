package com.space.app.ui.screens.idea

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.space.app.R

private const val MIN_IDEA_LENGTH = 8

/** Long enough for a real brief, short enough to keep the field readable. */
const val MAX_IDEA_LENGTH = 400

@Composable
fun IdeaScreen(
    idea: String,
    onIdeaChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val ready = idea.trim().length >= MIN_IDEA_LENGTH

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.idea_title),
            style = typography.titleLarge,
            color = colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = idea,
            onValueChange = { onIdeaChange(it.take(MAX_IDEA_LENGTH)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text(stringResource(R.string.idea_title)) },
            placeholder = {
                Text(
                    stringResource(R.string.idea_hint),
                    style = typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant
                )
            },
            textStyle = typography.bodyLarge,
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colorScheme.surface,
                unfocusedContainerColor = colorScheme.surface,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline
            )
        )

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            if (!ready) {
                Text(
                    text = pluralStringResource(
                        R.plurals.idea_characters_left,
                        MIN_IDEA_LENGTH - idea.trim().length,
                        MIN_IDEA_LENGTH - idea.trim().length
                    ),
                    style = typography.labelSmall,
                    color = colorScheme.primary
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.idea_counter, idea.length, MAX_IDEA_LENGTH),
                style = typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onContinue,
            enabled = ready,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(stringResource(R.string.idea_continue), style = typography.labelLarge)
        }
        Spacer(Modifier.height(24.dp))
    }
}
