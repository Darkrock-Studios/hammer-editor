package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.darkrockstudios.texteditor.spellcheck.diagnostics.LineDiagnostic
import com.darkrockstudios.texteditor.spellcheck.diagnostics.TextDiagnosticsChecker
import com.darkrockstudios.texteditor.spellcheck.diagnostics.TextDiagnosticsState
import com.darkrockstudios.texteditor.spellcheck.diagnostics.rememberTextDiagnosticsState
import com.darkrockstudios.texteditor.state.TextEditorState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

/**
 * Underlines what active plugins' text checks find in [textState], written in [language]. Nothing is
 * checked until [languageLoaded], so a check never runs with the wrong language.
 */
@Composable
fun rememberPluginTextDiagnostics(
	textState: TextEditorState,
	language: String?,
	languageLoaded: Boolean,
): TextDiagnosticsState {
	val providers = koinInject<PluginUiRegistry>().textDiagnostics()
	val checker = remember(providers, language, languageLoaded) {
		if (providers.isEmpty() || !languageLoaded) return@remember null
		TextDiagnosticsChecker { lines ->
			val merged = List(lines.size) { mutableListOf<LineDiagnostic>() }
			providers.forEach { provider ->
				// The editor's effects run this, where a throw would take the app down.
				val found = try {
					provider.diagnose(lines, language)
				} catch (e: CancellationException) {
					throw e
				} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
					Napier.w(e) { "Text check '${provider.label}' failed" }
					emptyList()
				}
				found.forEachIndexed { index, diagnostics ->
					merged.getOrNull(index)?.addAll(diagnostics.map { LineDiagnostic(it.start, it.end, it.message, it.fixes) })
				}
			}
			merged
		}
	}
	return rememberTextDiagnosticsState(textState, checker)
}
