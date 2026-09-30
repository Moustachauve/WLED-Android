package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import kotlinx.coroutines.flow.Flow
import okio.Path.Companion.toOkioPath
import java.io.File

fun GithubApiEndpoints.downloadReleaseBinary(
    repoOwner: String,
    repoName: String,
    assetId: Int,
    targetFile: File,
): Flow<DownloadState> = downloadReleaseBinary(repoOwner, repoName, assetId, targetFile.toOkioPath())

fun GithubApi.downloadReleaseBinary(
    asset: Asset,
    repoOwner: String,
    repoName: String,
    targetFile: File,
): Flow<DownloadState> = downloadReleaseBinary(asset, repoOwner, repoName, targetFile.toOkioPath())
