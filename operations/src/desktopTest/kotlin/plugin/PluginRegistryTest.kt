package plugin

import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.cli.CliCommand
import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import com.darkrockstudios.apps.hammer.operations.cli.Dispatcher
import com.darkrockstudios.apps.hammer.operations.plugin.ClientPlugin
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.peanuuutz.tomlkt.Toml
import okio.BufferedSink
import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PluginRegistryTest : KoinComponent {

	private val fileSystem = FakeFileSystem()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private fun startKoin(registry: PluginRegistry) {
		val base = module {
			single<FileSystem> { fileSystem }
			single<Toml> { createTomlSerializer() }
			single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
			single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
			single { StoryExporterRegistry(getAll(), getAll()) }
		}
		GlobalContext.startKoin { modules(base, registry.koinModule()) }
	}

	@Test
	fun `refuses ids that are not lowercase and directory-safe`() {
		assertThrows<IllegalArgumentException> { PluginRegistry().add(FakePlugin("Bad Id")) }
		assertThrows<IllegalArgumentException> { PluginRegistry().add(FakePlugin("../escape")) }
	}

	@Test
	fun `plugins join and leave the exporters while running`() {
		val registry = PluginRegistry()
		startKoin(registry)
		val exporters = getKoin().get<StoryExporterRegistry>()

		registry.add(FakePlugin("recorder", exporters = listOf(FakeExporter("recorder.txt"))))
		assertTrue(exporters.isRegistered("recorder.txt"))
		assertEquals(listOf("recorder"), registry.active.value.map { it.id })

		registry.remove("recorder")
		assertFalse(exporters.isRegistered("recorder.txt"))
	}

	@Test
	fun `refuses export formats not prefixed with the plugin id`() {
		assertThrows<IllegalArgumentException> { PluginRegistry().add(FakePlugin("recorder", exporters = listOf(FakeExporter("txt")))) }
	}

	@Test
	fun `adding a plugin with an id already added replaces it`() {
		val registry = PluginRegistry()
		val replacement = FakePlugin("added")
		registry.add(FakePlugin("added"))
		registry.add(replacement)

		assertEquals(listOf(replacement), registry.plugins)
	}

	@Test
	fun `refuses a command another plugin has, or one that shadows operations`() {
		val registry = PluginRegistry()
		registry.add(FakePlugin("one", commands = listOf(FakeCommand("echo"))))

		assertThrows<IllegalArgumentException> { registry.add(FakePlugin("two", commands = listOf(FakeCommand("echo")))) }
		assertThrows<IllegalArgumentException> { registry.validate(FakePlugin("two", commands = listOf(FakeCommand("scene")))) }
		assertEquals(listOf("one"), registry.plugins.map { it.id })
		assertEquals(listOf("echo"), registry.cliCommands.map { it.name })
	}

	@Test
	fun `a replaced plugin gets a settings store for its own declarations`() {
		val registry = PluginRegistry()
		startKoin(registry)
		registry.add(FakePlugin("tuned", settings = listOf(SettingDeclaration.Toggle("loud", "Loud", defaultValue = false))))
		val first = registry.settings("tuned")!!
		assertSame(first, registry.settings("tuned"))

		registry.add(FakePlugin("tuned", settings = listOf(SettingDeclaration.Toggle("quiet", "Quiet", defaultValue = true))))
		assertEquals(listOf("quiet"), registry.settings("tuned")!!.declarations.map { it.key })

		registry.remove("tuned")
		assertNull(registry.settings("tuned"))
	}

	private class FakePlugin(
		override val id: String,
		private val exporters: List<StoryExporter> = emptyList(),
		private val settings: List<SettingDeclaration> = emptyList(),
		private val commands: List<CliCommand> = emptyList(),
	) : ClientPlugin {
		override fun exporters() = exporters
		override fun settings() = settings
		override fun cliCommands() = commands
	}

	private class FakeCommand(override val name: String) : CliCommand {
		override val help = ""
		override suspend fun run(args: List<String>, io: CliIo, dispatcher: Dispatcher) = 0
	}

	private class FakeExporter(override val formatId: String) : StoryExporter {
		override val fileExtension = "txt"
		override val mimeType = "text/plain"
		override fun render(sink: BufferedSink, input: ExportInput) = Unit
	}
}
