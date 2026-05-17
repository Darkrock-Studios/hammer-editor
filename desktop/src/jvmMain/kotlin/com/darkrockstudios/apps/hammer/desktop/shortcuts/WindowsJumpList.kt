package com.darkrockstudios.apps.hammer.desktop.shortcuts

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.desktop.PROJECT_FLAG
import com.darkrockstudios.apps.hammer.jump_list_recent_projects
import io.github.aakira.napier.Napier
import io.github.kdroidfilter.nucleus.launcher.windows.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext

class WindowsJumpList(
	private val projectsRepository: ProjectsRepository,
	private val ioDispatcher: CoroutineContext,
) : QuickShortcuts {

	// Windows jump-list items relaunch the executable rather than firing in-process events.
	override val projectClicks: SharedFlow<ProjectDef> = MutableSharedFlow<ProjectDef>().asSharedFlow()

	override fun init() {
		if (!WindowsJumpListManager.isAvailable) return
		runCatching { WindowsJumpListManager.setProcessAppId() }
			.onFailure { Napier.w("WindowsJumpListManager.setProcessAppId failed", it) }
	}

	override suspend fun refresh(excludeCurrent: ProjectDef?) {
		if (!WindowsJumpListManager.isAvailable) return

		val categoryName = runCatching { getString(Res.string.jump_list_recent_projects) }
			.getOrDefault("Recent Projects")

		withContext(ioDispatcher) {
			runCatching {
				val items = projectsRepository.getRecentProjects(MAX_RECENT_PROJECTS).map { def ->
					JumpListItem(
						title = def.name,
						arguments = "--$PROJECT_FLAG \"${def.name}\"",
						icon = TaskbarIconSource.FromStock(StockIcon.FOLDER_OPEN),
					)
				}

				WindowsJumpListManager.setJumpList(
					categories = listOf(JumpListCategory(name = categoryName, items = items)),
					tasks = emptyList(),
				)
			}.onFailure {
				Napier.w("Failed to refresh Windows jump list", it)
			}
		}
	}

	private companion object {
		const val MAX_RECENT_PROJECTS = 5
	}
}
