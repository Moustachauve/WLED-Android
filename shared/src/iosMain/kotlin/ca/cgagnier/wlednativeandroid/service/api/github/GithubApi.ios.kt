package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.createHttpClient
import platform.Foundation.NSURLSessionConfiguration

private const val GITHUB_TIMEOUT_SECONDS = 60L

fun GithubApi.Companion.create(): GithubApi = GithubApi(
    KtorGithubApiEndpoints(DeviceApiFactory.createHttpClient(timeoutSeconds = GITHUB_TIMEOUT_SECONDS)),
)

fun GithubApi.Companion.create(configureSession: (NSURLSessionConfiguration) -> Unit): GithubApi = GithubApi(
    KtorGithubApiEndpoints(
        DeviceApiFactory.createHttpClient(
            timeoutSeconds = GITHUB_TIMEOUT_SECONDS,
            configureSession = configureSession,
        ),
    ),
)
