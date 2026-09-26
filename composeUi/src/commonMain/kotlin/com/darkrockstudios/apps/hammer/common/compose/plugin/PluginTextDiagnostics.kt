package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticSeverity
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsProvider
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsRequest
import com.darkrockstudios.texteditor.spellcheck.diagnostics.DiagnosticFix
import com.darkrockstudios.texteditor.spellcheck.diagnostics.DiagnosticSeverity
import com.darkrockstudios.texteditor.spellcheck.diagnostics.LineDiagnostic
import com.darkrockstudios.texteditor.spellcheck.diagnostics.TextDiagnosticsChecker
import com.darkrockstudios.texteditor.spellcheck.diagnostics.TextDiagnosticsState
import com.darkrockstudios.texteditor.spellcheck.diagnostics.rememberTextDiagnosticsState
import com.darkrockstudios.texteditor.state.TextEditorState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

/**
 * Underlines what active plugins' text checks find in [textState], a scene of [project] written in
 * [language]. Nothing is checked until [languageLoaded], so a check never runs with the wrong language.
 */
@Composable
fun rememberPluginTextDiagnostics(
	textState: TextEditorState,
	project: String,
	language: String?,
	languageLoaded: Boolean,
): TextDiagnosticsState {
	val providers = koinInject<PluginUiRegistry>().textDiagnostics()
	val checker = remember(providers, project, language, languageLoaded) {
		if (providers.isNullOrEmpty() || !languageLoaded) null else PluginChecker(providers, project, language)
	}
	return rememberTextDiagnosticsState(textState, checker)
}

internal class PluginChecker(
	providers: List<TextDiagnosticsProvider>,
	private val project: String,
	private val language: String?,
) : TextDiagnosticsChecker {
	private val byParagraph = providers.filterNot { it.wholeScene }
	private val byScene = providers.filter { it.wholeScene }

	override suspend fun check(lines: List<String>) = diagnose(byParagraph, lines)

	override suspend fun checkText(lines: List<String>) = if (byScene.isEmpty()) null else diagnose(byScene, lines)

	private suspend fun diagnose(providers: List<TextDiagnosticsProvider>, lines: List<String>): List<List<LineDiagnostic>> {
		val merged = List(lines.size) { mutableListOf<LineDiagnostic>() }
		val request = TextDiagnosticsRequest(lines, language, project)
		providers.forEach { provider ->
			// The editor's effects run this, where a throw would take the app down.
			val found = try {
				provider.diagnose(request)
			} catch (e: CancellationException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.w(e) { "Text check '${provider.label}' failed" }
				emptyList()
			}
			found.forEachIndexed { index, diagnostics ->
				merged.getOrNull(index)?.addAll(
					diagnostics.map { issue ->
						LineDiagnostic(
							start = issue.start,
							end = issue.end,
							message = issue.message,
							fixes = issue.fixes.map { DiagnosticFix(it.replacement, it.label) },
							severity = when (issue.severity) {
								TextDiagnosticSeverity.Error -> DiagnosticSeverity.Error
								TextDiagnosticSeverity.Suggestion -> DiagnosticSeverity.Suggestion
							},
						)
					}
				)
			}
		}
		return merged
	}
}
