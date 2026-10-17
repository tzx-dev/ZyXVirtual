package app.aemu.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

class ZyxOsPackageStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "zyx-os")
    private val current = File(root, "current")

    fun installed(): InstalledZyxOs? = runCatching {
        val manifestFile = File(current, ZYX_OS_MANIFEST_NAME)
        if (!manifestFile.isFile) return null
        val manifest = ZyxOsPackageManifest.parse(manifestFile.readText(Charsets.UTF_8)).requireSupported()
        if (manifest.files.any { item ->
                val file = safeTarget(current, item.path)
                !file.isFile || file.length() != item.size
            }) return null
        InstalledZyxOs(manifest, current, manifest.files.sumOf { it.size }, current.lastModified())
    }.getOrNull()

    suspend fun installFromUri(uri: Uri, onProgress: (Long, Long) -> Unit = { _, _ -> }): InstalledZyxOs = withContext(Dispatchers.IO) {
        root.mkdirs()
        val id = UUID.randomUUID().toString()
        val archiveFile = File(root, "download-$id.part")
        var stageDir: File? = null
        var backupDir: File? = null
        try {
            val expectedDownloadSize = querySize(uri)
            val input = app.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Não foi possível abrir o pacote selecionado.")
            input.use { source ->
                FileOutputStream(archiveFile).use { output ->
                    BufferedInputStream(source).use { buffered ->
                        val buffer = ByteArray(COPY_BUFFER)
                        var copied = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = buffered.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            onProgress(copied, expectedDownloadSize)
                        }
                        output.fd.sync()
                    }
                }
            }
            if (expectedDownloadSize > 0 && archiveFile.length() != expectedDownloadSize) {
                throw IllegalStateException("O download terminou incompleto; tente selecionar o arquivo novamente.")
            }

            val staging = File(root, "stage-$id")
            stageDir = staging
            val manifest: ZyxOsPackageManifest
            val payloadSize: Long
            ZipFile(archiveFile).use { zip ->
                val manifestEntry = zip.getEntry(ZYX_OS_MANIFEST_NAME)
                    ?: throw IllegalArgumentException("Arquivo inválido: manifesto ZyXOS ausente.")
                if (manifestEntry.size !in 1..MAX_MANIFEST_BYTES) {
                    throw IllegalArgumentException("Manifesto ZyXOS grande ou inválido.")
                }
                val manifestJson = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                manifest = ZyxOsPackageManifest.parse(manifestJson).requireSupported()

                val expectedPaths = manifest.files.map { it.path }.toSet()
                val actualPaths = linkedSetOf<String>()
                var manifestCount = 0
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name == ZYX_OS_MANIFEST_NAME) {
                        manifestCount++
                        continue
                    }
                    val entryPath = if (entry.isDirectory) entry.name.trimEnd('/') else entry.name
                    if (!isSafeZyxPackagePath(entryPath)) throw IllegalArgumentException("Caminho inseguro no ZIP: ${entry.name}")
                    if (entry.isDirectory) continue
                    if (!actualPaths.add(entry.name)) throw IllegalArgumentException("O ZIP contém caminhos duplicados.")
                }
                if (manifestCount != 1) throw IllegalArgumentException("O pacote deve conter exatamente um manifesto ZyXOS.")
                if (actualPaths != expectedPaths) {
                    throw IllegalArgumentException("Os arquivos do pacote não correspondem ao manifesto ZyXOS.")
                }
                payloadSize = manifest.files.sumOf { it.size }
                val available = StatFs(root.absolutePath).availableBytes
                val required = archiveFile.length() + payloadSize + MIN_FREE_SPACE_BYTES
                if (required > available) {
                    throw IllegalStateException("Espaço insuficiente: libere armazenamento e tente novamente.")
                }

                staging.mkdirs()
                for (item in manifest.files) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.getEntry(item.path) ?: throw IllegalArgumentException("Arquivo ausente: ${item.path}")
                    if (entry.size != item.size) throw IllegalArgumentException("Tamanho incorreto em ${item.path}.")
                    val target = safeTarget(staging, item.path)
                    target.parentFile?.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var written = 0L
                    zip.getInputStream(entry).use { source ->
                        BufferedOutputStream(FileOutputStream(target)).use { output ->
                            val buffer = ByteArray(COPY_BUFFER)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                written += count
                            }
                            output.flush()
                        }
                    }
                    if (written != item.size || digest.digest().toHex() != item.sha256) {
                        throw IllegalArgumentException("A verificação SHA-256 falhou em ${item.path}.")
                    }
                }
                File(staging, ZYX_OS_MANIFEST_NAME).writeText(manifestJson, Charsets.UTF_8)
            }

            backupDir = File(root, "previous-$id")
            if (current.exists() && !current.renameTo(backupDir)) {
                throw IllegalStateException("Não foi possível substituir a instalação ZyX OS existente.")
            }
            if (!staging.renameTo(current)) {
                if (backupDir.exists()) backupDir.renameTo(current)
                throw IllegalStateException("Não foi possível ativar o novo pacote ZyX OS.")
            }
            backupDir.deleteRecursively()
            backupDir = null
            InstalledZyxOs(manifest, current, payloadSize, current.lastModified())
        } catch (t: Throwable) {
            stageDir?.deleteRecursively()
            backupDir?.let { if (it.exists() && !current.exists()) it.renameTo(current) else it.deleteRecursively() }
            throw t
        } finally {
            archiveFile.delete()
        }
    }

    suspend fun uninstall() = withContext(Dispatchers.IO) {
        current.deleteRecursively()
        root.listFiles()?.filter { it.name.startsWith("previous-") || it.name.startsWith("stage-") }?.forEach { it.deleteRecursively() }
    }

    private fun querySize(uri: Uri): Long = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun safeTarget(base: File, relativePath: String): File {
        require(isSafeZyxPackagePath(relativePath)) { "Caminho inseguro: $relativePath" }
        val basePath = base.canonicalFile.path + File.separator
        val target = File(base, relativePath).canonicalFile
        require(target.path.startsWith(basePath)) { "Caminho fora do pacote: $relativePath" }
        return target
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val COPY_BUFFER = 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 2L * 1024 * 1024
        private const val MIN_FREE_SPACE_BYTES = 128L * 1024 * 1024
    }
}
