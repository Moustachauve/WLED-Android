package ca.cgagnier.wlednativeandroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["repositoryId", "tagName"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Repository::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("repositoryId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Version(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val repositoryId: Long,
    val tagName: String,
    val name: String,
    val description: String,
    val isPrerelease: Boolean,
    val publishedDate: String,
    val htmlUrl: String,
) {

    companion object {
        fun getPreviewVersion(): Version = Version(
            repositoryId = 1,
            tagName = "v1.0.0",
            name = "new version",
            description = "this is a test version",
            isPrerelease = false,
            publishedDate = "2024-10-13T15:54:31Z",
            htmlUrl = "https://github.com/",
        )
    }
}
