package ca.cgagnier.wlednativeandroid.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavHostTest {

    @Test
    fun `initial back stack contains DeviceListDetailKey as root destination`() {
        val backStack = NavBackStack<NavKey>(DeviceListDetailKey)

        assertEquals(1, backStack.size)
        assertEquals(DeviceListDetailKey, backStack.last())
    }

    @Test
    fun `navigating to settings pushes SettingsKey on top of back stack`() {
        val backStack = NavBackStack<NavKey>(DeviceListDetailKey)

        backStack.add(SettingsKey)

        assertEquals(2, backStack.size)
        assertEquals(SettingsKey, backStack.last())
        assertEquals(DeviceListDetailKey, backStack.first())
    }

    @Test
    fun `navigating back from settings restores DeviceListDetailKey`() {
        val backStack = NavBackStack<NavKey>(DeviceListDetailKey)
        backStack.add(SettingsKey)

        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }

        assertEquals(1, backStack.size)
        assertEquals(DeviceListDetailKey, backStack.last())
    }

    @Test
    fun `back navigation on root destination does not remove last destination`() {
        val backStack = NavBackStack<NavKey>(DeviceListDetailKey)

        // Simulating the onBack guard condition from MainNavHost:
        // if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }

        assertEquals(1, backStack.size)
        assertEquals(DeviceListDetailKey, backStack.last())
    }

    @Test
    fun `navigation keys are distinct and identify destinations correctly`() {
        val detailKey: NavKey = DeviceListDetailKey
        val settingsKey: NavKey = SettingsKey

        assertTrue(detailKey is DeviceListDetailKey)
        assertTrue(settingsKey is SettingsKey)
        assertEquals("DeviceListDetailKey", detailKey.toString())
        assertEquals("SettingsKey", settingsKey.toString())
    }
}
