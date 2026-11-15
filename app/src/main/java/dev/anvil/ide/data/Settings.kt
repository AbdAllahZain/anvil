package dev.anvil.ide.data

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KProperty

/** A SharedPreferences-backed value that Compose can observe. */
class Pref<T>(private val state: MutableState<T>, private val save: (T) -> Unit) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = value
        save(value)
    }
}

class Settings(context: Context) {
    private val sp = context.getSharedPreferences("anvil", Context.MODE_PRIVATE)

    private fun int(key: String, def: Int) =
        Pref(mutableStateOf(sp.getInt(key, def))) { sp.edit().putInt(key, it).apply() }

    private fun bool(key: String, def: Boolean) =
        Pref(mutableStateOf(sp.getBoolean(key, def))) { sp.edit().putBoolean(key, it).apply() }

    private fun str(key: String, def: String) =
        Pref(mutableStateOf(sp.getString(key, def) ?: def)) { sp.edit().putString(key, it).apply() }

    /** 0 = follow system, 1 = dark, 2 = light */
    var themeMode by int("theme", 0)
    var fontSize by int("font_size", 14)
    var tabSize by int("tab_size", 4)
    var wordWrap by bool("word_wrap", false)
    var lineNumbers by bool("line_numbers", true)
    var highlightLine by bool("highlight_line", true)
    var indentGuides by bool("indent_guides", true)
    var autoClosePairs by bool("auto_close", true)
    var stickyScroll by bool("sticky_scroll", true)
    var ligatures by bool("ligatures", true)
    var showWhitespace by bool("show_ws", false)
    var autoSave by bool("auto_save", true)
    var showHidden by bool("show_hidden", false)

    var gitName by str("git_name", "")
    var gitEmail by str("git_email", "")
    var gitUser by str("git_user", "")
    var gitToken by str("git_token", "")

    var lastProject by str("last_project", "")
    private var recentRaw by str("recent", "")
    private var tabsRaw by str("open_tabs", "")

    val recents: List<String>
        get() = recentRaw.split('\n').filter { it.isNotBlank() }

    fun addRecent(path: String) {
        val list = (listOf(path) + recents.filter { it != path }).take(12)
        recentRaw = list.joinToString("\n")
    }

    fun removeRecent(path: String) {
        recentRaw = recents.filter { it != path }.joinToString("\n")
    }

    fun saveTabs(paths: List<String>) {
        tabsRaw = paths.joinToString("\n")
    }

    fun savedTabs(): List<String> = tabsRaw.split('\n').filter { it.isNotBlank() }
}
