package ca.cgagnier.wlednativeandroid.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(private val preferencesRepository: UserPreferencesRepository) :
    ViewModel() {

    val theme = preferencesRepository.themeMode.map { it }
        .stateIn(
            scope = viewModelScope,
            started = WhileSubscribed(5000),
            initialValue = runBlocking{preferencesRepository.themeMode.first()}
        )
}