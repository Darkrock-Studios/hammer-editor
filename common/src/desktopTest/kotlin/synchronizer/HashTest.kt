package synchronizer

import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HashTest {
	@Test
	fun EntityHashTest() {
		val hash = EntityHasher.hashNote(
			id = 1,
			created = Instant.fromEpochSeconds(123),
			content = "this is some tet text",
			tags = emptySet(),
		)

		// Pinned golden value: clients and server must agree on this hash across releases.
		assertEquals("IAvvisZNMehI-2mBnczrnw", hash)
	}
}