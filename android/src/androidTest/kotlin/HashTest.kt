import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class HashTest {
	@Test
	fun EntityHashTest() {
		val hash = EntityHasher.hashNote(
			id = 2,
			created = Instant.fromEpochMilliseconds(0),
			content = "Content",
			tags = emptySet(),
		)

		val expected = "NKZ2n0XDoHLagRABzkb8Yg"
		assert(expected == hash)
	}
}