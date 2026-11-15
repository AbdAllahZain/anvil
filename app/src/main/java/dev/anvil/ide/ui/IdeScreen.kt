package dev.anvil.ide.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anvil.ide.EditorTab
import dev.anvil.ide.IdeViewModel
import dev.anvil.ide.PaletteMode
import dev.anvil.ide.Section
import dev.anvil.ide.data.Workspace
import kotlinx.coroutines.launch

@Composable
fun IdeScreen(vm: IdeViewModel, dark: Boolean) {
    val c = A.c
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                if (vm.sidebarOpen) {
                    Sidebar(vm, Modifier.width(310.dp).fillMaxHeight())
                    VHairline()
                }
                Workbench(vm, dark, { vm.sidebarOpen = !vm.sidebarOpen }, Modifier.weight(1f))
            }
        } else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = drawer.isOpen,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(330.dp),
                        drawerShape = RectangleShape,
                        drawerContainerColor = c.surface,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        Sidebar(vm, Modifier.fillMaxSize(), onFileOpened = { scope.launch { drawer.close() } })
                    }
                },
            ) {
                Workbench(vm, dark, { scope.launch { drawer.open() } }, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun Workbench(vm: IdeViewModel, dark: Boolean, onMenu: () -> Unit, modifier: Modifier) {
    val c = A.c
    var panelHeight by remember { mutableStateOf(240.dp) }
    val tab = vm.active
    Column(modifier.background(c.bg)) {
        TopBar(vm, onMenu)
        Hairline()
        if (vm.tabs.isNotEmpty()) {
            TabStrip(vm)
            Hairline()
        }
        if (tab != null) Breadcrumbs(vm, tab)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab != null) EditorPane(vm, tab, dark, Modifier.fillMaxSize()) else EmptyWorkbench(vm)
        }
        if (vm.findOpen && tab != null && !tab.isImage) {
            Hairline()
            FindBar(vm, tab)
        }
        if (tab != null && !tab.isImage) {
            Hairline()
            SymbolBar(vm)
        }
        if (vm.panelOpen) {
            BottomPanel(vm, panelHeight) { panelHeight = (panelHeight - it).coerceIn(140.dp, 560.dp) }
        }
        Hairline()
        StatusBar(vm, tab)
    }
}

// ---------------------------------------------------------------------- top bar

@Composable
private fun TopBar(vm: IdeViewModel, onMenu: () -> Unit) {
    val c = A.c
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(46.dp).background(c.surface).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Outlined.Menu, "Files", onMenu)
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable { vm.palette(PaletteMode.Files) }
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                vm.project?.name ?: "", color = c.text, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val hasActive = vm.active != null
        IconBtn(Icons.Outlined.Search, "Find in file", { vm.findOpen = !vm.findOpen }, enabled = hasActive && vm.active?.isImage == false)
        IconBtn(Icons.Outlined.PlayArrow, "Run", { vm.runActive() }, tint = if (hasActive) c.success else c.textDim, enabled = hasActive)
        IconBtn(Icons.Outlined.Terminal, "Terminal", { vm.panelOpen = !vm.panelOpen }, tint = if (vm.panelOpen) c.accent else c.textDim)
        Box {
            IconBtn(Icons.Outlined.MoreVert, "More", { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.raised) {
                @Composable
                fun item(label: String, action: () -> Unit) = DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text) },
                    onClick = { menu = false; action() },
                )
                item("Command palette") { vm.palette(PaletteMode.Commands) }
                item("Go to file") { vm.palette(PaletteMode.Files) }
                item("Go to line") { vm.palette(PaletteMode.GoToLine) }
                item("Save all") { vm.saveAll() }
                item("Format document") { vm.formatDocument() }
                item("Toggle line comment") { vm.toggleComment() }
                item("Settings") { vm.settingsOpen = true }
                item("Close project") { vm.closeProject() }
            }
        }
    }
}

