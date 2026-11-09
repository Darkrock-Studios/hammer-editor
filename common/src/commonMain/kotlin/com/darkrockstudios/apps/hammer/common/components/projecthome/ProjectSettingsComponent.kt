package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickSession
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.components.images.findPickedImage
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettingsComponent
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.data.projectplugins.ProjectPluginsRepository
import com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectDictionaryUseCase
import com.darkrockstudios.apps.hammer.common.spellcheck.displayName
import com.darkrockstudios.apps.hammer.common.util.AvailableLocalesProvider
import com.darkrockstudios.apps.hammer.common.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.ProjectSyncToggle
import io.github.aakira.napier.Napier
import org.koin.core.component.inject

class ProjectSettingsComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
) : ProjectComponentBase(projectDef, componentContext), ProjectSettings {

	private val mainDispatcher by injectMainDispatcher()
	private val projectDataRepository: ProjectDataRepository by projectInject()
	private val projectDictionary: ProjectDictionaryUseCase by projectInject()
	private val accountTagService: AccountTagService by inject()
	private val availableLocalesProvider: AvailableLocalesProvider by inject()
	private val projectPlugins: ProjectPluginsRepository by inject()
	private val imagesService: ImagesService by projectInject()
	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val projectsRepository: ProjectsRepository by inject()
	private val syncToggle: ProjectSyncToggle by inject()

	override val projectName: String = projectDef.name

	override val spellCheckSettings: SpellCheckSettings = SpellCheckSettingsComponent(componentContext)

	override val timelineSettings: TimelineSettings =
		TimelineSettingsComponent(childContext(TIMELINE_SETTINGS_KEY), projectDef)

	private val _projectInfoState = MutableValue(ProjectSettings.ProjectInfoState())
	override val projectInfoState: Value<ProjectSettings.ProjectInfoState> = _projectInfoState

	private val _syncState = MutableValue(ProjectSettings.SyncState())
	override val syncState: Value<ProjectSettings.SyncState> = _syncState

	private val _disabledPlugins = MutableValue(emptySet<String>())
	override val disabledPlugins: Value<Set<String>> = _disabledPlugins

	private val coverPick = ImagePickSession(
		requests = projectInject(),
		stateKeeper = stateKeeper,
		key = COVER_PICK_KEY,
		scope = scope,
		mainDispatcher = mainDispatcher,
		onInProgressChange = { pending -> _projectInfoState.getAndUpdate { it.copy(pendingCoverPick = pending) } },
	)

	init {
		scope.launch { accountTagService.refreshProjectTags() }
		scope.launch {
			projectPlugins.disabledPlugins(projectDef.id).collect { disabled ->
				withContext(mainDispatcher) { _disabledPlugins.value = disabled }
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
		scope.launch {
			coverImageFlow(projectDataRepository, imagesService).collect { cover ->
				withContext(mainDispatcher) {
					_projectInfoState.getAndUpdate { it.copy(coverImage = cover) }
				}
			}
		}
		scope.launch {
			globalSettingsStore.serverSettingsUpdates.collect { settings ->
				withContext(mainDispatcher) {
					_syncState.getAndUpdate { it.copy(serverConfigured = settings != null) }
				}
			}
		}
		scope.launch {
			projectsRepository.syncEnabledUpdates(projectDef).collect { enabled ->
				val owner = projectsRepository.getOwner(projectDef)
				withContext(mainDispatcher) {
					_syncState.getAndUpdate { it.copy(syncEnabled = enabled, owner = owner) }
				}
			}
		}
		coverPick.restore(ImagePickTarget.ProjectCover, ::setCover)
	}

	override fun setSyncEnabled(enabled: Boolean) {
		if (_syncState.value.owner != null) return
		if (!enabled) {
			_syncState.getAndUpdate { it.copy(confirmingOff = true) }
			return
		}
		scope.launch {
			val result = syncToggle.turnOn(projectDef)
			if (result == ProjectSyncToggle.Result.Failed) Napier.w("Could not turn sync on for '${projectDef.name}'")
			withContext(mainDispatcher) {
				_syncState.getAndUpdate { it.copy(deletedOnServer = result == ProjectSyncToggle.Result.DeletedOnServer) }
			}
		}
	}

	override fun confirmSyncOff() {
		_syncState.getAndUpdate { it.copy(confirmingOff = false) }
		scope.launch {
			if (syncToggle.turnOff(projectDef) == ProjectSyncToggle.Result.Failed) {
				Napier.w("Could not turn sync off for '${projectDef.name}'")
			}
		}
	}

	override fun dismissSyncDialog() {
		_syncState.getAndUpdate { it.copy(confirmingOff = false, deletedOnServer = false) }
	}

	override fun pickCoverImage() {
		coverPick.start(ImagePickTarget.ProjectCover, ::setCover)
	}

	private suspend fun setCover(imageId: Int) {
		val image = findPickedImage(imageId, imagesService) ?: return
		projectDataRepository.updateData { it.copy(coverImageId = image.id) }
	}

	override fun clearCoverImage() {
		scope.launch {
			projectDataRepository.updateData { it.copy(coverImageId = null) }
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

	override fun addDictionaryWord(word: String) {
		scope.launch { projectDictionary.addWord(word) }
	}

	override fun removeDictionaryWord(word: String) {
		scope.launch { projectDictionary.removeWord(word) }
	}

	override fun setPluginEnabled(pluginId: String, enabled: Boolean) {
		scope.launch { projectPlugins.setPluginEnabled(projectDef.id, pluginId, enabled) }
	}

	override fun suggestProjectTags(prefix: String): List<String> {
		// Exclude this project's own tags so it suggests from the *other* projects' / ideas'
		// vocabulary, rather than offering back tags already applied here.
		return accountTagService.suggest(prefix, exclude = _projectInfoState.value.data.tags)
			.map { it.tag }
	}
}

private const val COVER_PICK_KEY = "project_settings_cover_pick"
private const val TIMELINE_SETTINGS_KEY = "project_settings_timeline"
