package app.aemu.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/** Resolve a estrutura UTM do pacote e prepara cópias graváveis sem alterar os arquivos verificados. */
object ZyxOsVmStorage {
    data class Sources(
        val systemDisk: File,
        val dataDisk: File?,
        val firmwareCode: File,
        val firmwareVarsTemplate: File,
    )

    private data class CopyItem(val source: File, val target: File, val expectedSha256: String)

    fun resolve(installed: InstalledZyxOs): Sources {
        val root = installed.directory.canonicalFile
        val entries = installed.manifest.files

        fun resolveOne(label: String, predicate: (String) -> Boolean, required: Boolean = true): File? {
            val matches = entries.filter { predicate(it.path) }
            if (!required && matches.isEmpty()) return null
            require(matches.size == 1) { "O pacote ZyX OS precisa conter exatamente um arquivo $label." }
            val entry = matches.single()
            require(isSafeZyxPackagePath(entry.path)) { "Caminho inseguro no manifesto ZyXOS: ${entry.path}" }
            val file = File(root, entry.path).canonicalFile
            require(file.path.startsWith(root.path + File.separator)) { "Arquivo fora do pacote ZyXOS: ${entry.path}" }
            require(file.isFile && file.length() == entry.size) { "Arquivo ausente ou incompleto no pacote: ${entry.path}" }
            return file
        }

        val systemDisk = resolveOne("disco principal vda.qcow2", { it.endsWith("/Data/vda.qcow2", ignoreCase = true) })
        val dataDisk = resolveOne("disco de dados vdb.qcow2", { it.endsWith("/Data/vdb.qcow2", ignoreCase = true) }, required = false)
        val firmwareCode = resolveOne("firmware AAVMF_CODE.no-secboot.fd", {
            it.endsWith("/AAVMF_CODE.no-secboot.fd", ignoreCase = true)
        })
        val firmwareVars = resolveOne("modelo de variáveis AAVMF_VARS.fd", {
            it.endsWith("/AAVMF_VARS.fd", ignoreCase = true)
        })
        return Sources(systemDisk!!, dataDisk, firmwareCode!!, firmwareVars!!)
    }

    /**
     * Cria a configuração QEMU e mantém as alterações do guest em cópias privadas.
     * A cópia é feita uma vez por build; o pacote base e o firmware code permanecem intactos.
     */
    fun prepare(
        installed: InstalledZyxOs,
        memoryMiB: Int = maxOf(2048, installed.manifest.minimumMemoryMiB),
        cpuCount: Int = 2,
        vncDisplay: Int = 1,
    ): ZyxSystemVmConfig {
        require(memoryMiB >= installed.manifest.minimumMemoryMiB) {
            "A memória configurada é menor que o mínimo exigido pelo pacote ZyX OS."
        }
        require(memoryMiB in 2048..8192) { "A memória do guest deve ficar entre 2048 e 8192 MiB." }
        require(cpuCount in 1..8) { "A quantidade de CPUs virtuais deve ficar entre 1 e 8." }

        val sources = resolve(installed)
        val parent = installed.directory.canonicalFile.parentFile
            ?: throw IllegalStateException("Não foi possível localizar a pasta privada do ZyX OS.")
        val buildKey = installed.manifest.buildId
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
            .take(64)
            .ifBlank { "unknown" }
        val packageKey = packageFingerprint(installed).take(16)
        val stateDir = File(parent, "vm-state/$buildKey-$packageKey")
        require(stateDir.mkdirs() || stateDir.isDirectory) { "Não foi possível criar o armazenamento persistente da VM." }
        val stateMarker = File(stateDir, ".running")
        val stateSchema = File(stateDir, ".schema")

        fun copyItem(source: File, name: String): CopyItem {
            val relative = source.relativeTo(installed.directory.canonicalFile).invariantSeparatorsPath
            val entry = installed.manifest.files.singleOrNull { it.path == relative }
                ?: throw IllegalArgumentException("O manifesto não identifica ${source.name}.")
            return CopyItem(source, File(stateDir, name), entry.sha256)
        }
        val copies = buildList {
            add(copyItem(sources.systemDisk, "vda-writable.qcow2"))
            sources.dataDisk?.let { add(copyItem(it, "vdb-writable.qcow2")) }
            add(copyItem(sources.firmwareVarsTemplate, "efi_vars.fd"))
        }
        if (stateMarker.isFile || stateSchema.readTextOrNull() != STATE_SCHEMA) {
            // Um QEMU morto durante init_user0/APEX pode deixar o ext4 do guest
            // somente leitura mesmo quando o QCOW2 ainda tem o tamanho correto.
            // Estados criados antes do marcador também são descartados na migração.
            // Nunca reutilize esse estado: volte às imagens verificadas do pacote.
            copies.forEach { item ->
                item.target.delete()
                File(item.target.parentFile, item.target.name + ".part").delete()
            }
            stateMarker.delete()
            stateSchema.delete()
        }
        val invalidExisting = copies.filter { item ->
            item.target.exists() && (!item.target.isFile || item.target.length() != item.source.length())
        }
        invalidExisting.forEach { item ->
            require(item.target.deleteRecursively()) {
                "Não foi possível remover a cópia persistente incompleta ${item.target.name}."
            }
            File(item.target.parentFile, item.target.name + ".part").delete()
        }
        val missingBytes = copies.filter { !it.target.exists() }.sumOf { it.source.length() }
        require(stateDir.usableSpace >= missingBytes + MIN_FREE_SPACE_BYTES) {
            "Espaço insuficiente para criar cópias graváveis do sistema ZyX OS."
        }
        copies.forEach(::copyVerifiedIfNeeded)
        stateSchema.writeText(STATE_SCHEMA, Charsets.US_ASCII)

        val logDir = File(stateDir, "logs")
        require(logDir.mkdirs() || logDir.isDirectory) { "Não foi possível criar a pasta de registros da VM." }
        val runDir = File(stateDir, "run")
        require(runDir.mkdirs() || runDir.isDirectory) { "Não foi possível criar a pasta de execução da VM." }

        return ZyxSystemVmConfig(
            diskPath = File(stateDir, "vda-writable.qcow2").absolutePath,
            dataDiskPath = sources.dataDisk?.let { File(stateDir, "vdb-writable.qcow2").absolutePath },
            memoryMiB = memoryMiB,
            cpuCount = cpuCount,
            vncDisplay = vncDisplay,
            serialLogPath = File(logDir, "serial.log").absolutePath,
            qmpSocketPath = File(runDir, "qmp.sock").absolutePath,
            firmwareCodePath = sources.firmwareCode.absolutePath,
            firmwareVarsPath = File(stateDir, "efi_vars.fd").absolutePath,
            stateMarkerPath = stateMarker.absolutePath,
        )
    }

