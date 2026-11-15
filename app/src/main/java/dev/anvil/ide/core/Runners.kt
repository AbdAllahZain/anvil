package dev.anvil.ide.core

import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** Runs JavaScript in an off-screen WebView and reports console output. */
class JsRunner(private val context: Context) {
    private var web: WebView? = null

    fun run(code: String, log: (String, Boolean) -> Unit) {
        dispose()
        val w = WebView(context)
        web = w
        w.settings.javaScriptEnabled = true
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val isError = m.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                log(m.message(), isError)
                return true
            }
        }
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(
                    "(function(){try{" + code + "\n}catch(e){console.error(String(e&&e.stack||e));}})();",
                    null
                )
            }
        }
        w.loadDataWithBaseURL("https://anvil.local/", "<html><body></body></html>", "text/html", "utf-8", null)
    }

    fun dispose() {
        web?.let {
            runCatching {
                it.stopLoading()
                it.destroy()
            }
        }
        web = null
    }
}

object Markdown {
    private val extensions = listOf(TablesExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).build()

    fun toHtml(markdown: String, dark: Boolean): String {
        val body = renderer.render(parser.parse(markdown))
        val fg = if (dark) "#C9CFDA" else "#1E222A"
        val bg = if (dark) "#1B1E24" else "#FCFCFD"
        val rule = if (dark) "#2B3039" else "#DCDFE5"
        val codeBg = if (dark) "#15181D" else "#F1F3F6"
        val link = if (dark) "#5A9CF8" else "#2D6BD8"
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{font:16px/1.65 -apple-system,Roboto,sans-serif;color:$fg;background:$bg;margin:0;padding:20px 18px 48px;max-width:760px}
h1,h2,h3{line-height:1.25;margin:1.6em 0 .6em} h1{font-size:1.7em;border-bottom:1px solid $rule;padding-bottom:.3em} h2{font-size:1.35em;border-bottom:1px solid $rule;padding-bottom:.25em}
a{color:$link} code{font:13.5px monospace;background:$codeBg;padding:.15em .35em;border-radius:4px}
pre{background:$codeBg;padding:12px 14px;border-radius:6px;overflow:auto} pre code{background:none;padding:0}
blockquote{margin:1em 0;padding:0 1em;border-left:3px solid $rule;color:$fg;opacity:.8}
table{border-collapse:collapse} td,th{border:1px solid $rule;padding:6px 12px} img{max-width:100%}
hr{border:0;border-top:1px solid $rule}
</style></head><body>$body</body></html>"""
    }
}
