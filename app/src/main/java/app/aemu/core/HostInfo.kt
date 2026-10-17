package app.aemu.core

import android.os.Build
import java.io.File

/** Сведения о телефоне-хозяине, от которых зависят обходы в движке. */
object HostInfo {
    fun abis(): List<String> = Build.SUPPORTED_ABIS.toList()

    fun isArm64(): Boolean = abis().any { it == "arm64-v8a" }

    fun hasKvmDevice(): Boolean = File("/dev/kvm").let { it.exists() && it.canRead() }

    /** Драйвер GPU (adreno, mali, powervr…) из ro.hardware.egl. */
    fun gpu(): String = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        (c.getMethod("get", String::class.java).invoke(null, "ro.hardware.egl") as String).lowercase()
    }.getOrDefault("")
}
