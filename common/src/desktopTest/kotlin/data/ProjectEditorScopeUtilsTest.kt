package data

import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectDictionaryService
import getProjectDef
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.getScopeId
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProjectEditorScopeUtilsTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_EMPTY_NAME)
	private lateinit var dictionaryService: ProjectDictionaryService

	@BeforeEach
	override fun setup() {
		super.setup()
		dictionaryService = mockk(relaxed = true)
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped<SceneEditorService> { mockk(relaxed = true) }
				scoped<TimeLineRepository> { mockk(relaxed = true) }
				scoped { dictionaryService }
			}
		})
	}

	private fun scopeOrNull() = getKoin().getScopeOrNull(ProjectDefScope(projectDef).getScopeId())

	@Test
	fun `a temporary task alone never starts the editor services and closes its scope`() = runTest {
		temporaryProjectTask(projectDef) {}

		verify(exactly = 0) { dictionaryService.initialize() }
		assertNull(scopeOrNull())
	}

	@Test
	fun `opening for editing while a temporary task holds the scope starts the editor services and keeps the scope`() =
		runTest {
			temporaryProjectTask(projectDef) {
				verify(exactly = 0) { dictionaryService.initialize() }
				openProjectScope(projectDef)
				verify(exactly = 1) { dictionaryService.initialize() }
			}

			val scope = assertNotNull(scopeOrNull())
			closeProjectScope(scope, projectDef)
		}

	@Test
	fun `reopening for editing does not start the editor services twice`() = runTest {
		openProjectScope(projectDef)
		openProjectScope(projectDef)

		verify(exactly = 1) { dictionaryService.initialize() }
		closeProjectScope(assertNotNull(scopeOrNull()), projectDef)
	}
}
