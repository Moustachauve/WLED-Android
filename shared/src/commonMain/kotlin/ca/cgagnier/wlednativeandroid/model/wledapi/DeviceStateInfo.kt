package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEFAULT_MOCK_LED_COUNT = 30
private const val DEFAULT_MOCK_SIGNAL = -60

@Serializable
data class DeviceStateInfo(@SerialName("state") val state: State, @SerialName("info") val info: Info) {
    fun withVersion(newVersion: String): DeviceStateInfo = copy(info = info.copy(version = newVersion))

    fun withIsOn(isOn: Boolean): DeviceStateInfo = copy(state = state.copy(isOn = isOn))

    fun withBrightness(brightness: Int): DeviceStateInfo = copy(state = state.copy(brightness = brightness))

    fun withSignal(signal: Int): DeviceStateInfo = copy(info = info.copy(wifi = info.wifi.copy(signal = signal)))

    companion object {
        /**
         * Creates a fixture [DeviceStateInfo] instance for unit tests and UI previews.
         */
        @Suppress("LongParameterList")
        fun createMock(
            name: String = "Test Device",
            version: String = "0.14.0",
            red: Int = 255,
            green: Int = 160,
            blue: Int = 0,
            brightness: Int = 128,
            isOn: Boolean = true,
        ): DeviceStateInfo = DeviceStateInfo(
            state = State(
                isOn = isOn,
                brightness = brightness,
                segment = listOf(
                    Segment(
                        id = 0,
                        colors = listOf(listOf(red, green, blue)),
                    ),
                ),
            ),
            info = Info(
                version = version,
                name = name,
                leds = Leds(count = DEFAULT_MOCK_LED_COUNT),
                wifi = Wifi(signal = DEFAULT_MOCK_SIGNAL),
            ),
        )
    }
}
