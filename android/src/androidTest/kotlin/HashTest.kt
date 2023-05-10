import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HashTest {
	@Test
	fun EntityHashTest() {
		val hash = EntityHasher.hashNote(
			id = 1,
			created = Instant.fromEpochSeconds(123),
			content = "this is some tet text"
		)

		println("hash: $hash")

		val expected = "IAvvisZNMehI-2mBnczrnw"
		assert(expected == hash)
	}
}