package ca.cgagnier.wlednativeandroid.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import kotlinx.coroutines.flow.Flow

@Dao
interface VersionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: Version)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMany(version: List<Version>)

    @Update
    suspend fun update(version: Version)

    @Delete
    suspend fun delete(version: Version)

    @Query("DELETE FROM version")
    suspend fun deleteAll()

    @Query("SELECT * FROM version WHERE repositoryId = :repositoryId")
    suspend fun getVersionsByRepository(repositoryId: Long): List<Version>

    @Transaction
    @Query("SELECT * FROM version WHERE repositoryId = :repositoryId AND tagName = :tagName LIMIT 1")
    suspend fun getVersionByTagName(repositoryId: Long, tagName: String): VersionWithAssets?

    @Transaction
    @Query("SELECT * FROM version")
    fun getVersionsWithAsset(): Flow<List<VersionWithAssets>>
}