    /** Marca o estado como potencialmente sujo antes de entregar o guest ao QEMU. */
    fun markRunning(config: ZyxSystemVmConfig) {
        config.stateMarkerPath?.let { marker ->
            val file = File(marker)
            file.parentFile?.mkdirs()
            file.writeText("running\n", Charsets.US_ASCII)
        }
    }

    /** Remove o marcador somente quando o guest desligou de modo limpo. */
    fun markStoppedCleanly(config: ZyxSystemVmConfig) {
        config.stateMarkerPath?.let { File(it).delete() }
    }

    private fun copyVerifiedIfNeeded(item: CopyItem) {
        val (source, target, expectedSha256) = item
        if (target.isFile && target.length() == source.length()) {
            require(target.setWritable(true, true)) { "Não foi possível tornar gravável ${target.name}." }
            return
        }
        require(!target.exists()) { "O arquivo persistente ${target.name} já existe e não pode ser substituído automaticamente." }
        target.parentFile?.let { require(it.mkdirs() || it.isDirectory) { "Não foi possível criar ${it.absolutePath}." } }
        val temp = File(target.parentFile, target.name + ".part")
        temp.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(digest.digest().toHex() == expectedSha256) { "A imagem-base do pacote foi alterada: ${source.name}." }
            require(temp.length() == source.length()) { "Falha ao copiar ${source.name} para o armazenamento da VM." }
            require(temp.renameTo(target)) { "Não foi possível ativar a cópia gravável de ${source.name}." }
            require(target.setWritable(true, true)) { "Não foi possível tornar gravável ${target.name}." }
        } finally {
            temp.delete()
        }
    }

    private fun packageFingerprint(installed: InstalledZyxOs): String {
        val digest = MessageDigest.getInstance("SHA-256")
        installed.manifest.files.sortedBy { it.path }.forEach { entry ->
            digest.update(entry.path.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(entry.size.toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(entry.sha256.toByteArray(Charsets.US_ASCII))
            digest.update(0.toByte())
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun File.readTextOrNull(): String? =
        if (isFile) runCatching { readText(Charsets.US_ASCII).trim() }.getOrNull() else null

    private const val COPY_BUFFER = 1024 * 1024
    private const val MIN_FREE_SPACE_BYTES = 128L * 1024 * 1024
    private const val STATE_SCHEMA = "2"
}
