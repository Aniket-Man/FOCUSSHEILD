package com.example.core.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Labeled add/rename form: validates on submit and never dismisses before a save succeeds. */
@Composable
fun FocusNameDialog(
    title: String,
    initialValue: String,
    fieldLabel: String,
    example: String,
    emptyError: String,
    saveError: String,
    confirmLabel: String,
    onSave: suspend (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    deleteLabel: String = stringResource(R.string.action_delete)
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(inputError) {
        if (inputError != null) focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete, enabled = !saving) {
                        Icon(Icons.Rounded.DeleteOutline, deleteLabel, tint = FocusColors.BlockedText)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; inputError = null },
                    label = { Text(fieldLabel) },
                    placeholder = { Text(example) },
                    supportingText = { inputError?.let { Text(it) } },
                    isError = inputError != null,
                    enabled = !saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).semantics { inputError?.let { error(it) } }
                )
                FocusStatusMessage(failure, isError = true, onDismiss = { failure = null })
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    val name = value.trim()
                    if (name.isEmpty()) {
                        inputError = emptyError
                    } else {
                        saving = true
                        failure = null
                        scope.launch {
                            try {
                                onSave(name)
                                onDismiss()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (exception: Exception) {
                                Log.e("FocusNameDialog", "Unable to save a name", exception)
                                failure = saveError
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
            ) { Text(if (saving) stringResource(R.string.action_saving) else confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
