package ca.cgagnier.wlednativeandroid.service.websocket

import android.content.Context
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import com.squareup.moshi.Moshi
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WebsocketClientFactoryTest {

    private lateinit var context: Context
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var moshi: Moshi
    private lateinit var factory: WebsocketClientFactory

    // Using mockk for mocking dependencies (relaxed = true ignores unstubbed calls)
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val widgetManager: WledWidgetManager = mockk(relaxed = true)
    private val deviceUpdateManager: DeviceUpdateManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        okHttpClient = OkHttpClient.Builder().build()
        moshi = Moshi.Builder().build()

        factory = WebsocketClientFactory(
            applicationContext = context,
            deviceRepository = deviceRepository,
            widgetManager = widgetManager,
            deviceUpdateManager = deviceUpdateManager,
            okHttpClient = okHttpClient,
            moshi = moshi,
        )
    }

    @Test
    fun `create returns WebsocketClient with correct device`() {
        val device = createTestDevice("AABBCCDDEEFF", "192.168.1.100", "Test Device")

        val client = factory.create(device)

        assertNotNull(client)
        assertEquals(device, client.deviceState.device)
        assertEquals("AABBCCDDEEFF", client.deviceState.device.macAddress)
        assertEquals("192.168.1.100", client.deviceState.device.address)
    }

    @Test
    fun `create returns different instances for different devices`() {
        val device1 = createTestDevice("AABBCCDDEEFF", "192.168.1.100", "Device 1")
        val device2 = createTestDevice("112233445566", "192.168.1.101", "Device 2")

        val client1 = factory.create(device1)
        val client2 = factory.create(device2)

        assertNotSame(client1, client2)
        assertEquals("AABBCCDDEEFF", client1.deviceState.device.macAddress)
        assertEquals("112233445566", client2.deviceState.device.macAddress)
    }

    @Test
    fun `create preserves device properties`() {
        val device = createTestDevice(
            macAddress = "FFEEDDCCBBAA",
            address = "10.0.0.50",
            originalName = "Kitchen Lights",
        )

        val client = factory.create(device)

        assertEquals("FFEEDDCCBBAA", client.deviceState.device.macAddress)
        assertEquals("10.0.0.50", client.deviceState.device.address)
        assertEquals("Kitchen Lights", client.deviceState.device.originalName)
    }

    @Test
    fun `create returns client with initial disconnected status`() {
        val device = createTestDevice("AABBCCDDEEFF", "192.168.1.100", "Test")

        val client = factory.create(device)

        assertEquals(WebsocketStatus.DISCONNECTED, client.deviceState.websocketStatus.value)
    }

    private fun createTestDevice(macAddress: String, address: String, originalName: String): Device = Device(
        macAddress = macAddress,
        address = address,
        originalName = originalName,
    )
}
