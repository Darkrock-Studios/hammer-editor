package com.darkrockstudios.apps.hammer.common.components

import com.arkivanov.decompose.ComponentContext
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagSuggesting
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectSpellCheckRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class ProjectComponentBase(
	val projectDef: ProjectDef,
	componentContext: ComponentContext
) : ComponentBase(componentContext), ProjectScoped, TagSuggesting {
	override val projectScope = ProjectDefScope(projectDef)

	private val tagIndexService: TagIndexService by projectInject()
	private val projectSpellCheck: ProjectSpellCheckRepository by projectInject()

	override fun suggestTags(prefix: String, limit: Int): List<String> =
		tagIndexService.suggest(prefix, limit).map { it.tag }

	/** Collects whether the project's language allows spell check; call from onCreate. [onChange] runs on the main dispatcher. */
	protected fun watchSpellCheckAllowed(onChange: (Boolean) -> Unit) {
		scope.launch {
			projectSpellCheck.spellCheckAllowed.collect { allowed ->
				withContext(dispatcherMain) {
					onChange(allowed)
				}
			}
		}
	}

	/** Collects whether encyclopedia names feed the spell-check dictionary (global AND project toggles); call from onCreate. [onChange] runs on the main dispatcher. */
	protected fun watchEncyclopediaDictionaryEnabled(onChange: (Boolean) -> Unit) {
		scope.launch {
			projectSpellCheck.encyclopediaDictionaryEnabled.collect { enabled ->
				withContext(dispatcherMain) {
					onChange(enabled)
				}
			}
		}
	}
}

abstract class SavableProjectComponentBase<S : Any>(
	val projectDef: ProjectDef,
	componentContext: ComponentContext
) : SavableComponent<S>(componentContext), ProjectScoped, TagSuggesting {
	override val projectScope = ProjectDefScope(projectDef)

	private val tagIndexService: TagIndexService by projectInject()

	override fun suggestTags(prefix: String, limit: Int): List<String> =
		tagIndexService.suggest(prefix, limit).map { it.tag }
}