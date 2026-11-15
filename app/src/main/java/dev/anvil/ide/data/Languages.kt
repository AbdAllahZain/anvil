package dev.anvil.ide.data

import java.io.File

class Lang(
    val id: String,
    val name: String,
    /** TextMate scope; null means no highlighting. */
    val scope: String?,
    val exts: Set<String>,
    val lineComment: String? = null,
    val run: String? = null,
)

object Languages {
    val all = listOf(
        Lang("kotlin", "Kotlin", "source.kotlin", setOf("kt", "kts"), "//"),
        Lang("java", "Java", "source.java", setOf("java"), "//"),
        Lang("python", "Python", "source.python", setOf("py", "pyw"), "#", "python3 \"%b\""),
        Lang("javascript", "JavaScript", "source.js", setOf("js", "mjs", "cjs", "jsx"), "//", "node \"%b\""),
        Lang("typescript", "TypeScript", "source.ts", setOf("ts"), "//"),
        Lang("tsx", "TypeScript React", "source.tsx", setOf("tsx"), "//"),
        Lang("html", "HTML", "text.html.basic", setOf("html", "htm", "xhtml", "svg"), null),
        Lang("css", "CSS", "source.css", setOf("css"), null),
        Lang("scss", "SCSS", "source.css.scss", setOf("scss", "sass"), "//"),
        Lang("json", "JSON", "source.json", setOf("json", "jsonc", "webmanifest", "map"), null),
        Lang("xml", "XML", "text.xml", setOf("xml", "xsd", "xsl", "plist", "gradle.xml"), null),
        Lang("markdown", "Markdown", "text.html.markdown", setOf("md", "markdown", "mdx"), null),
        Lang("yaml", "YAML", "source.yaml", setOf("yml", "yaml"), "#"),
        Lang("c", "C", "source.c", setOf("c", "h"), "//", "gcc \"%b\" -o \"%n\" && ./\"%n\""),
        Lang("cpp", "C++", "source.cpp", setOf("cpp", "cc", "cxx", "hpp", "hh", "hxx"), "//", "g++ \"%b\" -o \"%n\" && ./\"%n\""),
        Lang("csharp", "C#", "source.cs", setOf("cs"), "//"),
        Lang("go", "Go", "source.go", setOf("go"), "//", "go run \"%b\""),
        Lang("rust", "Rust", "source.rust", setOf("rs"), "//"),
        Lang("php", "PHP", "source.php", setOf("php"), "//", "php \"%b\""),
        Lang("ruby", "Ruby", "source.ruby", setOf("rb"), "#", "ruby \"%b\""),
        Lang("lua", "Lua", "source.lua", setOf("lua"), "--", "lua \"%b\""),
        Lang("sql", "SQL", "source.sql", setOf("sql"), "--"),
        Lang("shell", "Shell", "source.shell", setOf("sh", "bash", "zsh", "env"), "#", "sh \"%b\""),
        Lang("groovy", "Groovy", "source.groovy", setOf("groovy", "gradle"), "//"),
        Lang("dockerfile", "Dockerfile", "source.dockerfile", setOf("dockerfile"), "#"),
        Lang("makefile", "Makefile", "source.makefile", setOf("mk", "makefile"), "#"),
        Lang("ini", "INI", "source.ini", setOf("ini", "cfg", "conf", "properties", "toml"), "#"),
        Lang("diff", "Diff", "source.diff", setOf("diff", "patch"), null),
    )

    val plain = Lang("text", "Plain text", null, emptySet())

    private val byExt: Map<String, Lang> = buildMap {
        all.forEach { l -> l.exts.forEach { put(it, l) } }
    }

    private val byName = mapOf(
        "dockerfile" to "dockerfile",
        "makefile" to "makefile",
        "gnumakefile" to "makefile",
        ".bashrc" to "shell",
        ".zshrc" to "shell",
        ".profile" to "shell",
        ".gitconfig" to "ini",
        ".editorconfig" to "ini",
    )

    fun of(file: File): Lang {
        val name = file.name.lowercase()
        byName[name]?.let { id -> return all.first { it.id == id } }
        return byExt[name.substringAfterLast('.', "")] ?: plain
    }

    private val imageExts = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    fun isImage(file: File) = file.extension.lowercase() in imageExts
    fun isPreviewable(file: File) = file.extension.lowercase() in setOf("html", "htm", "md", "markdown", "svg")
    fun isJs(file: File) = file.extension.lowercase() in setOf("js", "mjs")
}
