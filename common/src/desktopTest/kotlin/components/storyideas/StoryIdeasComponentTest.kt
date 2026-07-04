package components.storyideas

import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeas
import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeasComponent
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasDatasource
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.StoryIdeaCodec
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.bind
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
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
				IdeasRepository(datasource, Clock.System)
			} bind IdeasRepository::class
			single {
				AccountTagService(get(), projectsRepository, ffs, createTomlSerializer())
			}
		})
	}

	private fun newComponent() = StoryIdeasComponent(componentContext = context)

	@Test
	fun `Created ideas appear in component state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val error = comp.createIdea(title = "Tides", content = "A story about tides", tags = setOf("ocean"))
		advanceUntilIdle()

		assertEquals(IdeaError.NONE, error)
		assertEquals(1, comp.state.value.ideas.size)
		assertEquals("Tides", comp.state.value.ideas.single().title)
	}

	@Test
	fun `Editor state opens for create and edit and closes`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.createIdea(title = null, content = "edit me", tags = emptySet())
		advanceUntilIdle()
		val idea = comp.state.value.ideas.single()

		comp.showCreate()
		assertIs<StoryIdeas.Editor.Create>(comp.state.value.editor)

		comp.editIdea(idea.id)
		val editor = comp.state.value.editor
		assertIs<StoryIdeas.Editor.Edit>(editor)
		assertEquals(idea, editor.idea)

		comp.closeEditor()
		assertNull(comp.state.value.editor)
	}

	@Test
	fun `Save updates an existing idea`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.createIdea(title = null, content = "v1", tags = emptySet())
		advanceUntilIdle()
		val idea = comp.state.value.ideas.single()

		val error = comp.saveIdea(idea.id, title = "Now titled", content = "v2", tags = setOf("t"))
		advanceUntilIdle()

		assertEquals(IdeaError.NONE, error)
		val updated = comp.state.value.ideas.single()
		assertEquals("v2", updated.content)
		assertEquals("Now titled", updated.title)
		assertEquals(setOf("t"), updated.tags)
	}

	@Test
	fun `Delete removes the idea from state`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.createIdea(title = null, content = "doomed", tags = emptySet())
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
		comp.createIdea(title = null, content = "keep", tags = emptySet())
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
		comp.createIdea(title = null, content = "a", tags = setOf("gothic", "ghost"))
		comp.createIdea(title = null, content = "b", tags = setOf("gothic"))
		advanceUntilIdle()

		assertEquals(listOf("gothic", "ghost"), comp.suggestTags("g"))
		assertEquals(listOf("gothic"), comp.suggestTags("go"))
		assertTrue(comp.suggestTags("x").isEmpty())
	}
}
