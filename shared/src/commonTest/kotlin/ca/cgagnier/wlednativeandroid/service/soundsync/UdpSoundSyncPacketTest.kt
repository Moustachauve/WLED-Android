package ca.cgagnier.wlednativeandroid.service.soundsync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UdpSoundSyncPacketTest {

    @Test
    fun toByteArray_producesExactly44Bytes() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 128f,
            sampleSmth = 100f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16) { it.toByte() },
            fftMagnitude = 1024f,
            fftMajorPeak = 440f,
        )
        assertEquals(44, packet.toByteArray().size)
    }

    @Test
    fun toByteArray_headerIsV2MagicString() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        val bytes = packet.toByteArray()
        val header = bytes.sliceArray(0..4).toString(Charsets.US_ASCII)
        assertEquals("00002", header)
        assertEquals(0, bytes[5].toInt())
    }

    @Test
    fun toByteArray_gapBytesAreZero() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        val bytes = packet.toByteArray()
        assertEquals(0, bytes[6].toInt())
        assertEquals(0, bytes[7].toInt())
        assertEquals(0, bytes[34].toInt())
        assertEquals(0, bytes[35].toInt())
    }

    @Test
    fun toByteArray_sampleRawIsLittleEndianFloat() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 1.0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        val bytes = packet.toByteArray()
        // 1.0f in little-endian IEEE 754: 0x00, 0x00, 0x80, 0x3F
        assertEquals(0x00, bytes[8].toInt() and 0xFF)
        assertEquals(0x00, bytes[9].toInt() and 0xFF)
        assertEquals(0x80, bytes[10].toInt() and 0xFF)
        assertEquals(0x3F, bytes[11].toInt() and 0xFF)
    }

    @Test
    fun toByteArray_sampleSmthIsLittleEndianFloat() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 2.0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        val bytes = packet.toByteArray()
        // 2.0f in little-endian IEEE 754: 0x00, 0x00, 0x00, 0x40
        assertEquals(0x00, bytes[12].toInt() and 0xFF)
        assertEquals(0x00, bytes[13].toInt() and 0xFF)
        assertEquals(0x00, bytes[14].toInt() and 0xFF)
        assertEquals(0x40, bytes[15].toInt() and 0xFF)
    }

    @Test
    fun toByteArray_samplePeakIsZeroWhenNoPeak() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        assertEquals(0, packet.toByteArray()[16].toInt())
    }

    @Test
    fun toByteArray_samplePeakIsOneWhenPeakDetected() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = true,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        assertEquals(1, packet.toByteArray()[16].toInt())
    }

    @Test
    fun toByteArray_frameCounterIsWrittenAtOffset17() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 42,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        assertEquals(42, packet.toByteArray()[17].toInt())
    }

    @Test
    fun toByteArray_fftResultIs16BytesAtOffset18() {
        val fft = ByteArray(16) { (it * 16).toByte() }
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = fft,
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        val bytes = packet.toByteArray()
        val extractedFft = bytes.sliceArray(18..33)
        assertContentEquals(fft, extractedFft)
    }

    @Test
    fun toByteArray_fftMagnitudeIsLittleEndianFloatAtOffset36() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 1.0f,
            fftMajorPeak = 0f,
        )
        val bytes = packet.toByteArray()
        assertEquals(0x00, bytes[36].toInt() and 0xFF)
        assertEquals(0x00, bytes[37].toInt() and 0xFF)
        assertEquals(0x80, bytes[38].toInt() and 0xFF)
        assertEquals(0x3F, bytes[39].toInt() and 0xFF)
    }

    @Test
    fun toByteArray_fftMajorPeakIsLittleEndianFloatAtOffset40() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 440.0f,
        )
        val bytes = packet.toByteArray()
        // 440.0f in little-endian IEEE 754: 0x00, 0x00, 0xDC, 0x43
        assertEquals(0x00, bytes[40].toInt() and 0xFF)
        assertEquals(0x00, bytes[41].toInt() and 0xFF)
        assertEquals(0xDC, bytes[42].toInt() and 0xFF)
        assertEquals(0x43, bytes[43].toInt() and 0xFF)
    }

    @Test
    fun toByteArray_negativeSampleRawIsEncodedCorrectly() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = -1.0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        val bytes = packet.toByteArray()
        // -1.0f in little-endian IEEE 754: 0x00, 0x00, 0x80, 0xBF
        assertEquals(0x00, bytes[8].toInt() and 0xFF)
        assertEquals(0x00, bytes[9].toInt() and 0xFF)
        assertEquals(0x80, bytes[10].toInt() and 0xFF)
        assertEquals(0xBF, bytes[11].toInt() and 0xFF)
    }

    @Test
    fun toByteArray_frameCounterWrapsAt256() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 256,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        assertEquals(0, packet.toByteArray()[17].toInt())
    }

    @Test
    fun toByteArray_allZerosProducesValidPacket() {
        val packet = UdpSoundSyncPacket(
            sampleRaw = 0f,
            sampleSmth = 0f,
            samplePeak = false,
            frameCounter = 0,
            fftResult = ByteArray(16),
            fftMagnitude = 0f,
            fftMajorPeak = 0f,
        )
        val bytes = packet.toByteArray()
        assertEquals(44, bytes.size)
        // Header should still be "00002"
        assertEquals("00002", bytes.sliceArray(0..4).toString(Charsets.US_ASCII))
        assertEquals(0, bytes[5].toInt())
        // All other bytes should be zero
        for (i in 6 until 44) {
            assertEquals(0, bytes[i].toInt(), "Byte at offset $i should be zero")
        }
    }

    @Test
    fun defaultValues_produceSilentPacket() {
        val packet = UdpSoundSyncPacket()
        val bytes = packet.toByteArray()
        assertEquals(44, bytes.size)
        assertTrue(bytes[16].toInt() == 0, "samplePeak should be 0 by default")
    }
}
