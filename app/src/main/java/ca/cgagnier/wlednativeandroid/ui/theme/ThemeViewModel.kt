package ca.cgagnier.wlednativeandroid.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ThemeViewModel(preferencesRepository: UserPreferencesRepository) : ViewModel() {

    /**
     * Current theme preference, or `null` while the initial value is still loading from DataStore.
     *
     * `MainActivity` uses `null` in `SplashScreen.setKeepOnScreenCondition` (and `WLEDNativeTheme`
     * defers rendering while `null`) to avoid a light/dark flash on startup. Must remain
     * [SharingStarted.Eagerly] without `runBlocking`, as blocking the main thread on DataStore can
     * deadlock if another `Dispatchers.Main` coroutine holds DataStore's initialization lock.
     */
    val theme: StateFlow<ThemeSettings?> = preferencesRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
