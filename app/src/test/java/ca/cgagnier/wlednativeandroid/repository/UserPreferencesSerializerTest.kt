package ca.cgagnier.wlednativeandroid.repository

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class UserPreferencesSerializerTest {

    private val serializer = UserPreferencesSerializer()

    @Test
    fun defaultValue_hasExpectedValues() {
        val defaultPrefs = serializer.defaultValue
        assertEquals(ThemeSettings.Auto, defaultPrefs.theme)
        assertEquals(true, defaultPrefs.automaticDiscovery)
        assertEquals(true, defaultPrefs.showOfflineLast)
        assertEquals(false, defaultPrefs.showHiddenDevices)
        assertEquals(false, defaultPrefs.sendCrashData)
        assertEquals(false, defaultPrefs.sendPerformanceData)
        assertEquals(1, defaultPrefs.version)
        assertEquals("", defaultPrefs.lastChangelogVersionSeen)
    }

    @Test
    fun roundTrip_serializesAndDeserializesCorrectly() = runBlocking {
        val original = UserPreferences(
            selectedDeviceAddress = "192.168.1.50",
            hasMigratedSharedPref = true,
            theme = ThemeSettings.Dark,
            automaticDiscovery = false,
            version = 2,
            showOfflineLast = false,
            sendCrashData = true,
            sendPerformanceData = true,
            lastUpdateCheckDate = 1710000000L,
            dateLastWritten = 1710000010L,
            showHiddenDevices = true,
            lastChangelogVersionSeen = "1.5.0",
        )

        val output = ByteArrayOutputStream()
        serializer.writeTo(original, output)

        val input = ByteArrayInputStream(output.toByteArray())
        val deserialized = serializer.readFrom(input)

        assertEquals(original, deserialized)
    }

    @Test
    fun readFrom_emptyInput_returnsDefaultValue() = runBlocking {
        val input = ByteArrayInputStream(ByteArray(0))
        val deserialized = serializer.readFrom(input)

        assertEquals(serializer.defaultValue, deserialized)
    }

    @Test
    fun readFrom_blankInput_returnsDefaultValue() = runBlocking {
        val input = ByteArrayInputStream("   \n  ".toByteArray(Charsets.UTF_8))
        val deserialized = serializer.readFrom(input)

        assertEquals(serializer.defaultValue, deserialized)
    }

    @Test
    fun readFrom_corruptedInput_throwsCorruptionException() {
        val input = ByteArrayInputStream("this is not valid json".toByteArray(Charsets.UTF_8))

        assertThrows(CorruptionException::class.java) {
            runBlocking {
                serializer.readFrom(input)
            }
        }
    }

    @Test
    fun readFrom_unknownKeys_ignoresThemGracefully() = runBlocking {
        val json = """{"theme":"Light","unknown_field":123,"future_setting":true}"""
        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        val deserialized = serializer.readFrom(input)

        assertEquals(ThemeSettings.Light, deserialized.theme)
        assertEquals(true, deserialized.automaticDiscovery)
    }

    @Test
    fun readFrom_partialJson_usesDefaultValuesForMissingFields() = runBlocking {
        val json = """{"theme":"Dark"}"""
        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        val deserialized = serializer.readFrom(input)

        assertEquals(ThemeSettings.Dark, deserialized.theme)
        assertEquals(true, deserialized.automaticDiscovery)
        assertEquals(true, deserialized.showOfflineLast)
        assertEquals(false, deserialized.showHiddenDevices)
    }
}
