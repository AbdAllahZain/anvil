package dev.anvil.ide

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.anvil.ide.core.EditorSetup
import dev.anvil.ide.core.GitChange
import dev.anvil.ide.core.GitService
import dev.anvil.ide.core.GitSnapshot
import dev.anvil.ide.core.JsRunner
import dev.anvil.ide.core.LineKind
import dev.anvil.ide.core.ShellSession
import dev.anvil.ide.core.TermLine
import dev.anvil.ide.data.Lang
import dev.anvil.ide.data.Languages
import dev.anvil.ide.data.Settings
import dev.anvil.ide.data.Workspace
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EditorTab(val file: File, var initialText: String?) {
    val key: String = file.absolutePath
    val lang: Lang = Languages.of(file)
    val isImage: Boolean = Languages.isImage(file)
    var editor: CodeEditor? = null
    var dirty by mutableStateOf(false)
    var line by mutableIntStateOf(1)
    var col by mutableIntStateOf(1)
    /** [line, column] requested by search results or go-to-line, applied by the editor view. */
    var jump by mutableStateOf<IntArray?>(null)
    var edits = 0
    var saveJob: Job? = null
}

enum class Section { Explorer, Search, Git }
enum class PanelTab { Terminal, Output }
enum class PaletteMode { Commands, Files, GoToLine }

class SearchHit(val file: File, val line: Int, val column: Int, val text: String)
class PreviewRequest(val file: File, val markdown: String?)
class PickerRequest(val title: String, val onPick: (File) -> Unit)

class IdeViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)

    // Project and tabs
    var project by mutableStateOf<File?>(null)
        private set
    val tabs = mutableStateListOf<EditorTab>()
    var activeKey by mutableStateOf<String?>(null)
    val active: EditorTab? get() = tabs.firstOrNull { it.key == activeKey }
    var closeRequest by mutableStateOf<EditorTab?>(null)
    var newEntry by mutableStateOf<Pair<File, Boolean>?>(null)
    var renameTarget by mutableStateOf<File?>(null)
    var deleteTarget by mutableStateOf<File?>(null)

    // Layout state
    var sidebarOpen by mutableStateOf(true)
    var section by mutableStateOf(Section.Explorer)
    var panelOpen by mutableStateOf(false)
    var panelTab by mutableStateOf(PanelTab.Terminal)
    var palette by mutableStateOf<PaletteMode?>(null)
    var findOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var preview by mutableStateOf<PreviewRequest?>(null)
    var picker by mutableStateOf<PickerRequest?>(null)
    var busyMessage by mutableStateOf<String?>(null)
    var storageGranted by mutableStateOf(Workspace.storageGranted())

    // Explorer
    val expanded = mutableStateListOf<String>()
    var treeVersion by mutableIntStateOf(0)

    // Toasts
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastId = 0

    // Terminal and output
    var shell by mutableStateOf<ShellSession?>(null)
        private set
    val output = mutableStateListOf<TermLine>()
    private val jsRunner = JsRunner(app)

    // Git
    private var git: GitService? = null
    var branch by mutableStateOf<String?>(null)
        private set
    var gitSnap by mutableStateOf<GitSnapshot?>(null)
        private set
    var gitBusy by mutableStateOf(false)
        private set
    var gitError by mutableStateOf<String?>(null)
    var diff by mutableStateOf<Pair<String, String>?>(null)
    var isRepo by mutableStateOf(false)
        private set

    // Search
    var searchResults by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchNote by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    // Quick open
    private var indexCache: List<File>? = null

    fun toast(message: String) {
        toast = message
        val id = ++toastId
        viewModelScope.launch {
            delay(2400)
            if (id == toastId) toast = null
        }
    }

    fun workspaceRoot(): File = Workspace.root(getApplication())

    // ------------------------------------------------------------------ project

    fun openProject(dir: File, restoreTabs: Boolean = false) {
        if (project != null) {
            saveAllNow()
            tabs.toList().forEach { releaseTab(it) }
            tabs.clear()
            activeKey = null
        }
        shell?.stop(silent = true)
        project = dir
        settings.lastProject = dir.absolutePath
        settings.addRecent(dir.absolutePath)
        expanded.clear()
        treeVersion++
        indexCache = null
        output.clear()
        git = GitService(dir)
        gitSnap = null
        gitError = null
        refreshGit()
        shell = ShellSession(dir, dir) {
            treeVersion++
            indexCache = null
        }
        section = Section.Explorer
        if (restoreTabs) settings.savedTabs().map { File(it) }.filter { it.isFile }.forEach { openFile(it) }
    }

    fun closeProject() {
        saveAllNow()
        tabs.toList().forEach { releaseTab(it) }
        tabs.clear()
        activeKey = null
        shell?.stop(silent = true)
        shell = null
        project = null
        git = null
        branch = null
        gitSnap = null
        settings.lastProject = ""
        settings.saveTabs(emptyList())
    }

    // --------------------------------------------------------------------- tabs

    fun openFile(file: File, line: Int? = null, column: Int = 0) {
        val existing = tabs.firstOrNull { it.key == file.absolutePath }
        if (existing != null) {
            activeKey = existing.key
            if (line != null) existing.jump = intArrayOf(line - 1, column)
            return
        }
        if (Languages.isImage(file)) {
            val tab = EditorTab(file, null)
            tabs.add(tab)
            activeKey = tab.key
            persistTabs()
            return
        }
        viewModelScope.launch {
            val result: Pair<String?, String?> = withContext(Dispatchers.IO) {
                when {
                    !file.exists() -> Pair(null, "This file no longer exists.")
                    file.length() > 8_000_000L -> Pair(null, "Files over 8 MB are not opened in the editor.")
                    Workspace.looksBinary(file) -> Pair(null, "This looks like a binary file, so it cannot be edited as text.")
                    else -> try {
                        Pair(file.readText(), null)
                    } catch (e: Exception) {
                        Pair(null, "Could not read the file: ${e.message}")
                    }
                }
            }
            val text = result.first
            val error = result.second
            if (text == null) {
                toast(error ?: "Could not open the file.")
                return@launch
            }
            if (tabs.any { it.key == file.absolutePath }) return@launch
            val tab = EditorTab(file, text)
            if (line != null) tab.jump = intArrayOf(line - 1, column)
            tabs.add(tab)
            activeKey = tab.key
            persistTabs()
        }
    }

    private fun persistTabs() {
        settings.saveTabs(tabs.map { it.key })
    }

    fun requestClose(tab: EditorTab) {
        if (tab.dirty) closeRequest = tab else closeTab(tab)
    }

    fun closeTab(tab: EditorTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        releaseTab(tab)
        tabs.removeAt(index)
        if (activeKey == tab.key) {
            activeKey = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.key
        }
        persistTabs()
    }

    fun closeOthers(keep: EditorTab) {
        tabs.filter { it !== keep }.forEach {
            if (it.dirty) saveNow(it)
            closeTab(it)
        }
        activeKey = keep.key
    }

    fun closeAll() {
        tabs.toList().forEach {
            if (it.dirty) saveNow(it)
            closeTab(it)
        }
    }

    private fun releaseTab(tab: EditorTab) {
        tab.saveJob?.cancel()
        runCatching { tab.editor?.release() }
        tab.editor = null
    }

    // ------------------------------------------------------------------- saving

    fun onEdited(tab: EditorTab) {
        tab.edits++
        tab.dirty = true
        if (settings.autoSave) {
            tab.saveJob?.cancel()
            tab.saveJob = viewModelScope.launch {
                delay(1200)
                save(tab, quiet = true)
            }
        }
    }

    private fun textOf(tab: EditorTab): String? = tab.editor?.text?.toString() ?: tab.initialText

    fun save(tab: EditorTab, quiet: Boolean = false) {
        if (tab.isImage) return
        val text = textOf(tab) ?: return
        val version = tab.edits
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { tab.file.writeText(text) } }
            if (result.isSuccess) {
                if (tab.edits == version) tab.dirty = false
                if (!quiet) toast("Saved ${tab.file.name}")
            } else {
                toast("Could not save ${tab.file.name}: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** Writes the file synchronously. Used when the process may go away right after. */
    fun saveNow(tab: EditorTab) {
        if (tab.isImage) return
        val text = textOf(tab) ?: return
        runCatching { tab.file.writeText(text) }.onSuccess { tab.dirty = false }
    }

    fun saveAllNow() {
        tabs.filter { it.dirty }.forEach { saveNow(it) }
    }

    fun saveAll() {
        val dirty = tabs.filter { it.dirty }
        if (dirty.isEmpty()) return toast("Nothing to save")
        dirty.forEach { save(it, quiet = true) }
        toast("Saved ${dirty.size} file${if (dirty.size == 1) "" else "s"}")
    }

    // -------------------------------------------------------------- file actions

    private fun afterTreeChange() {
        treeVersion++
        indexCache = null
        gitVersionBump()
    }

    fun createFile(parent: File, name: String) {
        val target = File(parent, name)
        if (target.exists()) return toast("“$name” already exists")
        val ok = runCatching {
            target.parentFile?.mkdirs()
            target.createNewFile()
        }.getOrDefault(false)
        if (!ok) return toast("Could not create the file")
        expanded.add(parent.absolutePath)
        afterTreeChange()
        openFile(target)
    }

    fun createFolder(parent: File, name: String) {
        val target = File(parent, name)
        if (target.exists()) return toast("“$name” already exists")
        if (!target.mkdirs()) return toast("Could not create the folder")
        expanded.add(parent.absolutePath)
        expanded.add(target.absolutePath)
        afterTreeChange()
    }

    fun rename(file: File, newName: String) {
        val target = File(file.parentFile, newName)
        if (target.exists()) return toast("“$newName” already exists")
        val affected = tabs.filter { it.key == file.absolutePath || it.key.startsWith(file.absolutePath + File.separator) }
        affected.forEach { if (it.dirty) saveNow(it); closeTab(it) }
        if (!file.renameTo(target)) return toast("Could not rename")
        afterTreeChange()
        if (target.isFile && affected.isNotEmpty()) openFile(target)
    }

    fun delete(file: File) {
        tabs.filter { it.key == file.absolutePath || it.key.startsWith(file.absolutePath + File.separator) }
            .forEach { closeTab(it) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { file.deleteRecursively() }
            if (!ok) toast("Could not delete ${file.name}")
            afterTreeChange()
        }
    }

    fun duplicate(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val copy = Workspace.uniqueChild(file.parentFile ?: return@withContext, file.name)
                runCatching { file.copyRecursively(copy) }
            }
            afterTreeChange()
        }
    }

    fun toggleExpanded(dir: File) {
        val p = dir.absolutePath
        if (!expanded.remove(p)) expanded.add(p)
    }

    /** Opens the project's files for quick open. Cached until the tree changes. */
    suspend fun fileIndex(): List<File> {
        indexCache?.let { return it }
        val root = project ?: return emptyList()
        val list = withContext(Dispatchers.IO) { Workspace.walk(root) }
        indexCache = list
        return list
    }

    // ----------------------------------------------------------------- run / shell

    fun ensureShell(): ShellSession? {
        val s = shell ?: return null
        if (!s.alive) s.start()
        return s
    }

    fun runInTerminal(command: String) {
        panelOpen = true
        panelTab = PanelTab.Terminal
        ensureShell()?.exec(command)
    }

    fun openTerminalAt(dir: File) {
        panelOpen = true
        panelTab = PanelTab.Terminal
        ensureShell()?.changeDir(if (dir.isDirectory) dir else dir.parentFile ?: dir)
    }

    fun runActive() {
        val tab = active ?: return toast("Open a file to run it")
        if (tab.isImage) return
        val file = tab.file
        viewModelScope.launch {
            saveNow(tab)
            val text = textOf(tab) ?: ""
            when {
                Languages.isJs(file) -> {
                    panelOpen = true
                    panelTab = PanelTab.Output
                    output.add(TermLine("Running ${file.name}", LineKind.Command))
                    jsRunner.run(text) { message, isError ->
                        viewModelScope.launch {
                            output.add(TermLine(message, if (isError) LineKind.Error else LineKind.Output))
                        }
                    }
                }
                Languages.isPreviewable(file) -> {
                    val isMd = file.extension.lowercase() in setOf("md", "markdown")
                    preview = PreviewRequest(file, if (isMd) text else null)
                }
                tab.lang.run != null -> {
                    val cmd = tab.lang.run!!
                        .replace("%b", file.name)
                        .replace("%n", file.nameWithoutExtension)
                    runInTerminal("cd \"${file.parent}\" && $cmd")
                }
                else -> toast("No run action for ${tab.lang.name}")
            }
        }
    }

    fun clearOutput() = output.clear()

    // ------------------------------------------------------------------- search

    fun search(query: String, matchCase: Boolean, useRegex: Boolean) {
        searchJob?.cancel()
        searchNote = null
        if (query.isBlank()) {
            searchResults = emptyList()
            searching = false
            return
        }
        val root = project ?: return
        searching = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val pattern = runCatching {
                if (useRegex) Regex(query, options) else Regex(Regex.escape(query), options)
            }.getOrNull()
            if (pattern == null) {
                withContext(Dispatchers.Main) {
                    searchNote = "That regular expression is not valid."
                    searching = false
                }
                return@launch
            }
            val hits = ArrayList<SearchHit>()
            var total = 0
            for (f in Workspace.walk(root)) {
                ensureActive()
                if (f.length() > 1_000_000 || Workspace.looksBinary(f)) continue
                val lines = runCatching { f.readLines() }.getOrNull() ?: continue
                for ((i, l) in lines.withIndex()) {
                    val m = pattern.find(l) ?: continue
                    total++
                    if (hits.size < 500) hits.add(SearchHit(f, i + 1, m.range.first, l.trim().take(200)))
                }
            }
            withContext(Dispatchers.Main) {
                searchResults = hits
                searching = false
                searchNote = when {
                    total == 0 -> "No results."
                    total > hits.size -> "Showing the first ${hits.size} of $total matches."
                    else -> "$total match${if (total == 1) "" else "es"}."
                }
            }
        }
    }

    // ---------------------------------------------------------------------- git

    private var gitTick = 0
    var gitVersion by mutableIntStateOf(0)
        private set

    private fun gitVersionBump() {
        gitTick++
        gitVersion = gitTick
        branch = git?.branchName()
    }

    fun refreshGit() {
        val g = git ?: return
        viewModelScope.launch {
            gitBusy = true
            val result = withContext(Dispatchers.IO) {
                runCatching { if (g.isRepo()) g.snapshot() else null }
            }
            gitSnap = result.getOrNull()
            isRepo = withContext(Dispatchers.IO) { g.isRepo() }
            gitError = result.exceptionOrNull()?.message
            branch = if (isRepo) g.branchName() else null
            gitBusy = false
        }
    }

    /** Runs a Git operation off the main thread, reports the outcome and refreshes the status. */
    fun gitDo(success: String? = null, block: GitService.() -> String?) {
        val g = git ?: return
        viewModelScope.launch {
            gitBusy = true
            gitError = null
            val result = withContext(Dispatchers.IO) { runCatching { g.block() } }
            result.onSuccess { msg -> (msg ?: success)?.let { toast(it) } }
            result.onFailure { gitError = it.message ?: it.javaClass.simpleName }
            gitBusy = false
            treeVersion++
            refreshGit()
        }
    }

    fun gitInit() = gitDo("Repository created") { init(); null }

    fun gitStageAll() = gitDo { stageAll(); null }

    fun gitCommit(message: String) = gitDo("Committed") {
        commit(message, settings.gitName, settings.gitEmail); null
    }

    fun gitDiscard(change: GitChange) = gitDo("Changes discarded") { discard(change); null }

    fun gitPull() = gitDo { pull(settings.gitUser, settings.gitToken) }

    fun gitPush() = gitDo { push(settings.gitUser, settings.gitToken) }

    fun gitCheckout(name: String, create: Boolean) = gitDo("On branch $name") { checkout(name, create); null }

    fun showDiff(change: GitChange) {
        val g = git ?: return
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { g.diff(change.path, change.staged, change.kind == '?') }
                    .getOrElse { "Could not compute the diff: ${it.message}" }
            }
            diff = change.path to text
        }
    }

    fun cloneRepository(url: String, parent: File, name: String) {
        val target = File(parent, name)
        if (target.exists() && (target.list()?.isNotEmpty() == true)) return toast("“$name” already exists and is not empty")
        busyMessage = "Cloning $name…"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GitService.clone(url, target, settings.gitUser, settings.gitToken) }
            }
            busyMessage = null
            result.onSuccess { openProject(target) }
            result.onFailure { toast("Clone failed: ${it.message}") }
        }
    }

    fun createProject(parent: File, name: String, templateId: String) {
        val template = dev.anvil.ide.data.Templates.all.first { it.id == templateId }
        viewModelScope.launch {
            val dir = withContext(Dispatchers.IO) {
                runCatching { dev.anvil.ide.data.Templates.create(parent, name, template) }
            }
            dir.onSuccess {
                openProject(it)
                it.listFiles()?.filter { f -> f.isFile && f.name != ".gitignore" }
                    ?.minByOrNull { f -> if (f.name.startsWith("README")) 1 else 0 }
                    ?.let { f -> openFile(f) }
            }
            dir.onFailure { toast("Could not create the project: ${it.message}") }
        }
    }

    // ------------------------------------------------------------- editor tools

    fun undo() { active?.editor?.undo() }
    fun redo() { active?.editor?.redo() }

    fun insert(text: String, cursorOffset: Int = text.length) {
        active?.editor?.insertText(text, cursorOffset)
    }

    fun indentString(): String = " ".repeat(settings.tabSize)

    fun goToLine(line: Int) {
        val tab = active ?: return
        val ed = tab.editor ?: return
        val target = (line - 1).coerceIn(0, ed.text.lineCount - 1)
        ed.setSelection(target, 0, true)
        ed.requestFocus()
    }

    fun toggleComment() {
        val tab = active ?: return
        val ed = tab.editor ?: return
        val token = tab.lang.lineComment ?: return toast("${tab.lang.name} has no line comments")
        val text = ed.text
        val first = ed.cursor.leftLine
        val last = ed.cursor.rightLine
        val lines = (first..last).map { it to text.getLineString(it) }
        val nonBlank = lines.filter { it.second.isNotBlank() }
        if (nonBlank.isEmpty()) return
        val allCommented = nonBlank.all { it.second.trimStart().startsWith(token) }
        if (allCommented) {
            for ((i, s) in lines.reversed()) {
                val at = s.indexOf(token)
                if (at < 0 || s.isBlank()) continue
                val len = token.length + if (s.getOrNull(at + token.length) == ' ') 1 else 0
                text.delete(i, at, i, at + len)
            }
        } else {
            val indent = nonBlank.minOf { it.second.length - it.second.trimStart().length }
            for ((i, s) in lines.reversed()) {
                if (s.isBlank()) continue
                text.insert(i, indent, "$token ")
            }
        }
    }

    fun duplicateLine() {
        val ed = active?.editor ?: return
        val line = ed.cursor.leftLine
        val text = ed.text
        text.insert(line, text.getColumnCount(line), "\n" + text.getLineString(line))
    }

    fun formatDocument() {
        val tab = active ?: return
        val ed = tab.editor ?: return
        val text = ed.text
        val original = text.toString()
        val formatted = if (tab.lang.id == "json") {
            runCatching {
                val trimmed = original.trim()
                if (trimmed.startsWith("[")) JSONArray(trimmed).toString(2) else JSONObject(trimmed).toString(2)
            }.getOrElse { return toast("Not valid JSON: ${it.message?.take(80)}") } + "\n"
        } else {
            original.lines().joinToString("\n") { it.trimEnd() }.trimEnd() + "\n"
        }
        if (formatted != original) text.replace(0, text.length, formatted)
        toast(if (tab.lang.id == "json") "Formatted JSON" else "Trimmed trailing whitespace")
    }

    fun applyTheme(dark: Boolean) {
        val editors = tabs.mapNotNull { it.editor }
        if (editors.isEmpty()) {
            runCatching { EditorSetup.scheme(dark) }
            return
        }
        editors.forEach { it.colorScheme = EditorSetup.scheme(dark) }
    }

    fun applySettingsToEditors() {
        tabs.mapNotNull { it.editor }.forEach { EditorSetup.apply(it, settings) }
    }

    // ---------------------------------------------------------------- shortcuts

    fun palette(mode: PaletteMode) {
        palette = mode
    }

    init {
        val last = settings.lastProject
        if (last.isNotBlank() && File(last).isDirectory) {
            openProject(File(last), restoreTabs = true)
        }
    }

    override fun onCleared() {
        saveAllNow()
        jsRunner.dispose()
        shell?.stop(silent = true)
        tabs.forEach { runCatching { it.editor?.release() } }
        super.onCleared()
    }
}
