import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertTrue

/**
 * Installs the word count plugin built by hammer-plugins' `kotlin/wordcount/package.sh`, loads it as a
 * restarted app would, and exports through it. Runs when HAMMER_PLUGINS points at that checkout.
 */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class RuntimePluginEndToEndTest {

	private val fileSystem = FakeFileSystem()
	private val directory = "/config/plugins".toPath()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	@Serializable
	data class ProjectInput(val project: String)

	@Serializable
	data class Stats(val totalWords: Int)

	@Test
	fun `a packaged Kotlin plugin installs, loads, and exports`() {
		val built = File(System.getenv("HAMMER_PLUGINS"), "kotlin/wordcount/build/wordcount.hammerplugin")
		check(built.exists()) { "Run kotlin/wordcount/package.sh in hammer-plugins first" }
		val download = "/downloads/wordcount.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }

		RuntimePlugins(fileSystem, directory, emptySet()).install(download)

		// A fresh instance, as after a restart.
		val registry = PluginRegistry(emptyList()).also(RuntimePlugins(fileSystem, directory, emptySet())::activate)
		val stats = operation<ProjectInput, Stats>("stats.project", "", Access.Read, OperationScope.Content) { Stats(totalWords = 42) }
		GlobalContext.startKoin {
			modules(
				listOf(
					module {
						single<FileSystem> { fileSystem }
						single<Toml> { createTomlSerializer() }
						single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
						single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
						single { StoryExporterRegistry(getAll(), getAll()) }
					}
				) + registry.koinModules() + module {
					// After the registry's modules, so it replaces the core operations with this one.
					single { OperationRegistry(listOf(stats), NoProjects) }
				}
			)
		}
		registry.settings("wordcount")!!.set("perScene", JsonPrimitive(true))

		val exporter = GlobalContext.get().get<StoryExporterRegistry>().forFormat("wordcount.txt")
		val sink = Buffer()
		exporter.render(
			sink,
			ExportInput(
				projectName = "Storm",
				projectData = null,
				chapters = listOf(
					StoryChapter("Chapter One", listOf("The storm came early.", "Alice ran.")),
					StoryChapter("Chapter Two", listOf("It passed.")),
				),
				treatTopLevelAsChapters = true,
				language = "en",
				strings = ExportStrings(contentsTitle = "Contents", authorByline = null),
			),
		)
		val report = sink.readUtf8()
		println(report)

		assertTrue("Chapter One: 6 words" in report)
		assertTrue("  Scene 2: 2" in report)
		assertTrue("Total: 8 words" in report)
		assertTrue("Hammer's statistics: 42 words" in report)
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
