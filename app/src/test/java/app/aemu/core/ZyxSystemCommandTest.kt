package app.aemu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZyxSystemCommandTest {
    private fun config(disk: String = "/data/user/0/app/files/zyx-os/data/disk.qcow2") = ZyxSystemVmConfig(
        diskPath = disk,
        serialLogPath = "/data/user/0/app/files/zyx-os/logs/serial.log",
        qmpSocketPath = "/data/user/0/app/files/zyx-os/run/qmp.sock",
    )

    @Test fun emitsArm64SystemModeVirtTcgAndVirtioDevices() {
        val args = ZyxSystemCommand.build(config())
        assertEquals("virt,gic-version=3", args[args.indexOf("-machine") + 1])
        assertEquals("max", args[args.indexOf("-cpu") + 1])
        assertEquals("tcg,thread=multi", args[args.indexOf("-accel") + 1])
        assertTrue(args.contains("virtio-gpu-pci"))
        assertTrue(args.contains("virtio-keyboard-pci"))
        assertTrue(args.contains("virtio-tablet-pci"))
        assertTrue(args.contains("virtio-net-pci,netdev=zyxnet0,romfile="))
        assertTrue(args.contains("order=c"))
        assertTrue(args.contains("virtio-blk-pci,drive=zyxdisk0,bootindex=0"))
        assertTrue(args.none { it.contains("qemu-arm") || it.contains("linux-user") })
    }

    @Test fun diskPathWithSpacesIsKeptAsJsonArgument() {
        val args = ZyxSystemCommand.build(config("/data/app/zyx os/disk.qcow2"))
        val blockIndex = args.indexOf("-blockdev")
        assertTrue(args[blockIndex + 1].contains("/data/app/zyx os/disk.qcow2"))
        assertTrue(args[blockIndex + 1].startsWith("{\"driver\":\"file\""))
    }

    @Test fun supportsSecondVirtioDiskAndUefiPflashPair() {
        val args = ZyxSystemCommand.build(
            config().copy(
                dataDiskPath = "/data/user/0/app/files/zyx-os/run/userdata.qcow2",
                firmwareCodePath = "/data/user/0/app/files/zyx-os/firmware/AAVMF_CODE.no-secboot.fd",
                firmwareVarsPath = "/data/user/0/app/files/zyx-os/run/efi_vars.fd",
            )
        )
        assertTrue(args.contains("virtio-blk-pci,drive=zyxdisk0,bootindex=0"))
        assertTrue(args.contains("virtio-blk-pci,drive=zyxdisk1,bootindex=1"))
        assertTrue(args.contains("if=pflash,format=raw,readonly=on,file=/data/user/0/app/files/zyx-os/firmware/AAVMF_CODE.no-secboot.fd"))
        assertTrue(args.contains("if=pflash,format=raw,file=/data/user/0/app/files/zyx-os/run/efi_vars.fd"))
    }

    @Test fun rejectsTooLittleMemoryBadCpuCountAndRelativePaths() {
        assertTrue(runCatching { ZyxSystemCommand.build(config().copy(memoryMiB = 1024)) }.isFailure)
        assertTrue(runCatching { ZyxSystemCommand.build(config().copy(cpuCount = 9)) }.isFailure)
        assertTrue(runCatching { ZyxSystemCommand.build(config().copy(diskPath = "disk.qcow2")) }.isFailure)
    }
}
