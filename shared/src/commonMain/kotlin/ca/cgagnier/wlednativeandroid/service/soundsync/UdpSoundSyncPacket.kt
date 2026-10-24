package ca.cgagnier.wlednativeandroid.service.soundsync

/**
 * UDP Sound Sync V2 packet (44 bytes, packed, little-endian).
 *
 * Matches the `audioSyncPacket` struct from WLED audioreactive (WLED >= 0.14.0):
 * ```
 * struct __attribute__((packed)) audioSyncPacket {
 *   char    header[6];      // "00002"
 *   uint8_t gap1[2];        // padding, always 0
 *   float   sampleRaw;      // raw sample value (with AGC applied)
 *   float   sampleSmth;     // smoothed/averaged sample value
 *   uint8_t samplePeak;     // 0 = no peak, 1 = peak detected
 *   uint8_t frameCounter;   // incrementing counter (wraps at 256)
 *   uint8_t fftResult[16];  // 16 GEQ frequency channels, 0-255 each
 *   uint8_t gap2[2];        // padding, always 0
 *   float   FFT_Magnitude;  // amplitude of strongest frequency
 *   float   FFT_MajorPeak;  // strongest frequency in Hz
 * };
 * ```
 *
 * @see <a href="https://github.com/atuline/WLED/wiki/UDP-Sound-Sync">UDP Sound Sync Wiki</a>
 */
