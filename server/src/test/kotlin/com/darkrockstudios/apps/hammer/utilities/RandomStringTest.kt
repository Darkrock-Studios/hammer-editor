package com.darkrockstudios.apps.hammer.utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertEquals

class RandomStringTest {

	@Test
	fun `concurrent callers each get their own string`() = runBlocking {
		val generator = RandomString(SYNC_ID_LENGTH, SecureRandom())

		val generated = withContext(Dispatchers.Default) {
			(1..CALLERS).map { async { generator.nextString() } }.awaitAll()
		}

		assertEquals(
			CALLERS,
			generated.toSet().size,
			"One shared generator handed the same string to more than one caller",
		)
		assertEquals(
			emptyList(),
			generated.filter { it.length != SYNC_ID_LENGTH },
			"Every generated string should be exactly $SYNC_ID_LENGTH characters",
		)
	}

	private companion object {
		/** What SyncSessionManager asks for. */
		const val SYNC_ID_LENGTH = 30
		const val CALLERS = 2000
	}
}
