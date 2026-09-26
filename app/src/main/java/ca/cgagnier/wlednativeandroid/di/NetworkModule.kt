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
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val DEFAULT_TIMEOUT_SECONDS = 30L
    private const val PING_INTERVAL_SECONDS = 30
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

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext appContext: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .pingInterval(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .cache(Cache(appContext.cacheDir, CACHE_SIZE_BYTES))
        .build()

    @Provides
    @Singleton
    fun provideKtorHttpClient(okHttpClient: OkHttpClient, json: Json): HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets) {
            pingInterval = PING_INTERVAL_SECONDS.seconds
        }
    }

    @Provides
    @Singleton
    fun provideGithubApiEndpoints(httpClient: HttpClient): GithubApiEndpoints = KtorGithubApiEndpoints(httpClient)

    @Provides
    @Singleton
    fun provideDeviceApiFactory(httpClient: HttpClient): DeviceApiFactory = DeviceApiFactory(httpClient)
}
