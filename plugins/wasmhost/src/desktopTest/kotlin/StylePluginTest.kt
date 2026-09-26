import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionOutput
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionProgress
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.peanuuutz.tomlkt.Toml
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The style report plugin from hammer-plugins, run by the host. Runs when HAMMER_PLUGINS points at that checkout;
 * HAMMER_STYLE_PLUGIN picks another build of it there, such as assemblyscript/style/build/style.hammerplugin.
 */
@EnabledIfEnvironmentVariable(named = "HAMMER_PLUGINS", matches = ".+")
class StylePluginTest {

	private val fileSystem = FakeFileSystem()

	@AfterEach
	fun tearDown() {
		GlobalContext.stopKoin()
	}

	private val cacheDirectory = "/cache/plugins/style".toPath()

	// What the fake operations serve, and the UI's language.
	private var scenes = emptyList<String>()
	private var names = emptyList<String>()
	private var language: String? = null
	private var locale: String? = null
	private lateinit var registry: PluginRegistry

	private val action by lazy {
		val tree = operation<JsonObject, JsonObject>("scene.tree", "", Access.Read, OperationScope.Content) {
			buildJsonObject {
				putJsonArray("nodes") {
					add(buildJsonObject {
						put("id", 1000); put("name", "Part One"); put("kind", "group"); put("wordCount", 0)
						putJsonArray("children") {
							scenes.indices.forEach { i ->
								add(buildJsonObject { put("id", i + 1); put("name", names[i]); put("kind", "scene"); put("wordCount", 0); putJsonArray("children") {} })
							}
						}
					})
				}
			}
		}
		val read = operation<JsonObject, JsonObject>("scene.read", "", Access.Read, OperationScope.Content) { input ->
			buildJsonObject { put("markdown", scenes[input["id"]!!.jsonPrimitive.int - 1]) }
		}
		val info = operation<JsonObject, JsonObject>("project.info", "", Access.Read, OperationScope.Content) {
			buildJsonObject { put("language", language) }
		}

		val build = System.getenv("HAMMER_STYLE_PLUGIN") ?: "c/style/build/style.hammerplugin"
		val built = File(System.getenv("HAMMER_PLUGINS"), build)
		check(built.exists()) { "Build $build in hammer-plugins first" }
		val download = "/downloads/style.hammerplugin".toPath()
		fileSystem.createDirectories(download.parent!!)
		fileSystem.write(download) { write(built.readBytes()) }
		val plugins = RuntimePlugins(fileSystem, "/config/plugins".toPath(), cacheDirectory.parent!!, locale)
		plugins.install(download)
		registry = PluginRegistry().also(plugins::activate)
		GlobalContext.startKoin {
			modules(
				module {
					single<FileSystem> { fileSystem }
					single<Toml> { createTomlSerializer() }
					single<CoroutineContext>(named(DISPATCHER_IO)) { Dispatchers.Unconfined }
					single(named(APP_SCOPE)) { CoroutineScope(Dispatchers.Unconfined) }
				},
				registry.koinModule(),
				module { single { OperationRegistry(listOf(tree, read, info), NoProjects) } },
			)
		}
		registry.plugins.single().actions().single().also {
			if (locale == null) assertEquals("Style report", it.label)
			assertEquals(ActionOutput.Document, it.output)
		}
	}

	/**
	 * Runs the report on [scenes], grouped under one part, or on the ones with the ids in [chosen], and
	 * returns its markdown.
	 */
	private fun report(
		scenes: List<String>,
		names: List<String> = scenes.indices.map { "Scene ${it + 1}" },
		chosen: List<Int> = emptyList(),
		onProgress: (ActionProgress) -> Unit = {},
	): String {
		this.scenes = scenes
		this.names = names
		val input = JsonObject(mapOf("scenes" to JsonArray(chosen.map(::JsonPrimitive))))
		return runBlocking { action.run(ActionCall("Storm", ActionPlace.Project, null, input, onProgress = onProgress)) }.markdown!!
	}

