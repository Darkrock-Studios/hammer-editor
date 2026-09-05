package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Project-aware view of the global [SpellCheckRepository]: the dictionary is withheld
 * (null) while the project's declared language doesn't leniently match the user's
 * spell-check dictionary locale. An unset project language never gates.
 */
class ProjectSpellCheckRepository(
	spellCheckRepository: SpellCheckRepository,
	globalSettingsStore: GlobalSettingsStore,
	private val projectDataRepository: ProjectDataRepository,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	/** True while the project language allows the current dictionary locale. */
	val spellCheckAllowed: Flow<Boolean> = combine(
		globalSettingsStore.globalSettingsUpdates,
		projectDataRepository.state,
	) { settings, stored ->
		isSpellCheckAllowedForProject(stored?.data?.language, settings.spellCheckSettings.locale)
	}
		.onStart { projectDataRepository.load() }
		.distinctUntilChanged()

	/** [SpellCheckRepository.dictionaryFlow] gated on [spellCheckAllowed]. */
	val dictionaryFlow: Flow<PlatformSpellChecker?> = combine(
		spellCheckRepository.dictionaryFlow,
		spellCheckAllowed,
	) { checker, allowed ->
		if (allowed) checker else null
	}.distinctUntilChanged()

	/** The global spell-check master switch. */
	val spellCheckEnabled: Flow<Boolean> = globalSettingsStore.globalSettingsUpdates
		.map { it.spellCheckSettings.enabled }
		.distinctUntilChanged()

	/**
	 * True while encyclopedia names feed the spell-check session dictionary:
	 * the global setting AND this project's own toggle. Gates the feature's
	 * per-entry UI as well as the word loading itself.
	 */
	val encyclopediaDictionaryEnabled: Flow<Boolean> = combine(
		globalSettingsStore.globalSettingsUpdates,
		projectDataRepository.state,
	) { settings, stored ->
		settings.spellCheckSettings.includeEncyclopediaNames &&
			(stored?.data?.encyclopediaDictionary ?: true)
	}
		.onStart { projectDataRepository.load() }
		.distinctUntilChanged()

	/** The project's user-added dictionary words, as stored. */
	val userDictionaryWords: Flow<Set<String>> = projectDataRepository.state
		.map { stored -> stored?.data?.dictionaryWords ?: emptySet() }
		.onStart { projectDataRepository.load() }
		.distinctUntilChanged()
}
