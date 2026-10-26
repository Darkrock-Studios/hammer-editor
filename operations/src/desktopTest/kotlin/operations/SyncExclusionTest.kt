package operations

import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSyncGateRepository
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.KoinProjectResolver
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.core.Backup
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectItemInput
import com.darkrockstudios.apps.hammer.operations.core.SceneAppendInput
import com.darkrockstudios.apps.hammer.operations.core.SceneCreateInput
import com.darkrockstudios.apps.hammer.operations.core.SceneItemRef
import com.darkrockstudios.apps.hammer.operations.core.SceneText
import com.darkrockstudios.apps.hammer.operations.core.SceneTree
import com.darkrockstudios.apps.hammer.operations.core.SceneWordCount
import com.darkrockstudios.apps.hammer.operations.core.coreOperations
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncExclusionTest : KoinOperationsTest() {

	private lateinit var def: ProjectDef

	@BeforeEach
	fun setUp() = onTestThread {
		def = createProject(PROJECT)
		createProject(OTHER)
		Unit
	}

	private fun createProject(name: String): ProjectDef =
		(get<ProjectsRepository>().createProject(name, seedDefaultLanguage = false) as ClientResult.Success<ProjectDef>).data

	private val gate get() = get<ProjectSyncGateRepository>()

	private suspend fun createScene(project: String, name: String = "Opening") =
		run<SceneCreateInput, SceneItemRef>("scene.create", SceneCreateInput(project, name))

	private suspend fun sceneNames(project: String) =
		run<ProjectInput, SceneTree>("scene.tree", ProjectInput(project)).nodes.map { it.name }

	private suspend fun busy(block: suspend () -> Unit) =
		assertEquals(OperationException.Kind.Busy, assertFailsWith<OperationException> { block() }.kind)

	/** A write on [PROJECT] that holds it open until [release] completes, then runs [then]. */
	private fun holdingRegistry(
		started: CompletableDeferred<Unit>,
		release: CompletableDeferred<Unit>,
		then: suspend (OperationRegistry) -> Unit = {},
	): OperationRegistry {
		val hold = operation<ProjectInput, ProjectInput>(
			name = "test.hold",
			description = "Holds a project.",
			access = Access.Write,
			scope = OperationScope.Content,
		) { input ->
			projects.withProject(input.project) {
				started.complete(Unit)
				release.await()
				then(operations)
			}
			input
		}
		return OperationRegistry(coreOperations() + hold, KoinProjectResolver(), syncGate = { get() })
	}

	@Test
	fun `a write is refused while its project syncs, and changes nothing`() = onTestThread {
		gate.withSync(def) {
			busy { createScene(PROJECT) }
		}

		assertEquals(emptyList(), sceneNames(PROJECT))
	}

	@Test
	fun `a write that only names its project is refused while it syncs`() = onTestThread {
		gate.withSync(def) {
			busy { run<ProjectInput, Backup>("backup.create", ProjectInput(PROJECT)) }
		}
	}

	@Test
	fun `writing a scene's prose is refused while its project syncs`() = onTestThread {
		val scene = createScene(PROJECT)

		gate.withSync(def) {
			busy { run<SceneAppendInput, SceneWordCount>("scene.append", SceneAppendInput(PROJECT, scene.id, "Rain.")) }
		}

		assertEquals("", run<ProjectItemInput, SceneText>("scene.read", ProjectItemInput(PROJECT, scene.id)).markdown)
	}

	@Test
	fun `a read runs while its project syncs`() = onTestThread {
		createScene(PROJECT)

		val names = gate.withSync(def) { sceneNames(PROJECT) }

		assertEquals(listOf("Opening"), names)
	}

	@Test
	fun `a write to another project runs while one syncs`() = onTestThread {
		gate.withSync(def) { createScene(OTHER) }

		assertEquals(listOf("Opening"), sceneNames(OTHER))
	}

	@Test
	fun `a write runs once the sync finishes`() = onTestThread {
		gate.withSync(def) { }

		createScene(PROJECT)

		assertEquals(listOf("Opening"), sceneNames(PROJECT))
	}

	@Test
	fun `a sync waits for a write in progress on its project`() = onTestThread {
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val registry = holdingRegistry(started, release)
		var synced = false

		coroutineScope {
			val write = async { registry.call<ProjectInput, ProjectInput>("test.hold", ProjectInput(PROJECT)) }
			started.await()
			val sync = async { gate.withSync(def) { synced = true } }
			repeat(SETTLE) { yield() }
			assertFalse(synced)

			release.complete(Unit)
			write.await()
			sync.await()
		}

		assertTrue(synced)
	}

	@Test
	fun `a sync of another project does not wait for a write`() = onTestThread {
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val registry = holdingRegistry(started, release)
		val other = get<ProjectsRepository>().findProject(OTHER)!!
		var synced = false

		coroutineScope {
			val write = async { registry.call<ProjectInput, ProjectInput>("test.hold", ProjectInput(PROJECT)) }
			started.await()
			gate.withSync(other) { synced = true }
			release.complete(Unit)
			write.await()
		}

		assertTrue(synced)
	}

	@Test
	fun `a write nested in a write is not refused by a sync waiting on it`() = onTestThread {
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val registry = holdingRegistry(started, release) { operations ->
			operations.call<SceneCreateInput, SceneItemRef>("scene.create", SceneCreateInput(PROJECT, "Nested"))
		}

		coroutineScope {
			val write = async { registry.call<ProjectInput, ProjectInput>("test.hold", ProjectInput(PROJECT)) }
			started.await()
			val sync = async { gate.withSync(def) { } }
			repeat(SETTLE) { yield() }

			release.complete(Unit)
			write.await()
			sync.await()
		}

		assertEquals(listOf("Nested"), sceneNames(PROJECT))
	}

	private companion object {
		const val PROJECT = "Storm Novel"
		const val OTHER = "Calm Novel"

		/** Yields enough for anything not blocked to run on the single test thread. */
		const val SETTLE = 20
	}
}
