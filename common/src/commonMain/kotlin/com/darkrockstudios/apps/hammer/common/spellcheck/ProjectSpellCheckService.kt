package com.darkrockstudios.apps.hammer.common.spellcheck

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellChecker
import io.github.aakira.napier.Napier
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback

/**
 * Project-aware spell check. It is a view of the global [SpellCheckRepository] in which the
 * dictionary is withheld (null) while the project's declared language doesn't leniently match
 * the user's spell-check dictionary locale; an unset project language never gates.
 *
 * Once [initialize]d it also feeds the project's user dictionary words, plus its Encyclopedia
 * entry names and aliases, to the spell checker as session words for the lifetime of the
 * project scope. That is gated on spell check being enabled; the encyclopedia half is further
 * gated on the global feature setting and the project's own toggle, all live-reactive.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ProjectSpellCheckService(
	val projectDef: ProjectDef,
	private val spellCheckRepository: SpellCheckRepository,
	globalSettingsStore: GlobalSettingsStore,
	private val projectDataRepository: ProjectDataRepository,
) : ScopeCallback, ProjectScoped, KoinComponent {
	override val projectScope = ProjectDefScope(projectDef)

	// Lazy so consumers that only read the flows don't construct the encyclopedia.
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

	private val dispatcherDefault by injectDefaultDispatcher()
	private val serviceScope = CoroutineScope(dispatcherDefault)

	init {
		projectScope.scope.registerCallback(this)
	}

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

	/** The project's user-added dictionary words. Cleaned on read: the stored set may come from sync. */
	val userDictionaryWords: Flow<Set<String>> = projectDataRepository.state
		.map { stored -> normalizeStoredDictionaryWords(stored?.data?.dictionaryWords ?: emptySet()) }
		.onStart { projectDataRepository.load() }
		.distinctUntilChanged()

	/** Starts watching for words to load; called from initializeProjectScope on project open. */
	fun initialize() {
		serviceScope.launch {
			// Entries are only re-read when one changes or the feature toggles; a user word
			// edit unions into the cached set without touching the encyclopedia on disk.
			val encyclopediaWords = combine(
				encyclopediaDictionaryEnabled,
				encyclopediaRepository.entryContentChangedFlow.onStart { emit(Unit) },
			) { enabled, _ -> enabled }
				.debounce(REBUILD_DEBOUNCE)
				.mapLatest { enabled -> if (enabled) loadEncyclopediaWords() else emptySet() }

			combine(
				spellCheckEnabled,
				encyclopediaWords,
				userDictionaryWords,
			) { spellCheckOn, encyclopedia, userWords ->
				// Both the exact spelling and the tokenized form go in: the tokenizer lowercases,
				// which is what the encyclopedia path relies on, but a mixed-case word like
				// "McKinley" must also be accepted as typed.
				if (spellCheckOn) encyclopedia + userWords + tokenizeDictionaryWords(userWords) else null
			}
				.debounce(REBUILD_DEBOUNCE)
				.collect { words ->
					// Never push words after onScopeClose has cleared them: close cancels this
					// scope first, so a collect that raced past its last suspension bails here.
					if (!currentCoroutineContext().isActive) return@collect
					if (words != null) {
						spellCheckRepository.setSessionWords(projectDef, words)
					} else {
						spellCheckRepository.clearSessionWords(projectDef)
					}
				}
		}
	}

	@Suppress("TooGenericExceptionCaught") // A failed reload must not kill the collector
	private suspend fun loadEncyclopediaWords(): Set<String> {
		try {
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
			}.flatMapTo(mutableSetOf()) { it }
		} catch (e: CancellationException) {
			throw e
		} catch (t: Throwable) {
			Napier.e("ProjectSpellCheckService encyclopedia reload failed", t)
			return emptySet()
		}
	}

	override fun onScopeClose(scope: Scope) {
		serviceScope.cancel("ProjectSpellCheckService closed")
		spellCheckRepository.clearSessionWords(projectDef)
	}

	private companion object {
		val REBUILD_DEBOUNCE = 150.milliseconds
	}
}
