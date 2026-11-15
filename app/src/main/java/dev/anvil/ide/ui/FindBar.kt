package dev.anvil.ide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.anvil.ide.EditorTab
import dev.anvil.ide.IdeViewModel
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.delay

/** Find and replace for the active editor, built on Sora's searcher. */
@Composable
fun FindBar(vm: IdeViewModel, tab: EditorTab) {
    val c = A.c
    var query by remember(tab.key) { mutableStateOf("") }
    var replacement by remember(tab.key) { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val editor = tab.editor

    LaunchedEffect(Unit) { focus.requestFocus() }

    LaunchedEffect(query, matchCase, regex, tab.key, editor) {
        val ed = editor ?: return@LaunchedEffect
        if (query.isEmpty()) {
            ed.searcher.stopSearch()
            count = 0
            index = 0
            return@LaunchedEffect
        }
        runCatching { ed.searcher.search(query, EditorSearcher.SearchOptions(!matchCase, regex)) }
        delay(160)
        count = ed.searcher.matchedPositionCount
        index = ed.searcher.currentMatchedPositionIndex + 1
    }
    LaunchedEffect(tick) {
        val ed = editor ?: return@LaunchedEffect
        delay(120)
        count = ed.searcher.matchedPositionCount
        index = ed.searcher.currentMatchedPositionIndex + 1
    }
    DisposableEffect(tab.key) {
        onDispose { runCatching { tab.editor?.searcher?.stopSearch() } }
    }

    Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(
                query, { query = it }, Modifier.weight(1f), hint = "Find", focus = focus,
                imeAction = ImeAction.Search,
                onImeAction = { editor?.searcher?.gotoNext(); tick++ },
                trailing = {
                    Text(
                        if (query.isEmpty()) "" else if (count == 0) "0" else "$index/$count",
                        color = c.textFaint, style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.width(6.dp))
                    ToggleChip("Aa", matchCase) { matchCase = !matchCase }
                    Spacer(Modifier.width(2.dp))
                    ToggleChip(".*", regex) { regex = !regex }
                },
            )
            IconBtn(Icons.Outlined.KeyboardArrowUp, "Previous match", { editor?.searcher?.gotoPrevious(); tick++ }, size = 36.dp, iconSize = 18.dp)
            IconBtn(Icons.Outlined.KeyboardArrowDown, "Next match", { editor?.searcher?.gotoNext(); tick++ }, size = 36.dp, iconSize = 18.dp)
            IconBtn(Icons.Outlined.Close, "Close", { vm.findOpen = false }, size = 36.dp, iconSize = 18.dp)
        }
        if (showReplace) {
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Field(replacement, { replacement = it }, Modifier.weight(1f), hint = "Replace with")
                Btn("Replace", { editor?.searcher?.replaceCurrentMatch(replacement); tick++ }, enabled = count > 0)
                Btn("All", { editor?.searcher?.replaceAll(replacement); tick++ }, enabled = count > 0)
            }
        } else {
            TextAction("Replace…", { showReplace = true }, color = c.textDim)
        }
    }
}
