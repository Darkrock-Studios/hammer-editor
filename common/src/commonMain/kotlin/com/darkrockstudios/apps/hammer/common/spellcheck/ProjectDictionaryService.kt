package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Feeds the project's Encyclopedia entry names and aliases to the spell checker as
 * session words for the lifetime of the project scope. Gated on the global
 * spell-check setting and the project's own toggle, both live-reactive.
 */
@OptIn(FlowPreview::class)
class ProjectDictionaryService(
	private val projectDef: ProjectDef,
	private val encyclopediaRepository: EncyclopediaRepository,
	private val projectSpellCheckRepository: ProjectSpellCheckRepository,
	private val spellCheckRepository: SpellCheckRepository,
) : ScopeCallback, ProjectScoped, KoinComponent {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault: CoroutineContext by inject(named(DISPATCHER_DEFAULT))
	private val serviceScope = CoroutineScope(dispatcherDefault)

	init {
		projectScope.scope.registerCallback(this)
		serviceScope.launch {
			combine(
				projectSpellCheckRepository.encyclopediaDictionaryEnabled,
				encyclopediaRepository.entryContentChangedFlow.onStart { emit(Unit) },
			) { enabled, _ -> enabled }
				.debounce(REBUILD_DEBOUNCE)
				.collect { enabled ->
					if (enabled) {
						rebuild()
					} else {
						spellCheckRepository.clearSessionWords(projectDef)
					}
				}
		}
	}

	@Suppress("TooGenericExceptionCaught") // Background rebuild must not crash on any failure
	private suspend fun rebuild() {
		try {
			// entryListFlow's replay cache is not refreshed by entry writes, so force a
			// reload before reading defs - renames change EntryDef.name.
			encyclopediaRepository.loadEntriesImperative()
			val defs = encyclopediaRepository.ensureEntriesLoaded()
			val words = coroutineScope {
				defs.map { def ->
					async {
						val entry = encyclopediaRepository.loadEntry(def).entry
						if (entry.excludeFromDictionary) emptySet()
						else tokenizeDictionaryWords(listOf(entry.name) + entry.aliases)
					}
				}.awaitAll()
			}.flatten().toSet()
			spellCheckRepository.setSessionWords(projectDef, words)
		} catch (t: Throwable) {
			Napier.e("ProjectDictionaryService rebuild failed", t)
		}
	}

	override fun onScopeClose(scope: Scope) {
		spellCheckRepository.clearSessionWords(projectDef)
		serviceScope.cancel("ProjectDictionaryService closed")
	}

	private companion object {
		val REBUILD_DEBOUNCE = 150.milliseconds
	}
}
