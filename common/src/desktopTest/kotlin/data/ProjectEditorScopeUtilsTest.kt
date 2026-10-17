package data

import PROJECT_1_NAME
import PROJECT_EMPTY_NAME
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.closeProjectScope
import com.darkrockstudios.apps.hammer.common.data.isProjectInUse
import com.darkrockstudios.apps.hammer.common.data.openProjectScope
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectSpellCheckService
import getProjectDef
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.getScopeId
import org.koin.dsl.module
import utils.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectEditorScopeUtilsTest : BaseTest() {

	private val projectDef = getProjectDef(PROJECT_EMPTY_NAME)
	private lateinit var spellCheckService: ProjectSpellCheckService

	@BeforeEach
	override fun setup() {
		super.setup()
		spellCheckService = mockk(relaxed = true)
		setupKoin(module {
			scope<ProjectDefScope> {
				scoped { projectDef }
				scoped<SceneEditorService> { mockk(relaxed = true) }
				scoped<TimeLineRepository> { mockk(relaxed = true) }
				scoped { spellCheckService }
			}
		})
	}

	private fun scopeOrNull() = getKoin().getScopeOrNull(ProjectDefScope(projectDef).getScopeId())

	@Test
	fun `a temporary task alone never starts the editor services and closes its scope`() = runTest {
		temporaryProjectTask(projectDef) {}

		verify(exactly = 0) { spellCheckService.initialize() }
		assertNull(scopeOrNull())
	}

	@Test
	fun `opening for editing while a temporary task holds the scope starts the editor services and keeps the scope`() =
		runTest {
			temporaryProjectTask(projectDef) {
				verify(exactly = 0) { spellCheckService.initialize() }
				openProjectScope(projectDef)
				verify(exactly = 1) { spellCheckService.initialize() }
			}

			val scope = assertNotNull(scopeOrNull())
			closeProjectScope(scope, projectDef)
		}

	@Test
	fun `reopening for editing does not start the editor services twice`() = runTest {
		openProjectScope(projectDef)
		openProjectScope(projectDef)

		verify(exactly = 1) { spellCheckService.initialize() }
		closeProjectScope(assertNotNull(scopeOrNull()), projectDef)
	}

	@Test
	fun `defs with the same path share one scope whatever their ids`() = runTest {
		val reidentified = projectDef.copy(id = ProjectId.randomUUID())
		openProjectScope(projectDef)

		assertEquals(ProjectDefScope(projectDef).getScopeId(), ProjectDefScope(reidentified).getScopeId())
		assertEquals(ProjectDefScope(projectDef).getScopeId(), ProjectDefScope.scopeIdFor(projectDef.path))
		assertTrue(isProjectInUse(reidentified))
		assertTrue(isProjectInUse(projectDef.path))

		closeProjectScope(assertNotNull(scopeOrNull()), reidentified)
		assertFalse(isProjectInUse(projectDef))
	}

	@Test
	fun `defs with different paths get different scopes`() {
		val other = getProjectDef(PROJECT_1_NAME)

		assertNotEquals(ProjectDefScope(projectDef).getScopeId(), ProjectDefScope(other).getScopeId())
	}
}
