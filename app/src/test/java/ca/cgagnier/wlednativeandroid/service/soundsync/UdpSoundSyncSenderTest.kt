package ca.cgagnier.wlednativeandroid.service.soundsync

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UdpSoundSyncSenderTest {

    private var sender: UdpSoundSyncSender? = null

    @AfterEach
    fun tearDown() {
        sender?.close()
        sender = null
    }

    @Test
    fun sendPacket_sendsToUnicastAddresses() {
        withReceiver(unicastAddresses = listOf("127.0.0.1")) { receiver, udpSender ->
            val packet = UdpSoundSyncPacket(
                sampleRaw = 128f,
                sampleSmth = 100f,
                samplePeak = true,
                frameCounter = 42,
                fftResult = ByteArray(16) { it.toByte() },
                fftMagnitude = 1024f,
                fftMajorPeak = 440f,
            )

            udpSender.sendPacket(packet)

            val received = receiveOne(receiver)
            val expectedPacket = packet.copy(frameCounter = 0)
            assertContentEquals(expectedPacket.toByteArray(), received)
        }
    }

    @Test
    fun sendPacket_sendsOneDatagramPerUnicastEntry() {
        withReceiver(unicastAddresses = listOf("127.0.0.1", "localhost")) { receiver, udpSender ->
            udpSender.sendPacket(UdpSoundSyncPacket(sampleRaw = 64f))

            repeat(2) {
                val received = receiveOne(receiver)
                assertEquals(UdpSoundSyncPacket.PACKET_SIZE, received.size)
            }
        }
    }

    @Test
    fun sendPacket_incrementsFrameCounterAcrossPackets() {
        withReceiver(unicastAddresses = listOf("127.0.0.1")) { receiver, udpSender ->
            udpSender.sendPacket(UdpSoundSyncPacket(frameCounter = 0))
            udpSender.sendPacket(UdpSoundSyncPacket(frameCounter = 0))

            val buffer1 = receiveOne(receiver)
            val buffer2 = receiveOne(receiver)

            val frame1 = buffer1[UdpSoundSyncPacket.OFFSET_FRAME_COUNTER].toInt() and 0xFF
            val frame2 = buffer2[UdpSoundSyncPacket.OFFSET_FRAME_COUNTER].toInt() and 0xFF
            assertEquals(0, frame1)
            assertEquals(1, frame2)
        }
    }

    @Test
    fun sendPacket_afterClose_doesNotSendDatagram() {
        withReceiver(
            unicastAddresses = listOf("127.0.0.1"),
            timeoutMs = 150,
        ) { receiver, udpSender ->
            udpSender.close()
            udpSender.sendPacket(UdpSoundSyncPacket())

            assertFailsWith<SocketTimeoutException> {
                receiveOne(receiver)
            }
        }
    }

    @Test
    fun updateUnicastAddresses_changesTargetAddresses() {
        withReceiver(unicastAddresses = emptyList()) { receiver, udpSender ->
            udpSender.updateUnicastAddresses(listOf("127.0.0.1"))
            udpSender.sendPacket(UdpSoundSyncPacket(frameCounter = 5))

            val received = receiveOne(receiver)
            assertEquals(UdpSoundSyncPacket.PACKET_SIZE, received.size)
        }
    }

    private fun withReceiver(
        unicastAddresses: List<String>,
        timeoutMs: Int = 2000,
        block: (DatagramSocket, UdpSoundSyncSender) -> Unit,
    ) {
        DatagramSocket(0).use { receiver ->
            receiver.soTimeout = timeoutMs
            val udpSender = UdpSoundSyncSender(
                multicastAddress = "239.0.0.1",
                port = receiver.localPort,
                unicastAddresses = unicastAddresses,
                sendMulticast = false,
            )
            sender = udpSender
            block(receiver, udpSender)
        }
    }

    private fun receiveOne(receiver: DatagramSocket): ByteArray {
        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)
        receiver.receive(packet)
        return buffer.copyOf(packet.length)
    }
}
