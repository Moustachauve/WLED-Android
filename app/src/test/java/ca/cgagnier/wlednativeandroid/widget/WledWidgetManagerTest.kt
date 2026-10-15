package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import androidx.glance.GlanceId
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.api.ApiResponse
import ca.cgagnier.wlednativeandroid.service.api.DeviceApi
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WledWidgetManagerTest {

    private lateinit var context: Context
    private lateinit var deviceApiFactory: DeviceApiFactory
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var widgetStorage: FakeWidgetStorage
    private lateinit var widgetManager: WledWidgetManager

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true) {
            every { getString(R.string.default_device_name) } returns "(New Device)"
        }
        deviceApiFactory = mockk()
        deviceRepository = mockk()
        widgetStorage = FakeWidgetStorage()
        widgetManager = WledWidgetManager(deviceApiFactory, deviceRepository, widgetStorage)
    }

    @Test
    fun `updateAllWidgets refreshes widgets concurrently so offline timeout does not block online widget`() = runTest {
        val offlineId =
            registerWidget(mac = "AA1122334455", address = "192.168.1.50", name = "Offline Strip", isOnline = true)
        val onlineId =
            registerWidget(mac = "BB1122334455", address = "192.168.1.100", name = "Desktop", isOnline = false)

        val offlineApi = mockk<DeviceApi> {
            coEvery { postJson(any()) } coAnswers {
                delay(5_000L)
                throw java.io.IOException("Connect timeout")
            }
        }
        val onlineApi = mockk<DeviceApi> {
            coEvery { postJson(any()) } returns ApiResponse(code = 200, body = onlineState(isOn = true))
        }

        every {
            deviceApiFactory.create("192.168.1.50", WledWidgetManager.WIDGET_REQUEST_TIMEOUT_SECONDS)
        } returns offlineApi
        every {
            deviceApiFactory.create("192.168.1.100", WledWidgetManager.WIDGET_REQUEST_TIMEOUT_SECONDS)
        } returns onlineApi

        val job = launch { widgetManager.updateAllWidgets(context) }

        // Run immediate tasks before the 5s timeout elapses
        runCurrent()

        // Online widget should already be updated and online while offline widget is still waiting
        val onlineSaved = widgetStorage.savedUpdates[onlineId]
        assertNotNull(onlineSaved)
        assertTrue(onlineSaved!!.isOnline)
        assertTrue(onlineSaved.isOn)
        assertNull(widgetStorage.savedUpdates[offlineId])

        // Advance virtual time past the 5s timeout so the offline widget completes
        advanceTimeBy(5_000L)
        runCurrent()
        job.join()

        val offlineSaved = widgetStorage.savedUpdates[offlineId]
        assertNotNull(offlineSaved)
        assertFalse(offlineSaved!!.isOnline)
    }

    @Test
    fun `refreshWidget creates DeviceApi with 5s timeout and marks widget offline on non-2xx response`() = runTest {
        val glanceId =
            registerWidget(mac = "AA1122334455", address = "192.168.1.10", name = "Lamp", isOn = true, isOnline = true)

        val api = mockk<DeviceApi> {
            coEvery { postJson(any()) } returns ApiResponse(code = 503, errorBody = "Unavailable")
        }
        every {
            deviceApiFactory.create("192.168.1.10", WledWidgetManager.WIDGET_REQUEST_TIMEOUT_SECONDS)
        } returns api

        widgetManager.refreshWidget(context, glanceId)

        val saved = widgetStorage.savedUpdates[glanceId]
        assertNotNull(saved)
        assertFalse(saved!!.isOnline)
        assertEquals(true, saved.isOn)
        coVerify(exactly = 1) { api.postJson(any()) }
    }

    private fun registerWidget(
        mac: String,
        address: String,
        name: String,
        isOn: Boolean = false,
        isOnline: Boolean = true,
    ): GlanceId {
        val glanceId = mockk<GlanceId>()
        widgetStorage.states[glanceId] = WidgetStateData(
            macAddress = mac,
            address = address,
            name = name,
            isOn = isOn,
            isOnline = isOnline,
        )
        coEvery { deviceRepository.findDeviceByMacAddress(mac) } returns Device(
            macAddress = mac,
            address = address,
            originalName = name,
        )
        return glanceId
    }

    private fun onlineState(isOn: Boolean) = State(
        isOn = isOn,
        brightness = 255,
        segment = listOf(Segment(colors = listOf(listOf(0, 255, 0)))),
    )

    private class FakeWidgetStorage : WidgetStorage {
        val states = LinkedHashMap<GlanceId, WidgetStateData>()
        val savedUpdates = LinkedHashMap<GlanceId, WidgetStateData>()
        val clearedIds = mutableListOf<GlanceId>()

        override suspend fun getGlanceIds(context: Context): List<GlanceId> = states.keys.toList()

        override suspend fun getWidgetState(context: Context, glanceId: GlanceId): WidgetStateData? = states[glanceId]

        override suspend fun saveStateAndPush(context: Context, glanceId: GlanceId, data: WidgetStateData) {
            states[glanceId] = data
            savedUpdates[glanceId] = data
        }

        override suspend fun clearStateAndPush(context: Context, glanceId: GlanceId) {
            states.remove(glanceId)
            clearedIds.add(glanceId)
        }
    }
}
