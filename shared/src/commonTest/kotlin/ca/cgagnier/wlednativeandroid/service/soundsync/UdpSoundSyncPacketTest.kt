package ca.cgagnier.wlednativeandroid.service.soundsync

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        assertEquals(UdpSoundSyncPacket.PACKET_SIZE, packet.toByteArray().size)
    }

    @Test
    fun toByteArray_headerIsV2MagicString() {
        val bytes = UdpSoundSyncPacket().toByteArray()
        assertEquals("00002", bytes.decodeToString(startIndex = 0, endIndex = 5))
        assertEquals(0, bytes[5].toInt())
    }

    @Test
    fun toByteArray_gapBytesAreZero() {
        val bytes = UdpSoundSyncPacket().toByteArray()
        listOf(6, 7, 34, 35).forEach { offset ->
            assertEquals(0, bytes[offset].toInt(), "Gap byte at offset $offset should be 0")
        }
    }

    @Test
    fun toByteArray_floatFieldsAreEncodedLittleEndian() {
        data class FloatCase(
            val label: String,
            val packet: UdpSoundSyncPacket,
            val offset: Int,
            val expectedBytes: List<Int>,
        )

        listOf(
            FloatCase(
                label = "sampleRaw = 1.0f",
                packet = UdpSoundSyncPacket(sampleRaw = 1.0f),
                offset = 8,
                expectedBytes = listOf(0x00, 0x00, 0x80, 0x3F),
            ),
            FloatCase(
                label = "sampleRaw = -1.0f",
                packet = UdpSoundSyncPacket(sampleRaw = -1.0f),
                offset = 8,
                expectedBytes = listOf(0x00, 0x00, 0x80, 0xBF),
            ),
            FloatCase(
                label = "sampleSmth = 2.0f",
                packet = UdpSoundSyncPacket(sampleSmth = 2.0f),
                offset = 12,
                expectedBytes = listOf(0x00, 0x00, 0x00, 0x40),
            ),
            FloatCase(
                label = "fftMagnitude = 1.0f",
                packet = UdpSoundSyncPacket(fftMagnitude = 1.0f),
                offset = 36,
                expectedBytes = listOf(0x00, 0x00, 0x80, 0x3F),
            ),
            FloatCase(
                label = "fftMajorPeak = 440.0f",
                packet = UdpSoundSyncPacket(fftMajorPeak = 440.0f),
                offset = 40,
                expectedBytes = listOf(0x00, 0x00, 0xDC, 0x43),
            ),
        ).forEach { case ->
            val bytes = case.packet.toByteArray()
            case.expectedBytes.forEachIndexed { idx, expected ->
                assertEquals(
                    expected,
                    bytes[case.offset + idx].toInt() and 0xFF,
                    "${case.label} byte $idx",
                )
            }
        }
    }

    @Test
    fun toByteArray_samplePeakEncodesBooleanAtOffset16() {
        listOf(false to 0, true to 1).forEach { (isPeak, expectedByte) ->
            val bytes = UdpSoundSyncPacket(samplePeak = isPeak).toByteArray()
            assertEquals(expectedByte, bytes[16].toInt())
        }
    }

    @Test
    fun toByteArray_frameCounterWritesLowByteAtOffset17() {
        listOf(0 to 0, 42 to 42, 255 to 255, 256 to 0).forEach { (counter, expectedByte) ->
            val bytes = UdpSoundSyncPacket(frameCounter = counter).toByteArray()
            assertEquals(expectedByte, bytes[UdpSoundSyncPacket.OFFSET_FRAME_COUNTER].toInt() and 0xFF)
        }
    }

    @Test
    fun toByteArray_fftResultIs16BytesAtOffset18() {
        val fft = ByteArray(16) { (it * 16).toByte() }
        val bytes = UdpSoundSyncPacket(fftResult = fft).toByteArray()
        assertContentEquals(fft, bytes.sliceArray(18..33))
    }

    @Test
    fun defaultValues_produceSilentPacketWithValidV2Header() {
        val bytes = UdpSoundSyncPacket().toByteArray()
        assertEquals(UdpSoundSyncPacket.PACKET_SIZE, bytes.size)
        assertEquals("00002", bytes.decodeToString(startIndex = 0, endIndex = 5))
        for (i in 5 until UdpSoundSyncPacket.PACKET_SIZE) {
            assertEquals(0, bytes[i].toInt(), "Byte at offset $i should be zero")
        }
    }

    @Test
    fun init_invalidFftResultSize_throwsIllegalArgumentException() {
        listOf(0, 15, 17).forEach { size ->
            assertFailsWith<IllegalArgumentException> {
                UdpSoundSyncPacket(fftResult = ByteArray(size))
            }
        }
    }
}