	@Test
	fun `the report covers only the chosen scenes, and reports its progress scene by scene`() {
		val progress = mutableListOf<String?>()
		val report = report(listOf("One two three.", "Four five.", "Six."), chosen = listOf(3, 1), onProgress = { progress += it.message })

		assertTrue(report.startsWith("# Style report\n\n## Chosen scenes\n\n- **Words:** 4 in 2 sentences"), report)
		assertTrue("### Scene 1" in report && "### Scene 3" in report && "### Scene 2" !in report, report)
		assertEquals(listOf<String?>("Scene 1 of 2", "Scene 2 of 2"), progress)
	}

	@Test
	fun `the report is a markdown document, the whole story first`() {
		val report = report(listOf("The storm came *early* that year. Alice ran quickly!", "“Get inside,” she said. “Now.”"))

		assertEquals(
			"""
			# Style report

			## Whole story

			- **Words:** 14 in 4 sentences
			- **Reading ease:** 94.5, grade 0.9
			- **Adverbs:** 71.4 per 1,000 words
			- **Filter words:** 0.0 per 1,000 words
			- **Dialogue:** 21%
			- **Most used adverbs:** quickly (1)

			## Scenes

			### Scene 1
			9 words in 2 sentences. Reading ease 89.5, grade 1.9. 111.1 adverbs per 1,000 words. 0.0 filter words per 1,000 words. 0% dialogue.

			### Scene 2
			5 words in 2 sentences. Reading ease 102.8, grade -0.5. 0.0 adverbs per 1,000 words. 0.0 filter words per 1,000 words. 60% dialogue.
			""".trimIndent(),
			report,
		)
	}

	@Test
	fun `sentences, quotations, and markdown follow the rules`() {
		val lines = report(
			listOf(
				"“Hello.” She left.",
				"Mr. Smith met Dr. Jones.",
				"# A heading\n- A *list* item [link text](http://x.y) here.",
				"Don’t stop. It’s fine!",
				"The old lighthouse stood. The old lighthouse leaned. The old lighthouse fell.",
			)
		).lines()

		fun scene(n: Int, line: Int = 1) = lines[lines.indexOf("### Scene $n") + line]
		assertTrue(scene(1).startsWith("3 words in 2 sentences.") && scene(1).endsWith(" 33% dialogue."))
		assertTrue(scene(2).startsWith("5 words in 1 sentence."))
		assertTrue(scene(3).startsWith("6 words in 1 sentence."))
		assertTrue(scene(4).startsWith("4 words in 2 sentences."))
		assertEquals("Repeats: lighthouse (3)", scene(5, line = 3))
	}

	@Test
	fun `scene names cannot format the note`() {
		assertTrue("### \\*Interlude\\* at the\\_end" in report(listOf("Rain."), listOf("*Interlude* at the_end")).lines())
	}

	@Test
	fun `a long report lists every scene`() {
		val report = report(List(400) { "The storm came early that year." })

		assertEquals(400, report.lines().count { it.startsWith("### Scene ") })
		assertTrue(report.lines().last().startsWith("6 words in 1 sentence."))
	}

	@Test
	fun `each scene's counts are cached by its text`() {
		val story = listOf("The storm came *early* that year. Alice ran quickly!", "“Get inside,” she said. “Now.”", "Rain.")
		val cold = report(story)
		assertEquals(story.size, fileSystem.list(cacheDirectory).size)

		assertEquals(cold, report(story))
		report(story + "Thunder rolled.")
		assertEquals(story.size + 1, fileSystem.list(cacheDirectory).size)
	}

