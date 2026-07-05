package rotlin.cli

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.security.MessageDigest

/**
 * Watches one file for real content changes. Windows editors fire
 * CREATE/MODIFY/DELETE bursts on atomic save and the file can be momentarily
 * missing, so: debounce, retry reads, and skip when the content hash is
 * unchanged.
 */
class Watcher(private val file: Path) {

    fun watch(onChange: () -> Unit): Nothing {
        val dir = file.toAbsolutePath().parent
        val watchService = FileSystems.getDefault().newWatchService()
        dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
        var lastHash = hashWithRetry()

        while (true) {
            val key = watchService.take()
            key.pollEvents()
            key.reset()
            Thread.sleep(150) // let the editor's save burst settle
            while (true) {
                val extra = watchService.poll() ?: break
                extra.pollEvents()
                extra.reset()
            }
            val hash = hashWithRetry() ?: continue
            if (!hash.contentEquals(lastHash)) {
                lastHash = hash
                onChange()
            }
        }
    }

    private fun hashWithRetry(): ByteArray? {
        repeat(5) { attempt ->
            try {
                if (Files.exists(file)) {
                    return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
                }
            } catch (_: java.io.IOException) {
                // transient lock during atomic save
            }
            Thread.sleep(40L * (attempt + 1))
        }
        return null
    }
}
