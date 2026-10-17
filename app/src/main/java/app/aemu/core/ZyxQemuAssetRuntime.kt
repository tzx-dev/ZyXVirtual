package app.aemu.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Instala os assets imutáveis do runtime em uma pasta privada executável do app. */
object ZyxQemuAssetRuntime {
    private const val ASSET_MANIFEST = "zyx-qemu/runtime.json"
    private const val RUNTIME_DIR = "zyx-qemu/runtime"
    private const val COPY_BUFFER = 256 * 1024

    data class PreparedRuntime(
        val binary: File,
        val libraryDirectory: File,
        val dataDirectory: File,
        val version: String,
    )

    private data class RuntimeFile(val path: String, val size: Long, val sha256: String)
    private data class RuntimeManifest(val version: String, val files: List<RuntimeFile>, val digest: String) {
        val binary: RuntimeFile get() = files.singleOrNull { it.path == "bin/qemu-system-aarch64" }
            ?: throw IllegalArgumentException("Manifesto do runtime QEMU sem executável AArch64.")
    }

    fun isAvailable(context: Context): Boolean = runCatching {
        readManifest(context.applicationContext)
        true
    }.getOrDefault(false)

    @Synchronized
    fun install(context: Context): PreparedRuntime {
        val app = context.applicationContext
        val manifestBytes = app.assets.open(ASSET_MANIFEST).use { it.readBytes() }
        val manifest = parseManifest(manifestBytes)
        val root = File(app.filesDir, RUNTIME_DIR)
        val ready = File(root, ".complete")
        if (ready.isFile && ready.readText(Charsets.UTF_8) == manifest.digest && isRuntimeComplete(root, manifest)) {
            val binary = File(root, manifest.binary.path)
            if (!binary.canExecute()) require(binary.setExecutable(true, true)) { "Não foi possível habilitar a execução do QEMU." }
            return PreparedRuntime(binary, File(root, "lib"), File(root, "share/qemu"), manifest.version)
        }

        val parent = root.parentFile ?: throw IllegalStateException("Pasta privada do runtime QEMU inválida.")
        require(parent.mkdirs() || parent.isDirectory) { "Não foi possível criar a pasta privada do runtime QEMU." }
        val stage = File(parent, "runtime-stage-${manifest.digest.take(12)}")
        stage.deleteRecursively()
        require(stage.mkdirs()) { "Não foi possível preparar os arquivos do runtime QEMU." }
        try {
            for (item in manifest.files) copyAsset(app, item, stage)
            val binary = File(stage, manifest.binary.path)
            require(binary.setExecutable(true, true)) { "Não foi possível habilitar a execução do QEMU." }
            manifest.files.filter { it.path.startsWith("lib/") }.forEach { item ->
                val lib = File(stage, item.path)
                require(lib.setReadable(true, true)) { "Não foi possível ler a dependência QEMU ${lib.name}." }
            }
            File(stage, ".complete").writeText(manifest.digest, Charsets.UTF_8)
            if (root.exists()) require(root.deleteRecursively()) { "Não foi possível substituir o runtime QEMU anterior." }
            require(stage.renameTo(root)) { "Não foi possível ativar o runtime QEMU." }
        } catch (t: Throwable) {
            stage.deleteRecursively()
            throw t
        }
        return PreparedRuntime(File(root, manifest.binary.path), File(root, "lib"), File(root, "share/qemu"), manifest.version)
    }

    private fun readManifest(context: Context): RuntimeManifest =
        parseManifest(context.assets.open(ASSET_MANIFEST).use { it.readBytes() })

    private fun parseManifest(bytes: ByteArray): RuntimeManifest {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MANIFEST_BYTES) { "Manifesto do runtime QEMU inválido." }
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val version = json.getString("version")
        require(version.isNotBlank() && version.length <= 64) { "Versão do runtime QEMU inválida." }
        val array = json.getJSONArray("files")
        require(array.length() in 1..MAX_RUNTIME_FILES) { "Quantidade de arquivos no runtime QEMU inválida." }
        val files = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val path = item.getString("path")
            require(isSafeRelativePath(path)) { "Caminho inseguro no runtime QEMU: $path" }
            val size = item.getLong("size")
            require(size > 0) { "Tamanho inválido para o arquivo QEMU $path." }
            val sha = item.getString("sha256").lowercase()
            require(sha.matches(Regex("[0-9a-f]{64}"))) { "Hash inválido para o arquivo QEMU $path." }
            RuntimeFile(path, size, sha)
        }
        require(files.map { it.path }.toSet().size == files.size) { "O manifesto QEMU contém caminhos duplicados." }
        require(files.any { it.path == "bin/qemu-system-aarch64" }) { "Manifesto do runtime QEMU sem executável AArch64." }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        return RuntimeManifest(version, files, digest)
    }

    private fun copyAsset(context: Context, item: RuntimeFile, root: File) {
        val target = File(root, item.path).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator)) { "Arquivo QEMU fora da pasta privada." }
        require(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true) {
            "Não foi possível criar a pasta ${target.parentFile?.name}."
        }
        val temp = File(target.parentFile, target.name + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        try {
            context.assets.open("zyx-qemu/${item.path}").use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= item.size) { "O asset ${item.path} é maior que o manifesto." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(written == item.size && digest.digest().toHex() == item.sha256) {
                "A verificação de integridade do runtime falhou: ${item.path}."
            }
            require(temp.renameTo(target)) { "Não foi possível instalar ${item.path} no armazenamento privado." }
        } finally {
            temp.delete()
        }
    }

    private fun isRuntimeComplete(root: File, manifest: RuntimeManifest): Boolean = manifest.files.all { item ->
        val file = File(root, item.path)
        file.isFile && file.length() == item.size
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && '\\' !in path && '\u0000' !in path &&
            path.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    private const val MAX_RUNTIME_FILES = 512
}
