package ca.cgagnier.wlednativeandroid.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ca.cgagnier.wlednativeandroid.FileUploadContract
import ca.cgagnier.wlednativeandroid.FileUploadContractResult
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // For WebView file upload support
    var uploadMessage: ValueCallback<Array<Uri>>? = null
    val fileUpload =
        registerForActivityResult(FileUploadContract()) { result: FileUploadContractResult ->
            uploadMessage?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(
                    result.resultCode,
                    result.intent,
                ),
            )
            uploadMessage = null
        }

    private var deviceAddress by mutableStateOf<NavigationEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            WLEDNativeTheme {
                MainNavHost(startDeviceAddress = deviceAddress)
            }
        }
        viewModel.downloadUpdateMetadata()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val extraAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        if (extraAddress != null) {
            deviceAddress = NavigationEvent(extraAddress)
        }
    }

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "device_address"
    }
}

data class NavigationEvent(val address: String, val id: String = java.util.UUID.randomUUID().toString())
