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
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.util.AvailableLocalesProvider
import com.darkrockstudios.apps.hammer.common.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class ProjectSettingsComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
) : ProjectComponentBase(projectDef, componentContext), ProjectSettings {

	private val mainDispatcher by injectMainDispatcher()
	private val projectDataRepository: ProjectDataRepository by projectInject()
	private val accountTagService: AccountTagService by inject()
	private val availableLocalesProvider: AvailableLocalesProvider by inject()

	override val projectName: String = projectDef.name

	override val spellCheckSettings: SpellCheckSettings = SpellCheckSettingsComponent(componentContext)

	private val _projectInfoState = MutableValue(ProjectSettings.ProjectInfoState())
	override val projectInfoState: Value<ProjectSettings.ProjectInfoState> = _projectInfoState

	init {
		scope.launch { accountTagService.refreshProjectTags() }
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

	override val availableLanguages: List<ProjectSettings.LanguageOption> by lazy {
		availableLocalesProvider.allLocales()
			.map { ProjectSettings.LanguageOption(tag = it.toLanguageTag(), displayName = it.displayName()) }
			.sortedBy { it.displayName.lowercase() }
	}

	override fun setProjectLanguage(tag: String?) {
		val cleaned = tag?.takeIf { it.isNotBlank() }
			?.let { Locale.forLanguageTag(it).toLanguageTag() }
			?.takeIf { it.isNotBlank() }
		scope.launch {
			projectDataRepository.updateData { it.copy(language = cleaned) }
		}
	}

	override fun setEncyclopediaDictionaryEnabled(enabled: Boolean) {
		scope.launch {
			projectDataRepository.updateData { it.copy(encyclopediaDictionary = enabled) }
		}
	}

	override fun suggestProjectTags(prefix: String): List<String> {
		// Exclude this project's own tags so it suggests from the *other* projects' / ideas'
		// vocabulary, rather than offering back tags already applied here.
		return accountTagService.suggest(prefix, exclude = _projectInfoState.value.data.tags)
			.map { it.tag }
	}
}
