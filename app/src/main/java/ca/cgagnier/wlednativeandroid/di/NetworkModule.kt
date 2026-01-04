package ca.cgagnier.wlednativeandroid.di
import android.content.Context
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApiEndpoints
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val GITHUB_BASE_URL = "https://api.github.com"
    private const val DEFAULT_TIMEOUT_SECONDS = 30L
    private const val CACHE_SIZE_BYTES = 20 * 1024 * 1024L // 20MB

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder().build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext appContext: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(Cache(appContext.cacheDir, CACHE_SIZE_BYTES))
            .build()
    }

    @Provides
    @Singleton
    fun provideGithubRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideGithubApiEndpoints(retrofit: Retrofit): GithubApiEndpoints {
        return retrofit.create(GithubApiEndpoints::class.java)
    }

    @Provides
    fun provideDeviceApiFactory(okHttpClient: OkHttpClient): DeviceApiFactory {
        return DeviceApiFactory(okHttpClient)
    }
}