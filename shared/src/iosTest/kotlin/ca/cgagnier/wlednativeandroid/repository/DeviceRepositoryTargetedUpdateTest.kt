package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceRepositoryTargetedUpdateTest {
    private val database = DevicesDatabase.createInMemoryDatabase()
    private val repository = database.createDeviceRepository()

    private val device = Device(
        macAddress = "aabbccddeeff",
        address = "192.168.1.10",
        isHidden = false,
        originalName = "Original",
        customName = "Custom",
        skipUpdateTag = "v0.14.0",
        branch = Branch.STABLE,
        lastSeen = 1_000L,
    )

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private suspend fun savedDevice(): Device? = repository.findDeviceByMacAddress(device.macAddress)

    @Test
    fun updateCustomNameOnlyChangesCustomName() = runTest {
        repository.insert(device)

        repository.updateCustomName(device.macAddress, "Renamed")

        assertEquals(device.copy(customName = "Renamed"), savedDevice())
    }

    @Test
    fun updateAddressOnlyChangesAddress() = runTest {
        repository.insert(device)

        repository.updateAddress(device.macAddress, "192.168.1.20")

        assertEquals(device.copy(address = "192.168.1.20"), savedDevice())
    }

    @Test
    fun updateLastSeenOnlyChangesLastSeen() = runTest {
        repository.insert(device)

        repository.updateLastSeen(device.macAddress, 2_000L)

        assertEquals(device.copy(lastSeen = 2_000L), savedDevice())
    }

    @Test
    fun updateBranchClearsSkipUpdateTag() = runTest {
        repository.insert(device)

        repository.updateBranch(device.macAddress, Branch.BETA)

        assertEquals(device.copy(branch = Branch.BETA, skipUpdateTag = ""), savedDevice())
    }
}
