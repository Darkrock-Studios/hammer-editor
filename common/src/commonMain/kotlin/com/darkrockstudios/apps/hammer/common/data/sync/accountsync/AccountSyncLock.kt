package com.darkrockstudios.apps.hammer.common.data.sync.accountsync

import kotlinx.coroutines.sync.Mutex

/**
 * One whole account sync at a time in this process, so a background sync can skip one already
 * running instead of repeating it. Server sessions are guarded separately by [AccountSessionLock]
 * and, per project, by ProjectSyncGateRepository.
 */
class AccountSyncLock {
	internal val mutex = Mutex()
}
