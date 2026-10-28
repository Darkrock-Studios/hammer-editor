package components.images

import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagePickRequestsTest {

	private val entry = ImagePickTarget.EntryImage(entryId = 3)
	private val cover = ImagePickTarget.ProjectCover

	@Test
	fun `pick asks for the gallery and returns what it delivers`() = runTest(UnconfinedTestDispatcher()) {
		val bus = ImagePickRequests()
		val requested = mutableListOf<ImagePickTarget>()
		val listener = launch { bus.requests.collect { requested += it } }

		val picked = async { bus.pick(entry) }
		assertEquals(listOf<ImagePickTarget>(entry), requested)
		assertFalse(picked.isCompleted)

		bus.deliver(entry, 12)

		assertEquals(12, picked.await())
		listener.cancel()
	}

	@Test
	fun `a cancelled pick returns null`() = runTest(UnconfinedTestDispatcher()) {
		val bus = ImagePickRequests()
		val picked = async { bus.pick(entry) }

		bus.deliver(entry, null)

		assertNull(picked.await())
	}

	@Test
	fun `a result for another target is not taken`() = runTest(UnconfinedTestDispatcher()) {
		val bus = ImagePickRequests()
		val picked = async { bus.pick(entry) }

		bus.deliver(cover, 4)
		bus.deliver(ImagePickTarget.EntryImage(entryId = 9), 5)
		assertFalse(picked.isCompleted)

		bus.deliver(entry, 6)
		assertEquals(6, picked.await())
	}

	@Test
	fun `a restored pick waits without asking again`() = runTest(UnconfinedTestDispatcher()) {
		val bus = ImagePickRequests()
		val requested = mutableListOf<ImagePickTarget>()
		val listener = launch { bus.requests.collect { requested += it } }

		val restored = async(start = CoroutineStart.UNDISPATCHED) { bus.awaitResult(entry) }
		bus.deliver(entry, 8)

		assertEquals(8, restored.await())
		assertTrue(requested.isEmpty())
		listener.cancel()
	}
}
