package app.aemu.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale

/**
 * Estado observável do host e do backend efetivamente usado pelo ZyX Virtual.
 * Esta camada não anuncia AVF/KVM como disponível sem que o dispositivo exponha o recurso.
 */
object ZyxRuntime {
    const val PRODUCT_NAME = "ZyX Virtual"
    const val GUEST_NAME = "ZyX OS"
    const val VERSION = "0.1.0-dev"

    data class HostCapabilities(
        val abis: List<String>,
        val arm64: Boolean,
        val cpuCount: Int,
        val memoryMb: Int,
        val gpu: String,
        val kvmDevice: Boolean,
        val qemuSystemBundled: Boolean,
    )

    fun probe(ctx: Context): HostCapabilities {
        val abis = Build.SUPPORTED_ABIS.toList()
        val memory = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        return HostCapabilities(
            abis = abis,
            arm64 = abis.any { it == "arm64-v8a" },
            cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            memoryMb = (memory.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(0),
            gpu = HostInfo.gpu().ifBlank { "desconhecida" },
            kvmDevice = File("/dev/kvm").let { it.exists() && it.canRead() },
            qemuSystemBundled = ZyxQemuSystemBridge.isAvailable(ctx),
        )
    }

    fun diagnostics(ctx: Context): String {
        val h = probe(ctx)
        val abi = h.abis.ifEmpty { listOf("desconhecida") }.joinToString(", ")
        val backend = if (h.kvmDevice) {
            "KVM exposto pelo host; o backend atual ainda usa QEMU user-mode"
        } else {
            "QEMU user-mode; /dev/kvm não está acessível ao app"
        }
        return buildString {
            append("ZyX Virtual ").append(VERSION).append('\n')
            append("Host ABI: ").append(abi).append('\n')
            append("CPU: ").append(h.cpuCount).append(" threads · RAM ").append(h.memoryMb).append(" MB\n")
            append("GPU: ").append(h.gpu).append('\n')
            append("Backend legado: ").append(backend).append('\n')
            append("QEMU system-mode arm64: ").append(if (h.qemuSystemBundled) "bibliotecas presentes" else "não empacotado; boot desabilitado")
        }
    }

    fun bootSummary(ctx: Context, img: GuestImage): String {
        val h = probe(ctx)
        val abi = img.abi.ifBlank { "não detectada" }
        val mode = if (img.engine.kernelVirtualization) "kernel VM" else "QEMU user-mode"
        return String.format(
            Locale.US,
            "backend=%s mode=%s guestAbi=%s hostArm64=%s kvm=%s gpu=%s",
            img.engine.id,
            mode,
            abi,
            h.arm64,
            h.kvmDevice,
            h.gpu,
        )
    }
}
