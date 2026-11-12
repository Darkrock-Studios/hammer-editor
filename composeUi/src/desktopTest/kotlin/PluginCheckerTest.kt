import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginChecker
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnostic
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticSeverity
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsProvider
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsRequest
import com.darkrockstudios.apps.hammer.operations.plugin.TextFix
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

	private fun flagging(label: String, severity: TextDiagnosticSeverity, vararg fixes: String, end: Int = 3, wholeScene: Boolean = false) =
		TextDiagnosticsProvider(label, wholeScene) { request ->
			request.paragraphs.map { listOf(TextDiagnostic(0, end, label, fixes.map(::TextFix), severity)) }
		}

	@Test
	fun `checks flagging the same text share one underline, worded by the strongest`() = runBlocking {
		val checker = PluginChecker(
			listOf(
				flagging("Harper", TextDiagnosticSeverity.Suggestion, "the", "a"),
				flagging("LanguageTool", TextDiagnosticSeverity.Error, "a", "an"),
				flagging("Simple", TextDiagnosticSeverity.Error, "x", end = 2),
			),
			project = "Storm",
			language = "en",
		)

		val found = checker.check(listOf("teh cat")).single()

		assertEquals(listOf(0 to 3, 0 to 2), found.map { it.start to it.end })
		val shared = found.first()
		assertEquals("LanguageTool", shared.message)
		assertEquals(listOf("the", "a", "an"), shared.fixes.map { it.replacement })
		assertEquals(DiagnosticSeverity.Error, shared.severity)
	}

	@Test
	fun `scene checks flagging the same text share one underline too`() = runBlocking {
		val checker = PluginChecker(
			listOf(
				flagging("Echoes", TextDiagnosticSeverity.Suggestion, "very", wholeScene = true),
				flagging("Style", TextDiagnosticSeverity.Suggestion, "quite", wholeScene = true),
			),
			project = "Storm",
			language = "en",
		)

		val shared = checker.checkText(listOf("very very"))!!.single().single()

		assertEquals("Echoes", shared.message)
		assertEquals(listOf("very", "quite"), shared.fixes.map { it.replacement })
	}

	@Test
	fun `without scene checks nothing checks the whole text`() = runBlocking {
		assertNull(PluginChecker(listOf(provider("Grammar", wholeScene = false)), "Storm", null).checkText(listOf("One.")))
	}
}
