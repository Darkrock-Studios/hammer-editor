package com.darkrockstudios.apps.hammer.common.crowdin

import androidx.compose.runtime.Composable
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
import com.darkrockstudios.apps.hammer.common.preview.ScreenAccountSettingsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenEncyclopediaUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenSceneListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_TALL_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP
import com.darkrockstudios.apps.hammer.common.preview.encyclopedia.ScreenBrowseEntriesUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.encyclopedia.ScreenViewEntryUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.ScreenBrowseNotesUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.ScreenViewNoteUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectSettingsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectStatsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectCreateDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.ScreenDraftsListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.ScreenFocusModeUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.ScreenSceneEditorUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyeditor.ScreenMoveSceneDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.storyeditor.ScreenOutlineOverviewUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyideas.ScreenStoryIdeasUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyideas.ScreenStoryIdeasViewTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.ScreenTimeLineOverviewUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.ScreenViewTimeLineEventUiTabletPreview
import com.darkrockstudios.apps.hammer.common.projectselection.about.ScreenAboutAppUiTabletPreview
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
 * Stage 1 of the Crowdin screenshot pipeline: render each screen preview, capture
 * an aligned PNG plus every text node's pixel bounds, and map each node back to its
 * string resource key.
 *
 * Mapping prefers the scoped [StringKeyRecorder] (exact, this-screen-only, fed by
 * `.get()`), falling back to the full resolved string table and then to
 * format-string regexes. A string that renders more than once (list rows, grids)
 * is tagged at its topmost occurrence and skipped thereafter. Writes `<screen>.png`
 * and `<screen>.tags.json` to build/crowdin/ for the upload task to publish.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTagExtractorTest {

	private data class ScreenSpec(
		val name: String,
		val height: Int,
		val width: Int = TABLET_WIDTH_DP,
		val content: @Composable () -> Unit,
	)

	private data class Template(val key: String, val regex: Regex)

	private data class Resolved(val source: String, val keys: List<String>)

	private val screens = listOf(
		ScreenSpec("ScreenProjectStatsUiTabletPreview", TABLET_TALL_HEIGHT_DP) { ScreenProjectStatsUiTabletPreview() },
		ScreenSpec("ScreenViewEntryUiTabletPreview", TABLET_TALL_HEIGHT_DP) { ScreenViewEntryUiTabletPreview() },
		ScreenSpec("ScreenBrowseEntriesUiTabletPreview", TABLET_HEIGHT_DP) { ScreenBrowseEntriesUiTabletPreview() },
		ScreenSpec("ScreenBrowseNotesUiTabletPreview", TABLET_HEIGHT_DP) { ScreenBrowseNotesUiTabletPreview() },
		ScreenSpec("ScreenProjectSettingsUiTabletPreview", TABLET_HEIGHT_DP) { ScreenProjectSettingsUiTabletPreview() },
		ScreenSpec("ScreenStoryIdeasUiTabletPreview", TABLET_HEIGHT_DP) { ScreenStoryIdeasUiTabletPreview() },
		ScreenSpec("ScreenTimeLineOverviewUiTabletPreview", TABLET_HEIGHT_DP) { ScreenTimeLineOverviewUiTabletPreview() },
		ScreenSpec("ScreenProjectListUiTabletPreview", TABLET_HEIGHT_DP) { ScreenProjectListUiTabletPreview() },
		ScreenSpec("ScreenAccountSettingsUiTabletPreview", TABLET_HEIGHT_DP) { ScreenAccountSettingsUiTabletPreview() },
		ScreenSpec("ScreenAboutAppUiTabletPreview", TABLET_HEIGHT_DP) { ScreenAboutAppUiTabletPreview() },
		ScreenSpec("ScreenOutlineOverviewUiTabletPreview", TABLET_HEIGHT_DP) { ScreenOutlineOverviewUiTabletPreview() },
		ScreenSpec("ScreenEncyclopediaUiTabletPreview", TABLET_HEIGHT_DP) { ScreenEncyclopediaUiTabletPreview() },
		ScreenSpec("ScreenSceneListUiTabletPreview", TABLET_HEIGHT_DP) { ScreenSceneListUiTabletPreview() },
		ScreenSpec("ScreenSceneEditorUiTabletPreview", TABLET_HEIGHT_DP) { ScreenSceneEditorUiTabletPreview() },
		ScreenSpec("ScreenFocusModeUiTabletPreview", TABLET_HEIGHT_DP) { ScreenFocusModeUiTabletPreview() },
		ScreenSpec("ScreenDraftsListUiTabletPreview", TABLET_HEIGHT_DP) { ScreenDraftsListUiTabletPreview() },
		ScreenSpec("ScreenViewNoteUiTabletPreview", TABLET_HEIGHT_DP) { ScreenViewNoteUiTabletPreview() },
		ScreenSpec("ScreenViewTimeLineEventUiTabletPreview", TABLET_HEIGHT_DP) { ScreenViewTimeLineEventUiTabletPreview() },
		ScreenSpec("ScreenStoryIdeasViewTabletPreview", TABLET_HEIGHT_DP) { ScreenStoryIdeasViewTabletPreview() },
		ScreenSpec("ScreenProjectCreateDialogPreview", 460, width = 720) { ScreenProjectCreateDialogPreview() },
		ScreenSpec("ScreenMoveSceneDialogPreview", 720, width = 720) { ScreenMoveSceneDialogPreview() },
	)

	@Test
	fun extractAllTabletScreens() {
		val outDir = File("build/crowdin").apply { mkdirs() }
		val (tableKeysByText, templates) = buildStringTable()

		val unmatchedByScreen = LinkedHashMap<String, List<String>>()
		var totalTags = 0
		for (spec in screens) {
			val result = runCatching { extractScreen(spec, tableKeysByText, templates, outDir, unmatchedByScreen) }
			result.onFailure { println("Crowdin extractor: ${spec.name} FAILED: ${it.message}") }
			totalTags += result.getOrDefault(0)
		}

		val lint = buildString {
			appendLine("# Untranslated on-screen text (candidates)")
			appendLine()
			appendLine("Text rendered on a screen that maps to no string resource. Filter out fake")
			appendLine("preview data (names, dates); the rest are likely hardcoded UI strings.")
			for ((screen, texts) in unmatchedByScreen) {
				if (texts.isEmpty()) continue
				appendLine()
				appendLine("## $screen")
				texts.forEach { appendLine("- ${it.replace("\n", " ")}") }
			}
		}
		File(outDir, "_untranslated-candidates.md").writeText(lint)

		println("Crowdin extractor: wrote artifacts for ${screens.size} screens, $totalTags total tags")
		assertTrue(totalTags > 0, "no tags extracted across any screen")
	}

	private fun extractScreen(
		spec: ScreenSpec,
		tableKeysByText: Map<String, List<String>>,
		templates: List<Template>,
		outDir: File,
		unmatchedByScreen: MutableMap<String, List<String>>,
	): Int {
		var result = 0
		runDesktopComposeUiTest(width = spec.width, height = spec.height) {
			// Infinite animations (text-editor cursor blink, shimmers, spinners) never
			// let the clock go idle, so waitForIdle() would hang. Stop auto-advancing
			// so it settles composition/layout only.
			mainClock.autoAdvance = false
			val recorder = StringKeyRecorder()
			setContent {
				CompositionLocalProvider(LocalStringKeyRecorder provides recorder) {
					spec.content()
				}
			}
			waitForIdle()

			val recorderKeysByText = HashMap<String, MutableList<String>>()
			for ((key, text) in recorder.snapshot()) {
				recorderKeysByText.getOrPut(normalize(text)) { mutableListOf() }.add(key)
			}

			val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
			val textNodes = mutableListOf<SemanticsNode>()
			collectTextNodes(root, textNodes)

			// Topmost-first so a repeated string is tagged at its first on-screen row.
			val ordered = textNodes.sortedWith(
				compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }),
			)
			val seen = HashSet<String>()
			var tagCount = 0
			var ambiguous = 0
			// On-screen text that maps to no string resource — likely hardcoded UI
			// strings (or fake preview data, which the reader filters out).
			val unmatchedText = LinkedHashSet<String>()
			val tagsArr = buildJsonArray {
				for (node in ordered) {
					val text = nodeText(node) ?: continue
					val resolved = resolveKeys(normalize(text), recorderKeysByText, tableKeysByText, templates)
					when (resolved.keys.size) {
						1 -> {
							val key = resolved.keys.single()
							if (!seen.add(key)) continue
							tagCount++
							val b = node.boundsInRoot
							addJsonObject {
								put("key", key)
								put("text", text)
								put("source", resolved.source)
								put("x", b.left.roundToInt())
								put("y", b.top.roundToInt())
								put("width", b.width.roundToInt())
								put("height", b.height.roundToInt())
							}
						}
						0 -> if (looksLikeLabel(text)) unmatchedText.add(text.trim())
						else -> ambiguous++
					}
				}
			}

			val report = buildJsonObject {
				put("screen", spec.name)
				put("width", spec.width)
				put("height", spec.height)
				put("textNodes", textNodes.size)
				put("tagCount", tagCount)
				put("ambiguous", ambiguous)
				put("unmatched", buildJsonArray { unmatchedText.forEach { add(it) } })
				put("tags", tagsArr)
			}
			File(outDir, "${spec.name}.tags.json").writeText(report.toString())
			unmatchedByScreen[spec.name] = unmatchedText.toList()

			val awt = onRoot().captureToImage().toAwtImage()
			ImageIO.write(awt, "png", File(outDir, "${spec.name}.png"))

			println("Crowdin extractor: ${spec.name} -> $tagCount tags, $ambiguous ambiguous, ${unmatchedText.size} unmatched (${awt.width}x${awt.height})")
			result = tagCount
		}
		return result
	}

	private fun resolveKeys(
		norm: String,
		recorderKeysByText: Map<String, List<String>>,
		tableKeysByText: Map<String, List<String>>,
		templates: List<Template>,
	): Resolved {
		recorderKeysByText[norm]?.let { if (it.isNotEmpty()) return Resolved("recorder", it.distinct()) }
		tableKeysByText[norm]?.let { if (it.isNotEmpty()) return Resolved("table", it.distinct()) }
		val t = templates.filter { it.regex.matches(norm) }.map { it.key }.distinct()
		if (t.isNotEmpty()) return Resolved("template", t)
		return Resolved("none", emptyList())
	}

	private fun buildStringTable(): Pair<Map<String, List<String>>, List<Template>> {
		val byText = HashMap<String, MutableList<String>>()
		val templates = mutableListOf<Template>()
		runBlocking {
			for ((key, resource) in Res.allStringResources) {
				val text = runCatching { getString(resource) }.getOrNull() ?: continue
				if (text.contains('%')) {
					templates.add(Template(key, templateRegex(text)))
				} else {
					byText.getOrPut(normalize(text)) { mutableListOf() }.add(key)
				}
			}
		}
		return byText to templates
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

	/** Filters out numbers/dates/symbols so the unmatched list is mostly real labels. */
	private fun looksLikeLabel(text: String): Boolean =
		text.count { it.isLetter() } >= 2

	/** Turns a resolved format string like "created: %1$s" into a matching regex. */
	private fun templateRegex(text: String): Regex {
		val norm = normalize(text)
		val sb = StringBuilder("^")
		var i = 0
		while (i < norm.length) {
			val c = norm[i]
			if (c == '%') {
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
