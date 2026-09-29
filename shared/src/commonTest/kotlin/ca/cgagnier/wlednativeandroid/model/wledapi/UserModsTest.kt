package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserModsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun userMods_batteryDeserializationWithMixedArray() {
        val rawJson = """
            {
                "Battery level": [85, "%"],
                "Battery voltage": [3.95, "V"]
            }
        """.trimIndent()

        val userMods = json.decodeFromString<UserMods>(rawJson)
        assertEquals(85.0, userMods.batteryPercentage)
        assertEquals(3.95, userMods.batteryVoltageValue)
    }

    @Test
    fun userMods_batteryDeserializationWithSingleNumericValue() {
        val rawJson = """
            {
                "Battery level": [72.5]
            }
        """.trimIndent()

        val userMods = json.decodeFromString<UserMods>(rawJson)
        assertEquals(72.5, userMods.batteryPercentage)
        assertNull(userMods.batteryVoltageValue)
    }

    @Test
    fun userMods_nullValues() {
        val userMods = UserMods()
        assertNull(userMods.batteryLevel)
        assertNull(userMods.batteryVoltage)
        assertNull(userMods.batteryPercentage)
        assertNull(userMods.batteryVoltageValue)
    }

    @Test
    fun userMods_manualConstructionWithJsonPrimitive() {
        val userMods = UserMods(
            batteryLevel = listOf(JsonPrimitive(90)),
            batteryVoltage = listOf(JsonPrimitive(4.1)),
        )
        assertEquals(90.0, userMods.batteryPercentage)
        assertEquals(4.1, userMods.batteryVoltageValue)
    }
}
