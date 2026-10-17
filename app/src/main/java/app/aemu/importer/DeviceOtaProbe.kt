package app.aemu.importer

/** Metadados mínimos de uma OTA de aparelho; não representa uma imagem de máquina virtual. */
internal data class DeviceOtaDescriptor(
    val device: String?,
    val sdkLevel: Int?,
    val otaType: String?,
)

/** Reconhece pacotes Android OTA A/B para evitar importá-los como rootfs guest. */
internal object DeviceOtaProbe {
    fun inspect(entryNames: Iterable<String>, metadata: String?): DeviceOtaDescriptor? {
        val hasPayload = entryNames.any {
            it.replace('\\', '/').trimStart('/').substringAfterLast('/') == "payload.bin"
        }
        if (!hasPayload) return null

        val properties = metadata.orEmpty().lineSequence().mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null
            else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }.toMap()

        return DeviceOtaDescriptor(
            device = properties["pre-device"]?.takeIf(String::isNotBlank),
            sdkLevel = properties["post-sdk-level"]?.toIntOrNull(),
            otaType = properties["ota-type"]?.takeIf(String::isNotBlank),
        )
    }

    fun rejectionMessage(ota: DeviceOtaDescriptor): String = buildString {
        append("Este arquivo é uma OTA de atualização de Android")
        if (ota.otaType.equals("AB", ignoreCase = true)) append(" A/B")
        ota.device?.let { append(" para o aparelho ").append(it) }
        ota.sdkLevel?.let { append(" (API ").append(it).append(')') }
        append(". Uma OTA de aparelho não é uma imagem de guest virtual e não pode ser inicializada pelo motor atual.")
        append(" O ZyX OS precisará ser compilado para o contrato de dispositivos virtuais ZyX, com kernel e partições próprias.")
    }
}
