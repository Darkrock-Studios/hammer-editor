package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.wear.sync.SyncCoordinator
import com.darkrockstudios.apps.hammer.wear.sync.SyncScheduler

/**
 * Forgets the account and everything synced from it. The watch holds no data of its own beyond
 * what sync brought down, and leftover account sync state would act on the next account signed in.
 *
 * Callers run this in the app scope: dropping the server settings navigates away from the screen
 * that started it.
 */
class SignOutUseCase(
	private val globalSettingsStore: GlobalSettingsStore,
	private val projectsRepository: ProjectsRepository,
	private val subscriptions: SubscribedProjectsRepository,
	private val syncScheduler: SyncScheduler,
	private val syncCoordinator: SyncCoordinator,
) {
	suspend fun signOut() {
		syncScheduler.cancel()
		syncCoordinator.runExclusive {
			try {
				globalSettingsStore.deleteServerSettings()
				projectsRepository.deleteAllLocalData()
			} finally {
				// Even a failed wipe must not leave this account's subscriptions or its auto sync
				// latch behind for whoever signs in next.
				subscriptions.clear()
				syncCoordinator.resetAutoSync()
			}
		}
	}
}
