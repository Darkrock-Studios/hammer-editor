package components.storyideas

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeas
import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeasComponent
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.base.http.createJsonSerializer
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeasSyncDatasource
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.ComponentTest
import utils.TestComponentContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class StoryIdeasComponentTest : ComponentTest() {

	private lateinit var ffs: FakeFileSystem

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")

		val projectsRepository = mockk<ProjectsRepository>()
		every { projectsRepository.getProjects() } returns emptyList()

		setupKoin(module {
			single {
				val toml = createTomlSerializer()
				val datasource = IdeasDatasource(ffs, StoryIdeaCodec(toml), globalSettingsStore)
				val syncDatasource = IdeasSyncDatasource(ffs, createJsonSerializer(), datasource)
				IdeasRepository(datasource, syncDatasource, Clock.System)
			} bind IdeasRepository::class
			single {
				AccountTagService(get(), projectsRepository, ffs, createTomlSerializer())
			}
		})
	}

	private fun newComponent(componentContext: TestComponentContext = context) =
		StoryIdeasComponent(componentContext = componentContext)

	/** Types a whole idea into the create editor and saves it. */
	private suspend fun StoryIdeas.writeIdea(
		title: String? = null,
		content: String,
		tags: List<String> = emptyList(),
	): StoryIdeas.SaveResult {
		showCreate()
		if (title != null) updateTitle(title)
		updateContent(content)
		updateTags(tags)
		return saveDraft()
	}

	@Test
	fun `Created ideas appear in component state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val result = comp.writeIdea(title = "Tides", content = "A story about tides", tags = listOf("ocean"))
		advanceUntilIdle()

		assertEquals(StoryIdeas.SaveResult.Created, result)
		assertEquals(1, comp.state.value.ideas.size)
		assertEquals("Tides", comp.state.value.ideas.single().title)
		assertNull(comp.state.value.editor, "Saving a new idea closes the editor")
	}

	@Test
	fun `Editor state opens for create and edit and closes`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(content = "edit me")
		advanceUntilIdle()
		val idea = comp.state.value.ideas.single()

		comp.showCreate()
		assertIs<StoryIdeas.Editor.Create>(comp.state.value.editor)

		comp.editIdea(idea.id)
		val editor = comp.state.value.editor
		assertIs<StoryIdeas.Editor.Edit>(editor)
		assertEquals(idea, editor.idea)
		assertEquals("edit me", comp.state.value.draft?.content)
		assertFalse(comp.state.value.draft!!.isEditing, "An existing idea opens read-only")

		comp.closeEditor()
		assertNull(comp.state.value.editor)
	}

	@Test
	fun `Save updates an existing idea`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(content = "v1")
		advanceUntilIdle()
		val idea = comp.state.value.ideas.single()

		comp.editIdea(idea.id)
		comp.beginEdit()
		comp.updateTitle("Now titled")
		comp.updateContent("v2")
		comp.updateTags(listOf("t"))
		val result = comp.saveDraft()
		advanceUntilIdle()

		assertEquals(StoryIdeas.SaveResult.Saved, result)
		val updated = comp.state.value.ideas.single()
		assertEquals("v2", updated.content)
		assertEquals("Now titled", updated.title)
		assertEquals(setOf("t"), updated.tags)
		// Back to read-only, showing what was stored rather than looking unsaved.
		val draft = comp.state.value.draft!!
		assertFalse(draft.isEditing)
		assertFalse(draft.isDirty)
		assertEquals("v2", draft.savedContent)
	}

	@Test
	fun `A save landing after the editor moved on leaves the new draft alone`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(content = "first")
		comp.writeIdea(content = "second")
		advanceUntilIdle()

		val first = comp.state.value.ideas.single { it.content == "first" }
		val second = comp.state.value.ideas.single { it.content == "second" }

		comp.editIdea(first.id)
		comp.beginEdit()
		comp.updateContent("first, rewritten")

		// Unconfined so the save runs up to its first suspension, leaving the write in flight
		// while the editor is switched underneath it.
		val save = async(UnconfinedTestDispatcher(testScheduler)) { comp.saveDraft() }
		assertFalse(save.isCompleted, "The write must still be in flight for this to test anything")
		comp.editIdea(second.id)
		advanceUntilIdle()

		assertEquals(StoryIdeas.SaveResult.Saved, save.await())
		assertEquals("first, rewritten", comp.state.value.ideas.single { it.id == first.id }.content)

		val draft = comp.state.value.draft!!
		assertEquals("second", draft.content, "The open editor still shows the idea it was opened on")
		assertEquals("second", draft.savedContent)
	}

	@Test
	fun `A tag typed but not committed is folded in on save`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showCreate()
		comp.updateContent("half typed")
		comp.updateTags(listOf("committed"))
		comp.updateTagDraft("#pending")
		comp.saveDraft()
		advanceUntilIdle()

		assertEquals(setOf("committed", "pending"), comp.state.value.ideas.single().tags)
	}

	@Test
	fun `Discarding an edit restores the stored idea`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(title = "Keep", content = "original")
		advanceUntilIdle()

		comp.editIdea(comp.state.value.ideas.single().id)
		comp.beginEdit()
		comp.updateContent("scribbled over")
		assertTrue(comp.state.value.draft!!.isDirty)

		comp.discardEdit()

		val draft = comp.state.value.draft!!
		assertFalse(draft.isEditing)
		assertEquals("original", draft.content)
		assertEquals("Keep", draft.title)
	}

	@Test
	fun `An unsaved draft survives process death`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(title = "Stored title", content = "stored body")
		advanceUntilIdle()
		val idea = comp.state.value.ideas.single()

		comp.editIdea(idea.id)
		comp.beginEdit()
		comp.updateTitle("Typed title")
		comp.updateContent("typed body, never saved")
		comp.updateTags(listOf("fresh"))
		comp.updateTagDraft("half-typ")

		val restoredContext = context.saveAndRecreate()
		val restored = newComponent(restoredContext)
		restoredContext.resume()
		advanceUntilIdle()

		val editor = restored.state.value.editor
		assertIs<StoryIdeas.Editor.Edit>(editor)
		assertEquals(idea.id, editor.idea.id)

		val draft = restored.state.value.draft!!
		assertTrue(draft.isEditing)
		assertEquals("Typed title", draft.title)
		assertEquals("typed body, never saved", draft.content)
		assertEquals(listOf("fresh"), draft.tags)
		assertEquals("half-typ", draft.tagDraft)
		// The baseline came back too, so the editor still knows it has unsaved changes.
		assertEquals("stored body", draft.savedContent)
		assertTrue(draft.isDirty)
	}

	@Test
	fun `A restored draft can still be saved`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(content = "stored body")
		advanceUntilIdle()

		comp.editIdea(comp.state.value.ideas.single().id)
		comp.beginEdit()
		comp.updateContent("typed body, never saved")

		val restoredContext = context.saveAndRecreate()
		val restored = newComponent(restoredContext)
		restoredContext.resume()
		advanceUntilIdle()

		val result = restored.saveDraft()
		advanceUntilIdle()

		assertEquals(StoryIdeas.SaveResult.Saved, result)
		assertEquals("typed body, never saved", restored.state.value.ideas.single().content)
	}

	@Test
	fun `Nothing is stashed when no editor is open`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(content = "just browsing")
		advanceUntilIdle()

		val restoredContext = context.saveAndRecreate()
		val restored = newComponent(restoredContext)
		restoredContext.resume()
		advanceUntilIdle()

		assertNull(restored.state.value.editor)
		assertNull(restored.state.value.draft)
	}

	@Test
	fun `An empty idea is rejected`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.showCreate()
		comp.updateTitle("Title only")
		val result = comp.saveDraft()
		advanceUntilIdle()

		assertEquals(StoryIdeas.SaveResult.Failed(IdeaError.EMPTY), result)
		assertTrue(comp.state.value.ideas.isEmpty())
		assertIs<StoryIdeas.Editor.Create>(comp.state.value.editor, "A rejected idea keeps the editor open")
	}

	@Test
	fun `Delete removes the idea from state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(content = "doomed")
		advanceUntilIdle()
		val idea = comp.state.value.ideas.single()

		comp.deleteIdea(idea.id)
		advanceUntilIdle()

		assertTrue(comp.state.value.ideas.isEmpty())
	}

	@Test
	fun `Archive stamps the idea`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(content = "keep")
		advanceUntilIdle()
		val idea = comp.state.value.ideas.single()

		comp.archiveIdea(idea.id)
		advanceUntilIdle()

		assertTrue(comp.state.value.ideas.single().archived != null)
	}

	@Test
	fun `Tag suggestions rank by frequency and match prefix`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.writeIdea(content = "a", tags = listOf("gothic", "ghost"))
		comp.writeIdea(content = "b", tags = listOf("gothic"))
		advanceUntilIdle()

		assertEquals(listOf("gothic", "ghost"), comp.suggestTags("g"))
		assertEquals(listOf("gothic"), comp.suggestTags("go"))
		assertTrue(comp.suggestTags("x").isEmpty())
	}

	@Test
	fun `Editing an unknown idea does nothing`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.editIdea(IdeaId.randomUUID())

		assertNull(comp.state.value.editor)
	}
}
