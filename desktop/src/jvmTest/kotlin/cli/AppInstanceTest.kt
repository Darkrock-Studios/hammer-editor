package cli

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.desktop.cli.AppInstance
import com.darkrockstudios.apps.hammer.desktop.cli.Forwarding
import com.darkrockstudios.apps.hammer.desktop.cli.HeadlessSession
import com.darkrockstudios.apps.hammer.desktop.cli.WriterLock
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * App windows and CLI calls sharing one config directory. Each "process" is mostly an object here:
 * this JVM refuses a lock that overlaps one another channel in it holds, much as another process
 * would, even a shared one. The real thing differs (POSIX locks are per process), so one test runs
 * the app in a process of its own.
 */
class AppInstanceTest {

	@TempDir
	lateinit var directory: File

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val launches = CopyOnWriteArrayList<List<String>>()
	private val open = mutableListOf<AutoCloseable>()

	@Serializable
	data class Greeting(val name: String)

	private val holding = CompletableDeferred<Unit>()
	private val release = CompletableDeferred<Unit>()

	private val registry = OperationRegistry(
		listOf(
			operation<Greeting, Greeting>("greet", "", Access.Read, OperationScope.Content) { Greeting("Hello, ${it.name}") },
			operation<Greeting, Greeting>("hold", "", Access.Write, OperationScope.Content) {
				holding.complete(Unit)
				release.await()
				it
			},
		),
		object : ProjectResolver {
			override fun resolve(project: String): ProjectDef = error("unused")
			override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
		},
	)

	@AfterEach
	fun tearDown() {
		open.asReversed().forEach { it.close() }
		scope.cancel()
	}

	private fun app(handOffWait: Duration = 2.seconds, retry: Duration = 50.milliseconds, readyWait: Duration = 30.seconds) =
		AppInstance(directory, { registry }, { launches += it }, handOffWait, retry, readyWait).also { open += it }

	/** A CLI call: "Hello, Ada" when the app ran it, "headless" when it ran under the writer lock instead. */
	private fun call(wait: Duration = 2.seconds): String =
		Forwarding.route(directory, "greet", buildJsonObject { put("name", "Ada") }, wait) {
			runBlocking { HeadlessSession.withWriterLock(directory) { buildJsonObject { put("name", "headless") } to 0 } }
		}.first.jsonObject.getValue("name").jsonPrimitive.content

	/**
	 * An app window that holds the app lock but never answers on the socket. Only the app lock: in one
	 * JVM a second channel cannot share the window lock with the window under test.
	 */
	private fun hungApp(): AutoCloseable {
		val lock = assertNotNull(AppInstance.lockApp(directory))
		return AutoCloseable { lock.channel().close() }.also { open += it }
	}

