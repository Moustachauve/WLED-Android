package ca.cgagnier.wlednativeandroid.service.audio

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Constructs and sends WLED AudioReactive V2 UDP sync packets.
 *
 * Packet structure (44 bytes, little-endian, packed):
 *   offset 0:  char[6]    header = "00002\0"
 *   offset 6:  uint8[2]   reserved
 *   offset 8:  float      sampleRaw
 *   offset 12: float      sampleSmth
 *   offset 16: uint8      samplePeak
 *   offset 17: uint8      reserved
 *   offset 18: uint8[16]  fftResult (16 GEQ bands, 0-254)
 *   offset 34: uint16     reserved
 *   offset 36: float      FFT_Magnitude
 *   offset 40: float      FFT_MajorPeak (Hz)
 */
class UdpSyncSender {

    private var socket: DatagramSocket? = null

    fun open() {
        try {
            socket = DatagramSocket()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open UDP socket", e)
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing UDP socket", e)
        }
        socket = null
    }

    fun send(audioData: AudioData, targetAddress: String, port: Int = DEFAULT_PORT) {
        val sock = socket ?: return

        val buffer = ByteBuffer.allocate(PACKET_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        // Header: "00002" null-terminated (6 bytes)
        buffer.put(HEADER_BYTES)

        // Reserved (2 bytes)
        buffer.put(0)
        buffer.put(0)

        // sampleRaw (float, 4 bytes)
        buffer.putFloat(audioData.sampleRaw)

        // sampleSmth (float, 4 bytes)
        buffer.putFloat(audioData.sampleSmth)

        // samplePeak (uint8, 1 byte)
        buffer.put(audioData.samplePeak.toByte())

        // Reserved (1 byte)
        buffer.put(0)

        // fftResult (16 x uint8)
        for (i in 0 until 16) {
            buffer.put(audioData.fftResult[i].coerceIn(0, 254).toByte())
        }

        // Reserved (uint16, 2 bytes)
        buffer.putShort(0)

        // FFT_Magnitude (float, 4 bytes)
        buffer.putFloat(audioData.fftMagnitude)

        // FFT_MajorPeak (float, 4 bytes)
        buffer.putFloat(audioData.fftMajorPeak)

        try {
            val address = InetAddress.getByName(targetAddress)
            val packet = DatagramPacket(buffer.array(), PACKET_SIZE, address, port)
            sock.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send UDP packet to $targetAddress:$port", e)
        }
    }

    companion object {
        private const val TAG = "UdpSyncSender"
        const val DEFAULT_PORT = 11988
        const val DEFAULT_MULTICAST_ADDRESS = "239.0.0.1"
        private const val PACKET_SIZE = 44
        private val HEADER_BYTES = byteArrayOf(
            '0'.code.toByte(),
            '0'.code.toByte(),
            '0'.code.toByte(),
            '0'.code.toByte(),
            '2'.code.toByte(),
            0, // null terminator
        )
    }
}
