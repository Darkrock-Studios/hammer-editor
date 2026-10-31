package com.darkrockstudios.apps.hammer.common.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
	val size = length.toInt()
	val out = ByteArray(size)
	if (size == 0) return out
	out.usePinned { pinned ->
		memcpy(pinned.addressOf(0), bytes, length.convert())
	}
	return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData {
	if (isEmpty()) return NSData()
	return usePinned { pinned ->
		NSData.create(bytes = pinned.addressOf(0), length = size.convert())
	}
}