	/**
	 * An app window in a process of its own, serving [AppProcess]'s "greet", so its locks are real OS
	 * locks rather than ones this JVM tracks per channel. It closes with its stdin.
	 */
	private fun appProcess(): Process {
		val java = File(System.getProperty("java.home"), "bin/java").path
		val process = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), AppProcess::class.java.name, directory.path)
			.redirectError(ProcessBuilder.Redirect.INHERIT)
			.start()
		open += AutoCloseable { process.destroyForcibly().waitFor() }
		assertEquals(AppProcess.READY, process.inputStream.bufferedReader().readLine())
		return process
	}

	@Test
	fun `with no app open, calls run headless`() {
		assertEquals("headless", call())
	}

	@Test
	fun `an app opened during a long CLI call takes every call after it`() {
		val longCall = assertIs<WriterLock.Result.Acquired>(WriterLock.acquire(directory, WriterLock.Holder.Cli)).lock
		val app = app()
		assertTrue(app.open(scope, emptyList()))
		app.started()

		val next = scope.async { call() }
		Thread.sleep(300)
		assertFalse(next.isCompleted, "The next call must wait for the app, not run beside it")
		longCall.close()

		assertEquals("Hello, Ada", runBlocking { withTimeout(5.seconds) { next.await() } })
		assertEquals("Hello, Ada", call())
	}

	@Test
	fun `an app in another process that opened during a long CLI call takes every call after it`() {
		val longCall = assertIs<WriterLock.Result.Acquired>(WriterLock.acquire(directory, WriterLock.Holder.Cli)).lock
		val app = appProcess()

		val next = scope.async { call() }
		Thread.sleep(1000)
		assertFalse(next.isCompleted, "The next call must wait for the app, not run beside it")
		assertTrue(AppInstance.isOpen(directory), "The app must keep its locks while it waits for the writer lock")
		longCall.close()
		assertEquals("Hello, Ada", runBlocking { withTimeout(5.seconds) { next.await() } })

		app.outputStream.close()
		assertTrue(app.waitFor(10, TimeUnit.SECONDS))
		assertEquals("headless", call())
	}

	@Test
	fun `a second launch while the first is still starting is handed to it`() {
		assertTrue(app().open(scope, emptyList()))

		assertFalse(app().open(scope, listOf("--project", "Storm")))
		assertEquals(listOf(listOf("--project", "Storm")), launches)
	}

	@Test
	fun `a call while the app is still starting waits for it`() {
		val app = app()
		assertTrue(app.open(scope, emptyList()))

		val call = scope.async { call() }
		Thread.sleep(300)
		app.started()

		assertEquals("Hello, Ada", runBlocking { withTimeout(5.seconds) { call.await() } })
	}

	@Test
	fun `an app that never answers is refused, never run headless under`() {
		hungApp()

		assertThrows<Forwarding.Unanswered> { call(wait = 300.milliseconds) }
	}

	@Test
	fun `once the app closes, calls run headless again`() {
		val app = app()
		assertTrue(app.open(scope, emptyList()))
		app.started()
		assertEquals("Hello, Ada", call())

		app.close()
		assertEquals("headless", call())
	}

	@Test
	fun `a window that could not hand off takes over when the first closes`() {
		val first = hungApp()
		val second = app(handOffWait = 300.milliseconds)
		assertTrue(second.open(scope, emptyList()))
		second.started()

		first.close()
		assertEquals("Hello, Ada", call(wait = 5.seconds))
	}

	@Test
	fun `a window that could not hand off is never run headless under, even before it takes over`() {
		val first = hungApp()
		val second = app(handOffWait = Duration.ZERO, retry = 10.seconds)
		assertTrue(second.open(scope, emptyList()))
		second.started()

		// Its first try at the app lock fails; the next is far off.
		Thread.sleep(300)

		first.close()
		assertNotEquals("headless", runCatching { call(wait = 300.milliseconds) }.getOrNull())
	}

	@Test
	fun `a call waiting on a long CLI call is refused in time, not left hanging`() {
		val longCall = assertIs<WriterLock.Result.Acquired>(WriterLock.acquire(directory, WriterLock.Holder.Cli)).lock
		open += longCall
		val app = app(readyWait = 300.milliseconds)
		assertTrue(app.open(scope, emptyList()))
		app.started()

		val call = scope.async { runCatching { call() } }
		val result = runBlocking { withTimeout(5.seconds) { call.await() } }
		assertIs<Forwarding.Unanswered>(result.exceptionOrNull())
	}

	@Test
	fun `closing waits for a call in flight before a headless call can start`() {
		val app = app()
		assertTrue(app.open(scope, emptyList()))
		app.started()
		val inFlight = scope.async {
			Forwarding.dispatch(Forwarding.socketPath(directory), "hold", buildJsonObject { put("name", "Ada") })
		}
		runBlocking { withTimeout(5.seconds) { holding.await() } }

		val closing = scope.async { app.close() }
		Thread.sleep(300)
		assertTrue(AppInstance.isOpen(directory), "The app must keep its locks while its call writes")

		release.complete(Unit)
		runBlocking { withTimeout(5.seconds) { closing.await(); inFlight.await() } }
		assertFalse(AppInstance.isOpen(directory))
	}
}
