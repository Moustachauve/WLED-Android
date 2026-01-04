package ca.cgagnier.wlednativeandroid.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import ca.cgagnier.wlednativeandroid.model.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: Device)

    @Update
    suspend fun update(device: Device)

    @Delete
    suspend fun delete(device: Device)

    @Query("DELETE FROM Device2")
    suspend fun deleteAll()

    @Query("SELECT * FROM Device2 WHERE address = :address")
    suspend fun findDeviceByAddress(address: String): Device?

    @Query("SELECT * FROM Device2 WHERE address = :address")
    fun findLiveDeviceByAddress(address: String): Flow<Device?>

    @Query("SELECT * FROM Device2 WHERE macAddress != '' AND macAddress = :address")
    suspend fun findDeviceByMacAddress(address: String): Device?

    @Query("SELECT COUNT() FROM Device2 WHERE address = :address")
    fun count(address: String): Int

    @RawQuery
    suspend fun insert(query: SupportSQLiteQuery): Device

    @Query("SELECT * FROM Device2")
    fun getAllDevices(): List<Device>

    @Query("SELECT * FROM Device2 ORDER BY LOWER(COALESCE(customName, originalName)) ASC, LOWER(address) ASC")
    fun getAlphabetizedDevices(): Flow<List<Device>>

    @Query("SELECT COUNT() FROM Device2 WHERE isHidden = 1")
    suspend fun countHiddenDevices(): Int
}