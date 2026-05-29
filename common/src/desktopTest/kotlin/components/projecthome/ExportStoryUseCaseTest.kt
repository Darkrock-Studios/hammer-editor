package components.projecthome

import PROJECT_1_NAME
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.common.components.projecthome.ExportStoryUseCase
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectdata.StoredProjectData
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import integration.BaseIntegrationTest
import io.fluidsonic.locale.Locale
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportStoryUseCaseTest : BaseIntegrationTest() {

	private lateinit var projectDataDatasource: ProjectDataDatasource
	private lateinit var storedProjectData: StoredProjectData
	private lateinit var localeResolver: DeviceLocaleResolver

	@BeforeEach
	override fun setup() {
		super.setup()
		configureProject(PROJECT_1_NAME)
		storedProjectData = StoredProjectData(data = ProjectData())
		projectDataDatasource = mockk {
			coEvery { load() } answers { storedProjectData }
		}
		localeResolver = mockk {
			every { getCurrentLocale() } returns Locale.forLanguageTag("en-US")
		}
	}

	private suspend fun initRepo() {
		sceneEditorRepository.initializeSceneEditor()
	}

	private fun useCase() = ExportStoryUseCase(
		sceneEditorRepository = sceneEditorService,
		projectDataDatasource = projectDataDatasource,
		fileSystem = ffs,
		localeResolver = localeResolver,
	)

	@Test
	fun `markdown export renders project title, numbered chapters, and group children`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = ExportFormat.Markdown, treatTopLevelAsChapters = true),
		)

		assertTrue(exportPath.path.endsWith(".md"), "Should produce a .md file, got $exportPath")
		val text = ffs.read(exportPath.toOkioPath()) { readByteArray() }.decodeToString()
		assertEquals(EXPECTED_PROJECT_1_MARKDOWN.trim(), text.trim())
	}

	@Test
	fun `epub export produces a valid zip file with PK magic bytes`() = runTest {
		initRepo()
		storedProjectData = StoredProjectData(data = ProjectData(authorName = "Test Author"))

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = ExportFormat.Epub, treatTopLevelAsChapters = true),
		)

		assertTrue(exportPath.path.endsWith(".epub"), "Should produce a .epub file, got $exportPath")
		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertTrue(bytes.size > 100, "EPUB output should be more than a stub, got ${bytes.size} bytes")
		// EPUB is a ZIP container; first two bytes are the local file header signature "PK".
		assertEquals('P'.code.toByte(), bytes[0])
		assertEquals('K'.code.toByte(), bytes[1])
	}

	@Test
	fun `epub export still produces a valid file when treatTopLevelAsChapters is false`() = runTest {
		initRepo()

		val exportPath = useCase().execute(
			exportDir = projectPath,
			options = ExportOptions(format = ExportFormat.Epub, treatTopLevelAsChapters = false),
		)

		val bytes = ffs.read(exportPath.toOkioPath()) { readByteArray() }
		assertTrue(bytes.size > 100, "Single-chapter EPUB should still produce a real file")
	}

	companion object {
		private val EXPECTED_PROJECT_1_MARKDOWN = """
			# Test Project 1


			## 1. Scene ID 1

			Content of scene id 1

			## 2. Chapter ID 2

			Content of scene id 3

			Content of scene id 4

			Content of scene id 5

			## 3. Scene ID 6

			Content of scene id 6

			## 4. Scene ID 7

			Content of scene id 7
		""".trimIndent()
	}
}
