import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginChecker
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnostic
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticSeverity
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsProvider
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsRequest
import com.darkrockstudios.texteditor.spellcheck.diagnostics.DiagnosticSeverity
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginCheckerTest {
	private val requests = mutableListOf<Pair<String, TextDiagnosticsRequest>>()

	// Flags the first character of each paragraph.
	private fun provider(label: String, wholeScene: Boolean, severity: TextDiagnosticSeverity = TextDiagnosticSeverity.Error) =
		TextDiagnosticsProvider(label, wholeScene) { request ->
			requests += label to request
			request.paragraphs.map { if (it.isEmpty()) emptyList() else listOf(TextDiagnostic(0, 1, label, severity = severity)) }
		}

	@Test
	fun `paragraph checks get the lines to check, and scene checks the whole text`() = runBlocking {
		val checker = PluginChecker(
			listOf(provider("Grammar", wholeScene = false), provider("Echoes", wholeScene = true, TextDiagnosticSeverity.Suggestion)),
			project = "Storm",
			language = "en",
		)

		val byLine = checker.check(listOf("One."))
		val byText = checker.checkText(listOf("One.", "", "Two."))!!

		assertEquals(listOf("Grammar" to listOf("One."), "Echoes" to listOf("One.", "", "Two.")), requests.map { it.first to it.second.paragraphs })
		assertEquals(listOf("Storm" to "en"), requests.map { it.second.project to it.second.language }.distinct())
		assertEquals(listOf("Grammar"), byLine.single().map { it.message })
		assertEquals(listOf(1, 0, 1), byText.map { it.size })
		assertEquals(DiagnosticSeverity.Suggestion, byText.first().single().severity)
	}

	@Test
	fun `without scene checks nothing checks the whole text`() = runBlocking {
		assertNull(PluginChecker(listOf(provider("Grammar", wholeScene = false)), "Storm", null).checkText(listOf("One.")))
	}
}
