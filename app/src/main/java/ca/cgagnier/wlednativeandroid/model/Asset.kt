package ca.cgagnier.wlednativeandroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["versionTagName", "name"],
    foreignKeys = [
        ForeignKey(
            entity = Version::class,
            parentColumns = arrayOf("tagName"),
            childColumns = arrayOf("versionTagName"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Asset(

    @ColumnInfo(index = true)
    val versionTagName: String,
    val name: String,
    val size: Long,
    val downloadUrl: String,
    @ColumnInfo(defaultValue = "0")
    val assetId: Int,
)
