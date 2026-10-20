package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal fun cachesDirectory(): String {
    val cachesDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return cachesDirectory?.path ?: NSTemporaryDirectory()
}

@Suppress("LongParameterList")
fun DeviceUpdateService.Companion.create(
    device: Device,
    stateInfo: DeviceStateInfo?,
    versionWithAssets: VersionWithAssets,
    deviceApiFactory: DeviceApiFactory,
    githubApi: GithubApi,
    cacheDirPath: String? = null,
): DeviceUpdateService = DeviceUpdateService(
    device = DeviceWithState(device = device, stateInfo = stateInfo),
    versionWithAssets = versionWithAssets,
    cacheDir = (cacheDirPath ?: cachesDirectory()).toPath(),
    deviceApiFactory = deviceApiFactory,
    githubApi = githubApi,
)
