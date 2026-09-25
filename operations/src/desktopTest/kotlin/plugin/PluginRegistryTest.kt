package plugin

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectLifecycleListener
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectDictionaryService
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.NoInput
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.ProjectPluginContext
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.peanuuutz.tomlkt.Toml
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.component.KoinComponent
import org.koin.core.component.getScopeId
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PluginRegistryTest : KoinComponent {

	private val fileSystem = FakeFileSystem()
	private val projectDef = ProjectDef("Test Project", "/projects/Test Project".toPath().toHPath())

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
		fileSystem.checkNoOpenFiles()
	}

	private fun startKoin(registry: PluginRegistry, dispatcher: CoroutineContext = Dispatchers.Unconfined) {
		val base = module {
			single<FileSystem> { fileSystem }
			single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { dispatcher }
			single<CoroutineContext>(named(DISPATCHER_IO)) { dispatcher }
			single<Toml> { createTomlSerializer() }
			single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
			single { StoryExporterRegistry(getAll(), getAll()) }
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped<SceneEditorService> { mockk(relaxed = true) }
				scoped<TimeLineRepository> { mockk(relaxed = true) }
				scoped<ProjectDictionaryService> { mockk(relaxed = true) }
			}
		}
		GlobalContext.startKoin { modules(listOf(base) + registry.koinModules()) }
	}

	private fun scope() = getKoin().getScope(ProjectDefScope(projectDef).getScopeId())

	@Test
	fun `rejects ids that are not lowercase and directory-safe`() {
		assertThrows<IllegalArgumentException> { PluginRegistry(listOf(RecordingPlugin("Bad Id"))) }
		assertThrows<IllegalArgumentException> { PluginRegistry(listOf(RecordingPlugin("../escape"))) }
	}

	@Test
	fun `rejects duplicate ids`() {
		assertThrows<IllegalArgumentException> {
			PluginRegistry(listOf(RecordingPlugin("same"), RecordingPlugin("same")))
		}
	}

	@Test
	fun `installs plugin modules and binds itself as a lifecycle listener`() {
		val registry = PluginRegistry(listOf(RecordingPlugin("a", module { single { Marker } })))
		startKoin(registry)

		assertSame(Marker, getKoin().get<Marker>())
		assertSame(registry, getKoin().getAll<ProjectLifecycleListener>().single())
	}

	@Test
	fun `plugin export formats join the exporter registry`() {
		val exporter = FakeExporter("recorder.txt")
		val registry = PluginRegistry(listOf(RecordingPlugin("recorder", exporters = listOf(exporter))))
		startKoin(registry)

		assertSame(exporter, getKoin().get<StoryExporterRegistry>().forFormat("recorder.txt"))
	}

	@Test
	fun `added plugins join and leave the exporters while running`() {
		val registry = PluginRegistry(emptyList())
		startKoin(registry)
		val exporters = getKoin().get<StoryExporterRegistry>()

		registry.add(RecordingPlugin("recorder", exporters = listOf(FakeExporter("recorder.txt"))))
		assertTrue(exporters.isRegistered("recorder.txt"))
		assertEquals(listOf("recorder"), registry.active.value.map { it.id })

		registry.remove("recorder")
		assertFalse(exporters.isRegistered("recorder.txt"))
	}

	@Test
	fun `adding a plugin with an id already added replaces it, but compiled-in plugins stay`() {
		val compiled = RecordingPlugin("compiled")
		val registry = PluginRegistry(listOf(compiled))
		val replacement = RecordingPlugin("added")
		registry.add(RecordingPlugin("added"))
		registry.add(replacement)

		assertEquals(listOf(compiled, replacement), registry.plugins)
		assertThrows<IllegalArgumentException> { registry.add(RecordingPlugin("compiled")) }
		registry.remove("compiled")
		assertEquals(listOf(compiled, replacement), registry.plugins)
	}

	@Test
	fun `a replaced plugin gets a settings store for its own declarations`() {
		val registry = PluginRegistry(emptyList())
		startKoin(registry)
		registry.add(SettingsPlugin("tuned", SettingDeclaration.Toggle("loud", "Loud", defaultValue = false)))
		val first = registry.settings("tuned")!!
		assertSame(first, registry.settings("tuned"))

		registry.add(SettingsPlugin("tuned", SettingDeclaration.Toggle("quiet", "Quiet", defaultValue = true)))
		assertEquals(listOf("quiet"), registry.settings("tuned")!!.declarations.map { it.key })

		registry.remove("tuned")
		assertNull(registry.settings("tuned"))
	}

	@Test
	fun `added plugins cannot bring operations or Koin modules`() {
		val registry = PluginRegistry(emptyList())
		val op = operation<NoInput, NoInput>("recorder.ping", "", Access.Read, OperationScope.Content) { NoInput }

		assertThrows<IllegalArgumentException> { registry.add(RecordingPlugin("recorder", operations = listOf(op))) }
		assertThrows<IllegalArgumentException> { registry.validate(RecordingPlugin("recorder", module { })) }
		assertTrue(registry.plugins.isEmpty())
	}

	@Test
	fun `rejects export formats not prefixed with the plugin id`() {
		assertThrows<IllegalArgumentException> {
			PluginRegistry(listOf(RecordingPlugin("recorder", exporters = listOf(FakeExporter("txt")))))
		}
	}

	@Test
	fun `plugin operations join the core operations`() {
		val op = operation<NoInput, NoInput>("recorder.ping", "", Access.Read, OperationScope.Content) { NoInput }
		val registry = PluginRegistry(listOf(RecordingPlugin("recorder", operations = listOf(op))))
		startKoin(registry)

		val operations = getKoin().get<OperationRegistry>()
		assertSame(op, operations.find("recorder.ping"))
		assertNotNull(operations.find("scene.read"))
	}

	@Test
	fun `rejects operations not prefixed with the plugin id`() {
		val op = operation<NoInput, NoInput>("scene.ping", "", Access.Read, OperationScope.Content) { NoInput }
		assertThrows<IllegalArgumentException> {
			PluginRegistry(listOf(RecordingPlugin("recorder", operations = listOf(op))))
		}
	}

	@Test
	fun `rejects malformed operation names at construction`() {
		val op = operation<NoInput, NoInput>("recorder.Ping", "", Access.Read, OperationScope.Content) { NoInput }
		assertThrows<IllegalArgumentException> {
			PluginRegistry(listOf(RecordingPlugin("recorder", operations = listOf(op))))
		}
	}

	@Test
	fun `a plugin that throws on start does not stop the others`() {
		val failing = RecordingPlugin("failing", failOnStart = true)
		val healthy = RecordingPlugin("healthy")
		val registry = PluginRegistry(listOf(failing, healthy))
		startKoin(registry)

		registry.start()

		assertTrue(healthy.started)
	}

	@Test
	fun `opening and closing a project for editing reaches every plugin`() = runTest {
		val plugin = RecordingPlugin("recorder")
		val registry = PluginRegistry(listOf(plugin))
		startKoin(registry)

		openProjectScope(projectDef)
		val opened = assertNotNull(plugin.opened)
		assertEquals(projectDef, opened.projectDef)
		assertSame(opened, registry.contextFor("recorder", projectDef))

		closeProjectScope(scope(), projectDef)
		assertSame(opened, plugin.closed)
		assertFalse(opened.coroutineScope.isActive)
		assertNull(registry.contextFor("recorder", projectDef))
	}

	@Test
	fun `temporary project tasks are not reported`() = runTest {
		val plugin = RecordingPlugin("recorder")
		startKoin(PluginRegistry(listOf(plugin)))

		temporaryProjectTask(projectDef) {}

		assertNull(plugin.opened)
		assertNull(plugin.closed)
	}

	@Test
	fun `data directory is created under the project on first use`() = runTest {
		val plugin = RecordingPlugin("recorder")
		startKoin(PluginRegistry(listOf(plugin)))
		openProjectScope(projectDef)

		val dir = assertNotNull(plugin.opened).dataDirectory()

		assertEquals("/projects/Test Project/.plugins/recorder", dir.path)
		assertTrue(fileSystem.exists(dir.path.toPath()))
		closeProjectScope(scope(), projectDef)
	}

	@Test
	fun `closing a project waits for the plugin's project work to stop`() = runTest {
		var stopped = false
		val plugin = RecordingPlugin("worker", onOpened = { context ->
			context.coroutineScope.launch {
				try {
					awaitCancellation()
				} finally {
					withContext(NonCancellable) { delay(100) }
					stopped = true
				}
			}
		})
		startKoin(PluginRegistry(listOf(plugin)), dispatcher = Dispatchers.Default)
		openProjectScope(projectDef)

		closeProjectScope(scope(), projectDef)

		assertTrue(stopped)
	}

	@Test
	fun `an editor adopting a temporary task's scope keeps plugins open after the task ends`() = runTest {
		val plugin = RecordingPlugin("recorder")
		startKoin(PluginRegistry(listOf(plugin)))

		temporaryProjectTask(projectDef) { openProjectScope(projectDef) }

		assertNotNull(plugin.opened)
		assertNull(plugin.closed)
		closeProjectScope(scope(), projectDef)
		assertNotNull(plugin.closed)
	}

	@Test
	fun `plugins stay open until the last editor of a project closes`() = runTest {
		val plugin = RecordingPlugin("recorder")
		startKoin(PluginRegistry(listOf(plugin)))
		openProjectScope(projectDef)
		openProjectScope(projectDef)

		closeProjectScope(scope(), projectDef)
		assertNull(plugin.closed)

		closeProjectScope(scope(), projectDef)
		assertNotNull(plugin.closed)
	}

	private object Marker

	private class FakeExporter(override val formatId: String) : StoryExporter {
		override val fileExtension = "txt"
		override val mimeType = "text/plain"
		override fun render(sink: BufferedSink, input: ExportInput) = Unit
	}

	private class SettingsPlugin(override val id: String, private val declaration: SettingDeclaration) : ClientPlugin {
		override fun settings() = listOf(declaration)
	}

	private class RecordingPlugin(
		override val id: String,
		private val module: Module? = null,
		private val failOnStart: Boolean = false,
		private val onOpened: (ProjectPluginContext) -> Unit = {},
		private val exporters: List<StoryExporter> = emptyList(),
		private val operations: List<Operation<*, *>> = emptyList(),
	) : ClientPlugin {
		var started = false
		var opened: ProjectPluginContext? = null
		var closed: ProjectPluginContext? = null

		override fun koinModule(): Module? = module

		override fun exporters(): List<StoryExporter> = exporters

		override fun operations(): List<Operation<*, *>> = operations

		override fun onAppStart(appScope: CoroutineScope) {
			check(!failOnStart) { "boom" }
			started = true
		}

		override fun onProjectOpened(project: ProjectPluginContext) {
			opened = project
			onOpened(project)
		}

		override fun onProjectClosed(project: ProjectPluginContext) {
			closed = project
		}
	}
}
