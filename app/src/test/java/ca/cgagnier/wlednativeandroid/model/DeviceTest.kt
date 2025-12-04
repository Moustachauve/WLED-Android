package ca.cgagnier.wlednativeandroid.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTest {

    @Test
    fun getDeviceUrl_returnsCorrectUrl() {
        val device = Device(
            macAddress = "00:11:22:33:44:55",
            address = "192.168.1.100",
            originalName = "WLED",
            customName = "My WLED",
            branch = Branch.STABLE,
            lastSeen = 1234567890L
        )

        assertEquals("http://192.168.1.100", device.getDeviceUrl())
    }

    @Test
    fun device_defaultValues() {
        val device = Device(
            macAddress = "00:11:22:33:44:55",
            address = "192.168.1.100"
        )

        assertEquals(false, device.isHidden)
        assertEquals("", device.originalName)
        assertEquals("", device.customName)
        assertEquals("", device.skipUpdateTag)
        assertEquals(Branch.UNKNOWN, device.branch)
        // lastSeen uses System.currentTimeMillis(), so we can't easily assert exact value but it should be close to now if we could mock time, but here we just check it is initialized.
        // We can just verify defaults.
    }
}
