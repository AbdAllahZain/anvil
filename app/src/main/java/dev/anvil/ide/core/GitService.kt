package dev.anvil.ide.core

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GitChange(val path: String, val kind: Char, val staged: Boolean)
data class GitCommitInfo(val hash: String, val message: String, val author: String, val time: String)
data class GitSnapshot(
    val branch: String,
    val changes: List<GitChange>,
    val commits: List<GitCommitInfo>,
    val branches: List<String>,
)

class GitService(private val dir: File) {

    fun isRepo(): Boolean = File(dir, ".git").exists()

    /** Cheap branch lookup that reads .git/HEAD directly. */
    fun branchName(): String? = runCatching {
        val head = File(dir, ".git/HEAD").readText().trim()
        if (head.startsWith("ref: ")) head.removePrefix("ref: ").removePrefix("refs/heads/") else head.take(7)
    }.getOrNull()

    fun init() {
        Git.init().setDirectory(dir).call().close()
    }

    private fun <T> withGit(block: (Git) -> T): T = Git.open(dir).use(block)

    fun snapshot(): GitSnapshot = withGit { git ->
        val repo = git.repository
        val status = git.status().call()
        val changes = buildList {
            status.added.forEach { add(GitChange(it, 'A', true)) }
            status.changed.forEach { add(GitChange(it, 'M', true)) }
            status.removed.forEach { add(GitChange(it, 'D', true)) }
            status.modified.forEach { add(GitChange(it, 'M', false)) }
            status.missing.forEach { add(GitChange(it, 'D', false)) }
            status.untracked.forEach { add(GitChange(it, '?', false)) }
            status.conflicting.forEach { add(GitChange(it, 'U', false)) }
        }.sortedBy { it.path.lowercase() }

        val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        val commits = if (repo.resolve("HEAD") != null) {
            git.log().setMaxCount(40).call().map {
                GitCommitInfo(
                    hash = it.name.take(7),
                    message = it.shortMessage,
                    author = it.authorIdent.name,
                    time = fmt.format(Date(it.commitTime * 1000L)),
                )
            }
        } else emptyList()

        val branches = runCatching {
            git.branchList().call().map { it.name.removePrefix("refs/heads/") }
        }.getOrDefault(emptyList())

        GitSnapshot(repo.branch ?: "HEAD", changes, commits, branches)
    }

    fun stage(path: String, deleted: Boolean) = withGit { git ->
        if (deleted) git.rm().addFilepattern(path).call() else git.add().addFilepattern(path).call()
        Unit
    }

    fun unstage(path: String) = withGit { git ->
        if (git.repository.resolve("HEAD") != null) {
            git.reset().addPath(path).call()
        } else {
            git.rm().setCached(true).addFilepattern(path).call()
        }
        Unit
    }

    fun stageAll() = withGit { git ->
        git.add().addFilepattern(".").call()
        git.add().setUpdate(true).addFilepattern(".").call()
        Unit
    }

    fun discard(change: GitChange) = withGit { git ->
        if (change.kind == '?') {
            File(dir, change.path).deleteRecursively()
        } else {
            git.checkout().addPath(change.path).call()
        }
        Unit
    }

    fun commit(message: String, name: String, email: String) = withGit { git ->
        val who = PersonIdent(name.ifBlank { "Anvil User" }, email.ifBlank { "anvil@localhost" })
        git.commit().setMessage(message).setAuthor(who).setCommitter(who).call()
        Unit
    }

    fun checkout(branch: String, create: Boolean) = withGit { git ->
        git.checkout().setName(branch).setCreateBranch(create).call()
        Unit
    }

    fun diff(path: String, staged: Boolean, untracked: Boolean): String {
        if (untracked) return "New file: $path\n\n" + runCatching { File(dir, path).readText().take(20000) }.getOrDefault("")
        return withGit { git ->
            val out = ByteArrayOutputStream()
            git.diff()
                .setCached(staged)
                .setPathFilter(PathFilter.create(path))
                .setOutputStream(out)
                .call()
            out.toString("UTF-8").ifBlank { "No textual changes." }
        }
    }

    fun pull(user: String, token: String): String = withGit { git ->
        val r = git.pull().apply { creds(user, token)?.let { setCredentialsProvider(it) } }.call()
        if (r.isSuccessful) "Pull complete." else "Pull finished with conflicts or errors."
    }

    fun push(user: String, token: String): String = withGit { git ->
        val results = git.push().apply { creds(user, token)?.let { setCredentialsProvider(it) } }.call()
        val msgs = results.flatMap { r -> r.remoteUpdates.map { "${it.remoteName}: ${it.status}" } }
        if (msgs.isEmpty()) "Nothing to push." else msgs.joinToString("\n")
    }

    companion object {
        private fun creds(user: String, token: String): CredentialsProvider? =
            if (token.isBlank()) null else UsernamePasswordCredentialsProvider(user.ifBlank { token }, token)

        fun clone(url: String, target: File, user: String, token: String) {
            Git.cloneRepository()
                .setURI(url)
                .setDirectory(target)
                .apply { creds(user, token)?.let { setCredentialsProvider(it) } }
                .call()
                .close()
        }
    }
}
