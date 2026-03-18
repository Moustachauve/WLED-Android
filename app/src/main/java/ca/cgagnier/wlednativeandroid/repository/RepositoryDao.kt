package ca.cgagnier.wlednativeandroid.repository

import android.util.Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ca.cgagnier.wlednativeandroid.model.Repository
import kotlinx.coroutines.flow.Flow

@Dao
interface RepositoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repository: Repository): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMany(repositories: List<Repository>)

    @Update
    suspend fun update(repository: Repository)

    @Delete
    suspend fun delete(repository: Repository)

    @Query("DELETE FROM repository")
    suspend fun deleteAll()

    @Query("SELECT * FROM repository WHERE id = :id")
    suspend fun getRepositoryById(id: Long): Repository?

    @Query("SELECT * FROM repository WHERE ownerAndRepo = :ownerAndRepo LIMIT 1")
    suspend fun getRepositoryByOwnerAndRepo(ownerAndRepo: String): Repository?

    @Query("SELECT * FROM repository ORDER BY isDefault DESC, name ASC")
    fun getAllRepositories(): Flow<List<Repository>>
}

/**
 * Returns the database ID for a repository, creating a new entry if it doesn't exist.
 * Newly created repositories have updates disabled by default to prevent unwanted API
 * calls for unvetted forks.
 */
suspend fun RepositoryDao.getOrCreateRepositoryId(ownerAndRepo: String): Long {
    val repo = getRepositoryByOwnerAndRepo(ownerAndRepo)
    if (repo != null) {
        return repo.id
    }
    val autoDiscoveredRepo = Repository(
        name = ownerAndRepo,
        ownerAndRepo = ownerAndRepo,
        description = "",
        htmlUrl = "https://github.com/$ownerAndRepo",
        isDefault = false,
        isEnabled = false,
        isUpdateEnabled = false,
    )
    val newId = insert(autoDiscoveredRepo)
    Log.d("RepositoryDao", "Auto-discovered and inserted new repository: $ownerAndRepo (id=$newId)")
    return newId
}
