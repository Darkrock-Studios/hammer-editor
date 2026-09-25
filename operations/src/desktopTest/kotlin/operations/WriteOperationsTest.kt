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
import com.darkrockstudios.apps.hammer.operations.core.ArchivedScenes
import com.darkrockstudios.apps.hammer.operations.core.DraftApplied
import com.darkrockstudios.apps.hammer.operations.core.DraftApplyInput
import com.darkrockstudios.apps.hammer.operations.core.DraftCreateInput
import com.darkrockstudios.apps.hammer.operations.core.DraftInfo
import com.darkrockstudios.apps.hammer.operations.core.ProjectInput
import com.darkrockstudios.apps.hammer.operations.core.SceneCreateInput
import com.darkrockstudios.apps.hammer.operations.core.SceneItemRef
import com.darkrockstudios.apps.hammer.operations.core.SceneKind
import com.darkrockstudios.apps.hammer.operations.core.SceneMeta
import com.darkrockstudios.apps.hammer.operations.core.SceneMetaWriteInput
import com.darkrockstudios.apps.hammer.operations.core.SceneMoveInput
import com.darkrockstudios.apps.hammer.operations.core.SceneNode
import com.darkrockstudios.apps.hammer.operations.core.SceneRenameInput
import com.darkrockstudios.apps.hammer.operations.core.SceneTree
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

	private suspend fun tree() = run<ProjectInput, SceneTree>("scene.tree", ProjectInput(PROJECT)).nodes

	private fun List<SceneNode>.names(): List<Any> = map { if (it.kind == SceneKind.Group) it.name to it.children.names() else it.name }

	@Test
	fun `scenes and groups are created where asked`() = onTestThread {
		val first = run<SceneCreateInput, SceneItemRef>("scene.create", SceneCreateInput(PROJECT, "Arrival", parentId = group.id, index = 0))
		run<SceneCreateInput, SceneItemRef>("scene.create", SceneCreateInput(PROJECT, "Part Two", kind = SceneKind.Group))

		assertEquals(listOf("Chapter One" to listOf("Arrival", "Opening"), "Part Two" to emptyList<Any>()), tree().names())
		assertEquals("", read(first.id))
		for (bad in listOf(SceneCreateInput(PROJECT, "bad~name"), SceneCreateInput(PROJECT, "Late", index = 5), SceneCreateInput(PROJECT, "Inside", parentId = scene.id))) {
			assertEquals(OperationException.Kind.InvalidInput, assertFailsWith<OperationException> { run<SceneCreateInput, SceneItemRef>("scene.create", bad) }.kind)
		}
	}

	@Test
	fun `scenes are renamed and moved`() = onTestThread {
		val second = run<SceneCreateInput, SceneItemRef>("scene.create", SceneCreateInput(PROJECT, "Landfall", parentId = group.id))
		run<SceneRenameInput, SceneItemRef>("scene.rename", SceneRenameInput(PROJECT, scene.id, "Arrival"))
		run<SceneMoveInput, SceneItemRef>("scene.move", SceneMoveInput(PROJECT, scene.id, parentId = group.id, index = 1))
		assertEquals(listOf("Chapter One" to listOf("Landfall", "Arrival")), tree().names())
		assertEquals(ORIGINAL, read())

		run<SceneMoveInput, SceneItemRef>("scene.move", SceneMoveInput(PROJECT, second.id, index = 0))
		assertEquals(listOf("Landfall", "Chapter One" to listOf("Arrival")), tree().names())

		for (bad in listOf(SceneMoveInput(PROJECT, group.id, parentId = group.id, index = 0), SceneMoveInput(PROJECT, scene.id, index = 3))) {
			assertFailsWith<OperationException> { run<SceneMoveInput, SceneItemRef>("scene.move", bad) }
		}
	}

	@Test
	fun `archive keeps an open editor's edits, and unarchive brings the scene back`() = onTestThread {
		val scope = openProjectScope(def)
		val service = scope.get<SceneEditorService>()
		service.onContentChanged(SceneContent(scene, markdown = UNSAVED), UpdateSource.Editor)
		delay(SceneContentRepository.BUFFER_COOL_DOWN * 2)

		run<ProjectItemInput, SceneItemRef>("scene.archive", ProjectItemInput(PROJECT, scene.id))
		service.storeAllBuffers()
		assertEquals(listOf("Chapter One" to emptyList<Any>()), tree().names())
		assertEquals(UNSAVED, read())

		run<ProjectItemInput, SceneItemRef>("scene.unarchive", ProjectItemInput(PROJECT, scene.id))
		closeProjectScope(scope, def)
		assertEquals(listOf("Chapter One" to emptyList<Any>(), "Opening"), tree().names())
		assertEquals(UNSAVED, read())
	}

	@Test
	fun `a deleted scene stays deleted when an editor saves everything`() = onTestThread {
		val scope = openProjectScope(def)
		val service = scope.get<SceneEditorService>()
		service.onContentChanged(SceneContent(scene, markdown = UNSAVED), UpdateSource.Editor)
		delay(SceneContentRepository.BUFFER_COOL_DOWN * 2)
		// Still in the debounce when the scene goes.
		service.onContentChanged(SceneContent(scene, markdown = "$UNSAVED More."), UpdateSource.Editor)

		run<ProjectItemInput, SceneItemRef>("scene.delete", ProjectItemInput(PROJECT, scene.id))
		delay(SceneContentRepository.BUFFER_COOL_DOWN * 2)
		service.storeAllBuffers()
		closeProjectScope(scope, def)

		assertEquals(listOf("Chapter One" to emptyList<Any>()), tree().names())
		assertEquals(OperationException.Kind.NotFound, assertFailsWith<OperationException> { read() }.kind)
		run<ProjectItemInput, SceneItemRef>("scene.delete", ProjectItemInput(PROJECT, group.id))
		assertEquals(emptyList<Any>(), tree().names())

		run<ProjectItemInput, SceneItemRef>("scene.delete", ProjectItemInput(PROJECT, archived.id))
		assertEquals(emptyList(), run<ProjectInput, ArchivedScenes>("scene.archived", ProjectInput(PROJECT)).scenes)
	}

	@Test
	fun `a group with scenes in it is not deleted`() = onTestThread {
		val error = assertFailsWith<OperationException> {
			run<ProjectItemInput, SceneItemRef>("scene.delete", ProjectItemInput(PROJECT, group.id))
		}
		assertEquals(OperationException.Kind.InvalidInput, error.kind)
	}

	@Test
	fun `meta write changes only what is given`() = onTestThread {
		run<SceneMetaWriteInput, SceneMeta>("scene.meta.write", SceneMetaWriteInput(PROJECT, scene.id, outline = "Storm arrives", tags = listOf(" #weather ", "")))
		val meta = run<SceneMetaWriteInput, SceneMeta>("scene.meta.write", SceneMetaWriteInput(PROJECT, scene.id, notes = "Check tides"))

		assertEquals("Storm arrives", meta.outline)
		assertEquals("Check tides", meta.notes)
		assertEquals(listOf("weather"), meta.tags)
	}

	@Test
	fun `drafts are created, applied, and deleted`() = onTestThread {
		val draft = run<DraftCreateInput, DraftInfo>("draft.create", DraftCreateInput(PROJECT, scene.id, "First pass"))
		write(WriteMode.Live, REVISED)

		val applied = run<DraftApplyInput, DraftApplied>("draft.apply", DraftApplyInput(PROJECT, draft.id))
		assertEquals(ORIGINAL, read())
		assertEquals(REVISED, draft(assertNotNull(applied.previousDraftId)).markdown)

		run<ProjectItemInput, DraftInfo>("draft.delete", ProjectItemInput(PROJECT, draft.id))
		val names = run<DraftListInput, Drafts>("draft.list", DraftListInput(PROJECT, scene.id)).drafts.map { it.name }
		assertEquals(listOf("Before external edit", "Before external edit"), names)
	}

	private companion object {
		const val PROJECT = "Storm Novel"
		const val ORIGINAL = "The storm came *early* that year."
		const val REVISED = "The storm came late, and hard."
		const val UNSAVED = "The storm came early that year, and I typed this."
	}
}
