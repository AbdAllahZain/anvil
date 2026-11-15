package dev.anvil.ide.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = A.c.border)
}

@Composable
fun VHairline(modifier: Modifier = Modifier) {
    VerticalDivider(modifier = modifier, thickness = 1.dp, color = A.c.border)
}

/** Square icon button with a 40dp touch target. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconBtn(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = A.c.textDim,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, description, Modifier.size(iconSize),
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
        )
    }
}

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = A.c
    val bg = when {
        primary -> c.accent
        else -> Color.Transparent
    }
    val fg = when {
        primary -> c.onAccent
        danger -> c.danger
        else -> c.text
    }
    Row(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .then(if (primary) Modifier else Modifier.border(BorderStroke(1.dp, c.border), RoundedCornerShape(7.dp)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = fg.copy(alpha = if (enabled) 1f else 0.4f))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text, color = fg.copy(alpha = if (enabled) 1f else 0.4f),
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = A.c.accent) {
    Text(
        text,
        modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        color = color,
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        color = A.c.textDim,
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
    )
}

/** Compact single-line input with an optional hint and trailing content. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    mono: Boolean = false,
    singleLine: Boolean = true,
    password: Boolean = false,
    focus: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = A.c
    val style = TextStyle(
        color = c.text,
        fontSize = 13.sp,
        fontFamily = if (mono) CodeFont else UiFont,
    )
    Row(
        modifier
            .height(if (singleLine) 38.dp else 90.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        Box(
            Modifier
                .weight(1f)
                .padding(vertical = if (singleLine) 0.dp else 9.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) {
            if (value.isEmpty() && hint.isNotEmpty()) {
                Text(hint, color = c.textFaint, style = style, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = style,
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = imeAction, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
                modifier = (if (focus != null) Modifier.focusRequester(focus) else Modifier).fillMaxWidth(),
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = A.c
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = c.text, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        Box(
            Modifier
                .width(34.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) c.accent else c.border),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (checked) c.onAccent else c.textDim)
            )
        }
    }
}

/** A row of mutually exclusive options. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = A.c
    Row(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, c.border, RoundedCornerShape(7.dp))
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(if (i == selected) c.selected else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (i == selected) c.text else c.textDim,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun PromptDialog(
    title: String,
    initial: String = "",
    hint: String = "",
    confirm: String = "OK",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.substringBeforeLast('.', initial).length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = A.c.raised,
        title = { Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = A.c.text) },
        text = {
            Field(
                value = value.text,
                onValueChange = { value = TextFieldValue(it, TextRange(it.length)) },
                hint = hint,
                focus = focus,
                imeAction = ImeAction.Done,
                onImeAction = { if (value.text.isNotBlank()) onConfirm(value.text.trim()) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextAction(confirm, { if (value.text.isNotBlank()) onConfirm(value.text.trim()) }) },
        dismissButton = { TextAction("Cancel", onDismiss, color = A.c.textDim) },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = A.c.raised,
        title = { Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = A.c.text) },
        text = { Text(message, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = A.c.textDim) },
        confirmButton = { TextAction(confirm, onConfirm, color = if (danger) A.c.danger else A.c.accent) },
        dismissButton = { TextAction("Cancel", onDismiss, color = A.c.textDim) },
    )
}

@Composable
fun MessageDialog(title: String, body: String, mono: Boolean = false, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = A.c.raised,
        title = { Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = A.c.text) },
        text = {
            Text(
                body, color = A.c.textDim,
                style = if (mono) TextStyle(fontFamily = CodeFont, fontSize = 12.sp) else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextAction("Close", onDismiss) },
    )
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        color = A.c.textDim,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun PanelHeader(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(40.dp).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, Modifier.weight(1f), color = A.c.text,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}
