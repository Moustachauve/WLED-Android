package ca.cgagnier.wlednativeandroid.service.device.api

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.api.DeviceApi
import ca.cgagnier.wlednativeandroid.service.device.api.request.SoftwareUpdateRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "JsonApiRequestHandler"

class JsonApiRequestHandler @Inject constructor() : RequestHandler() {
    private fun getJsonApi(device: Device, timeout: Long = 10): DeviceApi {
        val okHttpClient = OkHttpClient().newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(device.getDeviceUrl())
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(DeviceApi::class.java)
    }

    override suspend fun handleSoftwareUpdateRequest(request: SoftwareUpdateRequest) {
        Log.d(TAG, "Installing software update: ${request.device.macAddress}")
        try {
            val reqFile =
                request.binaryFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val response = getJsonApi(request.device, timeout = 120).updateDevice(
                MultipartBody.Part.createFormData("file", "binary", reqFile)
            )
            request.callback?.invoke(response)
        } catch (e: Exception) {
            request.errorCallback?.invoke(e)
        }
    }
}