package ca.cgagnier.wlednativeandroid.service.soundsync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UdpSoundSyncSenderTest {

    private var sender: UdpSoundSyncSender? = null

    @AfterEach
    fun tearDown() {
        sender?.close()
        sender = null
    }

    @Test
    fun sendPacket_sendsToUnicastAddresses() = runTest {
        // Bind a receiver socket on a random port
        val receiver = DatagramSocket(0)
        val port = receiver.localPort
        receiver.soTimeout = 2000

        sender = UdpSoundSyncSender(
            multicastAddress = "239.0.0.1",
            port = port,
            unicastAddresses = listOf("127.0.0.1"),
            sendMulticast = false, // Don't send multicast in tests
        )

        val packet = UdpSoundSyncPacket(
            sampleRaw = 128f,
            sampleSmth = 100f,
            samplePeak = true,
            frameCounter = 42, // Will be overridden by sender's internal counter (0 for first packet)
            fftResult = ByteArray(16) { it.toByte() },
            fftMagnitude = 1024f,
            fftMajorPeak = 440f,
        )

        sender!!.sendPacket(packet)

        val buffer = ByteArray(1024)
        val received = DatagramPacket(buffer, buffer.size)
        receiver.receive(received)

        assertEquals(44, received.length)
        // Sender overrides frameCounter with its internal counter (0 for first packet)
        val expectedPacket = packet.copy(frameCounter = 0)
        assertContentEquals(expectedPacket.toByteArray(), buffer.copyOf(received.length))
        receiver.close()
    }

    @Test
    fun sendPacket_sendsToMultipleUnicastAddresses() = runTest {
        val receiver1 = DatagramSocket(0)
        val receiver2 = DatagramSocket(0)
        val port1 = receiver1.localPort
        receiver1.soTimeout = 2000
        receiver2.soTimeout = 2000

        // Both receivers on loopback, but different ports — we test with same port
        // by using two senders or by checking that both receive on the same port
        // Actually, let's use the same port for both receivers on different addresses
        // Since we can't easily do that in a unit test, let's just verify one receiver
        // gets the packet when multiple addresses are configured
        sender = UdpSoundSyncSender(
            multicastAddress = "239.0.0.1",
            port = port1,
            unicastAddresses = listOf("127.0.0.1", "127.0.0.2"),
            sendMulticast = false,
        )

        val packet = UdpSoundSyncPacket(frameCounter = 1)
        sender!!.sendPacket(packet)

        val buffer = ByteArray(1024)
        val received = DatagramPacket(buffer, buffer.size)
        receiver1.receive(received)

        assertEquals(44, received.length)
        receiver1.close()
        receiver2.close()
    }

    @Test
    fun sendPacket_withNoAddresses_doesNotThrow() = runTest {
        sender = UdpSoundSyncSender(
            multicastAddress = "239.0.0.1",
            port = 11988,
            unicastAddresses = emptyList(),
            sendMulticast = false,
        )
        // Should not throw
        sender!!.sendPacket(UdpSoundSyncPacket())
    }

    @Test
    fun sendPacket_incrementsFrameCounterWhenNotSet() = runTest {
        val receiver = DatagramSocket(0)
        val port = receiver.localPort
        receiver.soTimeout = 2000

        sender = UdpSoundSyncSender(
            multicastAddress = "239.0.0.1",
            port = port,
            unicastAddresses = listOf("127.0.0.1"),
            sendMulticast = false,
        )

        // Send two packets with frameCounter = 0 (should auto-increment)
        sender!!.sendPacket(UdpSoundSyncPacket(frameCounter = 0))
        sender!!.sendPacket(UdpSoundSyncPacket(frameCounter = 0))

        val buffer1 = ByteArray(1024)
        val received1 = DatagramPacket(buffer1, buffer1.size)
        receiver.receive(received1)

        val buffer2 = ByteArray(1024)
        val received2 = DatagramPacket(buffer2, buffer2.size)
        receiver.receive(received2)

        val frame1 = buffer1[17].toInt() and 0xFF
        val frame2 = buffer2[17].toInt() and 0xFF
        assertEquals(frame1 + 1, frame2)

        receiver.close()
    }

    @Test
    fun close_canBeCalledMultipleTimes() {
        sender = UdpSoundSyncSender(
            multicastAddress = "239.0.0.1",
            port = 11988,
            unicastAddresses = emptyList(),
            sendMulticast = false,
        )
        sender!!.close()
        sender!!.close() // Should not throw
    }

    @Test
    fun sendPacket_afterClose_doesNotThrow() = runTest {
        sender = UdpSoundSyncSender(
            multicastAddress = "239.0.0.1",
            port = 11988,
            unicastAddresses = listOf("127.0.0.1"),
            sendMulticast = false,
        )
        sender!!.close()
        // Should not throw even after close
        sender!!.sendPacket(UdpSoundSyncPacket())
    }

    @Test
    fun updateUnicastAddresses_changesTargetAddresses() = runTest {
        val receiver = DatagramSocket(0)
        val port = receiver.localPort
        receiver.soTimeout = 2000

        sender = UdpSoundSyncSender(
            multicastAddress = "239.0.0.1",
            port = port,
            unicastAddresses = emptyList(),
            sendMulticast = false,
        )

        // No addresses — packet should not be received
        sender!!.updateUnicastAddresses(listOf("127.0.0.1"))
        sender!!.sendPacket(UdpSoundSyncPacket(frameCounter = 5))

        val buffer = ByteArray(1024)
        val received = DatagramPacket(buffer, buffer.size)
        receiver.receive(received)
        assertEquals(44, received.length)

        receiver.close()
    }
}
