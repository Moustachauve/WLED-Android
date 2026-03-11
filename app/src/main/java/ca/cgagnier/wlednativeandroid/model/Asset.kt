package ca.cgagnier.wlednativeandroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["versionTagName", "repository", "name"],
    foreignKeys = [ForeignKey(
        entity = Version::class,
        parentColumns = arrayOf("tagName", "repository"),
        childColumns = arrayOf("versionTagName", "repository"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Asset(

    @ColumnInfo(index = true)
    val versionTagName: String,
    @ColumnInfo(index = true, defaultValue = "'wled/WLED'")
    val repository: String,
    val name: String,
    val size: Long,
    val downloadUrl: String,
    @ColumnInfo(defaultValue = "0")
    val assetId: Int,
)
