package dev.anvil.ide.ui

import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.anvil.ide.EditorTab
import dev.anvil.ide.IdeViewModel
import dev.anvil.ide.core.EditorSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EditorPane(vm: IdeViewModel, tab: EditorTab, dark: Boolean, modifier: Modifier = Modifier) {
    if (tab.isImage) {
        ImageViewer(tab, modifier)
        return
    }
    key(tab.key) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val ed = tab.editor ?: EditorSetup.create(ctx, tab, vm, dark).also { tab.editor = it }
                (ed.parent as? ViewGroup)?.removeView(ed)
                ed
            },
            update = { ed ->
                // Reading settings here makes this block re-run when they change.
                EditorSetup.apply(ed, vm.settings)
                val jump = tab.jump
                if (jump != null) {
                    val line = jump[0].coerceIn(0, ed.text.lineCount - 1)
                    val col = jump[1].coerceIn(0, ed.text.getColumnCount(line))
                    ed.setSelection(line, col, true)
                    ed.requestFocus()
                    tab.jump = null
                }
            },
        )
    }
}

@Composable
private fun ImageViewer(tab: EditorTab, modifier: Modifier) {
    val bitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, tab.key) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tab.file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(tab.file.absolutePath, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }.value
    Box(modifier.fillMaxSize().background(A.c.bg), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, tab.file.name, Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit)
        } else {
            EmptyNote("Could not decode this image.")
        }
    }
}
