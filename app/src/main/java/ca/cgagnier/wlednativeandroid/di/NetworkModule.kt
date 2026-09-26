package ca.cgagnier.wlednativeandroid.di

import android.content.Context
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.api.github.KtorGithubApiEndpoints
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val DEFAULT_TIMEOUT_SECONDS = 30L
    private const val CACHE_SIZE_BYTES = 20 * 1024 * 1024L // 20MB

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    private const val MAX_REQUESTS = 64
    private const val MAX_REQUESTS_PER_HOST = 16

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext appContext: Context): OkHttpClient {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = MAX_REQUESTS
            maxRequestsPerHost = MAX_REQUESTS_PER_HOST
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(Cache(appContext.cacheDir, CACHE_SIZE_BYTES))
            .build()
    }

    @Provides
    @Singleton
    fun provideKtorHttpClient(okHttpClient: OkHttpClient, json: Json): HttpClient =
        DeviceApiFactory.createHttpClient(okHttpClient, json)

    @Provides
    @Singleton
    fun provideGithubApiEndpoints(httpClient: HttpClient): GithubApiEndpoints = KtorGithubApiEndpoints(httpClient)

    @Provides
    @Singleton
    fun provideDeviceApiFactory(httpClient: HttpClient): DeviceApiFactory = DeviceApiFactory(httpClient)
}
