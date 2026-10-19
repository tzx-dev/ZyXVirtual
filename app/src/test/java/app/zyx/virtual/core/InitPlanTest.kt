package app.zyx.virtual.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InitPlanTest {
    @Test
    fun findsLazyAidlServiceInVendorInit() {
        val root = Files.createTempDirectory("zyx-init-plan").toFile()
        try {
            File(root, "vendor/etc/init").mkdirs()
            File(root, "vendor/bin/hw/android.hardware.graphics.allocator-service.minigbm").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
            }
            File(root, "vendor/etc/init/allocator.rc").writeText(
                """
                service vendor.graphics.allocator /vendor/bin/hw/android.hardware.graphics.allocator-service.minigbm
                    class hal animation
                    interface aidl android.hardware.graphics.allocator.IAllocator/default
                    user system
                    group graphics drmrpc
                """.trimIndent()
            )

            val service = InitPlan.lazyAidl(
                "android.hardware.graphics.allocator.IAllocator/default",
                root
            )

            assertNotNull(service)
            assertEquals("vendor.graphics.allocator", service!!.name)
            assertEquals(
                "/vendor/bin/hw/android.hardware.graphics.allocator-service.minigbm",
                service.argv.first()
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
