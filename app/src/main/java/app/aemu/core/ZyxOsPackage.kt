package app.aemu.core

import org.json.JSONObject

const val ZYX_OS_MANIFEST_NAME = "ZYX-MANIFEST.json"

data class ZyxPackageFile(
    val path: String,
    val size: Long,
    val sha256: String,
)

data class ZyxOsPackageManifest(
    val schemaVersion: Int,
    val packageId: String,
    val displayName: String,
    val lineageBranch: String,
    val androidMajorVersion: Int,
    val buildId: String,
    val gappsIncluded: Boolean,
    val product: String,
    val deviceCodename: String,
    val baseVirtualTarget: String,
    val architecture: String,
    val requiredBackend: String,
    val machine: String,
    val packageType: String,
    val minimumMemoryMiB: Int,
    val files: List<ZyxPackageFile>,
) {
    /** Throws unless this is the one supported ZyX OS package, not an arbitrary ROM archive. */
    fun requireSupported(): ZyxOsPackageManifest {
        require(schemaVersion == 1) { "Versão de manifesto ZyXOS não suportada: $schemaVersion." }
        require(packageId == "org.zyx.virtual.os") { "Este arquivo não é um pacote ZyX OS." }
        require(lineageBranch == "lineage-23.2" && androidMajorVersion == 16) {
            "Apenas ZyX OS baseado em LineageOS 23.2 / Android 16 é aceito."
        }
        require(!gappsIncluded) { "O pacote contém Google Apps; esta edição exige uma ROM sem GApps." }
        require(product == "lineage_zyx_virtual" && deviceCodename == "zyx_virtual") {
            "O produto do pacote não corresponde ao ZyX Virtual."
        }
        require(baseVirtualTarget == "virtio_arm64only" && architecture == "aarch64") {
            "O pacote não é o guest ARM64 virtual ZyX esperado."
        }
        require(requiredBackend == "qemu-system-aarch64" && machine == "virt") {
            "O pacote exige um backend de virtualização incompatível."
        }
        require(packageType == "utm-qemu-virtual-machine") {
            "Formato de pacote ZyX OS desconhecido."
        }
        require(minimumMemoryMiB in 1024..16384) { "Requisito de memória inválido no manifesto." }
        require(files.isNotEmpty()) { "O pacote não contém arquivos de sistema." }
        require(files.map { it.path }.distinct().size == files.size) { "O manifesto contém caminhos duplicados." }
        files.forEach { file ->
            require(isSafeZyxPackagePath(file.path)) { "Caminho inseguro no pacote: ${file.path}" }
            require(file.size > 0) { "Tamanho inválido para ${file.path}." }
            require(file.sha256.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 inválido para ${file.path}." }
        }
        require(files.any { it.path.endsWith("config.plist", ignoreCase = true) }) {
            "O pacote UTM/QEMU não contém config.plist."
        }
        require(files.any { it.path.endsWith(".qcow2", ignoreCase = true) || it.path.endsWith(".img", ignoreCase = true) }) {
            "O pacote UTM/QEMU não contém uma imagem de disco."
        }
        return this
    }

    companion object {
        fun parse(json: String): ZyxOsPackageManifest {
            val root = JSONObject(json)
            val os = root.getJSONObject("os")
            val guest = root.getJSONObject("guest")
            val runtime = root.getJSONObject("runtime")
            val entries = root.getJSONArray("files")
            val files = List(entries.length()) { index ->
                val item = entries.getJSONObject(index)
                ZyxPackageFile(
                    path = item.getString("path"),
                    size = item.getLong("size"),
                    sha256 = item.getString("sha256"),
                )
            }
            return ZyxOsPackageManifest(
                schemaVersion = root.getInt("schemaVersion"),
                packageId = root.getString("packageId"),
                displayName = root.optString("displayName", "ZyX OS"),
                lineageBranch = os.getString("lineageBranch"),
                androidMajorVersion = os.getInt("androidMajorVersion"),
                buildId = os.optString("buildId", "unknown"),
                gappsIncluded = os.optBoolean("gappsIncluded", true),
                product = guest.getString("product"),
                deviceCodename = guest.getString("deviceCodename"),
                baseVirtualTarget = guest.getString("baseVirtualTarget"),
                architecture = guest.getString("architecture"),
                requiredBackend = runtime.getString("requiredBackend"),
                machine = runtime.getString("machine"),
                packageType = runtime.getString("packageType"),
                minimumMemoryMiB = runtime.getInt("minimumMemoryMiB"),
                files = files,
            )
        }
    }
}

fun isSafeZyxPackagePath(path: String): Boolean {
    if (path.isBlank() || path.startsWith('/') || path.contains('\\') || path.contains(':')) return false
    if (path.any { it.code < 0x20 }) return false
    return path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
}

data class InstalledZyxOs(
    val manifest: ZyxOsPackageManifest,
    val directory: java.io.File,
    val payloadSizeBytes: Long,
    val installedAtMillis: Long,
)
