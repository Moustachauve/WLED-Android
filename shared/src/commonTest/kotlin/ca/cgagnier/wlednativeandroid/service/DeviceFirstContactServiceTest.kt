package ca.cgagnier.wlednativeandroid.service

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.repository.DeviceDao
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.FakeTransportException
import ca.cgagnier.wlednativeandroid.service.api.RecordingEngine
import ca.cgagnier.wlednativeandroid.service.api.respondingWith
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceFirstContactServiceTest {

    private class FakeDeviceDao : DeviceDao {
        val devices = mutableMapOf<String, Device>()
        var updateAddressCalls = 0
        var updateOriginalNameCalls = 0
        var updateRepositoryIdCalls = 0
        var updateAddressException: Exception? = null

        override suspend fun insert(device: Device) {
            devices[device.macAddress.lowercase()] = device
        }

        override suspend fun update(device: Device) {
            devices[device.macAddress.lowercase()] = device
        }

        override suspend fun updateAddress(macAddress: String, address: String) {
            updateAddressException?.let { throw it }
            updateAddressCalls++
            val key = macAddress.lowercase()
            devices[key]?.let { devices[key] = it.copy(address = address) }
        }

        override suspend fun updateOriginalName(macAddress: String, originalName: String) {
            updateOriginalNameCalls++
            val key = macAddress.lowercase()
            devices[key]?.let { devices[key] = it.copy(originalName = originalName) }
        }

        override suspend fun updateCustomName(macAddress: String, customName: String) = Unit
        override suspend fun updateIsHidden(macAddress: String, isHidden: Boolean) = Unit
        override suspend fun updateSkipUpdateTag(macAddress: String, skipUpdateTag: String) = Unit
        override suspend fun updateBranch(macAddress: String, branch: Branch) = Unit
        override suspend fun updateLastSeen(macAddress: String, lastSeen: Long) = Unit

        override suspend fun updateRepositoryId(macAddress: String, repositoryId: Long) {
            updateRepositoryIdCalls++
            val key = macAddress.lowercase()
            devices[key]?.let { devices[key] = it.copy(repositoryId = repositoryId) }
        }

        override suspend fun delete(device: Device) {
            devices.remove(device.macAddress.lowercase())
        }

        override suspend fun deleteAll() {
            devices.clear()
        }

        override suspend fun findDeviceByAddress(address: String): Device? =
            devices.values.firstOrNull { it.address == address }

        override fun findLiveDeviceByAddress(address: String): Flow<Device?> = MutableStateFlow(null)

        override suspend fun findDeviceByMacAddress(address: String): Device? =
            if (address.isEmpty()) null else devices[address.lowercase()]

        override suspend fun count(address: String): Int = devices.values.count { it.address == address }
        override suspend fun getAllDevices(): List<Device> = devices.values.toList()
        override suspend fun getUsedRepositoryIds(): List<Long> = devices.values.map { it.repositoryId }.distinct()
        override fun getAlphabetizedDevices(): Flow<List<Device>> = MutableStateFlow(devices.values.toList())
        override suspend fun countHiddenDevices(): Int = devices.values.count { it.isHidden }
    }

    private class FakeRepositoryDao : RepositoryDao {
        private var nextId = 2L
        val repositories = mutableMapOf(
            Repository.DEFAULT_OWNER_REPO to Repository(
                id = Repository.DEFAULT_ID,
                name = Repository.DEFAULT_OWNER_REPO,
                ownerAndRepo = Repository.DEFAULT_OWNER_REPO,
                description = "",
                htmlUrl = "",
                isDefault = true,
            ),
        )

        override suspend fun insert(repository: Repository): Long {
            val id = if (repository.id != 0L) repository.id else nextId++
            repositories[repository.ownerAndRepo] = repository.copy(id = id)
            return id
        }

        override suspend fun insertMany(repositories: List<Repository>) {
            repositories.forEach { insert(it) }
        }

        override suspend fun update(repository: Repository) {
            repositories[repository.ownerAndRepo] = repository
        }

        override suspend fun delete(repository: Repository) {
            repositories.remove(repository.ownerAndRepo)
        }

        override suspend fun deleteAll() {
            repositories.clear()
        }

        override suspend fun getRepositoryById(id: Long): Repository? = repositories.values.firstOrNull { it.id == id }

        override suspend fun getRepositoryByOwnerAndRepo(ownerAndRepo: String): Repository? = repositories[ownerAndRepo]

        override fun getAllRepositories(): Flow<List<Repository>> = MutableStateFlow(repositories.values.toList())
    }

    private val deviceDao = FakeDeviceDao()
    private val repositoryDao = FakeRepositoryDao()
    private val deviceRepository = DeviceRepository(deviceDao)
    private val updatedCallbackDevices = mutableListOf<Device>()

    private fun infoJson(
        name: String = "WLED Office",
        mac: String? = "001122334455",
        repo: String = "wled/WLED",
    ): String {
        val macField = mac?.let { """"mac": "$it",""" } ?: ""
        return """{ $macField "name": "$name", "ver": "0.15.0", "repo": "$repo", "leds": {}, "wifi": {} }"""
    }

    private fun makeService(engine: RecordingEngine): DeviceFirstContactService = DeviceFirstContactService(
        repositoryDao = repositoryDao,
        repository = deviceRepository,
        deviceApiFactory = DeviceApiFactory(engine.client()),
        onDeviceAddressUpdated = { updatedCallbackDevices += it },
    )

    @Test
    fun fetchAndUpsertDeviceSanitizesAddressAndCreatesNewDeviceWithRepositoryId() = runTest {
        val engine = respondingWith(infoJson(name = "Kitchen", mac = "aabbccddeeff", repo = "custom/fork"))
        val service = makeService(engine)

        val created = service.fetchAndUpsertDevice("http://192.168.1.50//")

        assertEquals("http://192.168.1.50/json/info", engine.lastRequest.url.toString())
        assertEquals("192.168.1.50", created.address)
        assertEquals("Kitchen", created.originalName)
        assertEquals("aabbccddeeff", created.macAddress)
        val expectedRepoId = repositoryDao.getRepositoryByOwnerAndRepo("custom/fork")?.id
        assertEquals(expectedRepoId, created.repositoryId)
        assertEquals(created, deviceRepository.findDeviceByMacAddress("aabbccddeeff"))
        assertTrue(updatedCallbackDevices.isEmpty())
    }

    @Test
    fun fetchAndUpsertDeviceUpdatesAddressWhenExistingAddressIsIp() = runTest {
        deviceRepository.insert(
            Device(macAddress = "001122334455", address = "10.0.0.1", originalName = "WLED Office"),
        )
        val engine = respondingWith(infoJson(name = "WLED Office", mac = "001122334455"))
        val service = makeService(engine)

        val updated = service.fetchAndUpsertDevice("10.0.0.2")

        assertEquals("10.0.0.2", updated.address)
        assertEquals("10.0.0.2", deviceRepository.findDeviceByMacAddress("001122334455")?.address)
        assertEquals(1, deviceDao.updateAddressCalls)
        assertEquals(listOf(updated), updatedCallbackDevices)
    }

    @Test
    fun fetchAndUpsertDevicePreservesExistingHostnameWhenContactedByIp() = runTest {
        deviceRepository.insert(
            Device(macAddress = "001122334455", address = "wled-office.local", originalName = "Old Name"),
        )
        val engine = respondingWith(infoJson(name = "New Name", mac = "001122334455"))
        val service = makeService(engine)

        val updated = service.fetchAndUpsertDevice("192.168.1.50")

        assertEquals("wled-office.local", updated.address)
        assertEquals("New Name", updated.originalName)
        assertEquals(0, deviceDao.updateAddressCalls)
        assertEquals(1, deviceDao.updateOriginalNameCalls)
        assertEquals(listOf(updated), updatedCallbackDevices)
    }

    @Test
    fun fetchAndUpsertDeviceSkipsDatabaseWritesWhenUnchanged() = runTest {
        deviceRepository.insert(
            Device(macAddress = "001122334455", address = "10.0.0.1", originalName = "WLED Office"),
        )
        val engine = respondingWith(infoJson(name = "WLED Office", mac = "001122334455"))
        val service = makeService(engine)

        val result = service.fetchAndUpsertDevice("10.0.0.1")

        assertEquals("10.0.0.1", result.address)
        assertEquals(0, deviceDao.updateAddressCalls)
        assertEquals(0, deviceDao.updateOriginalNameCalls)
        assertEquals(0, deviceDao.updateRepositoryIdCalls)
        assertTrue(updatedCallbackDevices.isEmpty())
    }

    @Test
    fun fetchAndUpsertDeviceThrowsMissingMacAddressWhenMacIsNullOrEmpty() = runTest {
        listOf(null, "").forEach { mac ->
            val engine = respondingWith(infoJson(mac = mac))
            val service = makeService(engine)

            assertFailsWith<DeviceFirstContactException.MissingMacAddress> {
                service.fetchAndUpsertDevice("10.0.0.1")
            }
        }
    }

    @Test
    fun fetchAndUpsertDeviceThrowsHttpErrorOnNonSuccessStatus() = runTest {
        val engine = respondingWith("Server error", status = HttpStatusCode.InternalServerError)
        val service = makeService(engine)

        val error = assertFailsWith<DeviceFirstContactException.HttpError> {
            service.fetchAndUpsertDevice("10.0.0.1")
        }
        assertEquals(500, error.statusCode)
    }

    @Test
    fun fetchAndUpsertDeviceThrowsRequestFailedOnTransportException() = runTest {
        val engine = RecordingEngine { throw FakeTransportException() }
        val service = makeService(engine)

        assertFailsWith<DeviceFirstContactException.RequestFailed> {
            service.fetchAndUpsertDevice("10.0.0.1")
        }
        assertNull(deviceRepository.findDeviceByMacAddress("001122334455"))
    }

    @Test
    fun tryUpdateAddressReturnsFalseForNullEmptyOrUnknownMac() = runTest {
        val engine = respondingWith(infoJson())
        val service = makeService(engine)

        assertFalse(service.tryUpdateAddress(null, "10.0.0.1"))
        assertFalse(service.tryUpdateAddress("", "10.0.0.1"))
        assertFalse(service.tryUpdateAddress("001122334455", "10.0.0.1"))
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun tryUpdateAddressUpdatesIpAddressAndInvokesCallback() = runTest {
        deviceRepository.insert(
            Device(macAddress = "001122334455", address = "10.0.0.1", originalName = "WLED Office"),
        )
        val engine = respondingWith(infoJson())
        val service = makeService(engine)

        val handled = service.tryUpdateAddress("001122334455", "http://10.0.0.2/")

        assertTrue(handled)
        assertEquals("10.0.0.2", deviceRepository.findDeviceByMacAddress("001122334455")?.address)
        assertEquals(1, updatedCallbackDevices.size)
        assertEquals("10.0.0.2", updatedCallbackDevices.single().address)
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun tryUpdateAddressPreservesExistingHostname() = runTest {
        deviceRepository.insert(
            Device(macAddress = "001122334455", address = "wled-office.local", originalName = "WLED Office"),
        )
        val engine = respondingWith(infoJson())
        val service = makeService(engine)

        val handled = service.tryUpdateAddress("001122334455", "10.0.0.2")

        assertTrue(handled)
        assertEquals("wled-office.local", deviceRepository.findDeviceByMacAddress("001122334455")?.address)
        assertEquals(0, deviceDao.updateAddressCalls)
        assertTrue(updatedCallbackDevices.isEmpty())
    }

    @Test
    fun processDiscoveredDeviceUsesFastPathWithoutNetworkCallWhenMacExists() = runTest {
        deviceRepository.insert(
            Device(macAddress = "aabbccddeeff", address = "192.168.1.10", originalName = "WLED Living Room"),
        )
        val engine = respondingWith(infoJson())
        val service = makeService(engine)

        service.processDiscoveredDevice(DiscoveredDevice(address = "192.168.1.50", macAddress = "aabbccddeeff"))

        assertEquals("192.168.1.50", deviceRepository.findDeviceByMacAddress("aabbccddeeff")?.address)
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun processDiscoveredDeviceFallsBackToFetchAndUpsertWhenMacIsUnknownOrNull() = runTest {
        val engine = respondingWith(infoJson(name = "Discovered Lamp", mac = "aabbccddeeff"))
        val service = makeService(engine)

        service.processDiscoveredDevice(DiscoveredDevice(address = "192.168.1.50", macAddress = null))

        val stored = deviceRepository.findDeviceByMacAddress("aabbccddeeff")
        assertEquals("192.168.1.50", stored?.address)
        assertEquals("Discovered Lamp", stored?.originalName)
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun processDiscoveredDeviceSwallowsNetworkErrorsGracefully() = runTest {
        val engine = RecordingEngine { throw FakeTransportException() }
        val service = makeService(engine)

        service.processDiscoveredDevice(rawAddress = "192.168.1.50", macAddress = null)

        assertTrue(deviceDao.devices.isEmpty())
    }
}
