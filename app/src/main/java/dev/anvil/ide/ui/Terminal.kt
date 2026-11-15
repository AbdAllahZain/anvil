package dev.anvil.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anvil.ide.IdeViewModel
import dev.anvil.ide.PanelTab
import dev.anvil.ide.core.LineKind
import dev.anvil.ide.core.TermLine

@Composable
fun TerminalPanel(vm: IdeViewModel, modifier: Modifier = Modifier) {
    val c = A.c
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelTabLabel("Terminal", vm.panelTab == PanelTab.Terminal) { vm.panelTab = PanelTab.Terminal }
            PanelTabLabel("Output", vm.panelTab == PanelTab.Output) { vm.panelTab = PanelTab.Output }
            Spacer(Modifier.weight(1f))
            if (vm.panelTab == PanelTab.Terminal) {
                val shell = vm.shell
                IconBtn(Icons.Outlined.Stop, "Stop", { shell?.stop() }, size = 32.dp, iconSize = 17.dp, enabled = shell?.alive == true)
                IconBtn(Icons.Outlined.Refresh, "Restart", { shell?.stop(true); shell?.start() }, size = 32.dp, iconSize = 17.dp)
                IconBtn(Icons.Outlined.Delete, "Clear", { shell?.lines?.clear() }, size = 32.dp, iconSize = 17.dp)
            } else {
                IconBtn(Icons.Outlined.Delete, "Clear", { vm.clearOutput() }, size = 32.dp, iconSize = 17.dp)
            }
            IconBtn(Icons.Outlined.Close, "Close panel", { vm.panelOpen = false }, size = 32.dp, iconSize = 17.dp)
        }
        Hairline()
        when (vm.panelTab) {
            PanelTab.Terminal -> TerminalView(vm)
            PanelTab.Output -> LogList(vm.output, Modifier.fillMaxSize().background(c.bg), "Run a JavaScript file to see its console output here.")
        }
    }
}

@Composable
private fun PanelTabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = A.c
    Box(
        Modifier.height(34.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) c.text else c.textFaint,
            style = MaterialTheme.typography.labelLarge,
        )
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(c.accent))
    }
}

private val logStyle = TextStyle(fontFamily = CodeFont, fontSize = 12.sp, lineHeight = 17.sp)

@Composable
private fun LogList(lines: List<TermLine>, modifier: Modifier, empty: String) {
    val c = A.c
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) state.scrollToItem(lines.lastIndex)
    }
    if (lines.isEmpty()) {
        Box(modifier) { EmptyNote(empty) }
        return
    }
    LazyColumn(modifier, state = state) {
        itemsIndexed(lines) { _, line ->
            val color: Color = when (line.kind) {
                LineKind.Command -> c.accent
                LineKind.Output -> c.text
                LineKind.Info -> c.textFaint
                LineKind.Error -> c.danger
            }
            Text(
                line.text, Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                color = color, style = logStyle,
            )
        }
    }
}

@Composable
private fun TerminalView(vm: IdeViewModel) {
    val c = A.c
    val shell = vm.shell
    if (shell == null) {
        EmptyNote("Open a project to use the terminal.")
        return
    }
    var input by remember { mutableStateOf("") }
    fun submit() {
        val text = input
        if (text.isBlank()) return
        input = ""
        vm.ensureShell()?.exec(text)
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        LogList(
            shell.lines, Modifier.weight(1f).fillMaxWidth(),
            "Commands run in ${shell.cwd}. Android ships only a minimal toolset, so many desktop programs are not available.",
        )
        Hairline()
        Row(Modifier.fillMaxWidth().height(42.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (shell.busy) "▸" else "$"), color = if (shell.busy) c.warning else c.accent,
                style = logStyle.copy(fontSize = 13.sp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) {
                    Text(
                        if (shell.busy) "Send input to the running program" else "Type a command",
                        color = c.textFaint, style = logStyle,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = logStyle.copy(color = c.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconBtn(Icons.Outlined.KeyboardArrowUp, "Previous command", { shell.historyPrev()?.let { input = it } }, size = 36.dp, iconSize = 18.dp)
            IconBtn(Icons.Outlined.KeyboardArrowDown, "Next command", { shell.historyNext()?.let { input = it } }, size = 36.dp, iconSize = 18.dp)
        }
    }
}
