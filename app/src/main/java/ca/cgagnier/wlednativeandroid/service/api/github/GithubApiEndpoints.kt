package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Streaming

interface GithubApiEndpoints {
    @GET("repos/{repoOwner}/{repoName}/releases")
    suspend fun getAllReleases(
        @Path("repoOwner") repoOwner: String,
        @Path("repoName") repoName: String
    ): List<Release>


    @Streaming
    @Headers("Accept: application/octet-stream")
    @GET("repos/{repoOwner}/{repoName}/releases/assets/{assetId}")
    suspend fun downloadReleaseBinary(
        @Path("repoOwner") repoOwner: String,
        @Path("repoName") repoName: String,
        @Path("assetId") assetId: Int
    ): ResponseBody
}