package dev.anvil.ide.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anvil.ide.IdeViewModel
import dev.anvil.ide.PickerRequest
import dev.anvil.ide.data.Templates
import dev.anvil.ide.data.Workspace
import java.io.File

@Composable
fun HomeScreen(vm: IdeViewModel) {
    val c = A.c
    val context = LocalContext.current
    var newProject by remember { mutableStateOf(false) }
    var clone by remember { mutableStateOf(false) }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.storageGranted = true
    }
    val recents = vm.settings.recents.map { File(it) }.filter { it.isDirectory }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Text("Anvil", color = c.text, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "A code editor with a terminal and Git, built for your phone.",
            color = c.textDim, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Open folder", {
                vm.picker = PickerRequest("Open folder") { vm.openProject(it) }
            }, primary = true, icon = Icons.Outlined.FolderOpen)
            Btn("New project", { newProject = true }, icon = Icons.Outlined.CreateNewFolder)
            Btn("Clone", { clone = true }, icon = Icons.Outlined.Download)
        }

        if (!vm.storageGranted) {
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, c.border, RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                Text("Allow access to files", color = c.text, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Without it you can only work inside the app’s own folder. Grant access to open projects anywhere on your storage.",
                    color = c.textDim, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Btn("Grant access", {
                    if (Build.VERSION.SDK_INT >= 30) {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    } else {
                        legacyPermission.launch(
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        )
                    }
                })
            }
        }

        Spacer(Modifier.height(26.dp))
        Text("Recent", color = c.textDim, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        if (recents.isEmpty()) {
            Text(
                "Projects you open will show up here.",
                color = c.textFaint, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).widthIn(max = 640.dp)) {
            items(recents, key = { it.absolutePath }) { dir ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { vm.openProject(dir) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Folder, null, tint = c.textDim)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(dir.name, color = c.text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            dir.absolutePath, color = c.textFaint, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconBtn(Icons.Outlined.Close, "Remove from recent", { vm.settings.removeRecent(dir.absolutePath) }, tint = c.textFaint, size = 34.dp, iconSize = 16.dp)
                }
            }
        }
    }

    if (newProject) NewProjectDialog(vm) { newProject = false }
    if (clone) CloneDialog(vm) { clone = false }
}

@Composable
private fun NewProjectDialog(vm: IdeViewModel, onDismiss: () -> Unit) {
    val c = A.c
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(Templates.all.first().id) }
    var parent by remember { mutableStateOf(vm.workspaceRoot()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.raised,
        title = { Text("New project", style = MaterialTheme.typography.titleMedium, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, Modifier.fillMaxWidth(), hint = "Project name")
                Column(
                    Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, c.border, RoundedCornerShape(8.dp))
                ) {
                    Templates.all.forEach { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (t.id == template) c.selected else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { template = t.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t.title, color = c.text, style = MaterialTheme.typography.labelLarge)
                            Text("   " + t.blurb, color = c.textFaint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        parent.absolutePath, Modifier.weight(1f), color = c.textDim,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    TextAction("Change", {
                        vm.picker = PickerRequest("Choose location") { parent = it }
                    })
                }
            }
        },
        confirmButton = {
            TextAction("Create", {
                val clean = name.trim()
                if (clean.isNotEmpty() && !clean.contains('/')) {
                    vm.createProject(parent, clean, template)
                    onDismiss()
                }
            })
        },
        dismissButton = { TextAction("Cancel", onDismiss, color = c.textDim) },
    )
}

@Composable
private fun CloneDialog(vm: IdeViewModel, onDismiss: () -> Unit) {
    val c = A.c
    var url by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf(vm.workspaceRoot()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.raised,
        title = { Text("Clone repository", style = MaterialTheme.typography.titleMedium, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(url, { url = it }, Modifier.fillMaxWidth(), hint = "https://github.com/user/repo.git", imeAction = ImeAction.Done)
                Text(
                    "Private repositories need a username and token in Settings.",
                    color = c.textFaint, style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        parent.absolutePath, Modifier.weight(1f), color = c.textDim,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    TextAction("Change", { vm.picker = PickerRequest("Clone into") { parent = it } })
                }
            }
        },
        confirmButton = {
            TextAction("Clone", {
                val clean = url.trim()
                if (clean.isNotEmpty()) {
                    val name = clean.trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "repository" }
                    vm.cloneRepository(clean, parent, name)
                    onDismiss()
                }
            })
        },
        dismissButton = { TextAction("Cancel", onDismiss, color = c.textDim) },
    )
}
