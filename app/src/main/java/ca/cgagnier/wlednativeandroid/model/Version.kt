package ca.cgagnier.wlednativeandroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    primaryKeys = ["tagName", "repository"],
)
data class Version(
    val tagName: String,
    @ColumnInfo(defaultValue = "'wled/WLED'")
    val repository: String,
    val name: String,
    val description: String,
    val isPrerelease: Boolean,
    val publishedDate: String,
    val htmlUrl: String,
) {

    companion object {
        fun getPreviewVersion(): Version = Version(
            tagName = "v1.0.0",
            repository = "wled/WLED",
            name = "new version",
            description = "this is a test version",
            isPrerelease = false,
            publishedDate = "2024-10-13T15:54:31Z",
            htmlUrl = "https://github.com/",
        )
    }
}
