package ca.cgagnier.wlednativeandroid.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

const val AP_MODE_MAC_ADDRESS = "AP-MODE"
const val DEFAULT_WLED_AP_IP = "4.3.2.1"

/**
 * Represents a stateless WLED device
 */
@Entity(tableName = "Device2")
@Parcelize
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
    val lastSeen: Long = System.currentTimeMillis(),
) : Parcelable {

    fun getDeviceUrl(): String {
        return "http://$address"
    }
}