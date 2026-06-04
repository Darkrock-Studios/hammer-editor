import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/**
 * On-device guard that [EntityHasher] produces the same golden hash on Android (ART) as on the
 * JVM — the cross-platform invariant sync relies on. Uses JUnit4 (not Jupiter) so the
 * AndroidJUnit4 runner can execute it, and assertEquals (not `assert`, which is a no-op when
 * assertions are disabled on-device). Golden vector matches base's EntityHasherTest.
 */
@RunWith(AndroidJUnit4::class)
class HashTest {
	@Test
	fun entityHashMatchesGoldenVector() {
		val hash = EntityHasher.hashNote(
			id = 2,
			created = Instant.fromEpochMilliseconds(0),
			content = "Content",
			tags = emptySet(),
		)

		assertEquals("NKZ2n0XDoHLagRABzkb8Yg", hash)
	}
}
