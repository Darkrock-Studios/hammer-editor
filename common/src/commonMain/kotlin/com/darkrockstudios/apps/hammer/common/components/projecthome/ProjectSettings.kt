package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.spellchecksettings.SpellCheckSettings
import com.darkrockstudios.apps.hammer.common.data.tagindex.cleanTags
import com.darkrockstudios.apps.hammer.common.dependencyinjection.HammerComponent

interface ProjectSettings : HammerComponent {
	val projectName: String
	val spellCheckSettings: SpellCheckSettings
	val projectInfoState: Value<ProjectInfoState>

	/** Every language the platform offers, sorted by display name, for the language picker. */
	val availableLanguages: List<LanguageOption>

	fun setAuthorName(name: String?)
	fun setTheme(theme: ProjectTheme?)
	fun setWordCountGoal(goal: WordCountGoal?)
	fun setTags(tags: Set<String>)

	/** [tag] is a BCP-47 tag; null or blank clears the project language. */
	fun setProjectLanguage(tag: String?)

	fun setEncyclopediaDictionaryEnabled(enabled: Boolean)

	data class LanguageOption(
		val tag: String,
		val displayName: String,
	)

	/** Suggests from the account-level tag vocabulary (project + story-idea tags, via [com.darkrockstudios.apps.hammer.common.data.tagindex.AccountTagService]) — distinct from [com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting.suggestTags], which suggests in-project entity tags. */
	fun suggestProjectTags(prefix: String): List<String>

	data class ProjectInfoState(
		val data: ProjectData = ProjectData(),
		val isLoaded: Boolean = false,
	)

	companion object {
		const val MAX_TAG_SIZE = 64

		/**
		 * The single normalization rule for project tags. The tag field UI must run additions
		 * through this too, so a chip that won't survive persistence is never shown.
		 */
		fun cleanProjectTags(tags: Set<String>): Set<String> =
			cleanTags(tags).filter { it.length <= MAX_TAG_SIZE }.toSet()
	}
}