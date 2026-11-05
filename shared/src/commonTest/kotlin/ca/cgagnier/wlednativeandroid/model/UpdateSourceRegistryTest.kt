package ca.cgagnier.wlednativeandroid.model

import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UpdateSourceRegistryTest {

    private fun createInfo(brand: String?, product: String? = null): Info = Info(
        name = "Test Device",
        leds = Leds(),
        wifi = Wifi(),
        brand = brand,
        product = product,
    )

    @Test
    fun getSource_withOfficialWledBrand_returnsOfficialSource() {
        val info = createInfo(brand = "WLED")
        val source = UpdateSourceRegistry.getSource(info)

        assertNotNull(source)
        assertEquals(UpdateSourceType.OFFICIAL_WLED, source.type)
        assertEquals("wled", source.githubOwner)
        assertEquals("WLED", source.githubRepo)
    }

    @Test
    fun getSource_withQuinLedBrand_returnsQuinLedSource() {
        val info = createInfo(brand = "QuinLED")
        val source = UpdateSourceRegistry.getSource(info)

        assertNotNull(source)
        assertEquals(UpdateSourceType.QUINLED, source.type)
        assertEquals("intermittech", source.githubOwner)
        assertEquals("QuinLED-Firmware", source.githubRepo)
    }

    @Test
    fun getSource_withMoonModulesProduct_returnsMoonModulesSource() {
        val info = createInfo(brand = "WLED", product = "MoonModules")
        val source = UpdateSourceRegistry.getSource(info)

        assertNotNull(source)
        assertEquals(UpdateSourceType.MOONMODULES, source.type)
        assertEquals("MoonModules", source.githubOwner)
        assertEquals("WLED-MM", source.githubRepo)
    }

    @Test
    fun getSource_withUnknownBrand_returnsNull() {
        val info = createInfo(brand = "UnknownBrand")
        val source = UpdateSourceRegistry.getSource(info)

        assertNull(source)
    }
}
