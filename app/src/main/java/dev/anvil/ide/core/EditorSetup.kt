package dev.anvil.ide.core

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import dev.anvil.ide.EditorTab
import dev.anvil.ide.IdeViewModel
import dev.anvil.ide.R
import dev.anvil.ide.data.Lang
import dev.anvil.ide.data.Settings
import dev.anvil.ide.ui.AnvilColors
import dev.anvil.ide.ui.DarkColors
import dev.anvil.ide.ui.LightColors
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.subscribeEvent

object EditorSetup {
    private var mono: Typeface? = null

    fun typeface(context: Context): Typeface {
        mono?.let { return it }
        val tf = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
        mono = tf
        return tf
    }

    /** Builds a colour scheme for the editor based on our design tokens. */
    fun scheme(dark: Boolean): EditorColorScheme {
        val registry = ThemeRegistry.getInstance()
        registry.setTheme(if (dark) "anvil-dark" else "anvil-light")
        val s = TextMateColorScheme.create(registry)
        val c: AnvilColors = if (dark) DarkColors else LightColors
        fun set(id: Int, color: Color) = s.setColor(id, color.toArgb())
        set(EditorColorScheme.LINE_NUMBER, c.textFaint)
        set(EditorColorScheme.LINE_NUMBER_CURRENT, c.textDim)
        set(EditorColorScheme.LINE_DIVIDER, Color.Transparent)
        set(EditorColorScheme.SELECTION_HANDLE, c.accent)
        set(EditorColorScheme.SELECTION_INSERT, c.accent)
        set(EditorColorScheme.SCROLL_BAR_THUMB, c.border)
        set(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, c.textFaint)
        set(EditorColorScheme.MATCHED_TEXT_BACKGROUND, c.accent.copy(alpha = 0.28f))
        set(EditorColorScheme.STICKY_SCROLL_DIVIDER, c.border)
        set(EditorColorScheme.COMPLETION_WND_BACKGROUND, c.raised)
        set(EditorColorScheme.COMPLETION_WND_CORNER, c.border)
        set(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, c.selected)
        set(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, c.text)
        set(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, c.textDim)
        set(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, c.raised)
        set(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, c.text)
        return s
    }

    fun language(lang: Lang): Language {
        val scope = lang.scope ?: return EmptyLanguage()
        return runCatching { TextMateLanguage.create(scope, true) as Language }.getOrElse { EmptyLanguage() }
    }

    fun apply(editor: CodeEditor, s: Settings) {
        editor.setTextSize(s.fontSize.toFloat())
        editor.setTabWidth(s.tabSize)
        editor.setWordwrap(s.wordWrap)
        editor.setLineNumberEnabled(s.lineNumbers)
        editor.setHighlightCurrentLine(s.highlightLine)
        editor.setBlockLineEnabled(s.indentGuides)
        editor.setLigatureEnabled(s.ligatures)
        editor.props.symbolPairAutoCompletion = s.autoClosePairs
        editor.props.stickyScroll = s.stickyScroll
        editor.setNonPrintablePaintingFlags(
            if (s.showWhitespace)
                CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_WHITESPACE_INNER or CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING
            else 0
        )
    }

    fun create(context: Context, tab: EditorTab, vm: IdeViewModel, dark: Boolean): CodeEditor {
        val ed = CodeEditor(context)
        ed.colorScheme = scheme(dark)
        val tf = typeface(context)
        ed.setTypefaceText(tf)
        ed.setTypefaceLineNumber(tf)
        ed.setEditorLanguage(language(tab.lang))
        ed.setText(tab.initialText ?: "")
        ed.setScalable(true)
        ed.setCursorAnimationEnabled(false)
        ed.setDividerWidth(0f)
        apply(ed, vm.settings)

        ed.subscribeEvent<SelectionChangeEvent> { _, _ ->
            tab.line = ed.cursor.leftLine + 1
            tab.col = ed.cursor.leftColumn + 1
        }
        ed.subscribeEvent<ContentChangeEvent> { event, _ ->
            if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) vm.onEdited(tab)
        }
        return ed
    }
}
