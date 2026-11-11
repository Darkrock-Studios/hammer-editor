package com.darkrockstudios.apps.hammer.android.shortcuts

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ProjectShortcutsManager(
	private val context: Context,
	private val projectsRepository: ProjectsRepository,
	private val ioDispatcher: CoroutineContext,
) {

	suspend fun refresh() = withContext(ioDispatcher) {
		runCatching {
			val recent = projectsRepository.getRecentProjects(MAX_DYNAMIC_SHORTCUTS)

			val shortcuts = recent.mapIndexed { index, def ->
				ShortcutInfoCompat.Builder(context, shortcutId(def.name))
					.setShortLabel(def.name)
					.setLongLabel(def.name)
					.setIcon(IconCompat.createWithResource(context, R.drawable.hammer_icon))
					.setRank(index)
					.setIntent(ProjectRootActivity.createShortcutIntent(context, def.name))
					.build()
			}

			ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
		}.onFailure {
			Logger.e("Failed to refresh project shortcuts", it)
		}
	}

	private fun shortcutId(projectName: String): String = "recent_project_$projectName"

	private companion object {
		const val MAX_DYNAMIC_SHORTCUTS = 3
	}
}
