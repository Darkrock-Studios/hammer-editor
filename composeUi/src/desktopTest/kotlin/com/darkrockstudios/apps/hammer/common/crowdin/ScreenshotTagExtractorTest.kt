package com.darkrockstudios.apps.hammer.common.crowdin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.allStringResources
import com.darkrockstudios.apps.hammer.common.compose.resources.LocalStringKeyRecorder
import com.darkrockstudios.apps.hammer.common.compose.resources.StringKeyRecorder
import com.darkrockstudios.apps.hammer.common.preview.TABLET_TALL_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectStatsUiTabletPreview
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.getString
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Prototype for the exact Crowdin tag pipeline (stage 1): render a screen, capture
 * every text node's pixel bounds, and map each back to its string resource key.
 *
 * Two mapping sources are compared:
 *  - recorder: keys captured live from `.get()` calls (exact but partial — most of
 *    the app calls `stringResource()` directly).
 *  - table: the full `Res.allStringResources` resolved to English text and reversed,
 *    with `%n$s` templates turned into regexes (covers direct `stringResource()`).
 *
 * Writes JSON to build/crowdin/ for inspection.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTagExtractorTest {

	private data class Template(val key: String, val regex: Regex)

	@Test
	fun extractProjectStatsTags() = runDesktopComposeUiTest(
		width = TABLET_WIDTH_DP,
		height = TABLET_TALL_HEIGHT_DP,
	) {
		val recorder = StringKeyRecorder()

		setContent {
			CompositionLocalProvider(LocalStringKeyRecorder provides recorder) {
				ScreenProjectStatsUiTabletPreview()
			}
		}
		waitForIdle()

		val recorderKeysByText = HashMap<String, MutableList<String>>()
		for ((key, text) in recorder.snapshot()) {
			recorderKeysByText.getOrPut(normalize(text)) { mutableListOf() }.add(key)
		}

		// Full resolved string table: normalized English text -> key(s), plus
		// regex templates for format strings.
		val tableKeysByText = HashMap<String, MutableList<String>>()
		val templates = mutableListOf<Template>()
		runBlocking {
			for ((key, resource) in Res.allStringResources) {
				val text = runCatching { getString(resource) }.getOrNull() ?: continue
				if (text.contains('%')) {
					templates.add(Template(key, templateRegex(text)))
				} else {
					tableKeysByText.getOrPut(normalize(text)) { mutableListOf() }.add(key)
				}
			}
		}

		val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
		val textNodes = mutableListOf<SemanticsNode>()
		collectTextNodes(root, textNodes)

		var matchedRecorder = 0
		var matchedTable = 0
		var matchedTemplate = 0
		var unmatched = 0
		val tags = buildJsonArray {
			for (node in textNodes) {
				val text = nodeText(node) ?: continue
				val norm = normalize(text)
				val bounds = node.boundsInRoot

				val recorderKeys = recorderKeysByText[norm].orEmpty()
				val exactKeys = tableKeysByText[norm].orEmpty()
				val templateKeys = if (recorderKeys.isEmpty() && exactKeys.isEmpty()) {
					templates.filter { it.regex.matches(norm) }.map { it.key }
				} else emptyList()

				val source = when {
					recorderKeys.isNotEmpty() -> "recorder".also { matchedRecorder++ }
					exactKeys.isNotEmpty() -> "table".also { matchedTable++ }
					templateKeys.isNotEmpty() -> "template".also { matchedTemplate++ }
					else -> "none".also { unmatched++ }
				}
				val keys = (recorderKeys + exactKeys + templateKeys).distinct()

				addJsonObject {
					put("text", text)
					put("source", source)
					put("keys", buildJsonArray { keys.forEach { add(it) } })
					put("x", bounds.left.roundToInt())
					put("y", bounds.top.roundToInt())
					put("width", bounds.width.roundToInt())
					put("height", bounds.height.roundToInt())
				}
			}
		}

		val report = buildJsonObject {
			put("screen", "ScreenProjectStatsUiTabletPreview")
			put("surfaceWidth", TABLET_WIDTH_DP)
			put("surfaceHeight", TABLET_TALL_HEIGHT_DP)
			put("textNodes", textNodes.size)
			put("matchedRecorder", matchedRecorder)
			put("matchedTable", matchedTable)
			put("matchedTemplate", matchedTemplate)
			put("unmatched", unmatched)
			put("tableSize", Res.allStringResources.size)
			put("tags", tags)
		}

		val dir = File("build/crowdin").apply { mkdirs() }
		val jsonFile = File(dir, "ScreenProjectStatsUiTabletPreview.tags.json")
		jsonFile.writeText(report.toString())

		val awt = onRoot().captureToImage().toAwtImage()
		val pngFile = File(dir, "ScreenProjectStatsUiTabletPreview.png")
		ImageIO.write(awt, "png", pngFile)

		println(
			"Crowdin extractor: ${textNodes.size} text nodes | " +
				"recorder=$matchedRecorder table=$matchedTable template=$matchedTemplate unmatched=$unmatched"
		)
		println("  image ${awt.width}x${awt.height} -> ${pngFile.absolutePath}")

		assertTrue(textNodes.isNotEmpty(), "no text nodes captured")
	}

	private fun collectTextNodes(node: SemanticsNode, out: MutableList<SemanticsNode>) {
		if (node.config.getOrNull(SemanticsProperties.Text) != null) out.add(node)
		node.children.forEach { collectTextNodes(it, out) }
	}

	private fun nodeText(node: SemanticsNode): String? =
		node.config.getOrNull(SemanticsProperties.Text)
			?.joinToString(" ") { it.text }
			?.takeIf { it.isNotBlank() }

	private fun normalize(text: String): String =
		text.trim().replace(Regex("\\s+"), " ").lowercase()

	/** Turns a resolved format string like "created: %1$s" into a matching regex. */
	private fun templateRegex(text: String): Regex {
		val norm = normalize(text)
		val sb = StringBuilder("^")
		var i = 0
		while (i < norm.length) {
			val c = norm[i]
			if (c == '%') {
				// Consume a %n$s / %d / %s style placeholder.
				val end = norm.indexOfFirst(i + 1) { it.isLetter() }
				sb.append(".+")
				i = if (end >= 0) end + 1 else norm.length
			} else {
				sb.append(Regex.escape(c.toString()))
				i++
			}
		}
		sb.append("$")
		return Regex(sb.toString())
	}

	private inline fun String.indexOfFirst(start: Int, predicate: (Char) -> Boolean): Int {
		for (i in start until length) if (predicate(this[i])) return i
		return -1
	}
}
