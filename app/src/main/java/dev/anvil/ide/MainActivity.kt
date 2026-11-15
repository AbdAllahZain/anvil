package dev.anvil.ide

import android.graphics.Color as AColor
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import dev.anvil.ide.data.Workspace
import dev.anvil.ide.ui.A
import dev.anvil.ide.ui.AnvilTheme
import dev.anvil.ide.ui.HomeScreen
import dev.anvil.ide.ui.IdeScreen
import dev.anvil.ide.ui.Overlays

class MainActivity : ComponentActivity() {
    private val vm: IdeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AnvilRoot() }
    }

    @Composable
    private fun AnvilRoot() {
        val system = isSystemInDarkTheme()
        val dark = when (vm.settings.themeMode) {
            1 -> true
            2 -> false
            else -> system
        }
        SideEffect {
            enableEdgeToEdge(
                statusBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                navigationBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
            )
        }
        LaunchedEffect(dark) { vm.applyTheme(dark) }
        AnvilTheme(dark) {
            Box(Modifier.fillMaxSize().background(A.c.surface)) {
                if (vm.project == null) HomeScreen(vm) else IdeScreen(vm, dark)
                Overlays(vm, dark)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && vm.project != null) {
            val handled = when (event.keyCode) {
                KeyEvent.KEYCODE_S -> { vm.active?.let { vm.save(it) }; true }
                KeyEvent.KEYCODE_P -> {
                    vm.palette(if (event.isShiftPressed) PaletteMode.Commands else PaletteMode.Files); true
                }
                KeyEvent.KEYCODE_F -> { if (vm.active != null) vm.findOpen = true; true }
                KeyEvent.KEYCODE_G -> { vm.palette(PaletteMode.GoToLine); true }
                KeyEvent.KEYCODE_B -> { vm.sidebarOpen = !vm.sidebarOpen; true }
                KeyEvent.KEYCODE_GRAVE -> { vm.panelOpen = !vm.panelOpen; true }
                KeyEvent.KEYCODE_W -> { vm.active?.let { vm.requestClose(it) }; true }
                KeyEvent.KEYCODE_SLASH -> { vm.toggleComment(); true }
                else -> false
            }
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        vm.storageGranted = Workspace.storageGranted()
    }

    override fun onStop() {
        vm.saveAllNow()
        super.onStop()
    }
}
