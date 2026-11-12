package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnostic
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticSeverity
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsProvider
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsRequest
import com.darkrockstudios.texteditor.spellcheck.diagnostics.DiagnosticFix
import com.darkrockstudios.texteditor.spellcheck.diagnostics.DiagnosticSeverity
import com.darkrockstudios.texteditor.spellcheck.diagnostics.LineCheck
import com.darkrockstudios.texteditor.spellcheck.diagnostics.LineDiagnostic
import com.darkrockstudios.texteditor.spellcheck.diagnostics.TextDiagnosticsChecker
import com.darkrockstudios.texteditor.spellcheck.diagnostics.TextDiagnosticsState
import com.darkrockstudios.texteditor.spellcheck.diagnostics.rememberTextDiagnosticsState
import com.darkrockstudios.texteditor.state.TextEditorState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.compose.koinInject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Underlines what the text checks of the plugins [project] has on find in [textState], a scene of it
 * written in [language]. Nothing is checked until [languageLoaded], so a check never runs with the wrong language.
 * When a check fails, the text is checked again once it may run, without waiting for an edit.
 */
@Composable
fun rememberPluginTextDiagnostics(
	textState: TextEditorState,
	project: String,
	language: String?,
	languageLoaded: Boolean,
): TextDiagnosticsState {
	val providers = koinInject<PluginUiRegistry>().textDiagnostics(LocalDisabledPlugins.current)
	val io = rememberIoDispatcher()
	val checker = remember(providers, project, language, languageLoaded) {
		if (providers.isNullOrEmpty() || !languageLoaded) null else PluginChecker(providers, project, language, io = io)
	}
	val state = rememberTextDiagnosticsState(textState, checker)
	LaunchedEffect(state, checker) { checker?.refreshWhenDue { state.refresh() } }
	return state
}

/** Calls [refresh] each time a failed check may run again, so its lines are checked without an edit. */
internal suspend fun PluginChecker.refreshWhenDue(refresh: suspend () -> Unit) {
	retryAt.collectLatest { at ->
		if (at == null) return@collectLatest
		delay(-at.elapsedNow())
		refresh()
	}
}

/**
 * Runs the plugins' checks side by side on [io], as they block. A check that fails leaves the lines it
 * checks incomplete, so they are sent again, to it alone, as each check's results are kept by line; and
 * it isn't called for a while: [FIRST_RETRY] after its first failure, doubling up to [MAX_RETRY], until
 * it succeeds.
 */
internal class PluginChecker(
	providers: List<TextDiagnosticsProvider>,
	private val project: String,
	private val language: String?,
	private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
	private val io: CoroutineContext = EmptyCoroutineContext,
) : TextDiagnosticsChecker {
	private val byParagraph = providers.filterNot { it.wholeScene }
	private val byScene = providers.filter { it.wholeScene }

	private class Failure(val count: Int, val until: ComparableTimeMark)

	private val lock = Mutex()
	private val failures = mutableMapOf<TextDiagnosticsProvider, Failure>()
	private val _retryAt = MutableStateFlow<ComparableTimeMark?>(null)

	/** When the soonest failed check may run again; null while none has failed. */
	val retryAt: StateFlow<ComparableTimeMark?> = _retryAt.asStateFlow()

	// Each paragraph check's results by line text, for lines another check left incomplete. The editor
	// sends every incomplete line on each refresh, so a line missing from a call is done or gone.
	private val checked = byParagraph.associateWith { LinkedHashMap<String, List<LineDiagnostic>>() }

	override suspend fun check(lines: List<String>): List<LineCheck> {
		coroutineScope {
			byParagraph.map { provider ->
				async(io) {
					val cache = checked.getValue(provider)
					val unchecked = lines.filter { lock.withLock { it !in cache } }.distinct()
					if (unchecked.isEmpty()) return@async
					val found = run(provider, TextDiagnosticsRequest(unchecked, language, project)) ?: return@async
					lock.withLock { unchecked.forEachIndexed { index, line -> cache[line] = found.getOrNull(index).orEmpty() } }
				}
			}.awaitAll()
		}
		return lock.withLock {
			val results = lines.map { line ->
				val byCheck = byParagraph.map { checked.getValue(it)[line] }
				LineCheck(sharingUnderlines(byCheck.flatMap { it.orEmpty() }), byCheck.none { it == null })
			}
			val incomplete = lines.filterIndexed { index, _ -> !results[index].complete }.toSet()
			checked.values.forEach { it.keys.retainAll(incomplete) }
			results
		}
	}

	override suspend fun checkText(lines: List<String>): List<List<LineDiagnostic>>? {
		if (byScene.isEmpty()) return null
		val request = TextDiagnosticsRequest(lines, language, project)
		val results = coroutineScope { byScene.map { async(io) { run(it, request) } }.awaitAll() }
		return List(lines.size) { index -> sharingUnderlines(results.flatMap { it?.getOrNull(index).orEmpty() }) }
	}

	/** Null when the check failed, now or too recently to run again. */
	private suspend fun run(provider: TextDiagnosticsProvider, request: TextDiagnosticsRequest): List<List<LineDiagnostic>>? {
		if (lock.withLock { failures[provider]?.until?.hasNotPassedNow() == true }) return null
		// The editor's effects run this, where a throw would take the app down.
		return try {
			provider.diagnose(request).map { line -> line.map(::lineDiagnostic) }
				.also { lock.withLock { if (failures.remove(provider) != null) publishRetry() } }
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.w(e) { "Text check '${provider.label}' failed" }
			lock.withLock {
				val count = (failures[provider]?.count ?: 0) + 1
				val wait = (FIRST_RETRY * (1 shl minOf(count - 1, MAX_DOUBLINGS))).coerceAtMost(MAX_RETRY)
				failures[provider] = Failure(count, timeSource.markNow() + wait)
				publishRetry()
			}
			null
		}
	}

	private fun publishRetry() {
		_retryAt.value = failures.values.minOfOrNull { it.until }
	}

	companion object {
		val FIRST_RETRY = 30.seconds
		val MAX_RETRY = 5.minutes
		private const val MAX_DOUBLINGS = 4
	}
}

private fun lineDiagnostic(issue: TextDiagnostic) = LineDiagnostic(
	start = issue.start,
	end = issue.end,
	message = issue.message,
	fixes = issue.fixes.map { DiagnosticFix(it.replacement, it.label) },
	severity = when (issue.severity) {
		TextDiagnosticSeverity.Error -> DiagnosticSeverity.Error
		TextDiagnosticSeverity.Suggestion -> DiagnosticSeverity.Suggestion
	},
)

/**
 * One underline per range, so checks that overlap, such as two grammar plugins, don't stack: the
 * first of the strongest severity's message, with every check's fixes.
 */
private fun sharingUnderlines(line: List<LineDiagnostic>): List<LineDiagnostic> =
	line.groupBy { it.start to it.end }.values.map { same ->
		val lead = same.firstOrNull { it.severity == DiagnosticSeverity.Error } ?: same.first()
		if (same.size == 1) lead else lead.copy(fixes = same.flatMap { it.fixes }.distinctBy { it.replacement })
	}
