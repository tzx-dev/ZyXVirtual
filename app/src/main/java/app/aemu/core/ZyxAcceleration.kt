package app.aemu.core

import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Seleção do acelerador para o QEMU system-mode.
 *
 * AVF/pKVM não é um backend que possa ser passado diretamente ao QEMU atual;
 * ele é reportado separadamente para diagnóstico. KVM só é selecionado quando o
 * processo consegue abrir /dev/kvm com leitura e escrita.
 */
object ZyxAcceleration {
    private const val KVM_DEVICE = "/dev/kvm"

    data class Capability(
        val kvmDevicePresent: Boolean,
        val kvmAccessible: Boolean,
        val avfFilesPresent: Boolean,
    ) {
        val selected: ZyxAccelerator
            get() = if (kvmAccessible) ZyxAccelerator.KVM else ZyxAccelerator.TCG
    }

    fun probe(): Capability {
        val device = File(KVM_DEVICE)
        val present = device.exists()
        val accessible = if (present) {
            runCatching {
                ParcelFileDescriptor.open(device, ParcelFileDescriptor.MODE_READ_WRITE).use { }
                true
            }.getOrDefault(false)
        } else {
            false
        }
        // AVF é deliberadamente apenas informativo nesta etapa: o QEMU
        // empacotado ainda não usa VirtualizationService/crosvm como VMM.
        val avf = File("/apex/com.android.virt").isDirectory ||
            File("/system_ext/apex/com.android.virt").isDirectory
        return Capability(present, accessible, avf)
    }

    fun select(): ZyxAccelerator = probe().selected
}
