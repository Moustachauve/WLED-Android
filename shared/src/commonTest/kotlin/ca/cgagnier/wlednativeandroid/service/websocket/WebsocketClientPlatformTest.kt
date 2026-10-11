package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.shared.getPlatformName
import kotlin.test.Test
import kotlin.test.assertEquals

class WebsocketClientPlatformTest {
    @Test
    fun userAgentMatchesPlatform() {
        assertEquals("WLED-${getPlatformName()}", WebsocketClient.USER_AGENT)
    }
}
