@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dodocomm.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import com.dodocomm.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dodocomm.app.ui.theme.FieldShape

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
)

/**
 * Text field that keeps a local draft and only writes to [SettingsRepository]
 * on focus loss / IME Done — settings writes are synchronous commits, so we
 * must never persist on every keystroke.
 */
@Composable
fun SettingTextField(
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    var text by rememberSaveable { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value) {
        if (!focused && text != value) text = value
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (it != value) onCommit(it)
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                val isFocused = focusState.isFocused
                if (focused && !isFocused) {
                    focused = false
                    if (text != value) onCommit(text)
                } else if (!focused && isFocused) {
                    focused = true
                }
            },
        enabled = enabled,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        supportingText = if (supportingText != null) { { Text(supportingText) } } else null,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = {
                if (text != value) onCommit(text)
                focusManager.clearFocus()
            },
        ),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = fieldColors(),
    )
}

/** Password-style field with a show / hide toggle. */
@Composable
fun SecretField(
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val description = if (visible) "隐藏$label" else "显示$label"
    SettingTextField(
        value = value,
        onCommit = onCommit,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        supportingText = supportingText,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = description,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private val TitleTokens = listOf(
    "{device}", "{sender}", "{sim}", "{number}", "{time}", "{date}", "{datetime}",
)
private val BodyTokens = listOf("{body}") + TitleTokens

/**
 * Title-template editor: a single-line field plus chips that insert the
 * supported Bark variables at the current cursor position.
 */
@Composable
fun TemplateField(
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "标题模板",
    placeholder: String = "{device} · {sender}",
    supportingText: String = "点击下方标签插入变量",
    tokens: List<String> = TitleTokens,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value))
    }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value) {
        if (!focused && field.text != value) field = TextFieldValue(value)
    }

    fun commit() {
        if (field.text != value) onCommit(field.text)
    }

    fun insert(token: String) {
        val start = field.selection.min.coerceIn(0, field.text.length)
        val end = field.selection.max.coerceIn(start, field.text.length)
        val newText = field.text.replaceRange(start, end, token)
        field = TextFieldValue(newText, selection = TextRange(start + token.length))
        if (!focused) onCommit(newText)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    val isFocused = focusState.isFocused
                    if (focused && !isFocused) {
                        focused = false
                        commit()
                    } else if (!focused && isFocused) {
                        focused = true
                    }
                },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    commit()
                    focusManager.clearFocus()
                },
            ),
            supportingText = { Text(supportingText) },
            colors = fieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tokens.forEach { token ->
                TokenChip(text = token, onClick = { insert(token) })
            }
        }
    }
}

private fun parseTokens(raw: String): List<String> =
    raw.split(Regex("[,\\n，、；;]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Whitelist / blacklist editor: comma or newline separated values persisted as
 * a list, with removable chips showing the parsed tokens.
 */
@Composable
fun FilterListEditor(
    title: String,
    value: List<String>,
    onCommit: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "多个值用逗号或换行分隔",
) {
    var text by rememberSaveable { mutableStateOf(value.joinToString("，")) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value) {
        if (!focused) {
            val joined = value.joinToString("，")
            if (joined != text) text = joined
        }
    }

    fun commit() {
        onCommit(parseTokens(text))
    }

    val tokens = remember(text) { parseTokens(text) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    val isFocused = focusState.isFocused
                    if (focused && !isFocused) {
                        focused = false
                        commit()
                    } else if (!focused && isFocused) {
                        focused = true
                    }
                },
            label = { Text(title) },
            placeholder = { Text(placeholder) },
            minLines = 2,
            maxLines = 4,
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    commit()
                    focusManager.clearFocus()
                },
            ),
            colors = fieldColors(),
        )
        if (tokens.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tokens.forEach { token ->
                    Surface(
                        onClick = {
                            val remaining = tokens - token
                            text = remaining.joinToString("，")
                            onCommit(remaining)
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                text = token,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "移除 $token",
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
