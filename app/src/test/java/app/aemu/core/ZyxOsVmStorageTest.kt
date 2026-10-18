package app.aemu.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ZyxOsVmStorageTest {
    @get:Rule val temp = TemporaryFolder()

    private fun installed(): Pair<InstalledZyxOs, Map<String, ByteArray>> {
        val dir = File(temp.root, "current").apply { mkdirs() }
        val payload = linkedMapOf(
            "LineageOS_on_arm64.utm/config.plist" to "plist".toByteArray(),
            "LineageOS_on_arm64.utm/Data/vda.qcow2" to byteArrayOf(1, 2, 3, 4, 5, 6),
            "LineageOS_on_arm64.utm/Data/vdb.qcow2" to byteArrayOf(7, 8, 9, 10),
            "ZyXFirmware/AAVMF_CODE.no-secboot.fd" to byteArrayOf(11, 12, 13),
            "ZyXFirmware/AAVMF_VARS.fd" to byteArrayOf(14, 15, 16, 17),
        )
        val entries = payload.map { (path, bytes) ->
            File(dir, path).apply { parentFile?.mkdirs(); writeBytes(bytes) }
            ZyxPackageFile(path, bytes.size.toLong(), bytes.sha256())
        }
        val manifest = ZyxOsPackageManifest(
            schemaVersion = 1,
            packageId = "org.zyx.virtual.os",
            displayName = "ZyX OS test",
            lineageBranch = "lineage-23.2",
            androidMajorVersion = 16,
            buildId = "test-build",
            gappsIncluded = false,
            product = "lineage_zyx_virtual",
            deviceCodename = "zyx_virtual",
            baseVirtualTarget = "virtio_arm64only",
            architecture = "aarch64",
            requiredBackend = "qemu-system-aarch64",
            machine = "virt",
            packageType = "utm-qemu-virtual-machine",
            minimumMemoryMiB = 2048,
            files = entries,
        ).requireSupported()
        return InstalledZyxOs(manifest, dir, entries.sumOf { it.size }, 1L) to payload
    }

    @Test fun resolvesTheExpectedSystemDataAndFirmwareFiles() {
        val (installed) = installed()
        val files = ZyxOsVmStorage.resolve(installed)
        assertEquals("vda.qcow2", files.systemDisk.name)
        assertEquals("vdb.qcow2", files.dataDisk?.name)
        assertEquals("AAVMF_CODE.no-secboot.fd", files.firmwareCode.name)
        assertEquals("AAVMF_VARS.fd", files.firmwareVarsTemplate.name)
    }

    @Test fun preparesWritableCopiesAndKeepsUserChangesBetweenRuns() {
        val (installed, payload) = installed()
        val config = ZyxOsVmStorage.prepare(installed)
        val systemCopy = File(config.diskPath)
        val dataCopy = File(config.dataDiskPath!!)
        val varsCopy = File(config.firmwareVarsPath!!)

        assertNotEquals(ZyxOsVmStorage.resolve(installed).systemDisk.absolutePath, config.diskPath)
        assertArrayEquals(payload.getValue("LineageOS_on_arm64.utm/Data/vda.qcow2"), systemCopy.readBytes())
        assertArrayEquals(payload.getValue("LineageOS_on_arm64.utm/Data/vdb.qcow2"), dataCopy.readBytes())
        assertArrayEquals(payload.getValue("ZyXFirmware/AAVMF_VARS.fd"), varsCopy.readBytes())
        assertTrue(systemCopy.canWrite())
        assertTrue(varsCopy.canWrite())
        assertTrue(File(config.serialLogPath).parentFile!!.isDirectory)
        assertTrue(File(config.qmpSocketPath).parentFile!!.isDirectory)

        val guestState = byteArrayOf(21, 22, 23, 24, 25, 26)
        systemCopy.writeBytes(guestState)
        val restarted = ZyxOsVmStorage.prepare(installed)
        assertArrayEquals(guestState, File(restarted.diskPath).readBytes())
    }

    @Test fun replacesAnInterruptedPersistentCopy() {
        val (installed, payload) = installed()
        val first = ZyxOsVmStorage.prepare(installed)
        val truncated = File(first.diskPath)
        truncated.writeBytes(payload.getValue("LineageOS_on_arm64.utm/Data/vda.qcow2").copyOf(2))

        val recovered = ZyxOsVmStorage.prepare(installed)

        assertArrayEquals(payload.getValue("LineageOS_on_arm64.utm/Data/vda.qcow2"), File(recovered.diskPath).readBytes())
    }

    @Test fun rejectsBaseDiskChangedAfterItsManifestHashWasRecorded() {
        val (installed) = installed()
        val base = ZyxOsVmStorage.resolve(installed).systemDisk
        base.writeBytes(byteArrayOf(99, 2, 3, 4, 5, 6)) // Mesmo tamanho, conteúdo diferente.
        val error = runCatching { ZyxOsVmStorage.prepare(installed) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("alterada"))
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
