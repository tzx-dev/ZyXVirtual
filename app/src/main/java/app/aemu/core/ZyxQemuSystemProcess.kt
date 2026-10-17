package app.aemu.core

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/** Executa o binário QEMU Android empacotado e controla o ciclo de vida por QMP. */
class ZyxQemuSystemProcess(
    context: Context,
    private val config: ZyxSystemVmConfig,
) : AutoCloseable {
    private val app = context.applicationContext
    @Volatile private var process: Process? = null

    val isRunning: Boolean get() = process?.isAlive == true

    @Synchronized
    fun start(): Process {
        check(!isRunning) { "A VM ZyX OS já está em execução." }
        val runtime = ZyxQemuAssetRuntime.install(app)
        val args = ZyxSystemCommand.build(config)
        File(config.qmpSocketPath).delete()
        File(config.serialLogPath).parentFile?.mkdirs()
        val qemuLog = File(config.serialLogPath).parentFile?.let { File(it, "qemu.log") }
            ?: File(app.cacheDir, "zyx-qemu.log")
        val builder = ProcessBuilder(listOf(runtime.binary.absolutePath) + args)
            .directory(app.filesDir)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(qemuLog))
        builder.environment().apply {
            put("HOME", app.filesDir.absolutePath)
            put("TMPDIR", app.cacheDir.absolutePath)
            put("LD_LIBRARY_PATH", runtime.libraryDirectory.absolutePath +
                (System.getenv("LD_LIBRARY_PATH")?.takeIf { it.isNotBlank() }?.let { ":$it" } ?: ""))
            put("QEMU_AUDIO_DRV", "none")
        }
        return builder.start().also { process = it }
    }

    fun requestReset(): Boolean = isRunning && sendQmp("system_reset")

    /** Solicita ACPI poweroff; se o guest não desligar, envia quit e encerra o processo. */
    @Synchronized
    fun stop(gracefulTimeoutSeconds: Long = 12): Boolean {
        val running = process ?: return true
        if (!running.isAlive) {
            process = null
            return true
        }
        sendQmp("system_powerdown")
        if (running.waitFor(gracefulTimeoutSeconds, TimeUnit.SECONDS)) {
            process = null
            return true
        }
        sendQmp("quit")
        if (!running.waitFor(3, TimeUnit.SECONDS)) {
            running.destroy()
            if (!running.waitFor(2, TimeUnit.SECONDS)) running.destroyForcibly()
        }
        val stopped = !running.isAlive
        if (stopped) process = null
        return stopped
    }

    override fun close() {
        stop()
    }

    private fun sendQmp(command: String): Boolean {
        require(command in QMP_COMMANDS) { "Comando QMP não permitido: $command" }
        repeat(QMP_CONNECT_ATTEMPTS) { attempt ->
            val socket = LocalSocket()
            try {
                socket.soTimeout = QMP_TIMEOUT_MS
                socket.connect(LocalSocketAddress(config.qmpSocketPath, LocalSocketAddress.Namespace.FILESYSTEM))
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
                if (reader.readLine() == null) return false
                writer.write("{\"execute\":\"qmp_capabilities\"}\r\n")
                writer.flush()
                if (!readQmpReply(reader)) return false
                writer.write("{\"execute\":\"$command\"}\r\n")
                writer.flush()
                val replied = readQmpReply(reader)
                if (replied || !isRunning) return true
            } catch (_: Exception) {
                if (!isRunning) return false
            } finally {
                runCatching { socket.close() }
            }
            if (attempt + 1 < QMP_CONNECT_ATTEMPTS) Thread.sleep(QMP_RETRY_DELAY_MS)
        }
        return false
    }

    private fun readQmpReply(reader: BufferedReader): Boolean {
        repeat(QMP_MAX_REPLY_LINES) {
            val line = reader.readLine() ?: return false
            if (line.contains("\"return\"") || line.contains("\"error\"")) return line.contains("\"return\"")
        }
        return false
    }

    companion object {
        private const val QMP_TIMEOUT_MS = 2_000
        private const val QMP_CONNECT_ATTEMPTS = 20
        private const val QMP_RETRY_DELAY_MS = 100L
        private const val QMP_MAX_REPLY_LINES = 16
        private val QMP_COMMANDS = setOf("system_powerdown", "system_reset", "quit")

        fun isAvailable(context: Context): Boolean = ZyxQemuAssetRuntime.isAvailable(context)
    }
}
