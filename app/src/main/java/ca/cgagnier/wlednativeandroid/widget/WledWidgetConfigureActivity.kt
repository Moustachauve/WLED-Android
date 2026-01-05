package ca.cgagnier.wlednativeandroid.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class WledWidgetConfigureActivity : ComponentActivity() {

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
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
                    saveWidgetState(device)
                }
            )
        }
    }

    private fun saveWidgetState(device: Device) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            val glanceId = GlanceAppWidgetManager(this@WledWidgetConfigureActivity).getGlanceIdBy(appWidgetId)

            var isOn = false
            try {
                val deviceApiFactory = DeviceApiFactory(okHttpClient)
                val api = deviceApiFactory.create(device.address)
                val response = api.postJson(JsonPost(verbose = true))
                if (response.isSuccessful && response.body() != null) {
                    isOn = response.body()!!.state.isOn ?: false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            updateAppWidgetState(this@WledWidgetConfigureActivity, glanceId) { prefs ->
                prefs[DEVICE_ADDRESS_KEY] = device.address
                prefs[DEVICE_NAME_KEY] = if (device.customName.isNotBlank()) device.customName else device.originalName
                prefs[DEVICE_IS_ON_KEY] = isOn
            }
            WledWidget().update(this@WledWidgetConfigureActivity, glanceId)

            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
fun ConfigurationScreen(
    devices: List<Device>,
    onDeviceSelected: (Device) -> Unit
) {
    Scaffold(
        topBar = {
            Text(
                text = "Select Device",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(devices) { device ->
                DeviceItem(device = device, onClick = { onDeviceSelected(device) })
                Divider()
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
            .padding(16.dp)
    ) {
        Text(
            text = if (device.customName.isNotBlank()) device.customName else device.originalName,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = device.address,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