// ------------------------------------------------------------------------- tabs

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabStrip(vm: IdeViewModel) {
    val c = A.c
    var menuFor by remember { mutableStateOf<EditorTab?>(null) }
    LazyRow(Modifier.fillMaxWidth().height(38.dp).background(c.surface)) {
        items(vm.tabs, key = { it.key }) { tab ->
            val selected = tab.key == vm.activeKey
            val duplicate = vm.tabs.count { it.file.name == tab.file.name } > 1
            Box {
                Row(
                    Modifier
                        .fillMaxHeight()
                        .background(if (selected) c.bg else c.surface)
                        .combinedClickable(
                            onClick = { vm.activeKey = tab.key },
                            onLongClick = { menuFor = tab },
                        )
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tab.file.name,
                        color = if (selected) c.text else c.textDim,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    if (duplicate) {
                        Text(
                            "  " + (tab.file.parentFile?.name ?: ""),
                            color = c.textFaint, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        )
                    }
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).clickable { vm.requestClose(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tab.dirty) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c.warning))
                        } else {
                            Icon(
                                Icons.Outlined.Close, "Close", Modifier.size(14.dp),
                                tint = if (selected) c.textDim else c.textFaint,
                            )
                        }
                    }
                }
                if (selected) {
                    Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(2.dp).background(c.accent))
                }
                DropdownMenu(expanded = menuFor == tab, onDismissRequest = { menuFor = null }, containerColor = c.raised) {
                    DropdownMenuItem(
                        text = { Text("Close", color = c.text, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { menuFor = null; vm.requestClose(tab) },
                    )
                    DropdownMenuItem(
                        text = { Text("Close others", color = c.text, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { menuFor = null; vm.closeOthers(tab) },
                    )
                    DropdownMenuItem(
                        text = { Text("Close all", color = c.text, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { menuFor = null; vm.closeAll() },
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(vm: IdeViewModel, tab: EditorTab) {
    val c = A.c
    val parts = Workspace.relative(vm.project, tab.file).split('/', '\\')
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(c.bg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.forEachIndexed { i, part ->
            if (i > 0) Text("  ›  ", color = c.textFaint, style = MaterialTheme.typography.bodySmall)
            Text(
                part,
                color = if (i == parts.lastIndex) c.textDim else c.textFaint,
                style = MaterialTheme.typography.bodySmall, maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyWorkbench(vm: IdeViewModel) {
    val c = A.c
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No file open", color = c.text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick a file from the explorer, or jump straight to one.",
            color = c.textDim, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Go to file", { vm.palette(PaletteMode.Files) }, primary = true)
            Btn("New file", { vm.project?.let { vm.newEntry = it to false } })
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "Keyboard: Ctrl+P go to file, Ctrl+Shift+P commands, Ctrl+S save, Ctrl+F find",
            color = c.textFaint, style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ------------------------------------------------------------------ symbol bar

private class Sym(val label: String, val text: String, val offset: Int = text.length)

private val symbols = listOf(
    Sym("{ }", "{}", 1), Sym("( )", "()", 1), Sym("[ ]", "[]", 1), Sym("< >", "<>", 1),
    Sym("\" \"", "\"\"", 1), Sym("' '", "''", 1), Sym(";", ";"), Sym(":", ":"), Sym("=", "="),
    Sym("+", "+"), Sym("-", "-"), Sym("*", "*"), Sym("/", "/"), Sym("\\", "\\"), Sym("|", "|"),
    Sym("&", "&"), Sym("!", "!"), Sym("_", "_"), Sym("#", "#"), Sym("$", "$"), Sym("%", "%"),
    Sym(".", "."), Sym(",", ","), Sym("?", "?"), Sym("`", "`"), Sym("~", "~"), Sym("@", "@"), Sym("^", "^"),
)

@Composable
private fun SymbolBar(vm: IdeViewModel) {
    val c = A.c
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(c.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Outlined.Undo, "Undo", { vm.undo() }, size = 38.dp, iconSize = 18.dp)
        IconBtn(Icons.Outlined.Redo, "Redo", { vm.redo() }, size = 38.dp, iconSize = 18.dp)
        VHairline(Modifier.height(20.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.height(40.dp).width(46.dp).clickable { vm.insert(vm.indentString()) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Tab", color = c.textDim, style = MaterialTheme.typography.labelMedium)
            }
            symbols.forEach { s ->
                Box(
                    Modifier.height(40.dp).width(38.dp).clickable { vm.insert(s.text, s.offset) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.label, color = c.text, style = TextStyle(fontFamily = CodeFont, fontSize = 15.sp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ status bar

@Composable
private fun StatusBar(vm: IdeViewModel, tab: EditorTab?) {
    val c = A.c
    val style = MaterialTheme.typography.labelSmall
    Row(
        Modifier.fillMaxWidth().height(26.dp).background(c.surface).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val branch = vm.branch
        if (branch != null) {
            Row(
                Modifier.clip(RoundedCornerShape(5.dp)).clickable { vm.section = Section.Git; vm.sidebarOpen = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CallSplit, null, Modifier.size(13.dp), tint = c.textDim)
                Spacer(Modifier.width(4.dp))
                Text(branch, color = c.textDim, style = style, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        if (tab != null && !tab.isImage) {
            Text(
                "Ln ${tab.line}, Col ${tab.col}", color = c.textDim, style = style,
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable { vm.palette(PaletteMode.GoToLine) }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text("Spaces ${vm.settings.tabSize}", color = c.textDim, style = style, modifier = Modifier.padding(horizontal = 8.dp))
            Text("UTF-8", color = c.textDim, style = style, modifier = Modifier.padding(horizontal = 8.dp))
            Text(tab.lang.name, color = c.textDim, style = style, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

// ------------------------------------------------------------------ bottom panel

@Composable
private fun BottomPanel(vm: IdeViewModel, height: Dp, onResize: (Dp) -> Unit) {
    val c = A.c
    Column(Modifier.fillMaxWidth().height(height).background(c.surface)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy -> onResize(dy.toDp()) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.border))
        }
        TerminalPanel(vm, Modifier.weight(1f))
    }
}
