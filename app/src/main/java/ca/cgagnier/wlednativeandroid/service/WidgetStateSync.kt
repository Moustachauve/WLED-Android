package ca.cgagnier.wlednativeandroid.service

import android.content.Context
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.WidgetSnapshot
import ca.cgagnier.wlednativeandroid.model.toWidgetSnapshot
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "WidgetStateSync"
private const val WIDGET_TIMER_REFRESH_INTERVAL_MS = 30_000L
private val logger = Logger.withTag(TAG)

/**
 * App-scoped service that observes [DeviceConnectionManager.allDevicesWithState],
 * projects connected devices into [WidgetSnapshot]s, and pushes updates to Glance widgets
 * when visible widget properties change or when the widget timer needs a periodic refresh.
 */
class WidgetStateSync(
    private val context: Context,
    private val connectionManager: DeviceConnectionManager,
    private val widgetManager: WledWidgetManager,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    private val knownMacs = ConcurrentHashMap.newKeySet<String>()
    private val lastSnapshots = ConcurrentHashMap<String, WidgetSnapshot>()
    private var syncJob: Job? = null

    fun start() {
        if (syncJob != null) return
        val defaultName = context.getString(R.string.default_device_name)
        syncJob = coroutineScope.launch {
            connectionManager.allDevicesWithState.collect { devices ->
                val currentMacs = devices.map { it.device.macAddress }.toSet()

                // 1. Clean up widgets for removed devices (including offline devices)
                val removedMacs = knownMacs - currentMacs
                for (mac in removedMacs) {
                    knownMacs.remove(mac)
                    lastSnapshots.remove(mac)
                    try {
                        withContext(ioDispatcher) {
                            widgetManager.deleteWidgetsForDevice(context, mac)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        logger.e(e) { "Failed to delete widgets for $mac" }
                    }
                }
                knownMacs.addAll(currentMacs)

                // 2. Update widgets for actively connected devices if snapshot changed or timer expired
                val now = timeProvider()
                for (device in devices) {
                    val mac = device.device.macAddress
                    if (!device.isOnline || device.stateInfo == null) {
                        // Evict cached snapshot so reconnecting will always push a fresh update and timer reset,
                        // without marking widgets offline when WebSockets pause or briefly reconnect.
                        lastSnapshots.remove(mac)
                        continue
                    }
                    val snapshot = device.toWidgetSnapshot(
                        defaultName = defaultName,
                        now = now,
                    )
                    val prev = lastSnapshots[mac]
                    if (prev == null || shouldUpdateWidget(prev, snapshot)) {
                        lastSnapshots[mac] = snapshot
                        try {
                            withContext(ioDispatcher) {
                                widgetManager.updateWidgetsFromSnapshot(context, snapshot)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                            logger.e(e) { "Failed to update widgets for $mac" }
                        }
                    }
                }
            }
        }
    }

    private fun shouldUpdateWidget(prev: WidgetSnapshot, next: WidgetSnapshot): Boolean =
        hasVisibleChanges(prev, next) ||
            next.lastUpdated - prev.lastUpdated >= WIDGET_TIMER_REFRESH_INTERVAL_MS

    private fun hasVisibleChanges(a: WidgetSnapshot, b: WidgetSnapshot): Boolean = a.isOn != b.isOn ||
        a.color != b.color ||
        a.name != b.name ||
        a.address != b.address ||
        a.isOnline != b.isOnline

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        lastSnapshots.clear()
    }
}
