package dev.anvil.ide.ui

import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.anvil.ide.IdeViewModel
import dev.anvil.ide.PaletteMode
import dev.anvil.ide.PickerRequest
import dev.anvil.ide.PreviewRequest
import dev.anvil.ide.Section
import dev.anvil.ide.core.Markdown
import dev.anvil.ide.data.Workspace
import java.io.File

@Composable
fun Overlays(vm: IdeViewModel, dark: Boolean) {
    val c = A.c

    vm.palette?.let { PalettePopup(vm, it) }
    if (vm.settingsOpen) SettingsDialog(vm)
    vm.preview?.let { PreviewDialog(vm, it, dark) }
    vm.picker?.let { FolderPicker(vm, it) }
    vm.diff?.let { (path, text) -> DiffDialog(path, text) { vm.diff = null } }

    vm.closeRequest?.let { tab ->
        AlertDialog(
            onDismissRequest = { vm.closeRequest = null },
            containerColor = c.raised,
            title = { Text("Save changes to ${tab.file.name}?", style = MaterialTheme.typography.titleMedium, color = c.text) },
            text = { Text("Your changes will be lost if you do not save them.", style = MaterialTheme.typography.bodyMedium, color = c.textDim) },
            confirmButton = {
                Row {
                    TextAction("Discard", { vm.closeRequest = null; tab.dirty = false; vm.closeTab(tab) }, color = c.danger)
                    TextAction("Save", { vm.closeRequest = null; vm.saveNow(tab); vm.closeTab(tab) })
                }
            },
            dismissButton = { TextAction("Cancel", { vm.closeRequest = null }, color = c.textDim) },
        )
    }

    vm.newEntry?.let { (parent, isDir) ->
        PromptDialog(
            title = if (isDir) "New folder" else "New file",
            hint = if (isDir) "folder-name" else "filename.ext",
            confirm = "Create",
            onConfirm = {
                vm.newEntry = null
                if (isDir) vm.createFolder(parent, it) else vm.createFile(parent, it)
            },
            onDismiss = { vm.newEntry = null },
        )
    }

    vm.renameTarget?.let { target ->
        PromptDialog(
            title = "Rename", initial = target.name, confirm = "Rename",
            onConfirm = { vm.renameTarget = null; if (it != target.name) vm.rename(target, it) },
            onDismiss = { vm.renameTarget = null },
        )
    }

    vm.deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete ${target.name}?",
            message = if (target.isDirectory) "The folder and everything inside it will be removed. This cannot be undone."
            else "This cannot be undone.",
            confirm = "Delete", danger = true,
            onConfirm = { vm.deleteTarget = null; vm.delete(target) },
            onDismiss = { vm.deleteTarget = null },
        )
    }

    vm.busyMessage?.let { msg ->
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000)).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) {},
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(c.raised).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(msg, color = c.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    vm.toast?.let { message ->
        Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(bottom = 84.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                message,
                Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.raised)
                    .border(1.dp, c.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                color = c.text, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ------------------------------------------------------------------- palette

private class Command(val title: String, val hint: String = "", val run: () -> Unit)

private fun commands(vm: IdeViewModel): List<Command> {
    val s = vm.settings
    return listOf(
        Command("Go to file", "Ctrl+P") { vm.palette(PaletteMode.Files) },
        Command("Go to line", "Ctrl+G") { vm.palette(PaletteMode.GoToLine) },
        Command("Save", "Ctrl+S") { vm.active?.let { vm.save(it) } },
        Command("Save all") { vm.saveAll() },
        Command("Find in file", "Ctrl+F") { if (vm.active != null) vm.findOpen = true },
        Command("Find in project") { vm.section = Section.Search; vm.sidebarOpen = true },
        Command("Run current file") { vm.runActive() },
        Command("Toggle terminal", "Ctrl+`") { vm.panelOpen = !vm.panelOpen },
        Command("Toggle line comment", "Ctrl+/") { vm.toggleComment() },
        Command("Duplicate line") { vm.duplicateLine() },
        Command("Format document") { vm.formatDocument() },
        Command("New file") { vm.project?.let { vm.newEntry = it to false } },
        Command("New folder") { vm.project?.let { vm.newEntry = it to true } },
        Command("Close tab", "Ctrl+W") { vm.active?.let { vm.requestClose(it) } },
        Command("Close all tabs") { vm.closeAll() },
        Command("Toggle word wrap") { s.wordWrap = !s.wordWrap },
        Command("Toggle line numbers") { s.lineNumbers = !s.lineNumbers },
        Command("Increase font size") { s.fontSize = (s.fontSize + 1).coerceAtMost(28) },
        Command("Decrease font size") { s.fontSize = (s.fontSize - 1).coerceAtLeast(9) },
        Command("Theme: dark") { s.themeMode = 1 },
        Command("Theme: light") { s.themeMode = 2 },
        Command("Theme: follow system") { s.themeMode = 0 },
        Command("Source control") { vm.section = Section.Git; vm.sidebarOpen = true },
        Command("Settings") { vm.settingsOpen = true },
        Command("Close project") { vm.closeProject() },
    )
}

private fun fuzzy(query: String, text: String): Int {
    if (query.isEmpty()) return 0
    val t = text.lowercase()
    val nameStart = t.lastIndexOf('/') + 1
    var from = 0
    var prev = -2
    var score = 0
    for (ch in query.lowercase()) {
        val at = t.indexOf(ch, from)
        if (at < 0) return -1
        score += if (at == prev + 1) 6 else 1
        if (at == nameStart || (at > 0 && t[at - 1] in "/._- ")) score += 4
        if (at >= nameStart) score += 2
        prev = at
        from = at + 1
    }
    return score - t.length / 20
}

@Composable
private fun PalettePopup(vm: IdeViewModel, mode: PaletteMode) {
    val c = A.c
    var query by remember(mode) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(mode) { focus.requestFocus() }
    val files by produceState(emptyList<File>(), vm.project, vm.treeVersion) { value = vm.fileIndex() }

    val close = { vm.palette = null }
    val entries: List<Pair<String, () -> Unit>> = when (mode) {
        PaletteMode.Commands -> commands(vm)
            .map { it to fuzzy(query, it.title) }.filter { it.second >= 0 }
            .sortedByDescending { it.second }
            .map { (cmd, _) -> (if (cmd.hint.isEmpty()) cmd.title else cmd.title + "\u0000" + cmd.hint) to { close(); cmd.run() } }
        PaletteMode.Files -> files
            .map { Workspace.relative(vm.project, it).replace('\\', '/') to it }
            .map { Triple(it.first, it.second, fuzzy(query, it.first)) }.filter { it.third >= 0 }
            .sortedByDescending { it.third }.take(60)
            .map { (rel, file, _) -> rel to { close(); vm.openFile(file) } }
        PaletteMode.GoToLine -> emptyList()
    }
    val openInput = interactionSourceless()

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = openInput, indication = null) { close() }.padding(top = 70.dp, start = 12.dp, end = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.raised)
                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            ) {
                Field(
                    query, { query = it },
                    Modifier.fillMaxWidth().padding(10.dp), focus = focus,
                    hint = when (mode) {
                        PaletteMode.Commands -> "Type a command"
                        PaletteMode.Files -> "Search files by name"
                        PaletteMode.GoToLine -> "Line number"
                    },
                    imeAction = ImeAction.Go,
                    onImeAction = {
                        if (mode == PaletteMode.GoToLine) {
                            query.trim().toIntOrNull()?.let { n -> close(); vm.goToLine(n) }
                        } else entries.firstOrNull()?.second?.invoke()
                    },
                )
                if (mode == PaletteMode.GoToLine) {
                    val total = vm.active?.editor?.text?.lineCount
                    Text(
                        if (total != null) "Enter a line between 1 and $total, then press Go." else "Open a file first.",
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = c.textDim, style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Hairline()
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(entries) { (label, action) ->
                            val parts = label.split('\u0000')
                            Row(
                                Modifier.fillMaxWidth().clickable { action() }.padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (mode == PaletteMode.Files) {
                                    val name = parts[0].substringAfterLast('/')
                                    val dir = parts[0].substringBeforeLast('/', "")
                                    Text(name, color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    if (dir.isNotEmpty()) {
                                        Text(
                                            "   $dir", Modifier.weight(1f), color = c.textFaint,
                                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                } else {
                                    Text(parts[0], Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.bodyMedium)
                                    if (parts.size > 1) {
                                        Text(parts[1], color = c.textFaint, style = TextStyle(fontFamily = CodeFont, fontSize = 11.sp))
                                    }
                                }
                            }
                        }
                    }
                    if (entries.isEmpty()) EmptyNote("Nothing matches.")
                }
            }
        }
    }
}

@Composable
private fun interactionSourceless(): MutableInteractionSource = remember { MutableInteractionSource() }

// ------------------------------------------------------------------ settings

@Composable
private fun SettingsDialog(vm: IdeViewModel) {
    val c = A.c
    val s = vm.settings
    Dialog(
        onDismissRequest = { vm.settingsOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(c.surface).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Outlined.ArrowBack, "Back", { vm.settingsOpen = false })
                Text("Settings", color = c.text, style = MaterialTheme.typography.titleMedium)
            }
            Hairline()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).widthIn(max = 640.dp)) {
                SectionLabel("Appearance")
                Segmented(
                    listOf("System", "Dark", "Light"), s.themeMode, { s.themeMode = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Editor font size", Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.bodyMedium)
                    IconBtn(Icons.Outlined.Remove, "Smaller", { s.fontSize = (s.fontSize - 1).coerceAtLeast(9) }, size = 34.dp, iconSize = 18.dp)
                    Text("${s.fontSize}", Modifier.width(30.dp), color = c.text, style = TextStyle(fontFamily = CodeFont, fontSize = 14.sp))
                    IconBtn(Icons.Outlined.Add, "Larger", { s.fontSize = (s.fontSize + 1).coerceAtMost(28) }, size = 34.dp, iconSize = 18.dp)
                }
                Toggle("Font ligatures", s.ligatures) { s.ligatures = it }

                SectionLabel("Editor")
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tab width", Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.bodyMedium)
                    val sizes = listOf(2, 4, 8)
                    Segmented(sizes.map { "$it" }, sizes.indexOf(s.tabSize).coerceAtLeast(0), { s.tabSize = sizes[it] }, Modifier.width(150.dp))
                }
                Toggle("Word wrap", s.wordWrap) { s.wordWrap = it }
                Toggle("Line numbers", s.lineNumbers) { s.lineNumbers = it }
                Toggle("Highlight current line", s.highlightLine) { s.highlightLine = it }
                Toggle("Indent guides", s.indentGuides) { s.indentGuides = it }
                Toggle("Auto-close brackets and quotes", s.autoClosePairs) { s.autoClosePairs = it }
                Toggle("Sticky scroll", s.stickyScroll) { s.stickyScroll = it }
                Toggle("Show whitespace", s.showWhitespace) { s.showWhitespace = it }
                Toggle("Auto-save after typing", s.autoSave) { s.autoSave = it }

                SectionLabel("Files")
                Toggle("Show hidden files", s.showHidden) { s.showHidden = it; vm.treeVersion++ }

                SectionLabel("Git identity")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(s.gitName, { s.gitName = it }, Modifier.fillMaxWidth(), hint = "Name for commits")
                    Field(s.gitEmail, { s.gitEmail = it }, Modifier.fillMaxWidth(), hint = "Email for commits")
                    Field(s.gitUser, { s.gitUser = it }, Modifier.fillMaxWidth(), hint = "Username for push and pull")
                    Field(s.gitToken, { s.gitToken = it }, Modifier.fillMaxWidth(), hint = "Access token", password = true)
                    Text(
                        "The token is stored on this device only, in the app’s private storage.",
                        color = c.textFaint, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ------------------------------------------------------------------- preview

@Composable
private fun PreviewDialog(vm: IdeViewModel, req: PreviewRequest, dark: Boolean) {
    val c = A.c
    var reload by remember { mutableIntStateOf(0) }
    Dialog(
        onDismissRequest = { vm.preview = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(46.dp).background(c.surface), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Outlined.Close, "Close preview", { vm.preview = null })
                Text(
                    req.file.name, Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                IconBtn(Icons.Outlined.Refresh, "Reload", { reload++ })
            }
            Hairline()
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        webViewClient = WebViewClient()
                        setBackgroundColor(if (dark) 0xFF1B1E24.toInt() else 0xFFFFFFFF.toInt())
                    }
                },
                update = { web ->
                    if (web.tag != reload) {
                        web.tag = reload
                        if (req.markdown != null) {
                            val md = runCatching { req.file.readText() }.getOrDefault(req.markdown)
                            web.loadDataWithBaseURL(
                                "file://${req.file.parent}/", Markdown.toHtml(md, dark), "text/html", "utf-8", null,
                            )
                        } else {
                            web.loadUrl("file://${req.file.absolutePath}")
                        }
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------- diff

@Composable
private fun DiffDialog(path: String, text: String, onDismiss: () -> Unit) {
    val c = A.c
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(46.dp).background(c.surface), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Outlined.Close, "Close", onDismiss)
                Text(path, Modifier.weight(1f), color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Hairline()
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(text.lines()) { _, line ->
                    val (fg, bg) = when {
                        line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ") ->
                            c.textFaint to Color.Transparent
                        line.startsWith("+") -> c.success to c.success.copy(alpha = 0.10f)
                        line.startsWith("-") -> c.danger to c.danger.copy(alpha = 0.10f)
                        line.startsWith("@@") -> c.accent to c.accent.copy(alpha = 0.08f)
                        else -> c.text to Color.Transparent
                    }
                    Text(
                        line.ifEmpty { " " },
                        Modifier.fillMaxWidth().background(bg).padding(horizontal = 12.dp, vertical = 1.dp),
                        color = fg, style = TextStyle(fontFamily = CodeFont, fontSize = 12.sp, lineHeight = 17.sp),
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------- folder picker

@Composable
private fun FolderPicker(vm: IdeViewModel, req: PickerRequest) {
    val c = A.c
    val storageRoot = remember { Environment.getExternalStorageDirectory() }
    val appRoot = remember { vm.workspaceRoot() }
    var dir by remember { mutableStateOf(appRoot) }
    var refresh by remember { mutableIntStateOf(0) }
    var newFolder by remember { mutableStateOf(false) }
    val children = remember(dir, refresh) {
        (dir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
    }

    Dialog(
        onDismissRequest = { vm.picker = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(c.surface).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Outlined.Close, "Cancel", { vm.picker = null })
                Text(req.title, color = c.text, style = MaterialTheme.typography.titleMedium)
            }
            Hairline()
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(
                    Icons.Outlined.ArrowUpward, "Parent folder",
                    { dir.parentFile?.let { dir = it } }, enabled = dir.parentFile != null,
                )
                Text(
                    dir.absolutePath, Modifier.weight(1f), color = c.textDim,
                    style = TextStyle(fontFamily = CodeFont, fontSize = 11.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Anvil folder", { dir = appRoot })
                Btn("Device storage", { dir = storageRoot })
            }
            if (!vm.storageGranted) {
                Text(
                    "File access is not granted, so most folders will look empty. You can grant it from the start screen.",
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = c.warning, style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(6.dp))
            Hairline()
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (children.isEmpty()) item { EmptyNote("No subfolders here.") }
                items(children, key = { it.absolutePath }) { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { dir = f }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Folder, null, Modifier.size(20.dp), tint = c.textDim)
                        Spacer(Modifier.width(12.dp))
                        Text(f.name, color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Hairline()
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn("New folder", { newFolder = true })
                Spacer(Modifier.weight(1f))
                Btn("Use this folder", { vm.picker = null; req.onPick(dir) }, primary = true)
            }
        }
    }

    if (newFolder) {
        PromptDialog(
            title = "New folder", hint = "folder-name", confirm = "Create",
            onConfirm = { name ->
                newFolder = false
                val f = File(dir, name)
                if (f.mkdirs()) { dir = f } else vm.toast("Could not create the folder")
                refresh++
            },
            onDismiss = { newFolder = false },
        )
    }
}
