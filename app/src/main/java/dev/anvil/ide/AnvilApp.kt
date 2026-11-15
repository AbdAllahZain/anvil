package dev.anvil.ide

import android.app.Application
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

class AnvilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        setupTextMate()
    }

    private fun setupTextMate() {
        val files = FileProviderRegistry.getInstance()
        files.addFileProvider(AssetsFileResolver(applicationContext.assets))
        val themes = ThemeRegistry.getInstance()
        for (name in listOf("anvil-dark", "anvil-light")) {
            val path = "textmate/$name.json"
            val model = ThemeModel(
                IThemeSource.fromInputStream(files.tryGetInputStream(path), path, null),
                name
            )
            model.isDark = name.endsWith("dark")
            themes.loadTheme(model)
        }
        themes.setTheme("anvil-dark")
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }
}
