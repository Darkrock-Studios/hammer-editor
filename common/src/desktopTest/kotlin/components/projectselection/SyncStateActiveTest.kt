package components.projectselection

import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList.ProjectSyncStatus
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList.Status
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.hasActiveSync
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncStateActiveTest {

	private fun syncState(
		syncComplete: Boolean,
		vararg statuses: Status,
	): ProjectsList.SyncState {
		val map = statuses.mapIndexed { index, status ->
			"project_$index" to ProjectSyncStatus(projectName = "project_$index", status = status)
		}.toMap()
		return ProjectsList.SyncState(syncComplete = syncComplete, projectsStatus = map)
	}

	@Test
	fun `Failed project with syncComplete still false is not active`() {
		// Reproduces the bug: a sync where one project failed left syncComplete=false,
		// so closing the dialog wrongly prompted the cancel confirmation.
		val state = syncState(syncComplete = false, Status.Complete, Status.Failed)

		assertFalse(state.hasActiveSync)
	}

	@Test
	fun `All complete is not active`() {
		val state = syncState(syncComplete = true, Status.Complete, Status.Complete)

		assertFalse(state.hasActiveSync)
	}

	@Test
	fun `Syncing project is active`() {
		val state = syncState(syncComplete = false, Status.Complete, Status.Syncing)

		assertTrue(state.hasActiveSync)
	}

	@Test
	fun `All pending while job runs is active`() {
		val state = syncState(syncComplete = false, Status.Pending, Status.Pending)

		assertTrue(state.hasActiveSync)
	}

	@Test
	fun `Skipped pending project after completion is not active`() {
		// A project without a server id stays Pending forever; once the job marks
		// syncComplete the dialog should still close without confirmation.
		val state = syncState(syncComplete = true, Status.Complete, Status.Pending)

		assertFalse(state.hasActiveSync)
	}

	@Test
	fun `Project needing conflict resolution is not active`() {
		// A conflict stops the project's sync in a terminal state; closing the dialog
		// should not prompt the cancel confirmation.
		val state = syncState(syncComplete = false, Status.Complete, Status.NeedsResolution)

		assertFalse(state.hasActiveSync)
	}

	@Test
	fun `Canceled projects are not active`() {
		val state = syncState(syncComplete = true, Status.Canceled, Status.Canceled)

		assertFalse(state.hasActiveSync)
	}
}
