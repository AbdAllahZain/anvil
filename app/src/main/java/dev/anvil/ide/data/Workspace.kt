package dev.anvil.ide.data

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File

object Workspace {
    /** Directories that are noisy to browse or search. */
    val ignoredDirs = setOf(".git", "node_modules", "build", ".gradle", ".idea", "__pycache__", ".dart_tool", "target", "dist")

    fun storageGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else true // < 30 is handled through runtime permissions in the UI

    fun root(context: Context): File {
        val shared = File(Environment.getExternalStorageDirectory(), "Anvil")
        val usable = runCatching {
            (shared.exists() || shared.mkdirs()) && shared.canWrite()
        }.getOrDefault(false)
        val dir = if (usable) shared else File(context.getExternalFilesDir(null) ?: context.filesDir, "Projects")
        dir.mkdirs()
        return dir
    }

    fun listChildren(dir: File, showHidden: Boolean): List<File> {
        val items = dir.listFiles() ?: return emptyList()
        return items
            .filter { it.name != ".git" && (showHidden || !it.name.startsWith(".")) }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /** All files below [root], skipping noisy directories. Capped to keep the UI responsive. */
    fun walk(root: File, limit: Int = 30000): List<File> {
        val out = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty() && out.size < limit) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory) {
                    if (f.name !in ignoredDirs) stack.addLast(f)
                } else out.add(f)
            }
        }
        return out
    }

    fun looksBinary(file: File): Boolean {
        return runCatching {
            file.inputStream().use { input ->
                val buf = ByteArray(4096)
                val n = input.read(buf)
                (0 until n).any { buf[it] == 0.toByte() }
            }
        }.getOrDefault(false)
    }

    fun relative(root: File?, file: File): String {
        if (root == null) return file.name
        return runCatching { file.relativeTo(root).path }.getOrDefault(file.path)
    }

    fun uniqueChild(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 2
        while (f.exists()) {
            f = File(dir, "$base $i$ext")
            i++
        }
        return f
    }
}

class Template(val id: String, val title: String, val blurb: String, val files: Map<String, String>)

object Templates {
    val all = listOf(
        Template("blank", "Blank", "An empty folder with a README", mapOf(
            "README.md" to "# %name%\n"
        )),
        Template("web", "Web page", "HTML, CSS and JavaScript", mapOf(
            "index.html" to """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>%name%</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <main>
    <h1>%name%</h1>
    <p id="out"></p>
  </main>
  <script src="script.js"></script>
</body>
</html>
""",
            "style.css" to "body {\n  font-family: system-ui, sans-serif;\n  margin: 0;\n  padding: 2rem;\n}\n",
            "script.js" to "document.getElementById('out').textContent = 'Hello from ' + document.title;\n"
        )),
        Template("python", "Python", "A single script", mapOf(
            "main.py" to "def main():\n    print(\"Hello, world\")\n\n\nif __name__ == \"__main__\":\n    main()\n",
            "README.md" to "# %name%\n"
        )),
        Template("node", "Node.js", "package.json and an entry file", mapOf(
            "package.json" to "{\n  \"name\": \"%slug%\",\n  \"version\": \"1.0.0\",\n  \"main\": \"index.js\",\n  \"scripts\": {\n    \"start\": \"node index.js\"\n  }\n}\n",
            "index.js" to "console.log('Hello, world');\n",
            ".gitignore" to "node_modules/\n"
        )),
        Template("c", "C", "main.c and a Makefile", mapOf(
            "main.c" to "#include <stdio.h>\n\nint main(void) {\n    printf(\"Hello, world\\n\");\n    return 0;\n}\n",
            "Makefile" to "main: main.c\n\tcc -o main main.c\n"
        )),
    )

    fun create(parent: File, name: String, template: Template): File {
        val dir = File(parent, name)
        dir.mkdirs()
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        template.files.forEach { (path, body) ->
            val f = File(dir, path)
            f.parentFile?.mkdirs()
            f.writeText(body.replace("%name%", name).replace("%slug%", slug))
        }
        return dir
    }
}
