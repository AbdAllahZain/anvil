package dev.anvil.ide.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anvil.ide.IdeViewModel
import dev.anvil.ide.Section
import dev.anvil.ide.data.Languages
import dev.anvil.ide.data.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Activity rail plus the content for the selected section. */
@Composable
fun Sidebar(vm: IdeViewModel, modifier: Modifier = Modifier, onFileOpened: () -> Unit = {}) {
    val c = A.c
    Row(modifier.background(c.surface)) {
        Column(
            Modifier.width(48.dp).fillMaxHeight().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailButton(Icons.Outlined.Folder, "Explorer", vm.section == Section.Explorer) { vm.section = Section.Explorer }
            RailButton(Icons.Outlined.Search, "Search", vm.section == Section.Search) { vm.section = Section.Search }
            RailButton(Icons.Outlined.CallSplit, "Source control", vm.section == Section.Git) { vm.section = Section.Git }
            Spacer(Modifier.weight(1f))
            RailButton(Icons.Outlined.Settings, "Settings", false) { vm.settingsOpen = true }
        }
        VHairline()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (vm.section) {
                Section.Explorer -> ExplorerPanel(vm, onFileOpened)
                Section.Search -> SearchPanel(vm, onFileOpened)
                Section.Git -> GitPanel(vm, onFileOpened)
            }
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = A.c
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (selected) {
            Box(Modifier.align(Alignment.CenterStart).width(2.dp).height(22.dp).background(c.accent))
        }
        IconBtn(icon, label, onClick, tint = if (selected) c.text else c.textFaint)
    }
}

private class Row_(val file: File, val depth: Int)

private fun extensionColor(file: File, c: AnvilColors): Color = when (file.extension.lowercase()) {
    "kt", "kts" -> Color(0xFFB07CE8)
    "java" -> Color(0xFFE08A5B)
    "js", "mjs", "ts", "tsx", "jsx" -> Color(0xFFE2B15C)
    "py" -> Color(0xFF5E9BD6)
    "html", "htm", "xml", "svg" -> Color(0xFFE5707A)
    "css", "scss" -> Color(0xFF5A9CF8)
    "json", "yml", "yaml", "toml" -> Color(0xFF8FBF7F)
    "md" -> Color(0xFF6FC9C0)
    else -> c.textDim
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerPanel(vm: IdeViewModel, onFileOpened: () -> Unit) {
    val c = A.c
    val root = vm.project ?: return
    val expandedPaths = vm.expanded.toList()
    val rows by produceState(emptyList<Row_>(), root, expandedPaths, vm.treeVersion, vm.settings.showHidden) {
        value = withContext(Dispatchers.IO) {
            val out = ArrayList<Row_>()
            fun walk(dir: File, depth: Int) {
                for (f in Workspace.listChildren(dir, vm.settings.showHidden)) {
                    out.add(Row_(f, depth))
                    if (f.isDirectory && f.absolutePath in expandedPaths) walk(f, depth + 1)
                }
            }
            walk(root, 0)
            out
        }
    }
    var menuFor by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        PanelHeader(root.name) {
            IconBtn(Icons.Outlined.NoteAdd, "New file", { vm.newEntry = root to false }, size = 34.dp, iconSize = 18.dp)
            IconBtn(Icons.Outlined.CreateNewFolder, "New folder", { vm.newEntry = root to true }, size = 34.dp, iconSize = 18.dp)
            IconBtn(Icons.Outlined.Refresh, "Refresh", { vm.treeVersion++ }, size = 34.dp, iconSize = 18.dp)
            IconBtn(Icons.Outlined.UnfoldLess, "Collapse all", { vm.expanded.clear() }, size = 34.dp, iconSize = 18.dp)
        }
        Hairline()
        if (rows.isEmpty()) {
            EmptyNote("This folder is empty. Create a file with the button above.")
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.file.absolutePath }) { row ->
                val f = row.file
                val isOpen = f.isDirectory && f.absolutePath in expandedPaths
                val selected = vm.activeKey == f.absolutePath
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(if (selected) c.selected else Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    if (f.isDirectory) vm.toggleExpanded(f) else {
                                        vm.openFile(f)
                                        onFileOpened()
                                    }
                                },
                                onLongClick = { menuFor = f },
                            )
                            .padding(start = (8 + row.depth * 14).dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (f.isDirectory) {
                            Icon(
                                if (isOpen) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                                null, Modifier.size(16.dp), tint = c.textFaint,
                            )
                            Icon(
                                if (isOpen) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                                null, Modifier.size(18.dp), tint = c.textDim,
                            )
                        } else {
                            Spacer(Modifier.width(16.dp))
                            Icon(
                                if (Languages.isImage(f)) Icons.Outlined.Image else Icons.Outlined.Description,
                                null, Modifier.size(18.dp), tint = extensionColor(f, c),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            f.name,
                            color = if (selected) c.text else c.text.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(expanded = menuFor == f, onDismissRequest = { menuFor = null }, containerColor = c.raised) {
                        val target = f
                        val parent = if (target.isDirectory) target else target.parentFile ?: root
                        MenuRow("New file", c.text) { menuFor = null; vm.newEntry = parent to false }
                        MenuRow("New folder", c.text) { menuFor = null; vm.newEntry = parent to true }
                        MenuRow("Rename", c.text) { menuFor = null; vm.renameTarget = target }
                        MenuRow("Duplicate", c.text) { menuFor = null; vm.duplicate(target) }
                        MenuRow("Open terminal here", c.text) { menuFor = null; vm.openTerminalAt(target) }
                        MenuRow("Copy path", c.text) {
                            menuFor = null
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("path", target.absolutePath))
                            vm.toast("Path copied")
                        }
                        MenuRow("Delete", c.danger) { menuFor = null; vm.deleteTarget = target }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = color, style = MaterialTheme.typography.bodyMedium) },
        onClick = onClick,
    )
}
