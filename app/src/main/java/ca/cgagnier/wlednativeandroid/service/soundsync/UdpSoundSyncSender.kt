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
 * Abstraction for sending UDP Sound Sync V2 packets, allowing tests to substitute a fake sender.
 */
interface SoundSyncPacketSender {
    fun sendPacket(packet: UdpSoundSyncPacket)
    fun updateUnicastAddresses(addresses: List<String>)
    fun close()
}

/**
 * Sends UDP Sound Sync V2 packets to WLED devices.
 *
 * Sends to the multicast group (239.0.0.1:11988 by default) so that all
 * WLED devices configured to receive on the same network get the data.
 * Also sends unicast copies to each known device address as a fallback
 * for networks where multicast is blocked.
 *
 * Thread-safe: can be called from any background thread. Hostname resolution
 * happens inside [updateUnicastAddresses] on the caller's IO coroutine so
 * [sendPacket] never blocks the real-time audio capture thread on DNS lookups.
 */
class UdpSoundSyncSender(
    private val multicastAddress: String = UdpSoundSyncPacket.DEFAULT_MULTICAST_ADDRESS,
    private val port: Int = UdpSoundSyncPacket.DEFAULT_PORT,
    unicastAddresses: List<String> = emptyList(),
    private val sendMulticast: Boolean = true,
) : SoundSyncPacketSender {
    private val closed = AtomicBoolean(false)
    private val frameCounter = AtomicInteger(0)
    private var socket: DatagramSocket? = null
    private var multicastInetAddress: InetAddress? = null

    @Volatile
    private var currentUnicastAddresses: List<InetAddress> = resolveAddresses(unicastAddresses)

    /**
     * Sends a sound sync packet to the multicast group and all unicast addresses.
     * The packet's frame counter is overridden with an auto-incrementing value.
     */
    @Synchronized
    override fun sendPacket(packet: UdpSoundSyncPacket) {
        if (closed.get()) return

        val ds = getOrCreateSocket() ?: return

        val counter = frameCounter.getAndIncrement() and FRAME_COUNTER_MASK
        val packetWithCounter = packet.copy(frameCounter = counter)
        val data = packetWithCounter.toByteArray()

        if (sendMulticast) {
            val multicastTarget = getOrResolveMulticastAddress()
            if (multicastTarget != null) {
                runCatching {
                    ds.send(DatagramPacket(data, data.size, multicastTarget, port))
                }.onFailure { logger.w(it) { "Failed to send multicast packet" } }
            }
        }

        for (address in currentUnicastAddresses) {
            runCatching {
                ds.send(DatagramPacket(data, data.size, address, port))
            }.onFailure { logger.w(it) { "Failed to send unicast packet to $address" } }
        }
    }

    /**
     * Updates and resolves the list of unicast target addresses (e.g., when the device list changes).
     * Call from an IO coroutine so hostname resolution does not block the audio capture thread.
     */
    override fun updateUnicastAddresses(addresses: List<String>) {
        if (closed.get()) return
        val resolved = resolveAddresses(addresses)
        if (!closed.get()) {
            currentUnicastAddresses = resolved
        }
    }

    @Synchronized
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { socket?.close() }
            socket = null
            multicastInetAddress = null
            currentUnicastAddresses = emptyList()
        }
    }

    private fun getOrCreateSocket(): DatagramSocket? {
        socket?.let { return it }
        return runCatching { DatagramSocket() }
            .onFailure { logger.w(it) { "Failed to create UDP socket" } }
            .getOrNull()
            ?.also { socket = it }
    }

    private fun getOrResolveMulticastAddress(): InetAddress? {
        multicastInetAddress?.let { return it }
        val resolved = runCatching { InetAddress.getByName(multicastAddress) }.getOrNull()
        multicastInetAddress = resolved
        return resolved
    }

    private fun resolveAddresses(addresses: List<String>): List<InetAddress> = addresses.mapNotNull { host ->
        runCatching { InetAddress.getByName(host) }.getOrNull()
    }
}
