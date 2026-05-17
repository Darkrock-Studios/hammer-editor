package com.darkrockstudios.apps.hammer.desktop.shortcuts

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.desktop.PROJECT_FLAG
import com.darkrockstudios.apps.hammer.jump_list_recent_projects
import io.github.aakira.napier.Napier
import io.github.kdroidfilter.nucleus.launcher.windows.*
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext

class DesktopJumpListManager(
	private val projectsRepository: ProjectsRepository,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
	private val ioDispatcher: CoroutineContext,
) {

	suspend fun refresh() {
		if (!WindowsJumpListManager.isAvailable) return

		val categoryName = runCatching { getString(Res.string.jump_list_recent_projects) }
			.getOrDefault("Recent Projects")

		withContext(ioDispatcher) {
			runCatching {
				val recent = projectsRepository.getProjects()
					.map { def ->
						val lastAccessed = runCatching {
							projectMetadataDatasource.loadMetadata(def).info.lastAccessed
						}.getOrNull()
						def to lastAccessed
					}
					.sortedByDescending { it.second }
					.take(MAX_RECENT_PROJECTS)
					.map { it.first }

				val items = recent.map { def ->
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
