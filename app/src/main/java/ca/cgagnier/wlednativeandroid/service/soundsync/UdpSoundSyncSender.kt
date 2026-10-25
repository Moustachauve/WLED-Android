package ca.cgagnier.wlednativeandroid.service.soundsync

import co.touchlab.kermit.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "UdpSoundSyncSender"
private const val FRAME_COUNTER_MASK = 0xFF
private val logger = Logger.withTag(TAG)

/**
 * Sends UDP Sound Sync V2 packets to WLED devices.
 *
 * Sends to the multicast group (239.0.0.1:11988 by default) so that all
 * WLED devices configured to receive on the same network get the data.
 * Also sends unicast copies to each known device address as a fallback
 * for networks where multicast is blocked.
 *
 * Thread-safe: can be called from any thread. The underlying [DatagramSocket]
 * is created lazily and reused.
 */
class UdpSoundSyncSender(
    private val multicastAddress: String = UdpSoundSyncPacket.DEFAULT_MULTICAST_ADDRESS,
    private val port: Int = UdpSoundSyncPacket.DEFAULT_PORT,
    unicastAddresses: List<String> = emptyList(),
    private val sendMulticast: Boolean = true,
) {
    private val closed = AtomicBoolean(false)
    private val frameCounter = AtomicInteger(0)
    private var socket: DatagramSocket? = null
    private var multicastInetAddress: InetAddress? = null

    @Volatile private var currentUnicastAddresses: List<InetAddress> = unicastAddresses.mapNotNull {
        runCatching { InetAddress.getByName(it) }.getOrNull()
    }

    init {
        if (sendMulticast) {
            multicastInetAddress = runCatching { InetAddress.getByName(multicastAddress) }.getOrNull()
        }
    }

    /**
     * Sends a sound sync packet to the multicast group and all unicast addresses.
     * The packet's frame counter is overridden with an auto-incrementing value.
     */
    @Synchronized
    fun sendPacket(packet: UdpSoundSyncPacket) {
        if (closed.get()) return

        val counter = frameCounter.getAndIncrement() and FRAME_COUNTER_MASK
        val packetWithCounter = packet.copy(frameCounter = counter)
        val data = packetWithCounter.toByteArray()

        val ds = getOrCreateSocket()

        // Send to multicast group
        if (sendMulticast) {
            multicastInetAddress?.let { address ->
                runCatching {
                    ds.send(DatagramPacket(data, data.size, address, port))
                }.onFailure { logger.w(it) { "Failed to send multicast packet" } }
            }
        }

        // Send unicast to each known device
        for (address in currentUnicastAddresses) {
            runCatching {
                ds.send(DatagramPacket(data, data.size, address, port))
            }.onFailure { logger.w(it) { "Failed to send unicast packet to $address" } }
        }
    }

    /** Updates the list of unicast target addresses (e.g., when the device list changes). */
    fun updateUnicastAddresses(addresses: List<String>) {
        currentUnicastAddresses = addresses.mapNotNull {
            runCatching { InetAddress.getByName(it) }.getOrNull()
        }
    }

    /** Updates whether multicast sending is enabled. */
    fun setSendMulticast(enabled: Boolean) {
        if (enabled && multicastInetAddress == null) {
            multicastInetAddress = runCatching { InetAddress.getByName(multicastAddress) }.getOrNull()
        }
    }

    @Synchronized
    fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { socket?.close() }
            socket = null
            multicastInetAddress = null
            currentUnicastAddresses = emptyList()
        }
    }

    private fun getOrCreateSocket(): DatagramSocket {
        socket?.let { return it }
        val newSocket = DatagramSocket()
        socket = newSocket
        return newSocket
    }
}
