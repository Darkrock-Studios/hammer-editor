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
 * Feeds the project's user dictionary words, plus its Encyclopedia entry names and
 * aliases, to the spell checker as session words for the lifetime of the project
 * scope. Gated on spell check being enabled; the encyclopedia half is further gated
 * on the global feature setting and the project's own toggle, all live-reactive.
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
				projectSpellCheckRepository.userDictionaryWords,
				encyclopediaRepository.entryContentChangedFlow.onStart { emit(Unit) },
			) { spellCheckOn, encyclopediaOn, userWords, _ ->
				if (spellCheckOn) Sources(encyclopediaOn, userWords) else null
			}
				.debounce(REBUILD_DEBOUNCE)
				.collect { sources ->
					if (sources != null) {
						rebuild(sources)
					} else {
						spellCheckRepository.clearSessionWords(projectDef)
					}
				}
		}
	}

	private data class Sources(val encyclopediaOn: Boolean, val userWords: Set<String>)

	@Suppress("TooGenericExceptionCaught") // Background rebuild must not crash on any failure
	private suspend fun rebuild(sources: Sources) {
		try {
			val encyclopediaWords = if (sources.encyclopediaOn) loadEncyclopediaWords() else emptySet()
			// Both the exact spelling and the tokenized form go in: the tokenizer lowercases,
			// which is what the encyclopedia path relies on, but a mixed-case word like
			// "McKinley" must also be accepted as typed.
			val words = encyclopediaWords + sources.userWords + tokenizeDictionaryWords(sources.userWords)
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

	private suspend fun loadEncyclopediaWords(): Set<String> {
		// entryListFlow's replay cache is not refreshed by entry writes, so force a
		// reload before reading defs - renames change EntryDef.name.
		val defs = encyclopediaRepository.loadEntriesImperative()
		return coroutineScope {
			defs.map { def ->
				async {
					val entry = encyclopediaRepository.loadEntry(def).entry
					if (entry.excludeFromDictionary) emptySet()
					else tokenizeDictionaryWords(listOf(entry.name) + entry.aliases)
				}
			}.awaitAll()
		}.flatten().toSet()
	}

	override fun onScopeClose(scope: Scope) {
		serviceScope.cancel("ProjectDictionaryService closed")
		spellCheckRepository.clearSessionWords(projectDef)
	}

	private companion object {
		val REBUILD_DEBOUNCE = 150.milliseconds
	}
}
