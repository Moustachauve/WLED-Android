package ca.cgagnier.wlednativeandroid.service.soundsync

import android.content.Context
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.AudioSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SoundSyncManagerTest {

    @Test
    fun start_whenPermissionDenied_setsPermissionErrorAndDoesNotStart() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = false)
        val manager = createManager(audioSource = audioSource)

        val started = manager.start(gain = 2.0f, agcEnabled = true)

        assertFalse(started)
        assertFalse(manager.state.value.isRunning)
        assertEquals(SoundSyncError.PERMISSION_REQUIRED, manager.state.value.error)
    }

    @Test
    fun start_whenAudioSourceFailsToStart_setsCaptureErrorAndKeepsRunningFalse() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = false)
        val manager = createManager(audioSource = audioSource)

        val started = manager.start(gain = 1.5f, agcEnabled = false)

        assertFalse(started)
        assertFalse(manager.state.value.isRunning)
        assertEquals(SoundSyncError.CAPTURE_FAILED, manager.state.value.error)
    }

    @Test
    fun start_whenAudioSourceStarts_setsRunningWithoutError() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val manager = createManager(audioSource = audioSource)

        val started = manager.start(gain = 3.0f, agcEnabled = false)

        assertTrue(started)
        assertTrue(manager.state.value.isRunning)
        assertNull(manager.state.value.error)
    }

    @Test
    fun processAudioSamples_whenRunning_updatesSampleLevelAndSendsPacket() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val sender = FakeSoundSyncPacketSender()
        val manager = createManager(audioSource = audioSource, sender = sender)
        manager.start(gain = 3.0f, agcEnabled = false)

        val sineSamples = FloatArray(512) { i -> sin(2.0 * PI * 440.0 * i / audioSource.sampleRate).toFloat() }
        audioSource.emitSamples(sineSamples)

        assertTrue(manager.latestPacket.value.sampleSmth > 0f)
        assertEquals(1, sender.sentPackets.size)
    }

    @Test
    fun processAudioSamples_whenAgcEnabled_updatesEffectiveGainAndPreservesManualGain() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val manager = createManager(audioSource = audioSource)
        manager.start(gain = 1.5f, agcEnabled = true)

        val quietSineSamples = FloatArray(4096) { i ->
            0.02f * sin(2.0 * PI * 440.0 * i / audioSource.sampleRate).toFloat()
        }
        repeat(3) {
            audioSource.emitSamples(quietSineSamples)
        }

        assertEquals(1.5f, manager.state.value.gain, 0.001f)
        assertTrue(manager.state.value.effectiveGain > 2.0f)
    }

    @Test
    fun stop_resetsRunningAndVisualizationState() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val sender = FakeSoundSyncPacketSender()
        val manager = createManager(audioSource = audioSource, sender = sender)
        manager.start(gain = 2.0f, agcEnabled = false)
        val sineSamples = FloatArray(512) { i -> sin(2.0 * PI * 440.0 * i / audioSource.sampleRate).toFloat() }
        audioSource.emitSamples(sineSamples)

        manager.stop()

        assertFalse(manager.state.value.isRunning)
        assertFalse(audioSource.isRunning.value)
        assertTrue(sender.isClosed)
        assertEquals(0f, manager.latestPacket.value.sampleSmth)
        assertFalse(manager.latestPacket.value.samplePeak)
        assertTrue(manager.latestPacket.value.fftResult.all { it == 0.toByte() })
    }

    @Test
    fun audioSourceStoppingUnexpectedly_stopsManagerAndSetsCaptureError() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val manager = createManager(audioSource = audioSource)
        manager.start(gain = 1.0f, agcEnabled = false)
        advanceUntilIdle()

        audioSource.simulateReadFailure()
        advanceUntilIdle()

        assertFalse(manager.state.value.isRunning)
        assertEquals(SoundSyncError.CAPTURE_FAILED, manager.state.value.error)
    }

    private fun TestScope.createManager(
        audioSource: AudioSource,
        sender: FakeSoundSyncPacketSender = FakeSoundSyncPacketSender(),
    ): SoundSyncManager {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val context = mockk<Context>(relaxed = true)
        val deviceRepository = mockk<DeviceRepository> {
            every { allDevices } returns flowOf(emptyList<Device>())
        }
        val manager = SoundSyncManager(
            context = context,
            deviceRepository = deviceRepository,
            coroutineScope = scope,
            ioDispatcher = dispatcher,
            audioSource = audioSource,
            senderFactory = { sender },
        )
        coroutineContext.job.invokeOnCompletion {
            manager.destroy()
            scope.cancel()
        }
        return manager
    }

    private class FakeSoundSyncPacketSender : SoundSyncPacketSender {
        val sentPackets = mutableListOf<UdpSoundSyncPacket>()
        var unicastAddresses: List<String> = emptyList()
            private set
        var isClosed: Boolean = false
            private set

        override fun sendPacket(packet: UdpSoundSyncPacket) {
            sentPackets.add(packet)
        }

        override fun updateUnicastAddresses(addresses: List<String>) {
            unicastAddresses = addresses
        }

        override fun close() {
            isClosed = true
        }
    }

    private class FakeAudioSource(
        private val permissionGranted: Boolean,
        private val startResult: Boolean = true,
        override val sampleRate: Int = 44100,
    ) : AudioSource {
        private val _isRunning = MutableStateFlow(false)
        override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        private var callback: ((FloatArray) -> Unit)? = null

        override fun hasPermission(): Boolean = permissionGranted

        override fun start(onAudioData: (FloatArray) -> Unit): Boolean {
            if (!startResult) return false
            callback = onAudioData
            _isRunning.value = true
            return true
        }

        override fun stop() {
            _isRunning.value = false
            callback = null
        }

        override fun destroy() {
            stop()
        }

        fun emitSamples(samples: FloatArray) {
            callback?.invoke(samples)
        }

        fun simulateReadFailure() {
            _isRunning.value = false
        }
    }
}
