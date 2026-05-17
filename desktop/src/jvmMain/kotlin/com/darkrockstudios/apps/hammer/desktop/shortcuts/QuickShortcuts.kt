package com.darkrockstudios.apps.hammer.desktop.shortcuts

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Cross-platform façade for the OS-level recent-projects launcher menu
 * (Windows Jump List, Linux Quicklist, macOS Dock, …).
 *
 * The concrete implementation is selected by OS at Koin construction time;
 * callers should not need to know which one is active.
 */
interface QuickShortcuts {
	/** Emits a [ProjectDef] when the user clicks an item in a live, in-process menu (Linux quicklist, macOS Dock). No-op for platforms whose menu items relaunch the executable instead (Windows jump list). */
	val projectClicks: SharedFlow<ProjectDef>

	/** Called once at app startup. Implementations register native resources here. */
	fun init() {}

	/** Rebuild the menu from the current recents. [excludeCurrent] suppresses the currently-open project on platforms that surface in-process clicks. */
	suspend fun refresh(excludeCurrent: ProjectDef? = null)

	/** Called once at app shutdown. Implementations release native resources here. */
	fun dispose() {}
}

class NoOpQuickShortcuts : QuickShortcuts {
	override val projectClicks: SharedFlow<ProjectDef> = MutableSharedFlow<ProjectDef>().asSharedFlow()
	override suspend fun refresh(excludeCurrent: ProjectDef?) = Unit
}
