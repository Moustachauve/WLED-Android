package ca.cgagnier.wlednativeandroid.service.api

/**
 * A generic response wrapper for API calls, decoupling caller code from any specific HTTP library.
 */
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299

data class ApiResponse<T>(val code: Int, val body: T? = null, val errorBody: String? = null) {
    val isSuccessful: Boolean
        get() = code in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
}
