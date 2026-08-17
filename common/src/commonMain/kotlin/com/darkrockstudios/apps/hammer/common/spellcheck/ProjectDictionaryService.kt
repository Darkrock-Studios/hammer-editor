package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback
import kotlin.time.Duration.Companion.milliseconds

/**
 * Feeds the project's Encyclopedia entry names and aliases to the spell checker as
 * session words for the lifetime of the project scope. Gated on spell check being
 * enabled, the global feature setting and the project's own toggle, all live-reactive.
 */
@OptIn(FlowPreview::class)
class ProjectDictionaryService(
	private val projectDef: ProjectDef,
	private val encyclopediaRepository: EncyclopediaRepository,
	private val projectSpellCheckRepository: ProjectSpellCheckRepository,
	private val spellCheckRepository: SpellCheckRepository,
) : ScopeCallback, ProjectScoped, KoinComponent {

	override val projectScope = ProjectDefScope(projectDef)

	private val dispatcherDefault by injectDefaultDispatcher()
	private val serviceScope = CoroutineScope(dispatcherDefault)

	init {
		projectScope.scope.registerCallback(this)
	}

	/** Starts watching for words to load; called from initializeProjectScope on project open. */
	fun initialize() {
		serviceScope.launch {
			combine(
				projectSpellCheckRepository.spellCheckEnabled,
				projectSpellCheckRepository.encyclopediaDictionaryEnabled,
				encyclopediaRepository.entryContentChangedFlow.onStart { emit(Unit) },
			) { spellCheckOn, featureOn, _ -> spellCheckOn && featureOn }
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
			val defs = encyclopediaRepository.loadEntriesImperative()
			val words = coroutineScope {
				defs.map { def ->
					async {
						val entry = encyclopediaRepository.loadEntry(def).entry
						if (entry.excludeFromDictionary) emptySet()
						else tokenizeDictionaryWords(listOf(entry.name) + entry.aliases)
					}
				}.awaitAll()
			}.flatten().toSet()
			// Never push words after onScopeClose has cleared them: close cancels this
			// scope first, so a rebuild that raced past its last suspension bails here.
			if (!currentCoroutineContext().isActive) return
			spellCheckRepository.setSessionWords(projectDef, words)
		} catch (e: CancellationException) {
			throw e
		} catch (t: Throwable) {
			Napier.e("ProjectDictionaryService rebuild failed", t)
		}
	}

	override fun onScopeClose(scope: Scope) {
		serviceScope.cancel("ProjectDictionaryService closed")
		spellCheckRepository.clearSessionWords(projectDef)
	}

	private companion object {
		val REBUILD_DEBOUNCE = 150.milliseconds
	}
}
