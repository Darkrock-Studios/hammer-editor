package operations

import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.dependencyinjection.RAW_FILESYSTEM
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The headless CLI starts and stops Koin once per call. This runs that cycle repeatedly against the
 * app's real Koin graph, and fails if any work outlives a stop or breaks the next start.
 */
class HeadlessRestartTest : KoinComponent {

	private val ffs = FakeFileSystem()
	private val executor = Executors.newSingleThreadExecutor()
	private val thread = executor.asCoroutineDispatcher()
	private val strRes = mockk<StrRes>(relaxed = true)
	private val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
	private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
		executor.shutdownNow()
		Thread.setDefaultUncaughtExceptionHandler(previousHandler)
	}

	private fun startKoin(appScope: CoroutineScope) {
		val overrides = module {
			single<FileSystem> { ffs }
			single(named(RAW_FILESYSTEM)) { ffs } bind FileSystem::class
			single { strRes }
			single<CoroutineContext>(named(DISPATCHER_MAIN)) { thread }
			single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { thread }
			single<CoroutineContext>(named(DISPATCHER_IO)) { thread }
			single(named(APP_SCOPE)) { appScope }
		}
		GlobalContext.startKoin {
			allowOverride(true)
			modules(listOf(mainModule, overrides) + PluginRegistry(emptyList()).koinModules())
		}
	}

	private fun <T> onThread(block: suspend () -> T): T = runBlocking(thread) { block() }

	private suspend fun dispatch(name: String, input: JsonObject) = get<OperationRegistry>().dispatch(name, input)

	@Test
	fun `Koin stops and starts cleanly between headless calls`() {
		Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
		ffs.createDirectories(getDefaultRootDocumentDirectory().toPath())
		val project = buildJsonObject { put("project", PROJECT) }

		repeat(CYCLES) { cycle ->
			val appScope = CoroutineScope(SupervisorJob() + thread)
			startKoin(appScope)
			onThread {
				get<DataMigrator>().handleDataMigration()
				if (cycle == 0) seed()

				val projects = dispatch("project.list", JsonObject(emptyMap())).jsonObject
				assertEquals(1, projects["projects"]!!.jsonArray.size)
				assertEquals(1, dispatch("scene.tree", project).jsonObject["nodes"]!!.jsonArray.size)
				assertEquals(1, dispatch("note.list", project).jsonObject["notes"]!!.jsonArray.size)
				val stats = dispatch("stats.project", buildJsonObject {
					put("project", PROJECT)
					put("recalculate", true)
				}).jsonObject
				assertEquals(JsonPrimitive(4), stats["totalWords"])
			}
			appScope.cancel()
			GlobalContext.stopKoin()
			// Anything still queued runs now, with Koin gone: exactly what a leak would do.
			executor.submit {}.get(5, TimeUnit.SECONDS)
		}

		assertTrue(uncaught.isEmpty(), "Work outlived Koin: $uncaught")
		assertNull(GlobalContext.getOrNull())
	}

	private suspend fun seed() {
		val created = get<ProjectsRepository>().createProject(PROJECT, seedDefaultLanguage = false)
		val def = (created as ClientResult.Success<ProjectDef>).data
		temporaryProjectTask(def) { scope ->
			val scenes = scope.get<SceneEditorService>()
			val scene = scenes.createScene(null, "Opening")!!
			scenes.storeSceneMarkdownRaw(SceneContent(scene, markdown = "The storm came early."))
			scope.get<NotesRepository>().createNote("Check the tide tables")
		}
	}

	private companion object {
		const val PROJECT = "Headless"
		const val CYCLES = 5
	}
}
