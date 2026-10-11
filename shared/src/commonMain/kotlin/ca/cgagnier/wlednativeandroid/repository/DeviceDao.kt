package ca.cgagnier.wlednativeandroid.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: Device)

    @Update
    suspend fun update(device: Device)

    @Query("UPDATE Device2 SET address = :address WHERE macAddress = :macAddress")
    suspend fun updateAddress(macAddress: String, address: String)

    @Query("UPDATE Device2 SET originalName = :originalName WHERE macAddress = :macAddress")
    suspend fun updateOriginalName(macAddress: String, originalName: String)

    @Query("UPDATE Device2 SET customName = :customName WHERE macAddress = :macAddress")
    suspend fun updateCustomName(macAddress: String, customName: String)

    @Query("UPDATE Device2 SET isHidden = :isHidden WHERE macAddress = :macAddress")
    suspend fun updateIsHidden(macAddress: String, isHidden: Boolean)

    @Query("UPDATE Device2 SET skipUpdateTag = :skipUpdateTag WHERE macAddress = :macAddress")
    suspend fun updateSkipUpdateTag(macAddress: String, skipUpdateTag: String)

    /** Changing branch also clears the skipped version, since it belongs to the previous branch. */
    @Query("UPDATE Device2 SET branch = :branch, skipUpdateTag = '' WHERE macAddress = :macAddress")
    suspend fun updateBranch(macAddress: String, branch: Branch)

    @Query("UPDATE Device2 SET lastSeen = :lastSeen WHERE macAddress = :macAddress")
    suspend fun updateLastSeen(macAddress: String, lastSeen: Long)

    @Delete
    suspend fun delete(device: Device)

    @Query("DELETE FROM Device2")
    suspend fun deleteAll()

    @Query("SELECT * FROM Device2 WHERE address = :address")
    suspend fun findDeviceByAddress(address: String): Device?

    @Query("SELECT * FROM Device2 WHERE address = :address")
    fun findLiveDeviceByAddress(address: String): Flow<Device?>

    @Query("SELECT * FROM Device2 WHERE macAddress != '' AND LOWER(macAddress) = LOWER(:address)")
    suspend fun findDeviceByMacAddress(address: String): Device?

    @Query("SELECT COUNT(*) FROM Device2 WHERE address = :address")
    suspend fun count(address: String): Int

    @Query("SELECT * FROM Device2")
    suspend fun getAllDevices(): List<Device>

    @Query("SELECT DISTINCT repositoryId FROM Device2")
    suspend fun getUsedRepositoryIds(): List<Long>

    @Query("SELECT * FROM Device2 ORDER BY LOWER(COALESCE(customName, originalName)) ASC, LOWER(address) ASC")
    fun getAlphabetizedDevices(): Flow<List<Device>>

    @Query("SELECT COUNT() FROM Device2 WHERE isHidden = 1")
    suspend fun countHiddenDevices(): Int
}
