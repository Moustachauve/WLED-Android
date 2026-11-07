package ca.cgagnier.wlednativeandroid.widget

import androidx.work.Data
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class WidgetActionRequestTest {

    private val requestedAt = 1_700_000_000_000L

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = [true, false])
    fun `input data round trips the request`(targetIsOn: Boolean?) {
        val request = WidgetActionRequest(appWidgetId = 42, targetIsOn = targetIsOn, requestedAtMillis = requestedAt)

        assertEquals(request, WidgetActionRequest.fromInputData(request.toInputData()))
    }

    @Test
    fun `fromInputData returns null without an app widget id`() {
        assertNull(WidgetActionRequest.fromInputData(Data.EMPTY))
    }

    @Test
    fun `powerStateToSend returns the target while the tap is recent`() {
        val request = WidgetActionRequest(appWidgetId = 42, targetIsOn = true, requestedAtMillis = requestedAt)

        assertEquals(true, request.powerStateToSend(requestedAt + WidgetActionRequest.MAX_TOGGLE_DELAY_MILLIS))
    }

    @Test
    fun `powerStateToSend drops a toggle that ran too long after the tap`() {
        val request = WidgetActionRequest(appWidgetId = 42, targetIsOn = true, requestedAtMillis = requestedAt)

        assertNull(request.powerStateToSend(requestedAt + WidgetActionRequest.MAX_TOGGLE_DELAY_MILLIS + 1))
    }

    @Test
    fun `powerStateToSend returns null for a refresh`() {
        val request = WidgetActionRequest(appWidgetId = 42, targetIsOn = null, requestedAtMillis = requestedAt)

        assertNull(request.powerStateToSend(requestedAt))
    }

    @Test
    fun `power and refresh requests for the same widget use separate unique work`() {
        val power = WidgetActionRequest(appWidgetId = 42, targetIsOn = true)
        val refresh = WidgetActionRequest(appWidgetId = 42, targetIsOn = null)

        assertNotEquals(power.uniqueWorkName, refresh.uniqueWorkName)
    }
}
