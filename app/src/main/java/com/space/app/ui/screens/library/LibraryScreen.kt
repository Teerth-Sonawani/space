package com.space.app.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.space.app.R
import com.space.app.data.StoredPlan
import kotlinx.coroutines.launch

/**
 * The plan library (M2): every saved plan, newest first, with rename and
 * delete-with-undo. Tapping a row opens it on the chart screen.
 */
@Composable
fun LibraryScreen(
    plans: List<StoredPlan>,
    onBack: () -> Unit,
    onOpen: (StoredPlan) -> Unit,
    onRename: (id: String, title: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onUndoDelete: (StoredPlan) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var renaming by remember { mutableStateOf<StoredPlan?>(null) }
    var pendingDelete by remember { mutableStateOf<StoredPlan?>(null) }

    val deletedMessage = stringResource(R.string.library_deleted)
    val undoLabel = stringResource(R.string.library_undo)

    fun confirmDelete(stored: StoredPlan) {
        pendingDelete = null
        onDelete(stored.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel
            )
            if (result == SnackbarResult.ActionPerformed) onUndoDelete(stored)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    text = stringResource(R.string.library_title),
                    style = typography.titleLarge,
                    color = colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(16.dp))

            if (plans.isEmpty()) {
                EmptyLibrary()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(plans, key = { it.id }) { stored ->
                        LibraryRow(
                            stored = stored,
                            onOpen = { onOpen(stored) },
                            onRename = { renaming = stored },
                            onDelete = { pendingDelete = stored }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(16.dp)
        )
    }

    renaming?.let { stored ->
        RenameDialog(
            initialTitle = stored.plan.title,
            onDismiss = { renaming = null },
            onConfirm = { newTitle ->
                renaming = null
                onRename(stored.id, newTitle)
            }
        )
    }

    pendingDelete?.let { stored ->
        DeleteDialog(
            title = stored.plan.title,
            onDismiss = { pendingDelete = null },
            onConfirm = { confirmDelete(stored) }
        )
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colorScheme.surfaceVariant, CircleShape)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = typography.titleMedium,
            color = colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.library_empty_body),
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LibraryRow(
    stored: StoredPlan,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stored.plan.title,
                    style = typography.titleMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stored.brief.idea,
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.library_rename),
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.library_delete),
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(120) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.library_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_rename_cancel))
            }
        }
    )
}

@Composable
private fun DeleteDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_confirm)) },
        text = { Text(stringResource(R.string.library_delete_confirm_body, title)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.library_delete_yes))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_delete_no))
            }
        }
    )
}