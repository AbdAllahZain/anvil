package dev.anvil.ide.core

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import kotlin.concurrent.thread

enum class LineKind { Command, Output, Info, Error }

class TermLine(val text: String, val kind: LineKind)

/**
 * A lightweight shell session on top of /system/bin/sh. There is no pseudo-terminal,
 * so full-screen programs (vim, top, REPLs) are not supported. Everything that reads
 * stdin line by line works, because input is forwarded while a command is running.
 */
class ShellSession(startDir: File, private val home: File, private val onFinished: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var process: Process? = null
    private var writer: BufferedWriter? = null

    val lines = mutableStateListOf<TermLine>()
    var cwd by mutableStateOf(startDir.absolutePath)
        private set
    var busy by mutableStateOf(false)
        private set
    var alive by mutableStateOf(false)
        private set

    private val history = ArrayList<String>()
    private var historyIndex = 0

    private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
    private val marker = "\u0001ANVIL_DONE:"

    fun start() {
        stop(silent = true)
        val dir = File(cwd).takeIf { it.isDirectory } ?: home
        val pb = ProcessBuilder("/system/bin/sh")
            .directory(dir)
            .redirectErrorStream(true)
        pb.environment().apply {
            put("HOME", home.absolutePath)
            put("TERM", "dumb")
            put("PWD", dir.absolutePath)
        }
        val p = pb.start()
        process = p
        writer = BufferedWriter(OutputStreamWriter(p.outputStream))
        alive = true
        thread(isDaemon = true, name = "anvil-shell-reader") {
            try {
                p.inputStream.bufferedReader().useLines { seq ->
                    seq.forEach { raw -> main.post { handle(raw) } }
                }
            } catch (_: Exception) {
            }
            main.post {
                if (process === p) {
                    alive = false
                    busy = false
                    push(TermLine("[session ended]", LineKind.Info))
                }
            }
        }
    }

    private fun push(line: TermLine) {
        lines.add(line)
        if (lines.size > 3000) repeat(lines.size - 2500) { lines.removeAt(0) }
    }

    private fun handle(raw: String) {
        val idx = raw.indexOf(marker)
        if (idx >= 0) {
            val parts = raw.substring(idx + marker.length).split(':', limit = 2)
            val code = parts.getOrNull(0)?.toIntOrNull() ?: 0
            parts.getOrNull(1)?.let { if (it.isNotBlank()) cwd = it.trim() }
            val before = raw.substring(0, idx)
            if (before.isNotEmpty()) push(TermLine(ansi.replace(before, ""), LineKind.Output))
            busy = false
            if (code != 0) push(TermLine("exit code $code", LineKind.Info))
            onFinished()
        } else {
            push(TermLine(ansi.replace(raw, ""), LineKind.Output))
        }
    }

    fun exec(command: String) {
        val cmd = command.trimEnd()
        if (cmd.isBlank()) return
        if (!alive) start()
        if (busy) {
            // Forward raw input to the running program.
            push(TermLine(cmd, LineKind.Command))
            send(cmd)
            return
        }
        if (cmd.trim() == "clear") {
            lines.clear()
            return
        }
        history.add(cmd)
        historyIndex = history.size
        push(TermLine("${File(cwd).name.ifEmpty { "/" }} $ $cmd", LineKind.Command))
        busy = true
        send(cmd)
        send("printf '\\001ANVIL_DONE:%s:%s\\n' \"\$?\" \"\$(pwd)\"")
    }

    private fun send(text: String) {
        try {
            writer?.apply {
                write(text)
                write("\n")
                flush()
            }
        } catch (e: Exception) {
            push(TermLine("Could not write to shell: ${e.message}", LineKind.Info))
            busy = false
        }
    }

    fun historyPrev(): String? {
        if (history.isEmpty()) return null
        historyIndex = (historyIndex - 1).coerceAtLeast(0)
        return history[historyIndex]
    }

    fun historyNext(): String? {
        if (history.isEmpty()) return null
        historyIndex = (historyIndex + 1).coerceAtMost(history.size)
        return history.getOrNull(historyIndex) ?: ""
    }

    /** Kills the running shell. The next command starts a fresh one in the same directory. */
    fun stop(silent: Boolean = false) {
        val p = process
        process = null
        runCatching { writer?.close() }
        writer = null
        if (p != null) {
            runCatching { p.destroy() }
            if (!silent) push(TermLine("[stopped]", LineKind.Info))
        }
        busy = false
        alive = false
    }

    fun changeDir(dir: File) {
        cwd = dir.absolutePath
        if (alive && !busy) exec("cd \"${dir.absolutePath}\"") else if (!alive) start()
    }
}
