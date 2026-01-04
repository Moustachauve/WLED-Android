package ca.cgagnier.wlednativeandroid.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Version(
    @PrimaryKey
    val tagName: String,
    val name: String,
    val description: String,
    val isPrerelease: Boolean,
    val publishedDate: String,
    val htmlUrl: String,
) {

    companion object {
        fun getPreviewVersion(): Version {
            return Version(
                tagName = "v1.0.0",
                name = "new version",
                description = "this is a test version",
                isPrerelease = false,
                publishedDate = "2024-10-13T15:54:31Z",
                htmlUrl = "https://github.com/"
            )
        }
    }
}