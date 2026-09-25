import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.ProjectItemInput
import com.darkrockstudios.apps.hammer.operations.core.SceneKind
import com.darkrockstudios.apps.hammer.operations.core.SceneMeta
import com.darkrockstudios.apps.hammer.operations.core.SceneNode
import com.darkrockstudios.apps.hammer.operations.core.SceneText
import com.darkrockstudios.apps.hammer.operations.core.SceneTree
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.core.Note
import com.darkrockstudios.apps.hammer.operations.core.NoteCreateInput
import com.darkrockstudios.apps.hammer.plugins.style.StylePlugin
import com.darkrockstudios.apps.hammer.plugins.style.StyleReportNote
import com.darkrockstudios.apps.hammer.plugins.style.StyleReport
import com.darkrockstudios.apps.hammer.plugins.style.StyleReportInput
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class StyleReportTest {

	private val ffs = FakeFileSystem()
	private val project = ProjectDef("Storm Novel", "/projects/Storm Novel".toPath().toHPath())
	private val texts = mutableMapOf(
		1 to "The storm came early. The storm came hard.",
		3 to "“Run,” said Alice. She ran quickly.",
	)
	private var reads = 0
	private val notes = mutableListOf<NoteCreateInput>()

	private val tree = SceneTree(
		listOf(
			SceneNode(1, "Opening", SceneKind.Scene, 0, emptyList()),
			SceneNode(2, "Chapter One", SceneKind.Group, 0, listOf(SceneNode(3, "Landfall", SceneKind.Scene, 0, emptyList()))),
		)
	)

	private val registry = OperationRegistry(
		StylePlugin.operations() + listOf<Operation<*, *>>(
			operation<ProjectInput, SceneTree>("scene.tree", "", Access.Read, OperationScope.Content) { tree },
			operation<NoteCreateInput, Note>("note.create", "", Access.Write, OperationScope.Content) { input ->
				notes += input
				Note(1, Instant.DISTANT_PAST, input.tags, input.content)
			},
			operation<ProjectItemInput, SceneText>("scene.read", "", Access.Read, OperationScope.Content) { input ->
				reads++
				val markdown = texts.getValue(input.id)
				SceneText(input.id, "", false, markdown, 0, SceneMeta("", "", emptyList(), "", null, null))
			},
		),
		object : ProjectResolver {
			override fun resolve(project: String): ProjectDef = this@StyleReportTest.project
			override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T =
				block(OpenProject(this@StyleReportTest.project, GlobalContext.get().getOrCreateScope("style-test", named("style-test"))))
		},
	)

	@BeforeEach
	fun setUp() {
		startKoin { modules(module { single<FileSystem> { ffs } }) }
	}

	@AfterEach
	fun tearDown() {
		stopKoin()
	}

	private fun report(vararg ids: Int) = runBlocking {
		registry.call<StyleReportInput, StyleReport>("style.report", StyleReportInput("Storm Novel", ids.toList()))
	}

	@Test
	fun `reports every scene in story order, and the whole`() {
		val report = report()

		assertEquals(listOf("Opening", "Landfall"), report.scenes.map { it.name })
		assertEquals(14, report.total.words)
		assertEquals(1, report.total.adverbs)
		assertEquals(listOf("the storm came"), report.scenes[0].figures.repeatedPhrases.map { it.text })
	}

	@Test
	fun `the report is saved as a tagged note`() {
		runBlocking { StyleReportNote.save(registry, "Storm Novel") }

		val note = notes.single()
		assertEquals(listOf(StyleReportNote.TAG), note.tags)
		assertTrue(note.content.startsWith("Style report"))
		assertTrue("Opening: 8 words in 2 sentences." in note.content)
	}

	@Test
	fun `a long report is cut to fit a note`() {
		val report = report()
		val long = report.copy(scenes = List(500) { report.scenes[it % 2] })
		val text = StyleReportNote.format(long, maxLength = 2000)

		assertTrue(text.length <= 2000)
		assertTrue(text.endsWith("Run style.report to see them all."))
	}

	@Test
	fun `the report still runs when the cache cannot be written`() {
		ffs.createDirectories("/projects/Storm Novel".toPath())
		ffs.write("/projects/Storm Novel/.plugins".toPath()) { writeUtf8("not a directory") }
		assertEquals(14, report().total.words)
	}

	@Test
	fun `a group stands for its scenes`() {
		assertEquals(listOf(3), report(2).scenes.map { it.id })
		assertFailsWith<OperationException> { report(9) }
	}

	@Test
	fun `unchanged scenes come from the cache, changed ones are counted again`() {
		report()
		texts[1] = "Calm."
		val again = report()

		assertEquals(1, again.scenes[0].figures.words)
		assertEquals(4, reads)
		assertEquals(2, ffs.list("/projects/Storm Novel/.plugins/style/scenes".toPath()).size)
	}
}
