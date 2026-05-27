package ca.cgagnier.wlednativeandroid.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@AndroidEntryPoint
class PresetWidgetConfigureActivity : ComponentActivity() {

    private val viewModel: PresetWidgetConfigureViewModel by viewModels()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            WLEDNativeTheme {
                DeviceSelectionScreen(viewModel) { device ->
                    saveDevicePref(this, appWidgetId, device)

                    val appWidgetManager = AppWidgetManager.getInstance(this)
                    val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    val providerClass = info?.provider?.className
                    if (providerClass == SmallPresetWidgetProvider::class.java.name) {
                        PresetWidgetProvider.updateAppWidget(
                            this,
                            appWidgetManager,
                            appWidgetId,
                            limit = 3,
                            layoutId = R.layout.widget_preset_horizontal,
                            listId = R.id.preset_grid,
                            itemLayoutId = R.layout.widget_preset_button_item,
                            titleViewId = null,
                        )
                    } else {
                        PresetWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
                    }

                    val resultValue = Intent()
                    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    setResult(RESULT_OK, resultValue)
                    finish()
                }
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "ca.cgagnier.wlednativeandroid.widget.PresetWidget"
        private const val PREF_PREFIX_KEY = "appwidget_"

        internal fun saveDevicePref(context: Context, appWidgetId: Int, device: Device) {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0).edit()
            prefs.putString(PREF_PREFIX_KEY + appWidgetId, device.address)
            prefs.putString(PREF_PREFIX_KEY + appWidgetId + "_name", device.name)
            prefs.apply()
        }

        internal fun loadDeviceAddress(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        }

        internal fun loadDeviceName(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, 0)
            return prefs.getString(PREF_PREFIX_KEY + appWidgetId + "_name", "WLED Device") ?: "WLED Device"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelectionScreen(viewModel: PresetWidgetConfigureViewModel, onDeviceSelected: (Device) -> Unit) {
    val devices by viewModel.allDevices.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Select WLED Device") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(devices) { device ->
                DeviceItem(device, onDeviceSelected)
            }
        }
    }
}

@Composable
private fun DeviceItem(device: Device, onClick: (Device) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick(device) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = device.name, style = MaterialTheme.typography.titleMedium)
            Text(text = device.address, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@HiltViewModel
class PresetWidgetConfigureViewModel @Inject constructor(private val deviceRepository: DeviceRepository) : ViewModel() {
    val allDevices: Flow<List<Device>> = deviceRepository.allDevices
}

val Device.name: String
    get() = if (customName.isNotEmpty()) {
        customName
    } else if (originalName.isNotEmpty()) {
        originalName
    } else {
        address
    }
