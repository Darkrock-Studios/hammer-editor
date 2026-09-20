package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryChange
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContent
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
 *
 * Entries are read from disk once, at [initialize]; after that a per-entry token
 * cache is kept fresh purely from [EncyclopediaRepository.entryChangeFlow] events,
 * which carry the written content.
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

	// Confined to the event-collector coroutine; other coroutines only see [words].
	private val tokensByEntry = mutableMapOf<Int, Set<String>>()

	/** Union of all entries' tokens; null until the initial load has seeded the cache. */
	private val words = MutableStateFlow<Set<String>?>(null)

	init {
		projectScope.scope.registerCallback(this)
	}

	/** Starts watching for words to load; called from initializeProjectScope on project open. */
	fun initialize() {
		serviceScope.launch {
			seedCache()
			encyclopediaRepository.entryChangeFlow.collect { change ->
				applyChange(change)
				words.value = tokensByEntry.values.flatMapTo(mutableSetOf()) { it }
			}
		}

		serviceScope.launch {
			val enabledFlow = combine(
				projectSpellCheckRepository.spellCheckEnabled,
				projectSpellCheckRepository.encyclopediaDictionaryEnabled,
			) { spellCheckOn, featureOn -> spellCheckOn && featureOn }

			combine(words, enabledFlow) { currentWords, enabled -> currentWords to enabled }
				.debounce(PUSH_DEBOUNCE)
				.collect { (currentWords, enabled) ->
					// Never push words after onScopeClose has cleared them: close cancels
					// this scope first, so a collect that raced past its last suspension
					// bails here.
					if (!currentCoroutineContext().isActive) return@collect
					when {
						!enabled -> spellCheckRepository.clearSessionWords(projectDef)
						currentWords != null -> spellCheckRepository.setSessionWords(projectDef, currentWords)
					}
				}
		}
	}

	@Suppress("TooGenericExceptionCaught") // Background load must not crash on any failure
	private suspend fun seedCache() {
		try {
			// entryListFlow's replay cache is not refreshed by entry writes, so read the
			// defs imperatively rather than trusting a stale cache.
			val defs = encyclopediaRepository.loadEntriesImperative()
			val loaded = coroutineScope {
				defs.map { def ->
					async {
						def.id to tokensFor(encyclopediaRepository.loadEntry(def).entry)
					}
				}.awaitAll()
			}
			tokensByEntry.clear()
			tokensByEntry.putAll(loaded)
			words.value = tokensByEntry.values.flatMapTo(mutableSetOf()) { it }
		} catch (e: CancellationException) {
			throw e
		} catch (t: Throwable) {
			Napier.e("ProjectDictionaryService initial load failed", t)
		}
	}

	private fun applyChange(change: EntryChange) {
		when (change) {
			is EntryChange.Saved -> tokensByEntry[change.entry.id] = tokensFor(change.entry)
			is EntryChange.Deleted -> tokensByEntry.remove(change.id)
			is EntryChange.ReId -> tokensByEntry.remove(change.oldId)?.let { tokens ->
				tokensByEntry[change.newId] = tokens
			}
		}
	}

	private fun tokensFor(entry: EntryContent): Set<String> =
		if (entry.excludeFromDictionary) emptySet()
		else tokenizeDictionaryWords(listOf(entry.name) + entry.aliases)

	override fun onScopeClose(scope: Scope) {
		serviceScope.cancel("ProjectDictionaryService closed")
		spellCheckRepository.clearSessionWords(projectDef)
	}

	private companion object {
		val PUSH_DEBOUNCE = 150.milliseconds
	}
}
