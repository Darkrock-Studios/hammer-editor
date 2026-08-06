package com.darkrockstudios.apps.hammer.desktop.shortcuts

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import io.github.aakira.napier.Napier
import dev.nucleusframework.launcher.macos.DockMenuItem
import dev.nucleusframework.launcher.macos.DockMenuListener
import dev.nucleusframework.launcher.macos.MacOsDockMenu
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Wired up but not bound on macOS (see desktopModule): Nucleus' dock menu only
 * shows while the app is running, and thats not useful for what this does.
 * Unlike the Windows jump list and Linux quicklist which work app-closed too.
 */
class MacOsDockShortcuts(
	private val projectsRepository: ProjectsRepository,
	private val ioDispatcher: CoroutineContext,
) : QuickShortcuts {
	private val idToProject = ConcurrentHashMap<Int, ProjectDef>()
	private val _projectClicks = MutableSharedFlow<ProjectDef>(extraBufferCapacity = 1)
	override val projectClicks: SharedFlow<ProjectDef> = _projectClicks.asSharedFlow()

	override fun init() {
		if (!MacOsDockMenu.isAvailable) return
		MacOsDockMenu.listener = DockMenuListener { id ->
			idToProject[id]?.let { def -> _projectClicks.tryEmit(def) }
		}
	}

	override suspend fun refresh(excludeCurrent: ProjectDef?) {
		if (!MacOsDockMenu.isAvailable) return

		withContext(ioDispatcher) {
			runCatching {
				val recent = projectsRepository.getRecentProjects(MAX_RECENT_PROJECTS, excludeCurrent)
				idToProject.clear()
				val items = recent.mapIndexed { idx, def ->
					val id = idx + 1
					idToProject[id] = def
					DockMenuItem(id = id, title = def.name)
				}
				// Defer to the EDT so AWT has installed the NSApplicationDelegate
				// that Nucleus method-swizzles in nativeSetDockMenu.
				javax.swing.SwingUtilities.invokeLater {
					MacOsDockMenu.setDockMenu(items)
				}
			}.onFailure { Napier.w("Failed to refresh macOS dock menu", it) }
		}
	}

	override fun dispose() {
		if (!MacOsDockMenu.isAvailable) return
		runCatching {
			MacOsDockMenu.clearDockMenu()
			MacOsDockMenu.listener = null
		}.onFailure { Napier.w("Failed to clear macOS dock menu", it) }
		idToProject.clear()
	}

	private companion object {
		const val MAX_RECENT_PROJECTS = 5
	}
}
