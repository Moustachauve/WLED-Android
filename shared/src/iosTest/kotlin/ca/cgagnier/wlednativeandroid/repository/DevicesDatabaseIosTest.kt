package ca.cgagnier.wlednativeandroid.repository

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import platform.Foundation.NSFileManager
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class DevicesDatabaseIosTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var rootDir: Path
    private lateinit var fromDir: Path
    private lateinit var toDir: Path

    @BeforeTest
    fun setUp() {
        rootDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "db_migration_test_${Random.nextLong()}"
        fromDir = rootDir / "documents"
        toDir = rootDir / "app_group"
        fileSystem.createDirectories(fromDir)
        fileSystem.createDirectories(toDir)
    }

    @AfterTest
    fun tearDown() {
        fileSystem.deleteRecursively(rootDir, mustExist = false)
    }

    @Test
    fun migratesDatabaseAndSidecarsToAppGroupDirectory() {
        writeText(fromDir / DATABASE_FILE_NAME, "db-bytes")
        writeText(fromDir / "$DATABASE_FILE_NAME-wal", "wal-bytes")
        writeText(fromDir / "$DATABASE_FILE_NAME-shm", "shm-bytes")

        val migrated = migrateDatabaseToAppGroupIfNeeded(
            fromDirectory = fromDir.toString(),
            toDirectory = toDir.toString(),
            fileManager = NSFileManager.defaultManager,
        )

        assertTrue(migrated)
        assertEquals("db-bytes", readText(toDir / DATABASE_FILE_NAME))
        assertEquals("wal-bytes", readText(toDir / "$DATABASE_FILE_NAME-wal"))
        assertEquals("shm-bytes", readText(toDir / "$DATABASE_FILE_NAME-shm"))
        assertFalse(fileSystem.exists(fromDir / DATABASE_FILE_NAME))
        assertFalse(fileSystem.exists(fromDir / "$DATABASE_FILE_NAME-wal"))
        assertFalse(fileSystem.exists(fromDir / "$DATABASE_FILE_NAME-shm"))
    }

    @Test
    fun skipsMigrationWhenAppGroupDatabaseAlreadyExists() {
        writeText(fromDir / DATABASE_FILE_NAME, "legacy-db")
        writeText(toDir / DATABASE_FILE_NAME, "existing-group-db")

        val migrated = migrateDatabaseToAppGroupIfNeeded(
            fromDirectory = fromDir.toString(),
            toDirectory = toDir.toString(),
            fileManager = NSFileManager.defaultManager,
        )

        assertTrue(migrated)
        assertEquals("existing-group-db", readText(toDir / DATABASE_FILE_NAME))
        assertTrue(fileSystem.exists(fromDir / DATABASE_FILE_NAME))
    }

    @Test
    fun succeedsWhenNeitherDirectoryHasDatabaseOnFreshInstall() {
        val migrated = migrateDatabaseToAppGroupIfNeeded(
            fromDirectory = fromDir.toString(),
            toDirectory = toDir.toString(),
            fileManager = NSFileManager.defaultManager,
        )

        assertTrue(migrated)
        assertFalse(fileSystem.exists(toDir / DATABASE_FILE_NAME))
    }

    private fun writeText(path: Path, text: String) {
        fileSystem.write(path) { writeUtf8(text) }
    }

    private fun readText(path: Path): String = fileSystem.read(path) { readUtf8() }
}
