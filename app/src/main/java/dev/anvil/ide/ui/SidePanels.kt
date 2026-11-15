package dev.anvil.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anvil.ide.IdeViewModel
import dev.anvil.ide.core.GitChange
import dev.anvil.ide.data.Workspace
import kotlinx.coroutines.delay
import java.io.File

// ---------------------------------------------------------------------- search

@Composable
fun SearchPanel(vm: IdeViewModel, onFileOpened: () -> Unit) {
    val c = A.c
    var query by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf(false) }

    LaunchedEffect(query, matchCase, regex, vm.project) {
        delay(350)
        vm.search(query, matchCase, regex)
    }

    Column(Modifier.fillMaxSize()) {
        PanelHeader("Search")
        Field(
            query, { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            hint = "Find in project",
            imeAction = ImeAction.Search,
            onImeAction = { vm.search(query, matchCase, regex) },
            trailing = {
                ToggleChip("Aa", matchCase) { matchCase = !matchCase }
                Spacer(Modifier.width(4.dp))
                ToggleChip(".*", regex) { regex = !regex }
            },
        )
        Spacer(Modifier.height(8.dp))
        vm.searchNote?.let {
            Text(it, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), color = c.textDim, style = MaterialTheme.typography.bodySmall)
        }
        if (vm.searching) {
            Text("Searching…", Modifier.padding(horizontal = 14.dp, vertical = 4.dp), color = c.textFaint, style = MaterialTheme.typography.bodySmall)
        }
        Hairline()
        val grouped = vm.searchResults.groupBy { it.file }
        LazyColumn(Modifier.fillMaxSize()) {
            grouped.forEach { (file, hits) ->
                item(key = file.absolutePath) {
                    Row(
                        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Workspace.relative(vm.project, file), Modifier.weight(1f),
                            color = c.text, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text("${hits.size}", color = c.textFaint, style = MaterialTheme.typography.labelSmall)
                    }
                }
                items(hits, key = { "${file.absolutePath}:${it.line}:${it.column}" }) { hit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.openFile(hit.file, hit.line, hit.column)
                                onFileOpened()
                            }
                            .padding(start = 20.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    ) {
                        Text(
                            "${hit.line}", Modifier.width(34.dp), color = c.textFaint,
                            style = TextStyle(fontFamily = CodeFont, fontSize = 11.sp),
                        )
                        Text(
                            hit.text, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontFamily = CodeFont, fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = A.c
    Box(
        Modifier
            .size(width = 28.dp, height = 24.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (on) c.selected else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (on) c.accent else c.textDim,
            style = TextStyle(fontFamily = CodeFont, fontSize = 12.sp),
        )
    }
}

// ------------------------------------------------------------------------- git

private fun kindColor(kind: Char, c: AnvilColors): Color = when (kind) {
    'A', '?' -> c.success
    'D' -> c.danger
    'U' -> c.warning
    else -> c.accent
}

@Composable
fun GitPanel(vm: IdeViewModel, onFileOpened: () -> Unit) {
    val c = A.c
    LaunchedEffect(vm.project, vm.gitVersion) { vm.refreshGit() }
    var message by remember { mutableStateOf("") }
    var branchMenu by remember { mutableStateOf(false) }
    var newBranch by remember { mutableStateOf(false) }
    val snap = vm.gitSnap

    Column(Modifier.fillMaxSize()) {
        PanelHeader("Source control") {
            if (vm.isRepo) {
                IconBtn(Icons.Outlined.Download, "Pull", { vm.gitPull() }, size = 34.dp, iconSize = 18.dp, enabled = !vm.gitBusy)
                IconBtn(Icons.Outlined.Upload, "Push", { vm.gitPush() }, size = 34.dp, iconSize = 18.dp, enabled = !vm.gitBusy)
            }
            IconBtn(Icons.Outlined.Refresh, "Refresh", { vm.refreshGit() }, size = 34.dp, iconSize = 18.dp)
        }
        Hairline()

        if (!vm.isRepo) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This folder is not a Git repository yet.",
                    color = c.textDim, style = MaterialTheme.typography.bodyMedium,
                )
                Btn("Initialize repository", { vm.gitInit() }, primary = true)
            }
            vm.gitError?.let { GitError(it) }
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { branchMenu = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CallSplit, null, Modifier.size(15.dp), tint = c.textDim)
                    Spacer(Modifier.width(6.dp))
                    Text(snap?.branch ?: vm.branch ?: "", color = c.text, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = c.textFaint)
                }
                DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }, containerColor = c.raised) {
                    snap?.branches?.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b, color = c.text, style = MaterialTheme.typography.bodyMedium) },
                            onClick = { branchMenu = false; vm.gitCheckout(b, false) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("New branch…", color = c.accent, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { branchMenu = false; newBranch = true },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (vm.gitBusy) Text("Working…", color = c.textFaint, style = MaterialTheme.typography.labelSmall)
        }

        vm.gitError?.let { GitError(it) }

        Field(
            message, { message = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            hint = "Commit message", singleLine = false,
        )
        Spacer(Modifier.height(8.dp))
        val staged = snap?.changes?.filter { it.staged } ?: emptyList()
        val unstaged = snap?.changes?.filter { !it.staged } ?: emptyList()
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(
                "Commit", { vm.gitCommit(message.trim()); message = "" },
                primary = true, enabled = staged.isNotEmpty() && message.isNotBlank() && !vm.gitBusy,
            )
            Btn("Stage all", { vm.gitStageAll() }, enabled = unstaged.isNotEmpty() && !vm.gitBusy)
        }
        Spacer(Modifier.height(8.dp))
        Hairline()

        LazyColumn(Modifier.fillMaxSize()) {
            if (staged.isNotEmpty()) {
                item { SectionLabel("Staged (${staged.size})") }
                items(staged, key = { "s:" + it.path }) { ch -> ChangeRow(vm, ch, onFileOpened) }
            }
            item { SectionLabel("Changes (${unstaged.size})") }
            if (unstaged.isEmpty()) item { EmptyNote("The working tree is clean.") }
            items(unstaged, key = { "u:" + it.path }) { ch -> ChangeRow(vm, ch, onFileOpened) }

            val commits = snap?.commits ?: emptyList()
            if (commits.isNotEmpty()) {
                item { SectionLabel("History") }
                items(commits, key = { "c:" + it.hash }) { cm ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(cm.message, color = c.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${cm.hash}  ${cm.author}  ${cm.time}",
                            color = c.textFaint, style = TextStyle(fontFamily = CodeFont, fontSize = 10.5.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (newBranch) {
        PromptDialog(
            title = "New branch", hint = "feature/my-change", confirm = "Create",
            onConfirm = { newBranch = false; vm.gitCheckout(it, true) },
            onDismiss = { newBranch = false },
        )
    }
}

@Composable
private fun GitError(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        color = A.c.danger, style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ChangeRow(vm: IdeViewModel, ch: GitChange, onFileOpened: () -> Unit) {
    val c = A.c
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.showDiff(ch) }
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            ch.kind.toString(), Modifier.width(18.dp), color = kindColor(ch.kind, c),
            style = TextStyle(fontFamily = CodeFont, fontSize = 12.sp),
        )
        Text(
            ch.path, Modifier.weight(1f).padding(vertical = 9.dp), color = c.text,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (!ch.staged) {
            IconBtn(Icons.Outlined.Undo, "Discard", { vm.gitDiscard(ch) }, size = 32.dp, iconSize = 16.dp, tint = c.textFaint)
        }
        IconBtn(
            if (ch.staged) Icons.Outlined.Remove else Icons.Outlined.Add,
            if (ch.staged) "Unstage" else "Stage",
            {
                val file = File(vm.project, ch.path)
                if (ch.staged) vm.gitDo { unstage(ch.path); null }
                else vm.gitDo { stage(ch.path, ch.kind == 'D' && !file.exists()); null }
            },
            size = 32.dp, iconSize = 16.dp, tint = c.textDim,
        )
    }
}
