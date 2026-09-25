import com.darkrockstudios.apps.hammer.common.data.export.ExportInput
import com.darkrockstudios.apps.hammer.common.data.export.ExportStrings
import com.darkrockstudios.apps.hammer.common.data.export.StoryChapter
import com.darkrockstudios.apps.hammer.common.data.export.StoryExporterRegistry
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonPrimitive
import net.peanuuutz.tomlkt.Toml
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * The plain text plugin built by hammer-plugins' `c/build.sh plaintext`, installed and loaded as a
 * restarted app would. Starts Koin; the caller stops it.
 */
class PlainTextPluginHarness {
	private val fileSystem = FakeFileSystem()
	private val registry: PluginRegistry

	init {
		val built = File(System.getenv("HAMMER_PLUGINS"), "c/plaintext/build/plaintext.hammerplugin")
		check(built.exists()) { "Run c/build.sh plaintext in hammer-plugins first" }
		val download = "/downloads/plaintext.hammerplugin".toPath()
		val directory = "/config/plugins".toPath()
		val cacheDirectory = "/cache/plugins".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		RuntimePlugins(fileSystem, directory, cacheDirectory).install(download)

		registry = PluginRegistry().also(RuntimePlugins(fileSystem, directory, cacheDirectory)::activate)
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
				) + listOf(registry.koinModule())
			)
		}
	}

	fun render(
		chapters: List<StoryChapter>,
		treatTopLevelAsChapters: Boolean = true,
		sceneBreak: String = "Hash",
		italics: String = "Underscores",
		paragraphs: String = "BlankLine",
		chapterHeadings: Boolean = true,
	): String {
		val settings = registry.settings("plaintext")!!
		settings.set("sceneBreak", JsonPrimitive(sceneBreak))
		settings.set("italics", JsonPrimitive(italics))
		settings.set("paragraphs", JsonPrimitive(paragraphs))
		settings.set("chapterHeadings", JsonPrimitive(chapterHeadings))
		return renderWithSavedSettings(chapters, treatTopLevelAsChapters)
	}

	/** Renders with whatever settings are saved, the package's defaults until something is set. */
	fun renderWithSavedSettings(chapters: List<StoryChapter>, treatTopLevelAsChapters: Boolean = true): String {

		val exporter = GlobalContext.get().get<StoryExporterRegistry>().forFormat("plaintext.txt")
		val input = ExportInput(
			projectName = "Tide",
			projectData = null,
			chapters = chapters,
			treatTopLevelAsChapters = treatTopLevelAsChapters,
			language = "en",
			strings = ExportStrings(contentsTitle = "Contents", authorByline = null),
		)
		return Buffer().also { exporter.render(it, input) }.readUtf8()
	}
}
