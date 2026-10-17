package app.aemu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZyxOsPackageTest {
    private fun manifest(
        androidVersion: Int = 16,
        gapps: Boolean = false,
        branch: String = "lineage-23.2",
        architecture: String = "aarch64",
    ) = ZyxOsPackageManifest(
        schemaVersion = 1,
        packageId = "org.zyx.virtual.os",
        displayName = "ZyX OS",
        lineageBranch = branch,
        androidMajorVersion = androidVersion,
        buildId = "test",
        gappsIncluded = gapps,
        product = "lineage_zyx_virtual",
        deviceCodename = "zyx_virtual",
        baseVirtualTarget = "virtio_arm64only",
        architecture = architecture,
        requiredBackend = "qemu-system-aarch64",
        machine = "virt",
        packageType = "utm-qemu-virtual-machine",
        minimumMemoryMiB = 2048,
        files = listOf(
            ZyxPackageFile("ZyX.utm/config.plist", 12, "a".repeat(64)),
            ZyxPackageFile("ZyX.utm/Data/disk.qcow2", 1024, "b".repeat(64)),
        ),
    )

    @Test fun acceptsOnlyExpectedNoGappsAndroid16Guest() {
        assertEquals("lineage_zyx_virtual", manifest().requireSupported().product)
    }

    @Test fun rejectsGappsBuild() {
        val error = runCatching { manifest(gapps = true).requireSupported() }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("GApps"))
    }

    @Test fun rejectsOtherAndroidVersionAndArchitecture() {
        assertTrue(runCatching { manifest(androidVersion = 15).requireSupported() }.isFailure)
        assertTrue(runCatching { manifest(architecture = "armeabi-v7a").requireSupported() }.isFailure)
        assertTrue(runCatching { manifest(branch = "lineage-23.1").requireSupported() }.isFailure)
    }

    @Test fun rejectsZipTraversalAndAbsolutePaths() {
        assertFalse(isSafeZyxPackagePath("../outside.img"))
        assertFalse(isSafeZyxPackagePath("/absolute/disk.img"))
        assertFalse(isSafeZyxPackagePath("ZyX.utm\\..\\outside.img"))
        assertFalse(isSafeZyxPackagePath("ZyX.utm//disk.img"))
        assertTrue(isSafeZyxPackagePath("ZyX.utm/Data/disk.qcow2"))
    }
}
