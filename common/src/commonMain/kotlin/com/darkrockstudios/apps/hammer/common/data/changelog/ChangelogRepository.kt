package com.darkrockstudios.apps.hammer.common.data.changelog

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * The baked changelog plus whether the user has seen it. Loaded once per session; the
 * resource can't change while the app runs.
 */
class ChangelogRepository(
	private val datasource: ChangelogDatasource,
	private val settingsStore: GlobalSettingsStore,
) {
	private val lock = reentrantLock()
	private var cached: Changelog? = null

	suspend fun getChangelog(): Changelog? {
		lock.withLock { cached }?.let { return it }

		val changelog = datasource.loadChangelog() ?: return null
		lock.withLock { cached = changelog }
		return changelog
	}

	suspend fun hasUnseenChangelog(): Boolean {
		val changelog = getChangelog() ?: return false
		return changelog.version != settingsStore.globalSettings.lastSeenChangelogVersion
	}

	suspend fun markSeen() {
		val version = getChangelog()?.version ?: return
		settingsStore.updateSettings { it.copy(lastSeenChangelogVersion = version) }
	}
}
