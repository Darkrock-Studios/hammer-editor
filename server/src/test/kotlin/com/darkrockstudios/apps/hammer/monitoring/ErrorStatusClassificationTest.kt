package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.plugins.UnsupportedProtocolVersionException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import korlibs.io.lang.InvalidArgumentException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ErrorStatusClassificationTest {

	@Test
	fun `protocol-version rejections carry their own 426 status`() {
		assertEquals(426, UnsupportedProtocolVersionException(null, 1).toMonitoredStatus())
	}

	@Test
	fun `generic library exceptions are treated as server faults`() {
		// Only our own status-carrying exceptions map to 4xx; broad library types
		// stay 500 so genuine server bugs aren't hidden as client errors.
		assertEquals(500, InvalidArgumentException("bad arg").toMonitoredStatus())
		assertEquals(500, IllegalArgumentException("bad arg").toMonitoredStatus())
		assertEquals(500, BadRequestException("bad body").toMonitoredStatus())
		assertEquals(500, NotFoundException("missing").toMonitoredStatus())
	}

	@Test
	fun `genuine server faults stay 500`() {
		assertEquals(500, NullPointerException("oops").toMonitoredStatus())
		assertEquals(500, IllegalStateException("bad state").toMonitoredStatus())
	}
}
