package ca.cgagnier.wlednativeandroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ca.cgagnier.wlednativeandroid.shared.currentTimeMillis

const val AP_MODE_MAC_ADDRESS = "AP-MODE"
const val DEFAULT_WLED_AP_IP = "4.3.2.1"

/**
 * Represents a stateless WLED device
 */
@Entity(tableName = "Device2")
data class Device(
    @PrimaryKey
    val macAddress: String,

    val address: String,

    val isHidden: Boolean = false,

    @ColumnInfo(defaultValue = "")
    val originalName: String = "",

    @ColumnInfo(defaultValue = "")
    val customName: String = "",

    @ColumnInfo(defaultValue = "")
    val skipUpdateTag: String = "",

    @ColumnInfo(defaultValue = "UNKNOWN")
    val branch: Branch = Branch.UNKNOWN,

    @ColumnInfo(defaultValue = "0")
    val lastSeen: Long = currentTimeMillis(),

    @ColumnInfo(defaultValue = "1")
    val repositoryId: Long = Repository.DEFAULT_ID,
) {

    fun getDeviceUrl(): String = "http://$address"
}
