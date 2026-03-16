package ca.cgagnier.wlednativeandroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [Index(value = ["versionId", "name"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = Version::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("versionId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Asset(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(index = true)
    val versionId: Long,
    val name: String,
    val size: Long,
    val downloadUrl: String,
    @ColumnInfo(defaultValue = "0")
    val assetId: Int,
)