	@Test
	fun `cached counts are used in place of counting`() {
		report(listOf("One two three four five six seven eight nine ten.", "Short."))
		val (small, large) = fileSystem.list(cacheDirectory).sortedBy { fileSystem.metadata(it).size }
		val smallBytes = fileSystem.read(small) { readByteArray() }
		fileSystem.write(small) { write(fileSystem.read(large) { readByteArray() }) }
		fileSystem.write(large) { write(smallBytes) }

		val lines = report(listOf("One two three four five six seven eight nine ten.", "Short.")).lines()

		assertTrue(lines[lines.indexOf("### Scene 1") + 1].startsWith("1 word in 1 sentence."))
	}

	@Test
	fun `a damaged cache entry is counted again`() {
		val story = listOf("The storm came *early* that year. Alice ran quickly!", "“Get inside,” she said. “Now.”")
		val cold = report(story)
		fileSystem.list(cacheDirectory).forEach { entry -> fileSystem.write(entry) { writeUtf8("damaged") } }

		assertEquals(cold, report(story))
	}

	@Test
	fun `filter words are counted and listed`() {
		val report = report(listOf("She felt cold. She saw the door and felt afraid."))
		assertTrue("- **Filter words:** 300.0 per 1,000 words" in report, report)
		assertTrue("- **Most used filter words:** felt (2), saw (1)" in report, report)
	}

	@Test
	fun `a French project gets French adverbs, filter words, and readability`() {
		language = "fr-FR"
		val report = report(listOf("Il marchait lentement vers le moment. Elle regarda la mer et pensa à l'homme."))
		assertTrue("- **Most used adverbs:** lentement (1)" in report, report)
		assertTrue("- **Most used filter words:** pensa (1), regarda (1)" in report, report)
		assertTrue(Regex("""- \*\*Reading ease:\*\* -?[0-9.]+ \(Kandel–Moles\)""").containsMatchIn(report), report)
		// "l'homme" is two words, as French elides.
		assertTrue("15 words in 2 sentences." in report, report)
	}

	@Test
	fun `dialogue in German quotes and behind Spanish dashes`() {
		language = "de"
		val german = report(listOf("„Komm her“, sagte er.", "„Komm.“ Er ging."))
		assertTrue("4 words in 1 sentence. Reading ease" in german && "50% dialogue." in german, german)
		// A sentence ends after the “ that closes it.
		assertTrue("3 words in 2 sentences." in german, german)
		language = "es"
		val spanish = report(listOf("—Ven aquí —dijo ella—. Ahora."))
		assertTrue("60% dialogue." in spanish, spanish)
	}

	@Test
	fun `a language without word lists gets its counts and a note`() {
		language = "pt-BR"
		val report = report(listOf("Ele saiu cedo. Ela ficou."))
		assertTrue("- **Words:** 5 in 2 sentences" in report, report)
		assertTrue("Reading ease" !in report && "Adverbs" !in report && "Filter words" !in report, report)
		assertTrue("need word lists in the project's language" in report, report)
	}

	@Test
	fun `settings leave out the scenes and shorten the lists`() {
		report(listOf("Rain."))
		val settings = registry.settings("style")!!
		settings.set("scenes", JsonPrimitive(false))
		settings.set("listSize", JsonPrimitive(1))
		val report = report(listOf("She felt cold. She saw the door and felt afraid."))
		assertTrue("## Scenes" !in report && "### Scene 1" !in report, report)
		assertTrue(report.endsWith("- **Most used filter words:** felt (2)"), report)
	}

	@Test
	fun `in French, its labels and report come from the locale`() {
		locale = "fr-CA"
		// French's no-break spaces, as plain ones.
		val report = report(listOf("One two three. Four.")).replace(' ', ' ')
		assertEquals("Rapport de style", registry.plugins.single().actions().single().label)
		assertTrue(report.startsWith("# Rapport de style\n\n## Toute l’histoire\n\n- **Mots :** 4 en 2 phrases\n"), report)
		assertTrue(Regex("""4 mots en 2 phrases\. Lisibilité -?[0-9]+,[0-9], niveau scolaire -?[0-9]+,[0-9]\. """).containsMatchIn(report), report)
		assertTrue(report.endsWith(" 0 % de dialogue."), report)
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
