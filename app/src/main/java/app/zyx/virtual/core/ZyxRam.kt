package app.zyx.virtual.core

import java.io.File
import kotlin.math.min

/**
 * Política de memória do ZyXVirtual.
 *
 * O Android não expõe RAM Plus como RAM física para o guest. Em vez disso, usamos
 * o swap/zram disponível como sinal seguro de que o aparelho tem memória virtual
 * configurada e anunciamos até 3 GiB adicionais ao sistema convidado.
 */
object ZyxRam {
    const val BASE_RAM_MB = 1024
    const val MAX_RAM_PLUS_MB = 3072

    data class Info(
        val physicalMb: Int,
        val swapMb: Int,
        val ramPlusMb: Int,
        val guestRamMb: Int,
    ) {
        val ramPlusAvailable: Boolean get() = ramPlusMb > 0
    }

    fun info(): Info {
        val values = runCatching {
            File("/proc/meminfo").useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2) parts[0].removeSuffix(":") to parts[1].toLong() else null
                }.toMap()
            }
        }.getOrDefault(emptyMap())
        val physical = (values["MemTotal"]?.div(1024) ?: 0L).toInt().coerceAtLeast(0)
        val swap = (values["SwapTotal"]?.div(1024) ?: 0L).toInt().coerceAtLeast(0)
        val plus = min(MAX_RAM_PLUS_MB, swap)
        return Info(physical, swap, plus, BASE_RAM_MB + plus)
    }

    /** Default requested guest budget: 1 GiB physical + up to 3 GiB RAM Plus. */
    fun defaultGuestRamMb(): Int = info().guestRamMb

    fun label(ramMb: Int): String = if (ramMb <= BASE_RAM_MB) {
        "${BASE_RAM_MB / 1024} GB normal"
    } else {
        val plus = (ramMb - BASE_RAM_MB).coerceAtLeast(0)
        "${BASE_RAM_MB / 1024} GB normal + ${plus / 1024} GB RAM Plus"
    }
}
