package com.space.app.ui.screens.questions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.space.app.R
import com.space.app.domain.model.ClarifyingQuestion
import com.space.app.ui.QuestionsLoadState
import com.space.app.ui.rememberReduceMotion

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionsScreen(
    questions: List<ClarifyingQuestion>,
    loadState: QuestionsLoadState,
    fromOfflinePack: Boolean,
    onBack: () -> Unit,
    onAnswer: (question: String, answer: String) -> Unit,
    onDone: () -> Unit
) {
    if (loadState is QuestionsLoadState.Loading || questions.isEmpty()) {
        QuestionsLoading(onBack = onBack)
        return
    }

    var index by rememberSaveable { mutableIntStateOf(0) }
    var freeText by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val reduceMotion = rememberReduceMotion()

    val safeIndex = index.coerceIn(0, questions.lastIndex)
    val isLast = safeIndex == questions.lastIndex
    val currentPrompt = questions[safeIndex].prompt
    val progressLabel = stringResource(
        R.string.question_progress,
        safeIndex + 1,
        questions.size
    )

    val advance = {
        val answer = freeText.trim().ifEmpty { selected.orEmpty() }
        onAnswer(currentPrompt, answer)
        if (isLast) {
            onDone()
        } else {
            index = safeIndex + 1
            selected = null
            freeText = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .systemBarsPadding()
            .imePadding()
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
            Row(
                modifier = Modifier.semantics { contentDescription = progressLabel },
                verticalAlignment = Alignment.CenterVertically
            ) {
                questions.indices.forEach { i ->
                    Spacer(Modifier.width(4.dp))
                    val segmentColor by animateColorAsState(
                        targetValue = if (i <= safeIndex) colorScheme.primary else colorScheme.outline,
                        animationSpec = tween(if (reduceMotion) 0 else 300),
                        label = "progress-segment"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 4.dp)
                            .background(segmentColor, CircleShape)
                    )
                }
            }
        }

        if (fromOfflinePack) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.questions_offline_banner),
                style = typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(32.dp))

        AnimatedContent(
            targetState = safeIndex,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            transitionSpec = {
                if (reduceMotion) {
                    fadeInInstant() togetherWith fadeOutInstant()
                } else if (targetState > initialState) {
                    slideInHorizontally(tween(260)) { it / 2 } togetherWith
                        slideOutHorizontally(tween(260)) { -it / 2 }
                } else {
                    slideInHorizontally(tween(260)) { -it / 2 } togetherWith
                        slideOutHorizontally(tween(260)) { it / 2 }
                }
            },
            label = "question-card"
        ) { qIndex ->
            val question = questions[qIndex]
            Column {
                Text(
                    text = stringResource(
                        R.string.question_progress,
                        qIndex + 1,
                        questions.size
                    ),
                    style = typography.labelSmall,
                    color = colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = question.prompt,
                    style = typography.titleLarge,
                    color = colorScheme.onBackground
                )
                Spacer(Modifier.height(24.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    question.suggestions.forEach { label ->
                        FilterChip(
                            selected = selected == label,
                            onClick = {
                                selected = if (selected == label) null else label
                                if (selected != null) freeText = ""
                            },
                            label = { Text(label, style = typography.bodySmall) },
                            shape = MaterialTheme.shapes.small,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colorScheme.surface,
                                selectedContainerColor = colorScheme.secondaryContainer,
                                selectedLabelColor = colorScheme.primary
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = label
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = freeText,
            onValueChange = {
                freeText = it
                if (it.isNotBlank()) selected = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.question_own_answer), style = typography.bodySmall) },
            placeholder = {
                Text(
                    stringResource(R.string.question_own_answer),
                    style = typography.bodySmall
                )
            },
            textStyle = typography.bodyLarge,
            shape = MaterialTheme.shapes.medium,
            minLines = 1
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = advance) {
                Text(
                    stringResource(R.string.question_skip),
                    style = typography.labelLarge,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = advance,
                modifier = Modifier
                    .widthIn(min = 132.dp)
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    stringResource(if (isLast) R.string.question_make else R.string.question_next),
                    style = typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun QuestionsLoading(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .systemBarsPadding()
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
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.questions_loading),
                style = typography.bodyLarge,
                color = colorScheme.onBackground
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

private fun fadeInInstant() =
    androidx.compose.animation.fadeIn(animationSpec = tween(0))

private fun fadeOutInstant() =
    androidx.compose.animation.fadeOut(animationSpec = tween(0))
