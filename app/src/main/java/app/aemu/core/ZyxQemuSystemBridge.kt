package app.aemu.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** JNI front-end for a QEMU shared library built with the ZyX API adapter. */
object ZyxQemuSystemBridge {
    private val bridgeLoaded: Boolean = runCatching { System.loadLibrary("zyx-qemu-jni") }.isSuccess

    fun isAvailable(context: Context): Boolean {
        if (!bridgeLoaded) return false
        val dir = File(context.applicationInfo.nativeLibraryDir)
        return File(dir, "libzyx-qemu-jni.so").isFile && File(dir, "libqemu-system-aarch64.so").isFile
    }

    suspend fun run(context: Context, arguments: List<String>): Int = withContext(Dispatchers.IO) {
        check(isAvailable(context)) { "QEMU system-mode arm64 ainda não está empacotado neste APK." }
        require(arguments.isNotEmpty()) { "A lista de argumentos QEMU está vazia." }
        nativeRun(File(context.applicationInfo.nativeLibraryDir, "libqemu-system-aarch64.so").absolutePath,
            arguments.toTypedArray())
    }

    fun requestStop(): Boolean = bridgeLoaded && nativeRequestStop() == 0
    fun requestReset(): Boolean = bridgeLoaded && nativeRequestReset() == 0
    fun isRunning(): Boolean = bridgeLoaded && nativeIsRunning()
    fun version(): String = if (bridgeLoaded) nativeVersion() else "bridge JNI não carregada"

    private external fun nativeRun(libraryPath: String, arguments: Array<String>): Int
    private external fun nativeRequestStop(): Int
    private external fun nativeRequestReset(): Int
    private external fun nativeIsRunning(): Boolean
    private external fun nativeVersion(): String
}
