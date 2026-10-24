package cli

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.desktop.cli.Forwarding
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.notFound
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ForwardingTest {

	@TempDir
	lateinit var directory: File

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	@Serializable
	data class Greeting(val name: String)

	private val registry = OperationRegistry(
		listOf(
			operation<Greeting, Greeting>("greet", "", Access.Read, OperationScope.Content) {
				if (it.name.isBlank()) notFound("Nobody to greet")
				Greeting("Hello, ${it.name}")
			}
		),
		object : ProjectResolver {
			override fun resolve(project: String): ProjectDef = error("unused")
			override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
		},
	)

	private val socket get() = Forwarding.socketPath(directory)

	@AfterEach
	fun tearDown() {
		scope.cancel()
	}

	private val launches = CopyOnWriteArrayList<List<String>>()

	private fun startServer() = Forwarding.Server(socket, { registry }, { launches += it }).also { it.start(scope) }

	@Test
	fun `calls run in the app and come back with their exit code`() {
		startServer().use {
			val (output, exitCode) = Forwarding.dispatch(socket, "greet", buildJsonObject { put("name", "Ada") })!!

			assertEquals(JsonPrimitive("Hello, Ada"), output.jsonObject["name"])
			assertEquals(0, exitCode)
		}
	}

	@Test
	fun `operation errors cross the socket intact`() {
		startServer().use {
			val error = assertThrows<OperationException> {
				Forwarding.dispatch(socket, "greet", buildJsonObject { put("name", "") })
			}
			assertEquals(OperationException.Kind.NotFound, error.kind)
		}
	}

	@Test
	fun `the app keeps its own sync and login, and project moves`() {
		startServer().use {
			assertThrows<Forwarding.Refused> { Forwarding.dispatch(socket, "sync.run", buildJsonObject {}) }
			assertThrows<Forwarding.Refused> { Forwarding.dispatch(socket, "account.logout", buildJsonObject {}) }
			assertThrows<Forwarding.Refused> { Forwarding.dispatch(socket, "project.delete", buildJsonObject {}) }
		}
	}

	@Test
	fun `the socket lives in a directory only this user can enter`() {
		startServer().use {
			assertEquals(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE), Files.getPosixFilePermissions(socket.parent))
		}
	}

	@Test
	fun `a second launch hands its arguments to the app`() {
		startServer().use {
			assertTrue(Forwarding.handOff(socket, listOf("--project", "Storm", "--scene", "3")))
		}
		assertEquals(listOf(listOf("--project", "Storm", "--scene", "3")), launches)
	}

	@Test
	fun `with no app listening a second launch starts as usual`() {
		assertFalse(Forwarding.handOff(socket, listOf("--project", "Storm")))
	}

	@Test
	fun `with no app listening the caller runs headless`() {
		assertNull(Forwarding.dispatch(socket, "greet", buildJsonObject { put("name", "Ada") }))
	}

	@Test
	fun `a socket file left by a crashed app is replaced, and removed on close`() {
		socket.parent.toFile().mkdirs()
		socket.toFile().writeText("stale")

		startServer().use {
			assertEquals(0, Forwarding.dispatch(socket, "greet", buildJsonObject { put("name", "Ada") })!!.second)
		}
		assertEquals(false, socket.toFile().exists())
	}
}
