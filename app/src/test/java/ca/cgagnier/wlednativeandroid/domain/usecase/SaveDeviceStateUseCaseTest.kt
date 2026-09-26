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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveDeviceStateUseCaseTest {

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
        useCase = SaveDeviceStateUseCase(deviceRepository, repositoryDao)
    }

    @Test
    fun `invoke updates device and returns newDevice when originalName changes`() = runTest {
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

    @Test
    fun `invoke infers Branch BETA when device branch is UNKNOWN and version contains -b`() = runTest {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.UNKNOWN,
            lastSeen = 10000L,
        )
        val stateInfo = createDeviceStateInfo(name = "WLED", version = "0.14.0-b1")

        val result = useCase(currentDevice, stateInfo, currentTimeMillis = 11000L)

        assertNotNull(result)
        assertEquals(Branch.BETA, result?.branch)
        coVerify(exactly = 1) { deviceRepository.update(result!!) }
    }

    @Test
    fun `invoke infers Branch STABLE when device branch is UNKNOWN and version does not contain -b`() = runTest {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.UNKNOWN,
            lastSeen = 10000L,
        )
        val stateInfo = createDeviceStateInfo(name = "WLED", version = "0.14.0")

        val result = useCase(currentDevice, stateInfo, currentTimeMillis = 11000L)

        assertNotNull(result)
        assertEquals(Branch.STABLE, result?.branch)
        coVerify(exactly = 1) { deviceRepository.update(result!!) }
    }

    @Test
    fun `invoke preserves existing branch when branch is already STABLE`() = runTest {
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
    fun `invoke updates device when repositoryId changes`() = runTest {
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
    fun `invoke updates device when timeSinceLastUpdate exceeds threshold`() = runTest {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.STABLE,
            lastSeen = 10000L,
            repositoryId = Repository.DEFAULT_ID,
        )
        val stateInfo = createDeviceStateInfo(name = "WLED", version = "0.14.0")

        // Delta is 5001ms > LAST_SEEN_UPDATE_THRESHOLD (5000ms)
        val result = useCase(currentDevice, stateInfo, currentTimeMillis = 15001L)

        assertNotNull(result)
        assertEquals(15001L, result?.lastSeen)
        coVerify(exactly = 1) { deviceRepository.update(result!!) }
    }

    @Test
    fun `invoke returns null and does not update when unchanged and within threshold`() = runTest {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.STABLE,
            lastSeen = 10000L,
            repositoryId = Repository.DEFAULT_ID,
        )
        val stateInfo = createDeviceStateInfo(name = "WLED", version = "0.14.0")

        // Delta is 3000ms <= LAST_SEEN_UPDATE_THRESHOLD (5000ms)
        val result = useCase(currentDevice, stateInfo, currentTimeMillis = 13000L)

        assertNull(result)
        coVerify(exactly = 0) { deviceRepository.update(any()) }
    }

    @Test
    fun `invoke does not call repositoryDao on unchanged frames within threshold`() = runTest {
        val currentDevice = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "WLED",
            branch = Branch.STABLE,
            lastSeen = 10000L,
            repositoryId = Repository.DEFAULT_ID,
        )
        val stateInfo = createDeviceStateInfo(name = "WLED", version = "0.14.0")

        // First frame within threshold: no DB query because repo is default and metadata unchanged
        val result1 = useCase(currentDevice, stateInfo, currentTimeMillis = 11000L)
        assertNull(result1)
        coVerify(exactly = 0) { repositoryDao.getRepositoryByOwnerAndRepo(any()) }

        // Second frame exceeding threshold: triggers persistence and queries repositoryDao
        val result2 = useCase(currentDevice, stateInfo, currentTimeMillis = 16000L)
        assertNotNull(result2)
        coVerify(exactly = 1) { repositoryDao.getRepositoryByOwnerAndRepo(any()) }

        // Subsequent frames within threshold: no DB query because metadata unchanged and repo is default
        val updatedDevice = result2!!
        val result3 = useCase(updatedDevice, stateInfo, currentTimeMillis = 17000L)
        assertNull(result3)
        // Verify repositoryDao was NOT called again (still exactly 1)
        coVerify(exactly = 1) { repositoryDao.getRepositoryByOwnerAndRepo(any()) }
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
