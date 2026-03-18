package ca.cgagnier.wlednativeandroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["ownerAndRepo"], unique = true)])
data class Repository(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val ownerAndRepo: String,
    val description: String,
    val htmlUrl: String,
    @ColumnInfo(defaultValue = "0")
    val isDefault: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    val isUpdateEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_ID = 1L
        const val DEFAULT_OWNER_REPO = "wled/WLED"

        val BUILT_IN_REPOSITORIES = listOf(
            Repository(
                id = DEFAULT_ID,
                name = "WLED",
                ownerAndRepo = DEFAULT_OWNER_REPO,
                description = "Official WLED Repository",
                htmlUrl = "https://github.com/wled/WLED",
                isDefault = true,
                isEnabled = true,
                isUpdateEnabled = true,
            ),
            Repository(
                name = "QuinLED",
                ownerAndRepo = "intermittech/QuinLED-Firmware",
                description = "QuinLED WLED Firmware",
                htmlUrl = "https://github.com/intermittech/QuinLED-Firmware",
                isDefault = true,
                isEnabled = true,
                isUpdateEnabled = true,
            ),
            Repository(
                name = "MoonModules",
                ownerAndRepo = "MoonModules/WLED-MM",
                description = "MoonModules WLED-MM",
                htmlUrl = "https://github.com/MoonModules/WLED-MM",
                isDefault = true,
                isEnabled = true,
                isUpdateEnabled = true,
            ),
        )
    }
}
