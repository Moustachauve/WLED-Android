package ca.cgagnier.wlednativeandroid.repository

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UserPreferencesSerializerTest {

    private val serializer = UserPreferencesSerializer()

    @Test
    fun defaultValue_hasExpectedValues() {
        val defaultPrefs = serializer.defaultValue
        assertEquals("", defaultPrefs.selectedDeviceAddress)
        assertEquals(ThemeSettings.Auto, defaultPrefs.theme)
        assertEquals(true, defaultPrefs.automaticDiscovery)
        assertEquals(1, defaultPrefs.version)
        assertEquals(true, defaultPrefs.showOfflineLast)
        assertEquals(false, defaultPrefs.sendCrashData)
        assertEquals(false, defaultPrefs.sendPerformanceData)
        assertEquals(0L, defaultPrefs.lastUpdateCheckDate)
        assertEquals(0L, defaultPrefs.dateLastWritten)
        assertEquals(false, defaultPrefs.showHiddenDevices)
        assertEquals("", defaultPrefs.lastChangelogVersionSeen)
    }

    @Test
    fun roundTrip_serializesAndDeserializesCorrectly() = runTest {
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

        val buffer = Buffer()
        serializer.writeTo(original, buffer)

        val deserialized = serializer.readFrom(buffer)
        assertEquals(original, deserialized)
    }

    @Test
    fun readFrom_emptyInput_returnsDefaultValue() = runTest {
        val buffer = Buffer()
        val deserialized = serializer.readFrom(buffer)
        assertEquals(serializer.defaultValue, deserialized)
    }

    @Test
    fun readFrom_blankInput_returnsDefaultValue() = runTest {
        val buffer = Buffer().writeUtf8("   \n  ")
        val deserialized = serializer.readFrom(buffer)
        assertEquals(serializer.defaultValue, deserialized)
    }

    @Test
    fun readFrom_corruptedInput_throwsCorruptionException() = runTest {
        val buffer = Buffer().writeUtf8("this is not valid json")
        assertFailsWith<CorruptionException> {
            serializer.readFrom(buffer)
        }
    }

    @Test
    fun readFrom_unknownKeys_ignoresThemGracefully() = runTest {
        val json = """{"theme":"Light","unknown_field":123,"future_setting":true}"""
        val buffer = Buffer().writeUtf8(json)
        val deserialized = serializer.readFrom(buffer)
        assertEquals(ThemeSettings.Light, deserialized.theme)
    }

    @Test
    fun readFrom_partialJson_usesDefaultValuesForMissingFields() = runTest {
        val json = """{"theme":"Dark"}"""
        val buffer = Buffer().writeUtf8(json)
        val deserialized = serializer.readFrom(buffer)
        assertEquals(ThemeSettings.Dark, deserialized.theme)
        assertEquals(1, deserialized.version)
        assertEquals(true, deserialized.automaticDiscovery)
    }
}
