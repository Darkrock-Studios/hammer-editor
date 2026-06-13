package com.darkrockstudios.apps.hammer.common.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

actual class NetworkConnectivity {
	actual suspend fun hasActiveConnection(): Boolean =
		withTimeoutOrNull(PATH_CHECK_TIMEOUT) { awaitFirstPathStatus() } ?: false

	private suspend fun awaitFirstPathStatus(): Boolean = suspendCancellableCoroutine { cont ->
		val monitor = nw_path_monitor_create()
		val queue = dispatch_queue_create("hammer.network-connectivity", null)
		nw_path_monitor_set_queue(monitor, queue)
		nw_path_monitor_set_update_handler(monitor) { path ->
			val connected = nw_path_get_status(path) == nw_path_status_satisfied
			nw_path_monitor_cancel(monitor)
			if (cont.isActive) cont.resume(connected)
		}
		cont.invokeOnCancellation { nw_path_monitor_cancel(monitor) }
		nw_path_monitor_start(monitor)
	}

	private companion object {
		val PATH_CHECK_TIMEOUT = 2.seconds
	}
}
