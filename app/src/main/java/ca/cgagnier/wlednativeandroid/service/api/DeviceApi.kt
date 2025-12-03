package ca.cgagnier.wlednativeandroid.service.api

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

interface DeviceApi {
    @GET("json/info")
    suspend fun getInfo(): Response<Info>

    @Multipart
    @POST("update")
    suspend fun updateDevice(
        @Part binaryFile: MultipartBody.Part
    ): Response<ResponseBody>
}

/**
 * Factory for creating instances of DeviceApi.
 *
 * Since the base URL is dynamic per device, we can't provide a singleton Retrofit instance.
 * Instead, we provide this factory to create a new DeviceApi on-demand.
 *
 * @param client The OkHttpClient to use for the API calls.
 */
class DeviceApiFactory(private val client: OkHttpClient) {

    /**
     * Create a new DeviceApi instance from a device address.
     *
     * @param address The address of a device to create the API for.
     */
    fun create(address: String): DeviceApi {
        // Normalize the address to ensure it's a valid base URL
        val baseUrl = if (!address.startsWith("http://") && !address.startsWith("https://")) {
            "http://$address/"
        } else {
            address
        }
        return createForDeviceAndClient(baseUrl, client)
    }

    /**
     * Create a new DeviceApi instance for a device.
     *
     * @param device The device to create the API for.
     */
    fun create(device: Device): DeviceApi {
        return createForDeviceAndClient(device.getDeviceUrl(), client)
    }

    /**
     * Create a new DeviceApi instance with a custom timeout.
     *
     * @param device The device to create the API for.
     * @param timeout The custom timeout in seconds.
     */
    fun create(device: Device, timeout: Long): DeviceApi {
        val customClient = client.newBuilder().connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS).writeTimeout(timeout, TimeUnit.SECONDS).build()

        return createForDeviceAndClient(device.getDeviceUrl(), customClient)
    }

    private fun createForDeviceAndClient(address: String, client: OkHttpClient): DeviceApi {
        return Retrofit.Builder().baseUrl(address).client(client)
            .addConverterFactory(MoshiConverterFactory.create()).build()
            .create(DeviceApi::class.java)
    }
}