data class UdpSoundSyncPacket(
    val sampleRaw: Float = 0f,
    val sampleSmth: Float = 0f,
    val samplePeak: Boolean = false,
    val frameCounter: Int = 0,
    val fftResult: ByteArray = ByteArray(FFT_CHANNEL_COUNT),
    val fftMagnitude: Float = 0f,
    val fftMajorPeak: Float = 0f,
) {
    /**
     * Serializes this packet to a 44-byte little-endian binary array matching
     * the WLED V2 `audioSyncPacket` struct layout.
     */
    fun toByteArray(): ByteArray = ByteArray(PACKET_SIZE).apply {
        // Header: "00002\0" (6 bytes, null-terminated to match C char[6])
        // ASCII bytes for "00002" — avoids Charsets dependency for Kotlin/Native compatibility
        this[OFFSET_HEADER_0] = HEADER_BYTE_0
        this[OFFSET_HEADER_1] = HEADER_BYTE_1
        this[OFFSET_HEADER_2] = HEADER_BYTE_2
        this[OFFSET_HEADER_3] = HEADER_BYTE_3
        this[OFFSET_HEADER_4] = HEADER_BYTE_4
        this[OFFSET_HEADER_TERMINATOR] = 0

        // gap1[2] at offset 6 — already zero from ByteArray initialization

        // sampleRaw (float, little-endian) at offset 8
        writeFloatLE(sampleRaw, offset = OFFSET_SAMPLE_RAW)

        // sampleSmth (float, little-endian) at offset 12
        writeFloatLE(sampleSmth, offset = OFFSET_SAMPLE_SMTH)

        // samplePeak (uint8) at offset 16
        this[OFFSET_SAMPLE_PEAK] = if (samplePeak) BYTE_TRUE else BYTE_FALSE

        // frameCounter (uint8, wraps at 256) at offset 17
        this[OFFSET_FRAME_COUNTER] = (frameCounter and BYTE_MASK).toByte()

        // fftResult[16] at offset 18
        fftResult.copyInto(this, destinationOffset = OFFSET_FFT_RESULT)

        // gap2[2] at offset 34 — already zero

        // FFT_Magnitude (float, little-endian) at offset 36
        writeFloatLE(fftMagnitude, offset = OFFSET_FFT_MAGNITUDE)

        // FFT_MajorPeak (float, little-endian) at offset 40
        writeFloatLE(fftMajorPeak, offset = OFFSET_FFT_MAJOR_PEAK)
    }

    private fun ByteArray.writeFloatLE(value: Float, offset: Int) {
        val bits = value.toBits()
        this[offset + BYTE_OFFSET_0] = (bits and BYTE_MASK).toByte()
        this[offset + BYTE_OFFSET_1] = ((bits shr BYTE_SHIFT_1) and BYTE_MASK).toByte()
        this[offset + BYTE_OFFSET_2] = ((bits shr BYTE_SHIFT_2) and BYTE_MASK).toByte()
        this[offset + BYTE_OFFSET_3] = ((bits shr BYTE_SHIFT_3) and BYTE_MASK).toByte()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UdpSoundSyncPacket) return false
        return sampleRaw == other.sampleRaw &&
            sampleSmth == other.sampleSmth &&
            samplePeak == other.samplePeak &&
            frameCounter == other.frameCounter &&
            fftResult.contentEquals(other.fftResult) &&
            fftMagnitude == other.fftMagnitude &&
            fftMajorPeak == other.fftMajorPeak
    }

    override fun hashCode(): Int {
        var result = sampleRaw.hashCode()
        result = 31 * result + sampleSmth.hashCode()
        result = 31 * result + samplePeak.hashCode()
        result = 31 * result + frameCounter
        result = 31 * result + fftResult.contentHashCode()
        result = 31 * result + fftMagnitude.hashCode()
        result = 31 * result + fftMajorPeak.hashCode()
        return result
    }

    companion object {
        const val V2_HEADER = "00002"
        const val PACKET_SIZE = 44
        const val FFT_CHANNEL_COUNT = 16

        // Packet field offsets (matching WLED audioSyncPacket struct layout)
        private const val OFFSET_HEADER_0 = 0
        private const val OFFSET_HEADER_1 = 1
        private const val OFFSET_HEADER_2 = 2
        private const val OFFSET_HEADER_3 = 3
        private const val OFFSET_HEADER_4 = 4
        private const val OFFSET_HEADER_TERMINATOR = 5
        private const val OFFSET_SAMPLE_RAW = 8
        private const val OFFSET_SAMPLE_SMTH = 12
        private const val OFFSET_SAMPLE_PEAK = 16
        private const val OFFSET_FRAME_COUNTER = 17
        private const val OFFSET_FFT_RESULT = 18
        private const val OFFSET_FFT_MAGNITUDE = 36
        private const val OFFSET_FFT_MAJOR_PEAK = 40

        // Byte manipulation constants
        private const val BYTE_MASK = 0xFF
        private const val BYTE_TRUE = 1.toByte()
        private const val BYTE_FALSE = 0.toByte()
        private const val BYTE_OFFSET_0 = 0
        private const val BYTE_OFFSET_1 = 1
        private const val BYTE_OFFSET_2 = 2
        private const val BYTE_OFFSET_3 = 3
        private const val BYTE_SHIFT_1 = 8
        private const val BYTE_SHIFT_2 = 16
        private const val BYTE_SHIFT_3 = 24

        // ASCII bytes for "00002" header (avoids Charsets dependency for Kotlin/Native)
        private const val HEADER_BYTE_0: Byte = 48 // '0'
        private const val HEADER_BYTE_1: Byte = 48 // '0'
        private const val HEADER_BYTE_2: Byte = 48 // '0'
        private const val HEADER_BYTE_3: Byte = 48 // '0'
        private const val HEADER_BYTE_4: Byte = 50 // '2'

        /** Default UDP multicast group for WLED sound sync. */
        const val DEFAULT_MULTICAST_ADDRESS = "239.0.0.1"

        /** Default UDP port for WLED sound sync. */
        const val DEFAULT_PORT = 11988

        /** Maximum packets per second (50 fps = one packet every 20ms). */
        const val MAX_PACKETS_PER_SECOND = 50

        /** Interval between packets in milliseconds. */
        const val PACKET_INTERVAL_MS = 1000L / MAX_PACKETS_PER_SECOND
    }
}
