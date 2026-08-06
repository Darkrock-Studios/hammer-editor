package com.darkrockstudios.apps.hammer.desktop.shortcuts

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import io.github.aakira.napier.Napier
import dev.nucleusframework.freedesktop.icons.FreedesktopIcon
import dev.nucleusframework.launcher.linux.DbusmenuItem
import dev.nucleusframework.launcher.linux.LauncherProperties
import dev.nucleusframework.launcher.linux.LinuxLauncherEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import dev.nucleusframework.launcher.linux.LinuxQuicklist as NucleusQuicklist

class LinuxQuicklist(
	private val projectsRepository: ProjectsRepository,
	private val ioDispatcher: CoroutineContext,
) : QuickShortcuts {
	private var quicklist: NucleusQuicklist? = null
	private val idToProject = ConcurrentHashMap<Int, ProjectDef>()

	private val _projectClicks = MutableSharedFlow<ProjectDef>(extraBufferCapacity = 1)
	override val projectClicks: SharedFlow<ProjectDef> = _projectClicks.asSharedFlow()

	override fun init() {
		if (!LinuxLauncherEntry.isAvailable) return

		runCatching {
			val uri = LinuxLauncherEntry.appUri(detectDesktopFileId())
			val ql = NucleusQuicklist(QUICKLIST_OBJECT_PATH)
			ql.listener = NucleusQuicklist.Listener { id ->
				idToProject[id]?.let { def -> _projectClicks.tryEmit(def) }
			}
			LinuxLauncherEntry.update(uri, LauncherProperties(quicklist = ql.objectPath))
			quicklist = ql
		}.onFailure { Napier.w("Failed to initialize Linux quicklist", it) }
	}

	override suspend fun refresh(excludeCurrent: ProjectDef?) {
		val ql = quicklist ?: return

		withContext(ioDispatcher) {
			runCatching {
				val recent = projectsRepository.getRecentProjects(MAX_RECENT_PROJECTS, excludeCurrent)

				idToProject.clear()
				val items = recent.mapIndexed { idx, def ->
					val id = idx + 1
					idToProject[id] = def
					DbusmenuItem(
						id = id,
						label = def.name,
						icon = FreedesktopIcon.Action.DOCUMENT_OPEN,
					)
				}

				ql.setMenu(items)
			}.onFailure { Napier.w("Failed to refresh Linux quicklist", it) }
		}
	}

	override fun dispose() {
		quicklist?.dispose()
		quicklist = null
		idToProject.clear()
	}

	private fun detectDesktopFileId(): String = when {
		System.getenv("FLATPAK_ID") != null -> "studio.darkrock.hammer.desktop"
		System.getenv("SNAP") != null -> "hammer-editor.desktop"
		else -> "hammer.desktop"
	}

	private companion object {
		const val MAX_RECENT_PROJECTS = 5
		const val QUICKLIST_OBJECT_PATH = "/studio/darkrock/hammer/Menu"
	}
}
