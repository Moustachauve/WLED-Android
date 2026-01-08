package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class WledWidgetConfigureActivity : ComponentActivity() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var widgetManager: WledWidgetManager

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            val devices by deviceRepository.allDevices.collectAsState(initial = emptyList())

            ConfigurationScreen(
                devices = devices,
                onDeviceSelected = { device ->
                    confirmConfiguration(device)
                },
            )
        }
    }

    private fun confirmConfiguration(device: Device) {
        lifecycleScope.launch(Dispatchers.IO) {
            val glanceId = GlanceAppWidgetManager(this@WledWidgetConfigureActivity)
                .getGlanceIdBy(appWidgetId)

            // Create initial state (Assume OFF or Unknown, do not block UI for network)
            val widgetData = WidgetStateData(
                macAddress = device.macAddress,
                address = device.address,
                name = device.customName.ifBlank { device.originalName },
                isOn = false,
                lastUpdated = System.currentTimeMillis()
            )

            updateAppWidgetState(this@WledWidgetConfigureActivity, glanceId) { prefs ->
                prefs[WIDGET_DATA_KEY] = Json.encodeToString(widgetData)
            }
            WledWidget().update(this@WledWidgetConfigureActivity, glanceId)

            // Trigger a background refresh immediately to get real data
            launch {
                widgetManager.refreshWidget(this@WledWidgetConfigureActivity, glanceId)
            }

            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
fun ConfigurationScreen(devices: List<Device>, onDeviceSelected: (Device) -> Unit) {
    Scaffold(
        topBar = {
            Text(
                text = "Select Device",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(devices) { device ->
                DeviceItem(device = device, onClick = { onDeviceSelected(device) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun DeviceItem(device: Device, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            text = if (device.customName.isNotBlank()) device.customName else device.originalName,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = device.address,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
