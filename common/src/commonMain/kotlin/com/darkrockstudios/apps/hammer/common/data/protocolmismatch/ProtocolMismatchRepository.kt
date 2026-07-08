package com.darkrockstudios.apps.hammer.common.data.protocolmismatch

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-scoped signal that a server rejected the client's protocol version (HTTP 426).
 * The HTTP layer emits here from a single choke point; UI observes and surfaces the
 * "update your client" dialog. Conflated with `replay = 1` so a subscriber that
 * connects after the rejection still sees it.
 */
class ProtocolMismatchRepository {
	private val _mismatches = MutableSharedFlow<ProtocolMismatchInfo>(
		extraBufferCapacity = 1,
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val mismatches: SharedFlow<ProtocolMismatchInfo> = _mismatches

	fun notifyMismatch(clientProtocolVersion: Int, serverProtocolVersion: Int?) {
		_mismatches.tryEmit(
			ProtocolMismatchInfo(
				clientProtocolVersion = clientProtocolVersion,
				serverProtocolVersion = serverProtocolVersion,
			)
		)
	}
}

data class ProtocolMismatchInfo(
	val clientProtocolVersion: Int,
	val serverProtocolVersion: Int?,
) {
	/**
	 * True when the client should update: the server is on a newer protocol, or the
	 * direction is unknown (no server version header — the common "update your client"
	 * case). Only a server that is provably older flips this to false.
	 */
	val clientIsBehind: Boolean
		get() = serverProtocolVersion == null || serverProtocolVersion > clientProtocolVersion
}
