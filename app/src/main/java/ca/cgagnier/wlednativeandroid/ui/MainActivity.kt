package ca.cgagnier.wlednativeandroid.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import ca.cgagnier.wlednativeandroid.FileUploadContract
import ca.cgagnier.wlednativeandroid.FileUploadContractResult
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject
    lateinit var versionWithAssetsRepository: VersionWithAssetsRepository
    @Inject
    lateinit var githubApi: GithubApi

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
        updateDeviceVersionList()
    }

    /**
     * Checks for device updates once in a while
     */
    private fun updateDeviceVersionList() {
        lifecycleScope.launch(Dispatchers.IO) {
            userPreferencesRepository.lastUpdateCheckDate.collect {
                val now = System.currentTimeMillis()
                if (now < it) {
                    Log.i(TAG, "Not updating version list since it was done recently.")
                    return@collect
                }
                val releaseService = ReleaseService(versionWithAssetsRepository)
                releaseService.refreshVersions(githubApi)
                // Set the next date to check in minimum 24 hours from now.
                userPreferencesRepository.updateLastUpdateCheckDate(now + (24 * 60 * 60 * 1000))
            }
        }
    }
}