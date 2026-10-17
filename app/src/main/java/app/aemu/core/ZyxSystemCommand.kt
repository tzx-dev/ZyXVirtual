package app.aemu.core

import java.io.File

/** Inputs for a full-system AArch64 guest. The diskPath must be a private writable copy, never the package base image. */
data class ZyxSystemVmConfig(
    val diskPath: String,
    val diskFormat: String = "qcow2",
    val memoryMiB: Int = 2048,
    val cpuCount: Int = 2,
    val vncDisplay: Int = 1,
    val serialLogPath: String,
    val qmpSocketPath: String,
    val kernelPath: String? = null,
    val initrdPath: String? = null,
    val firmwareCodePath: String? = null,
    val firmwareVarsPath: String? = null,
)

/** Pure command construction, intentionally separate from the legacy qemu-linux-user runner. */
object ZyxSystemCommand {
    fun build(config: ZyxSystemVmConfig): List<String> {
        require(config.diskFormat == "qcow2" || config.diskFormat == "raw") { "Formato de disco ZyX não suportado." }
        require(config.memoryMiB in 2048..8192) { "A memória do guest deve ficar entre 2048 e 8192 MiB." }
        require(config.cpuCount in 1..8) { "A quantidade de CPUs virtuais deve ficar entre 1 e 8." }
        require(config.vncDisplay in 0..99) { "Display VNC inválido." }
        listOf(config.diskPath, config.serialLogPath, config.qmpSocketPath).forEach(::requireAbsolutePath)
        config.kernelPath?.let(::requireAbsolutePath)
        config.initrdPath?.let(::requireAbsolutePath)
        config.firmwareCodePath?.let(::requireAbsolutePath)
        config.firmwareVarsPath?.let(::requireAbsolutePath)
        require(config.initrdPath == null || config.kernelPath != null) { "initrd requer um kernel explícito." }
        require((config.firmwareCodePath == null) == (config.firmwareVarsPath == null)) {
            "Firmware pflash precisa de imagem code e vars em conjunto."
        }

        val args = mutableListOf(
            "-name", "ZyX-OS",
            "-machine", "virt,gic-version=3",
            "-cpu", "cortex-a57",
            "-smp", config.cpuCount.toString(),
            "-m", config.memoryMiB.toString(),
            "-accel", "tcg,thread=multi",
            "-nodefaults",
            "-no-user-config",
            "-display", "none",
            "-vnc", "127.0.0.1:${config.vncDisplay}",
            "-device", "virtio-gpu-pci",
            "-device", "virtio-keyboard-pci",
            "-device", "virtio-tablet-pci",
            "-netdev", "user,id=zyxnet0",
            "-device", "virtio-net-pci,netdev=zyxnet0",
            "-serial", "file:${config.serialLogPath}",
            "-monitor", "none",
            "-qmp", "unix:${config.qmpSocketPath},server=on,wait=off",
            "-no-reboot",
        )
        if (config.firmwareCodePath != null) {
            args += listOf("-drive", "if=pflash,format=raw,readonly=on,file=${qemuOptionPath(config.firmwareCodePath)}")
            args += listOf("-drive", "if=pflash,format=raw,file=${qemuOptionPath(config.firmwareVarsPath!!)}")
        }
        if (config.kernelPath != null) args += listOf("-kernel", config.kernelPath)
        if (config.initrdPath != null) args += listOf("-initrd", config.initrdPath)

        val fileNode = "{\"driver\":\"file\",\"filename\":${jsonQuote(config.diskPath)},\"node-name\":\"zyxfile0\"}"
        val diskNode = "{\"driver\":\"${config.diskFormat}\",\"file\":\"zyxfile0\",\"node-name\":\"zyxdisk0\"}"
        args += listOf("-blockdev", fileNode, "-blockdev", diskNode, "-device", "virtio-blk-pci,drive=zyxdisk0")
        return args
    }

    private fun requireAbsolutePath(path: String) {
        require(path.isNotBlank() && File(path).isAbsolute && '\u0000' !in path) { "Caminho de guest inválido." }
    }

    private fun jsonQuote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    private fun qemuOptionPath(value: String): String = value.replace("\\", "\\\\").replace(",", "\\,")
}
