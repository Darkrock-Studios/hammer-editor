package operations

import com.darkrockstudios.apps.hammer.common.data.ClientResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneContent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneContentRepository
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingSessionTracker
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.core.DraftListInput
import com.darkrockstudios.apps.hammer.operations.core.DraftText
import com.darkrockstudios.apps.hammer.operations.core.Drafts
import com.darkrockstudios.apps.hammer.operations.core.ProjectItemInput
import com.darkrockstudios.apps.hammer.operations.core.SceneAppendInput
import com.darkrockstudios.apps.hammer.operations.core.SceneText
import com.darkrockstudios.apps.hammer.operations.core.SceneWordCount
import com.darkrockstudios.apps.hammer.operations.core.SceneWriteInput
import com.darkrockstudios.apps.hammer.operations.core.SceneWriteResult
import com.darkrockstudios.apps.hammer.operations.core.WriteMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WriteOperationsTest : KoinOperationsTest() {

	private lateinit var def: ProjectDef
	private lateinit var scene: SceneItem
	private lateinit var group: SceneItem
	private lateinit var archived: SceneItem

	@BeforeEach
	fun setUp() = onTestThread {
		val created = get<ProjectsRepository>().createProject(PROJECT, seedDefaultLanguage = false)
		def = (created as ClientResult.Success<ProjectDef>).data
		temporaryProjectTask(def) { scope ->
			val scenes = scope.get<SceneEditorService>()
			group = scenes.createGroup(null, "Chapter One")!!
			scene = scenes.createScene(group, "Opening")!!
			scenes.storeSceneMarkdownRaw(SceneContent(scene, markdown = ORIGINAL))
			archived = scenes.createScene(group, "Cut")!!
			scenes.storeSceneMarkdownRaw(SceneContent(archived, markdown = "Nobody needs this."))
			scenes.archiveScene(archived)
		}
	}

	private suspend fun write(mode: WriteMode, markdown: String, id: Int = scene.id) =
		run<SceneWriteInput, SceneWriteResult>("scene.write", SceneWriteInput(PROJECT, id, mode, markdown))

	private suspend fun read(id: Int = scene.id) =
		run<ProjectItemInput, SceneText>("scene.read", ProjectItemInput(PROJECT, id)).markdown

	private suspend fun draft(id: Int) = run<ProjectItemInput, DraftText>("draft.read", ProjectItemInput(PROJECT, id))

	@Test
	fun `a draft write leaves the scene alone`() = onTestThread {
		val result = write(WriteMode.Draft, REVISED)

		assertEquals(ORIGINAL, read())
		val draft = draft(assertNotNull(result.draftId))
		assertEquals(REVISED, draft.markdown)
		assertEquals("Suggested edit", draft.name)
		assertEquals(scene.id, draft.sceneId)
	}

	@Test
	fun `a live write replaces the text and keeps the old text as a draft`() = onTestThread {
		val result = write(WriteMode.Live, REVISED)

		assertNull(result.draftId)
		assertEquals(REVISED, read())
		assertEquals(ORIGINAL, draft(assertNotNull(result.previousDraftId)).markdown)
	}

	@Test
	fun `a live write of the same text changes nothing`() = onTestThread {
		assertNull(write(WriteMode.Live, ORIGINAL).previousDraftId)
		val drafts = run<DraftListInput, Drafts>("draft.list", DraftListInput(PROJECT, scene.id))
		assertEquals(emptyList(), drafts.drafts)
	}

	@Test
	fun `append adds a paragraph`() = onTestThread {
		val result = run<SceneAppendInput, SceneWordCount>("scene.append", SceneAppendInput(PROJECT, scene.id, "\nThen it rained.\n"))

		assertEquals("$ORIGINAL\n\nThen it rained.\n", read())
		assertEquals(9, result.wordCount)
	}

	@Test
	fun `groups, archived scenes, and bad draft names are refused`() = onTestThread {
		for (id in listOf(group.id, archived.id)) {
			assertFailsWith<OperationException> { write(WriteMode.Live, REVISED, id) }
		}
		val badName = assertFailsWith<OperationException> {
			run<SceneWriteInput, SceneWriteResult>("scene.write", SceneWriteInput(PROJECT, scene.id, WriteMode.Draft, REVISED, "bad~name"))
		}
		assertEquals(OperationException.Kind.InvalidInput, badName.kind)
		assertEquals(ORIGINAL, read())
	}

	@Test
	fun `mode has no default`() = onTestThread {
		val input = buildJsonObject {
			put("project", PROJECT)
			put("id", scene.id)
			put("markdown", REVISED)
		}
		val error = assertFailsWith<OperationException> { get<OperationRegistry>().dispatch("scene.write", input) }
		assertEquals(OperationException.Kind.InvalidInput, error.kind)
	}

	@Test
	fun `a live write into an open editor shows there, keeps its unsaved text, and credits the writer nothing`() = onTestThread {
		val scope = openProjectScope(def)
		val service = scope.get<SceneEditorService>()
		service.loadSceneBuffer(scene)
		val collector = CoroutineScope(currentCoroutineContext() + Job())
		val seen = mutableListOf<SceneBuffer>()
		service.subscribeToBufferUpdates(scene, collector) { seen += it }
		service.onContentChanged(SceneContent(scene, markdown = UNSAVED), UpdateSource.Editor)
		delay(SceneContentRepository.BUFFER_COOL_DOWN * 2)
		// Typed just before the write, still in the debounce when it lands.
		service.onContentChanged(SceneContent(scene, markdown = "$UNSAVED More."), UpdateSource.Editor)

		val result = write(WriteMode.Live, REVISED)
		delay(SceneContentRepository.BUFFER_COOL_DOWN * 2)

		assertEquals(UNSAVED, draft(assertNotNull(result.previousDraftId)).markdown)
		assertEquals(REVISED, seen.last().content.markdown)
		assertEquals(UpdateSource.Repository, seen.last().source)
		assertFalse(service.hasDirtyBuffers())
		assertEquals(2, scope.get<WritingSessionTracker>().onSceneSaved(scene.id, "$REVISED Two more.", UpdateSource.Editor))

		collector.cancel()
		closeProjectScope(scope, def)
		assertEquals(REVISED, read())
	}

	@Test
	fun `a live write of the text an editor holds unsaved saves it`() = onTestThread {
		val scope = openProjectScope(def)
		val service = scope.get<SceneEditorService>()
		service.onContentChanged(SceneContent(scene, markdown = REVISED), UpdateSource.Editor)
		delay(SceneContentRepository.BUFFER_COOL_DOWN * 2)

		assertNull(write(WriteMode.Live, REVISED).previousDraftId)

		assertFalse(service.hasDirtyBuffers())
		closeProjectScope(scope, def)
		assertEquals(REVISED, read())
	}

	@Test
	fun `unsaved edits from a crashed session block live changes but not drafts`() = onTestThread {
		val scenes = SceneDatasource(def, ffs)
		scenes.storeTempSceneBuffer(SceneBuffer(SceneContent(scene, markdown = UNSAVED), dirty = true, source = UpdateSource.Editor))

		assertFailsWith<OperationException> { write(WriteMode.Live, REVISED) }
		assertFailsWith<OperationException> {
			run<SceneAppendInput, SceneWordCount>("scene.append", SceneAppendInput(PROJECT, scene.id, "More."))
		}
		assertNotNull(write(WriteMode.Draft, REVISED).draftId)

		assertEquals(ORIGINAL, read())
		assertEquals(listOf(UNSAVED), scenes.getSceneTempBufferContents().map { it.markdown })
	}

	@Test
	fun `a draft name is only for draft mode`() = onTestThread {
		val error = assertFailsWith<OperationException> {
			run<SceneWriteInput, SceneWriteResult>("scene.write", SceneWriteInput(PROJECT, scene.id, WriteMode.Live, REVISED, "Mine"))
		}
		assertEquals(OperationException.Kind.InvalidInput, error.kind)
	}

	private companion object {
		const val PROJECT = "Storm Novel"
		const val ORIGINAL = "The storm came *early* that year."
		const val REVISED = "The storm came late, and hard."
		const val UNSAVED = "The storm came early that year, and I typed this."
	}
}
