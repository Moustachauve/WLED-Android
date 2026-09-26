package ca.cgagnier.wlednativeandroid.domain.usecase

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SaveDeviceStateUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val repositoryDao: RepositoryDao = mockk(relaxed = true)
    private lateinit var useCase: SaveDeviceStateUseCase

    private val defaultRepo = Repository(
        id = Repository.DEFAULT_ID,
        name = "wled/WLED",
        ownerAndRepo = Repository.DEFAULT_OWNER_REPO,
        description = "",
        htmlUrl = "https://github.com/wled/WLED",
    )

    @BeforeEach
    fun setUp() {
        coEvery { repositoryDao.getRepositoryByOwnerAndRepo(any()) } returns defaultRepo
        useCase = SaveDeviceStateUseCase(deviceRepository, repositoryDao, testDispatcher)
    }

    @Test
    fun `invoke updates device and returns newDevice when originalName changes`() = runTest(testDispatcher) {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "Old Name",
            branch = Branch.STABLE,
            lastSeen = 10000L,
            repositoryId = Repository.DEFAULT_ID,
        )
        val stateInfo = createDeviceStateInfo(name = "New Name", version = "0.14.0")

        val result = useCase(currentDevice, stateInfo, currentTimeMillis = 11000L)

        assertNotNull(result)
        assertEquals("New Name", result?.originalName)
        assertEquals(11000L, result?.lastSeen)
        coVerify(exactly = 1) { deviceRepository.update(result!!) }
    }

    @ParameterizedTest
    @CsvSource(
        "0.14.0-b1, BETA",
        "0.14.0, STABLE",
    )
    fun `invoke infers correct Branch when device branch is UNKNOWN`(version: String, expectedBranch: Branch) =
        runTest(testDispatcher) {
            val currentDevice = Device(
                macAddress = "AABBCCDDEEFF",
                address = "192.168.1.100",
                originalName = "WLED",
                branch = Branch.UNKNOWN,
                lastSeen = 10000L,
            )
            val stateInfo = createDeviceStateInfo(name = "WLED", version = version)

            val result = useCase(currentDevice, stateInfo, currentTimeMillis = 11000L)

            assertNotNull(result)
            assertEquals(expectedBranch, result?.branch)
            coVerify(exactly = 1) { deviceRepository.update(result!!) }
        }

    @Test
    fun `invoke preserves existing branch when branch is already STABLE`() = runTest(testDispatcher) {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.STABLE,
            lastSeen = 10000L,
        )
        // Even if incoming version has -b, if user/device is already set to STABLE, branch is kept
        val stateInfo = createDeviceStateInfo(name = "WLED", version = "0.14.0-b1")

        // time delta is within threshold (2000ms <= 5000ms) and name/branch/repo unchanged
        val result = useCase(currentDevice, stateInfo, currentTimeMillis = 12000L)

        assertNull(result)
        coVerify(exactly = 0) { deviceRepository.update(any()) }
    }

    @Test
    fun `invoke updates device when repositoryId changes`() = runTest(testDispatcher) {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.STABLE,
            lastSeen = 10000L,
            repositoryId = Repository.DEFAULT_ID,
        )
        val customRepo = Repository(
            id = 42L,
            name = "custom/wled",
            ownerAndRepo = "custom/wled",
            description = "",
            htmlUrl = "https://github.com/custom/wled",
        )
        coEvery { repositoryDao.getRepositoryByOwnerAndRepo("custom/wled") } returns customRepo

        val stateInfo = createDeviceStateInfo(
            name = "WLED",
            version = "0.14.0",
            repository = "custom/wled",
        )

        val result = useCase(currentDevice, stateInfo, currentTimeMillis = 11000L)

        assertNotNull(result)
        assertEquals(42L, result?.repositoryId)
        coVerify(exactly = 1) { deviceRepository.update(result!!) }
    }

    @Test
    fun `invoke updates device when timeSinceLastUpdate exceeds threshold`() = runTest(testDispatcher) {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.STABLE,
            lastSeen = 10000L,
            repositoryId = Repository.DEFAULT_ID,
        )
        val stateInfo = createDeviceStateInfo(name = "WLED", version = "0.14.0")

        // Delta exceeds LAST_SEEN_UPDATE_THRESHOLD
        val updatedTime = currentDevice.lastSeen + SaveDeviceStateUseCase.LAST_SEEN_UPDATE_THRESHOLD + 1L
        val result = useCase(currentDevice, stateInfo, currentTimeMillis = updatedTime)

        assertNotNull(result)
        assertEquals(updatedTime, result?.lastSeen)
        coVerify(exactly = 1) { deviceRepository.update(result!!) }
    }

    @Test
    fun `invoke returns null and does not update when unchanged and within threshold`() = runTest(testDispatcher) {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.STABLE,
            lastSeen = 10000L,
            repositoryId = Repository.DEFAULT_ID,
        )
        val stateInfo = createDeviceStateInfo(name = "WLED", version = "0.14.0")

        // Delta is within LAST_SEEN_UPDATE_THRESHOLD
        val withinThresholdTime = currentDevice.lastSeen + (SaveDeviceStateUseCase.LAST_SEEN_UPDATE_THRESHOLD / 2)
        val result = useCase(currentDevice, stateInfo, currentTimeMillis = withinThresholdTime)

        assertNull(result)
        coVerify(exactly = 0) { deviceRepository.update(any()) }
    }

    @Test
    fun `invoke does not query repositoryDao when device uses default repository`() = runTest(testDispatcher) {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.STABLE,
            lastSeen = 10000L,
            repositoryId = Repository.DEFAULT_ID,
        )
        val stateInfo = createDeviceStateInfo(name = "WLED", version = "0.14.0")

        // First frame within threshold: unchanged, returns null, no DB query
        val result1 = useCase(currentDevice, stateInfo, currentTimeMillis = 11000L)
        assertNull(result1)
        coVerify(exactly = 0) { repositoryDao.getRepositoryByOwnerAndRepo(any()) }

        // Second frame exceeding threshold: triggers persistence, but uses Repository.DEFAULT_ID without DB query
        val exceedingTime = currentDevice.lastSeen + SaveDeviceStateUseCase.LAST_SEEN_UPDATE_THRESHOLD + 1000L
        val result2 = useCase(currentDevice, stateInfo, currentTimeMillis = exceedingTime)
        assertNotNull(result2)
        assertEquals(Repository.DEFAULT_ID, result2?.repositoryId)
        coVerify(exactly = 0) { repositoryDao.getRepositoryByOwnerAndRepo(any()) }
        coVerify(exactly = 1) { deviceRepository.update(result2!!) }

        // Subsequent frames within threshold: no DB query and no update
        val updatedDevice = result2!!
        val subsequentTime = updatedDevice.lastSeen + 1000L
        val result3 = useCase(updatedDevice, stateInfo, currentTimeMillis = subsequentTime)
        assertNull(result3)
        coVerify(exactly = 0) { repositoryDao.getRepositoryByOwnerAndRepo(any()) }
    }

    private fun createDeviceStateInfo(
        name: String = "Test Device",
        version: String? = "0.14.0",
        repository: String? = null,
    ): DeviceStateInfo = DeviceStateInfo(
        state = State(isOn = true),
        info = Info(
            leds = Leds(count = 30, fps = 30, maxPower = 0, maxSegment = 1),
            wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
            name = name,
            version = version,
            repository = repository,
        ),
    )
}
