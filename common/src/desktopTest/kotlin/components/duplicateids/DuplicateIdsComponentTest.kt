package components.duplicateids

import com.darkrockstudios.apps.hammer.common.components.duplicateids.DuplicateIdsComponent
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdService
import com.darkrockstudios.apps.hammer.common.data.duplicateids.ProjectFileRationalizer
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import utils.ComponentTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateIdsComponentTest : ComponentTest() {

	private lateinit var projectFiles: FakeFileSystem
	private lateinit var duplicateIdService: DuplicateIdService
	private var dismissed = false

	private val personDir get() = projectDef.path.toOkioPath() / "encyclopedia" / "person"

	@BeforeEach
	override fun setup() {
		super.setup()
		dismissed = false
		projectFiles = FakeFileSystem()
		projectFiles.createDirectories(personDir)
		duplicateIdService = DuplicateIdService(
			projectDef = projectDef,
			rationalizer = ProjectFileRationalizer(projectDef, projectFiles, createTomlSerializer()),
			syncJournal = mockk(relaxed = true),
			backupService = mockk(relaxed = true),
		)
		setupComponentKoin(module {
			scope<ProjectDefScope> {
				scoped { duplicateIdService }
				scoped<EncyclopediaRepository> { mockk(relaxed = true) }
			}
		})
	}

	private fun writeImages() {
		projectFiles.write(personDir / "person~5~image.png") { writeUtf8("png") }
		projectFiles.write(personDir / "person~5~image.jpg") { writeUtf8("jpg") }
	}

	private fun newComponent() = DuplicateIdsComponent(context, projectDef, dismissDialog = { dismissed = true })

	@Test
	fun `Opens on the first set with only the copy in use kept`() = runTest(mainTestDispatcher) {
		writeImages()
		duplicateIdService.rationalizeOnOpen()

		val comp = newComponent()
		context.resume()
		advanceUntilIdle()

		val state = comp.state.value
		assertEquals(5, state.current?.id)
		assertEquals(setOf(state.current!!.inUse.path), state.keep)
	}

	@Test
	fun `An image set keeps one copy, so picking another replaces the selection`() = runTest(mainTestDispatcher) {
		writeImages()
		duplicateIdService.rationalizeOnOpen()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		val other = comp.state.value.current!!.copies.last().path

		comp.toggleKeep(other)

		assertEquals(setOf(other), comp.state.value.keep)
	}

	@Test
	fun `Resolving the last set deletes the rest and closes the dialog`() = runTest(mainTestDispatcher) {
		writeImages()
		duplicateIdService.rationalizeOnOpen()
		val comp = newComponent()
		context.resume()
		advanceUntilIdle()
		val kept = comp.state.value.current!!.inUse.path

		comp.keepSelected()
		advanceUntilIdle()

		assertTrue(dismissed)
		assertTrue(projectFiles.exists(kept.toOkioPath()))
		assertEquals(1, projectFiles.list(personDir).size)
		assertFalse(comp.state.value.working)
	}
}
