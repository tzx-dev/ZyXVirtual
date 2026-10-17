package app.aemu.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOtaProbeTest {
    @Test
    fun recognizesMiA3AtoPackage() {
        val result = DeviceOtaProbe.inspect(
            listOf("META-INF/com/android/metadata", "payload.bin", "payload_properties.txt"),
            "ota-type=AB\npost-sdk-level=36\npre-device=laurel_sprout\n",
        )

        requireNotNull(result)
        assertEquals("laurel_sprout", result.device)
        assertEquals(36, result.sdkLevel)
        assertEquals("AB", result.otaType)
        val message = DeviceOtaProbe.rejectionMessage(result)
        assertTrue(message.contains("laurel_sprout"))
        assertTrue(message.contains("não é uma imagem de guest virtual"))
    }

    @Test
    fun payloadWithoutMetadataIsStillRecognizedAsOta() {
        val result = DeviceOtaProbe.inspect(listOf("payload.bin"), null)

        requireNotNull(result)
        assertNull(result.device)
        assertNull(result.sdkLevel)
        assertFalse(result.otaType.equals("AB", ignoreCase = true))
    }

    @Test
    fun ordinaryGuestArchiveWithoutPayloadIsNotRejectedAsDeviceOta() {
        assertNull(
            DeviceOtaProbe.inspect(
                listOf("manifest.json", "system.img", "boot.img"),
                "pre-device=laurel_sprout\npost-sdk-level=36\n",
            ),
        )
    }
}
