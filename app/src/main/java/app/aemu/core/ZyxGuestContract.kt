package app.aemu.core

import java.io.File

/**
 * Contrato da primeira etapa do ZyX OS.
 *
 * O backend herdado executa processos ARM32 em QEMU user-mode e emula apenas as
 * interfaces de kernel que o guest legado usa. Isso é deliberadamente diferente de
 * uma VM arm64 completa; imagens modernas recebem diagnóstico explícito até o backend
 * AVF/KVM e o kernel virtual correspondente serem entregues.
 */
object ZyxGuestContract {
    const val CONTRACT_VERSION = 1
    const val PRODUCT = "zyx_virtual"
    val CURRENT_GUEST_ABIS = setOf("armeabi", "armeabi-v7a")
    const val MIN_API = 9
    const val MAX_VALIDATED_API = 25

    fun validate(img: GuestImage, root: File): List<String> = buildList {
        if (!CURRENT_GUEST_ABIS.contains(img.abi)) {
            add("guest ABI ${img.abi.ifBlank { "unknown" }} ainda não é suportada pelo backend QEMU user-mode atual")
        }
        if (img.api < MIN_API) add("guest API ${img.api} está abaixo do contrato mínimo $MIN_API")
        if (img.api > MAX_VALIDATED_API) {
            add("guest API ${img.api} está acima da faixa validada; Android moderno exige kernel/HALs ZyX próprios")
        }
        if (!File(root, "system/framework").isDirectory) add("system/framework ausente")
        if (img.bootclasspath.isBlank()) add("BOOTCLASSPATH não foi detectado durante a importação")
        if (img.engine.kernelVirtualization) {
            add("engine declara virtualização de kernel, mas esta etapa não inclui um VMM ZyX independente")
        }
    }
}
