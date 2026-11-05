package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import okio.Path.Companion.toOkioPath
import java.io.File

operator fun DeviceUpdateService.Companion.invoke(
    device: DeviceWithState,
    versionWithAssets: VersionWithAssets,
    cacheDir: File,
    deviceApiFactory: DeviceApiFactory,
    githubApi: GithubApi,
): DeviceUpdateService = DeviceUpdateService(
    device = device,
    versionWithAssets = versionWithAssets,
    cacheDir = cacheDir.toOkioPath(),
    deviceApiFactory = deviceApiFactory,
    githubApi = githubApi,
)
