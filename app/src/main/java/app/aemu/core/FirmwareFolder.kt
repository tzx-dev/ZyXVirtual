package app.aemu.core

import android.os.Environment
import java.io.File

/**
 * Internal storage/ZyXVirtual/firmware: firmware files dropped there show up in the library for one-tap import.
 * The legacy AEmulator folder is also scanned so existing imports remain usable.
 */
object FirmwareFolder {
    private val EXT = Regex("""(?i).*\.(zip|7z|tar|md5|tgz|gz|xz|bz2|tbz2?|img|win|ext4)$""")

    val dir: File get() = File(Environment.getExternalStorageDirectory(), "ZyXVirtual/firmware")
    private val legacyDir: File get() = File(Environment.getExternalStorageDirectory(), "aemulator/firmware")

    /** Firmware files, newest first; empty when there is no all-files access. */
    fun list(): List<File> = runCatching {
        if (!Environment.isExternalStorageManager()) {
            emptyList()
        } else {
            dir.mkdirs()
            (dir.listFiles().orEmpty().asSequence() + legacyDir.listFiles().orEmpty().asSequence())
                .filter { it.isFile && it.length() > 1_000_000 && EXT.matches(it.name) }
                .distinctBy { it.absolutePath }
                .sortedByDescending { it.lastModified() }
                .toList()
        }
    }.getOrDefault(emptyList())
}
