package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettingsComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectdata.SuggestProjectTagsUseCase
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class ProjectSettingsComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
) : ProjectComponentBase(projectDef, componentContext), ProjectSettings {

	private val mainDispatcher by injectMainDispatcher()
	private val projectDataRepository: ProjectDataRepository by projectInject()
	private val suggestProjectTagsUseCase: SuggestProjectTagsUseCase by inject()

	// Loaded once at init; sorted for stable suggestion order. Staleness within a session is
	// fine. Written and read on the main dispatcher only.
	private var otherProjectTags: List<String> = emptyList()

	override val projectName: String = projectDef.name

	override val spellCheckSettings: SpellCheckSettings = SpellCheckSettingsComponent(componentContext)

	private val _projectInfoState = MutableValue(ProjectSettings.ProjectInfoState())
	override val projectInfoState: Value<ProjectSettings.ProjectInfoState> = _projectInfoState

	init {
		scope.launch {
			val tags = suggestProjectTagsUseCase.tagsFromOtherProjects(projectDef).sorted()
			withContext(mainDispatcher) {
				otherProjectTags = tags
			}
		}
		scope.launch {
			projectDataRepository.load()
			projectDataRepository.state.collect { stored ->
				if (stored != null) {
					withContext(mainDispatcher) {
						_projectInfoState.getAndUpdate {
							it.copy(data = stored.data, isLoaded = true)
						}
					}
				}
			}
		}
	}

	override fun setAuthorName(name: String?) {
		val cleaned = name?.takeIf { it.isNotBlank() }
		scope.launch {
			projectDataRepository.updateData { it.copy(authorName = cleaned) }
		}
	}

	override fun setTheme(theme: ProjectTheme?) {
		scope.launch {
			projectDataRepository.updateData { it.copy(theme = theme) }
		}
	}

	override fun setWordCountGoal(goal: WordCountGoal?) {
		scope.launch {
			projectDataRepository.updateData { it.copy(wordCountGoal = goal) }
		}
	}

	override fun setTags(tags: Set<String>) {
		val cleaned = ProjectSettings.cleanProjectTags(tags)
		scope.launch {
			projectDataRepository.updateData { it.copy(tags = cleaned) }
		}
	}

	override fun suggestProjectTags(prefix: String): List<String> {
		if (prefix.isBlank()) return emptyList()
		return otherProjectTags
			.filter { it.startsWith(prefix, ignoreCase = true) }
			.take(MAX_TAG_SUGGESTIONS)
	}

	companion object {
		private const val MAX_TAG_SUGGESTIONS = 5
	}
}
