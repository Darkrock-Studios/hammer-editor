package cli

import com.darkrockstudios.apps.hammer.desktop.cli.WriterLock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class WriterLockTest {

	@TempDir
	lateinit var directory: File

	private fun acquire(holder: WriterLock.Holder, wait: kotlin.time.Duration = 200.milliseconds) =
		WriterLock.acquire(directory, holder, wait)

	@Test
	fun `one holder at a time, and it frees on close`() {
		val first = assertIs<WriterLock.Result.Acquired>(acquire(WriterLock.Holder.Cli))
		assertEquals(WriterLock.Holder.Cli, assertIs<WriterLock.Result.Busy>(acquire(WriterLock.Holder.Cli)).holder)

		first.lock.close()
		assertIs<WriterLock.Result.Acquired>(acquire(WriterLock.Holder.Cli)).lock.close()
	}

	@Test
	fun `nobody waits on the app`() {
		val app = assertIs<WriterLock.Result.Acquired>(acquire(WriterLock.Holder.App))

		val waited = measureTime {
			assertEquals(WriterLock.Holder.App, assertIs<WriterLock.Result.Busy>(acquire(WriterLock.Holder.Cli, 5.seconds)).holder)
		}
		assert(waited < 1.seconds) { "Waited $waited" }
		app.lock.close()
	}
}
