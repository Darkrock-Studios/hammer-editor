package repositories

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneEditorService
import com.darkrockstudios.apps.hammer.common.data.temporaryProjectTask
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.getScopeId
import org.koin.dsl.module
import org.koin.mp.KoinPlatform.getKoin
import utils.BaseTest

class TemporaryProjectTaskTest : BaseTest(), KoinComponent {

	private val projectDef = ProjectDef(
		name = "Test Project",
		path = HPath(path = "/projects/Test Project", name = "Test Project", isAbsolute = true),
	)

	@BeforeEach
	override fun setup() {
		super.setup()
		setupKoin(
			module {
				scope<ProjectDefScope> {
					scoped<SceneEditorService> { mockk(relaxed = true) }
					scoped<TimeLineRepository> { mockk(relaxed = true) }
				}
			}
		)
	}

	private fun scopeIsOpen(): Boolean =
		getKoin().getScopeOrNull(ProjectDefScope(projectDef).getScopeId()) != null

	@Test
	fun `a lone task opens the scope and closes it again`() = runTest {
		assertFalse(scopeIsOpen())

		temporaryProjectTask(projectDef) {
			assertTrue(scopeIsOpen())
		}

		assertFalse(scopeIsOpen())
	}

	@Test
	fun `an overlapping task does not lose the scope when the first one finishes`() = runTest {
		val firstIsIn = CompletableDeferred<Unit>()
		val releaseFirst = CompletableDeferred<Unit>()
		val secondIsIn = CompletableDeferred<Unit>()
		val releaseSecond = CompletableDeferred<Unit>()

		val first = async {
			temporaryProjectTask(projectDef) {
				firstIsIn.complete(Unit)
				releaseFirst.await()
			}
		}
		firstIsIn.await()

		val second = async {
			temporaryProjectTask(projectDef) {
				secondIsIn.complete(Unit)
				releaseSecond.await()
				// The first task created the scope and has already returned by now. Closing on its
				// way out would leave this one resolving against a closed scope.
				assertTrue(scopeIsOpen())
				getKoin().getScope(ProjectDefScope(projectDef).getScopeId()).get<SceneEditorService>()
			}
		}
		secondIsIn.await()

		releaseFirst.complete(Unit)
		first.await()

		assertTrue(scopeIsOpen())

		releaseSecond.complete(Unit)
		second.await()

		// The last one out still closes it, so an overlap cannot leak the scope either.
		assertFalse(scopeIsOpen())
	}

	@Test
	fun `a task that throws still releases its claim on the scope`() = runTest {
		runCatching {
			temporaryProjectTask(projectDef) { error("the task blew up") }
		}

		assertFalse(scopeIsOpen())

		temporaryProjectTask(projectDef) {
			assertTrue(scopeIsOpen())
		}

		assertFalse(scopeIsOpen())
	}
}
