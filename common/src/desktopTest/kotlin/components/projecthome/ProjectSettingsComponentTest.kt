package components.projecthome

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickRequests
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.components.projecthome.CoverImage
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettingsComponent
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageFileStamp
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeasRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsDatasource
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncJournalDatasource
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectDictionaryUseCase
import com.darkrockstudios.apps.hammer.common.util.AvailableLocalesProvider
import com.darkrockstudios.apps.hammer.common.util.Locale
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.peanuuutz.tomlkt.Toml
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import utils.TestComponentContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSettingsComponentTest : ComponentTest() {

	private lateinit var ffs: FakeFileSystem
	private lateinit var toml: Toml
	private lateinit var datasource: ProjectDataDatasource
	private lateinit var ideasFlow: MutableStateFlow<List<StoryIdea>>
	private lateinit var imagesService: ImagesService
	private lateinit var pickRequests: ImagePickRequests
	private val images = mutableMapOf<Int, ImageDef>()

	@BeforeEach
	override fun setup() {
		super.setup()

		ffs = FakeFileSystem()
		ffs.createDirectories(projectDef.path.toOkioPath())
		toml = createTomlSerializer()

		ideasFlow = MutableStateFlow(emptyList())

		images.clear()
		pickRequests = ImagePickRequests()
		imagesService = mockk()
		every { imagesService.imageChangedFlow } returns MutableSharedFlow()
		coEvery { imagesService.ensureImagesLoaded() } answers { images.values.toList() }
		every { imagesService.findImage(any()) } answers { images[firstArg()] }
		every { imagesService.fileStamp(any()) } returns ImageFileStamp(size = 10, lastModifiedAtMillis = 20)
		every { imagesService.imagePath(any()) } answers {
			val def = firstArg<ImageDef>()
			HPath(path = coverPath(def), name = "${def.id}~${def.name}.png", isAbsolute = true)
		}
		every { imagesService.cachedContentHash(any(), any()) } answers { "h${firstArg<ImageDef>().id}" }
		val ideasRepository = mockk<IdeasRepository>()
		every { ideasRepository.ideasFlow } returns ideasFlow

		val projectsRepository = mockk<ProjectsRepository>()
		every { projectsRepository.getProjects() } returns emptyList()

		val globalSettingsStore = mockk<GlobalSettingsStore>()
		every { globalSettingsStore.globalSettings } returns GlobalSettings(projectsDirectory = "/projects")
		every { globalSettingsStore.globalSettingsUpdates } returns MutableSharedFlow()

		setupComponentKoin(module {
			single { globalSettingsStore }
			single { AccountTagService(ideasRepository, projectsRepository, ffs, toml) }
			single { ProjectPluginsRepository(ProjectPluginsDatasource(ffs, toml)) }
			single<PlatformSpellCheckerFactory> {
				mockk { every { availableLocales() } returns emptyList() }
			}
			single<AvailableLocalesProvider> {
				mockk {
					every { allLocales() } returns listOf(
						Locale.forLanguage("fr", "FR"),
						Locale.forLanguage("en", "US"),
					)
				}
			}
			scope<ProjectDefScope> {
				scoped { ProjectDataRepository(datasource, projectDef) }
				scoped { ProjectDictionaryUseCase(get()) }
				scoped<SyncJournalDatasource> { mockk(relaxed = true) }
				scoped { imagesService }
				scoped { pickRequests }
			}
		})

		datasource = ProjectDataDatasource(ffs, toml, projectDef)
	}

	private fun newComponent(componentContext: TestComponentContext = context) = ProjectSettingsComponent(
		componentContext = componentContext,
		projectDef = projectDef,
	)

	private fun coverPath(def: ImageDef) = "/images/${def.id}~${def.name}.png"

	private fun cover(def: ImageDef) = CoverImage(def = def, path = coverPath(def), version = "h${def.id}")

	private fun idea(vararg tags: String) = StoryIdea(
		id = IdeaId.randomUUID(),
		created = Instant.DISTANT_PAST,
		updated = Instant.DISTANT_PAST,
		content = "content",
		tags = tags.toSet(),
	)

	@Test
	fun `Stored project data loads into state`() = runTest(mainTestDispatcher) {
		datasource.save(
			StoredProjectData(data = ProjectData(authorName = "Jane", tags = setOf("gothic")))
		)

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val state = comp.projectInfoState.value
		assertTrue(state.isLoaded)
		assertEquals("Jane", state.data.authorName)
		assertEquals(setOf("gothic"), state.data.tags)
		assertEquals("Test", comp.projectName)
	}

	@Test
	fun `Missing project data file loads defaults`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(comp.projectInfoState.value.isLoaded)
		assertEquals(ProjectData(), comp.projectInfoState.value.data)
	}

	@Test
	fun `setAuthorName persists the new name`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.setAuthorName("Mary Shelley")
		advanceUntilIdle()

		assertEquals("Mary Shelley", comp.projectInfoState.value.data.authorName)
		assertEquals("Mary Shelley", datasource.load().data.authorName)
	}

	@Test
	fun `Blank author name is stored as null`() = runTest(mainTestDispatcher) {
		datasource.save(StoredProjectData(data = ProjectData(authorName = "Jane")))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.setAuthorName("   ")
		advanceUntilIdle()

		assertNull(comp.projectInfoState.value.data.authorName)
		assertNull(datasource.load().data.authorName)
	}

	@Test
	fun `setTheme persists the theme`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val theme = ProjectTheme(primary = "#112233", secondary = "#445566")
		comp.setTheme(theme)
		advanceUntilIdle()

		assertEquals(theme, comp.projectInfoState.value.data.theme)
		assertEquals(theme, datasource.load().data.theme)
	}

	@Test
	fun `setWordCountGoal persists the goal`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val goal = WordCountGoal(cadence = WordCountGoal.Cadence.WEEK, count = 5000)
		comp.setWordCountGoal(goal)
		advanceUntilIdle()

		assertEquals(goal, comp.projectInfoState.value.data.wordCountGoal)
		assertEquals(goal, datasource.load().data.wordCountGoal)
	}

	@Test
	fun `addDictionaryWord normalizes and persists`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.addDictionaryWord("  Kvothe ")
		advanceUntilIdle()

		assertEquals(setOf("Kvothe"), comp.projectInfoState.value.data.dictionaryWords)
		assertEquals(setOf("Kvothe"), datasource.load().data.dictionaryWords)
	}

	@Test
	fun `addDictionaryWord ignores input that is not a single word`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.addDictionaryWord("two words")
		comp.addDictionaryWord("   ")
		advanceUntilIdle()

		assertEquals(emptySet(), comp.projectInfoState.value.data.dictionaryWords)
	}

	@Test
	fun `addDictionaryWord ignores a word already present in another case`() = runTest(mainTestDispatcher) {
		datasource.save(StoredProjectData(data = ProjectData(dictionaryWords = setOf("Kvothe"))))
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.addDictionaryWord("kvothe")
		advanceUntilIdle()

		assertEquals(setOf("Kvothe"), datasource.load().data.dictionaryWords)
	}

	@Test
	fun `removeDictionaryWord persists the removal`() = runTest(mainTestDispatcher) {
		datasource.save(StoredProjectData(data = ProjectData(dictionaryWords = setOf("Kvothe", "Denna"))))
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.removeDictionaryWord("Kvothe")
		advanceUntilIdle()

		assertEquals(setOf("Denna"), comp.projectInfoState.value.data.dictionaryWords)
		assertEquals(setOf("Denna"), datasource.load().data.dictionaryWords)
	}

	@Test
	fun `setPluginEnabled turns a plugin off and back on for this device, outside the synced project data`() =
		runTest(mainTestDispatcher) {
			val comp = newComponent()
			context.resume()
			advanceUntilIdle()

			comp.setPluginEnabled("style", false)
			comp.setPluginEnabled("plaintext", false)
			advanceUntilIdle()
			assertEquals(setOf("style", "plaintext"), comp.disabledPlugins.value)

			comp.setPluginEnabled("plaintext", true)
			advanceUntilIdle()
			assertEquals(setOf("style"), comp.disabledPlugins.value)
			assertEquals(setOf("style"), ProjectPluginsDatasource(ffs, toml).load()[projectDef.id])
			assertEquals(ProjectData(), datasource.load().data)
		}

	@Test
	fun `setTags normalizes tags before persisting`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.setTags(setOf("#gothic", " haunted ", "bad tag!", ""))
		advanceUntilIdle()

		assertEquals(setOf("gothic", "haunted"), comp.projectInfoState.value.data.tags)
		assertEquals(setOf("gothic", "haunted"), datasource.load().data.tags)
	}

	@Test
	fun `setProjectLanguage persists a normalized tag`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.setProjectLanguage("EN-us")
		advanceUntilIdle()

		assertEquals("en-US", comp.projectInfoState.value.data.language)
		assertEquals("en-US", datasource.load().data.language)
	}

	@Test
	fun `setProjectLanguage with null or blank clears the language`() = runTest(mainTestDispatcher) {
		datasource.save(StoredProjectData(data = ProjectData(language = "fr")))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.setProjectLanguage("   ")
		advanceUntilIdle()

		assertNull(comp.projectInfoState.value.data.language)
		assertNull(datasource.load().data.language)
	}

	@Test
	fun `availableLanguages are sorted by display name`() = runTest(mainTestDispatcher) {
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val names = comp.availableLanguages.map { it.displayName }
		assertEquals(names.sortedBy { it.lowercase() }, names)
		assertEquals(setOf("fr-FR", "en-US"), comp.availableLanguages.map { it.tag }.toSet())
	}

	@Test
	fun `Project tag suggestions come from the account tag vocabulary`() = runTest(mainTestDispatcher) {
		ideasFlow.value = listOf(idea("gothic", "ghost"), idea("gothic"))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf("gothic", "ghost"), comp.suggestProjectTags("g"))
		assertEquals(listOf("gothic"), comp.suggestProjectTags("go"))
		assertTrue(comp.suggestProjectTags("x").isEmpty())
	}

	@Test
	fun `Project tag suggestions exclude tags already on the project`() = runTest(mainTestDispatcher) {
		ideasFlow.value = listOf(idea("gothic", "ghost"), idea("gothic"))
		datasource.save(StoredProjectData(data = ProjectData(tags = setOf("gothic"))))

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf("ghost"), comp.suggestProjectTags("g"))
	}

	@Test
	fun `Picking a cover asks the gallery and stores the picked image`() = runTest(mainTestDispatcher) {
		images[4] = ImageDef(4, "Cover", "png")
		val requested = mutableListOf<ImagePickTarget>()
		backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pickRequests.requests.collect { requested += it } }
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.pickCoverImage()
		advanceUntilIdle()
		assertEquals(listOf<ImagePickTarget>(ImagePickTarget.ProjectCover), requested)
		assertTrue(comp.projectInfoState.value.pendingCoverPick)

		comp.pickCoverImage()
		advanceUntilIdle()
		assertEquals(1, requested.size, "a second pick while one is pending is ignored")

		pickRequests.deliver(ImagePickTarget.ProjectCover, 4)
		advanceUntilIdle()

		assertFalse(comp.projectInfoState.value.pendingCoverPick)
		assertEquals(4, datasource.load().data.coverImageId)
		assertEquals(4, comp.projectInfoState.value.data.coverImageId)
		assertEquals(cover(images.getValue(4)), comp.projectInfoState.value.coverImage)
	}

	@Test
	fun `A cancelled or vanished pick leaves the cover alone`() = runTest(mainTestDispatcher) {
		datasource.save(StoredProjectData(data = ProjectData(coverImageId = 2)))
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		comp.pickCoverImage()
		advanceUntilIdle()
		pickRequests.deliver(ImagePickTarget.ProjectCover, null)
		advanceUntilIdle()
		assertFalse(comp.projectInfoState.value.pendingCoverPick)

		comp.pickCoverImage()
		advanceUntilIdle()
		pickRequests.deliver(ImagePickTarget.ProjectCover, 99)
		advanceUntilIdle()

		assertFalse(comp.projectInfoState.value.pendingCoverPick)
		assertEquals(2, datasource.load().data.coverImageId)
	}

	@Test
	fun `clearCoverImage removes the cover`() = runTest(mainTestDispatcher) {
		images[4] = ImageDef(4, "Cover", "png")
		datasource.save(StoredProjectData(data = ProjectData(coverImageId = 4)))
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(4, comp.projectInfoState.value.coverImage?.def?.id)

		comp.clearCoverImage()
		advanceUntilIdle()

		assertNull(datasource.load().data.coverImageId)
		assertNull(comp.projectInfoState.value.coverImage)
	}

	@Test
	fun `A cover pick survives process death`() = runTest(mainTestDispatcher) {
		images[4] = ImageDef(4, "Cover", "png")
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		comp.pickCoverImage()
		advanceUntilIdle()

		val restoredContext = context.saveAndRecreate()
		context.destroy()
		val restored = newComponent(restoredContext)
		restoredContext.resume()
		advanceUntilIdle()
		assertTrue(restored.projectInfoState.value.pendingCoverPick)

		pickRequests.deliver(ImagePickTarget.ProjectCover, 4)
		advanceUntilIdle()

		assertFalse(restored.projectInfoState.value.pendingCoverPick)
		assertEquals(4, datasource.load().data.coverImageId)
	}
}
