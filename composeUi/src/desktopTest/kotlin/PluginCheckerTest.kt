import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginChecker
import com.darkrockstudios.apps.hammer.common.compose.plugin.refreshWhenDue
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnostic
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticSeverity
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsProvider
import com.darkrockstudios.apps.hammer.operations.plugin.TextDiagnosticsRequest
import com.darkrockstudios.apps.hammer.operations.plugin.TextFix
import com.darkrockstudios.texteditor.spellcheck.diagnostics.DiagnosticSeverity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

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
		assertEquals(listOf("Grammar"), byLine.single().diagnostics.map { it.message })
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

		val found = checker.check(listOf("teh cat")).single().diagnostics

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

	private var calls = 0

	// Fails while [offline], flagging the first character otherwise.
	private var offline = true
	private val languageTool = TextDiagnosticsProvider("LanguageTool") { request ->
		calls++
		if (offline) error("unreachable")
		request.paragraphs.map { listOf(TextDiagnostic(0, 1, "LanguageTool")) }
	}

	@Test
	fun `a failed check leaves its lines incomplete, with what the others found`() = runBlocking {
		val checker = PluginChecker(listOf(languageTool, flagging("Harper", TextDiagnosticSeverity.Error, end = 2)), "Storm", "en")

		val line = checker.check(listOf("teh cat")).single()

		assertEquals(false, line.complete)
		assertEquals(listOf("Harper"), line.diagnostics.map { it.message })
	}

	@Test
	fun `a failed check waits before it runs again, longer each time, and a success ends the wait`() = runBlocking {
		val time = TestTimeSource()
		val checker = PluginChecker(listOf(languageTool), "Storm", "en", time)

		checker.check(listOf("One."))
		val firstRetry = checker.retryAt.value!!
		time += 29.seconds
		assertEquals(false, checker.check(listOf("One.")).single().complete)
		assertEquals(1, calls)

		time += 1.seconds
		assertTrue(firstRetry.hasPassedNow())
		checker.check(listOf("One."))
		assertEquals(2, calls)
		assertEquals(60.seconds, checker.retryAt.value!! - time.markNow())

		time += 60.seconds
		offline = false
		val line = checker.check(listOf("One.")).single()
		assertEquals(true, line.complete)
		assertNull(checker.retryAt.value)
	}

	@Test
	fun `lines left incomplete go again only to the check that failed`() = runBlocking {
		val harperSaw = mutableListOf<List<String>>()
		val harper = TextDiagnosticsProvider("Harper") { request -> harperSaw += request.paragraphs; request.paragraphs.map { emptyList() } }
		val time = TestTimeSource()
		val checker = PluginChecker(listOf(harper, languageTool), "Storm", "en", time)

		checker.check(listOf("One.", "Two."))
		time += 30.seconds
		offline = false
		val lines = checker.check(listOf("One.", "Two."))

		assertEquals(listOf(listOf("One.", "Two.")), harperSaw)
		assertEquals(listOf(true, true), lines.map { it.complete })
		assertEquals(listOf("LanguageTool"), lines.first().diagnostics.map { it.message })
	}

	@Test
	fun `the text is refreshed once a failed check may run again`() = runTest {
		val checker = PluginChecker(listOf(languageTool), "Storm", "en", TestTimeSource())
		checker.check(listOf("One."))
		var refreshes = 0
		val retrying = launch { checker.refreshWhenDue { refreshes++ } }

		advanceTimeBy(29.seconds)
		assertEquals(0, refreshes)
		advanceTimeBy(2.seconds)
		assertEquals(1, refreshes)
		retrying.cancel()
	}

	@Test
	fun `checks run side by side`() = runBlocking {
		val first = CompletableDeferred<Unit>()
		val waits = TextDiagnosticsProvider("Waits") { request -> first.await(); request.paragraphs.map { emptyList() } }
		val goes = TextDiagnosticsProvider("Goes") { request -> first.complete(Unit); request.paragraphs.map { emptyList() } }

		val line = withTimeout(5.seconds) { PluginChecker(listOf(waits, goes), "Storm", "en").check(listOf("One.")).single() }

		assertEquals(true, line.complete)
	}

	@Test
	fun `without scene checks nothing checks the whole text`() = runBlocking {
		assertNull(PluginChecker(listOf(provider("Grammar", wholeScene = false)), "Storm", null).checkText(listOf("One.")))
	}
}
