package ca.cgagnier.wlednativeandroid.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ca.cgagnier.wlednativeandroid.model.Asset

@Dao
interface AssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: Asset)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMany(asset: List<Asset>)

    @Update
    suspend fun update(asset: Asset)

    @Delete
    suspend fun delete(asset: Asset)

    @Query("DELETE FROM asset")
    suspend fun deleteAll()
}
