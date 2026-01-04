package ca.cgagnier.wlednativeandroid.ui

import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
                    result.intent
                )
            )
            uploadMessage = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WLEDNativeTheme {
                MainNavHost()
            }
        }
        viewModel.downloadUpdateMetadata()
    }
}