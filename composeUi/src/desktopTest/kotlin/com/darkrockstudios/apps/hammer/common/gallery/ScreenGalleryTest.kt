package com.darkrockstudios.apps.hammer.common.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdGrouping
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTexture
import com.darkrockstudios.apps.hammer.common.compose.designsystem.LocalHdGrouping
import com.darkrockstudios.apps.hammer.common.compose.designsystem.LocalHdTexture
import com.darkrockstudios.apps.hammer.common.compose.theme.hammerMonoFontFamily
import com.darkrockstudios.apps.hammer.common.preview.ScreenAccountSettingsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.SceneConflictCompactPreview
import com.darkrockstudios.apps.hammer.common.preview.SceneConflictPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenCreateEntryPreview
import com.darkrockstudios.apps.hammer.common.preview.ScreenSceneListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.TabletPreviewSurface
import com.darkrockstudios.apps.hammer.common.preview.encyclopedia.ScreenBrowseEntriesUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.encyclopedia.ScreenViewEntryUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.ScreenBrowseNotesUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.ScreenCreateNoteUiPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.ScreenViewNoteUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ExportOptionsDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectSettingsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectStatsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectCreateDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.ScreenProjectListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.ScreenDraftsListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.ScreenFocusModeUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.sceneeditor.ScreenSceneEditorUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyeditor.ScreenMoveSceneDialogPreview
import com.darkrockstudios.apps.hammer.common.preview.storyeditor.ScreenOutlineOverviewUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyeditor.ScreenReaderModeUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyideas.ScreenStoryIdeasUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.storyideas.ScreenStoryIdeasViewTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.ScreenCreateTimeLineEventUiPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.ScreenTimeLineOverviewUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.ScreenViewTimeLineEventUiTabletPreview
import com.darkrockstudios.apps.hammer.common.projectselection.about.ScreenAboutAppUiTabletPreview
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.assertTrue

/**
 * Renders every major client screen at phone and tablet/desktop sizes, in light and
 * dark, into a browsable gallery for UI review. Skipped in normal test runs; use
 * `./gradlew :composeUi:renderScreenGallery`, which writes `build/screen-gallery/index.html`.
 *
 * Screens come from their tablet previews: `TabletPreviewSurface` picks the layout
 * from the canvas size, so the same preview yields the compact layout on a phone canvas.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class ScreenGalleryTest {

	private enum class FormFactor(val label: String, val widthDp: Int, val heightDp: Int) {
		Phone("Phone", 412, 915),
		Tablet("Tablet / desktop", 1280, 800),
	}

	private data class Variant(val formFactor: FormFactor, val dark: Boolean) {
		val dir = "${formFactor.name.lowercase()}-${if (dark) "dark" else "light"}"
		val label = "${formFactor.label}, ${if (dark) "dark" else "light"}"
	}

	private class Screen(
		val name: String,
		val note: String? = null,
		val content: @Composable (FormFactor) -> Unit,
	)

	private data class StudyStyle(val label: String, val grouping: HdGrouping, val texture: HdTexture = HdTexture.None)

	private class Capture(val image: BufferedImage, val overflowPx: Int, val textInventory: String)

	/** [full] is the grown-canvas capture; null when the screen fits its viewport. */
	private data class Shot(val viewport: String, val full: String?, val fullComplete: Boolean)

	private fun screen(name: String, note: String? = null, content: @Composable () -> Unit) =
		Screen(name, note) { content() }

	/** For previews that don't wrap themselves in the theme-aware, size-aware surface. */
	private fun surfaced(name: String, note: String? = null, content: @Composable () -> Unit) =
		Screen(name, note = note) { TabletPreviewSurface(content) }

	private val screens = listOf(
		screen("Project list") { ScreenProjectListUiTabletPreview() },
		screen("Create project dialog", note = "Preview hardcodes the dark theme.") { ScreenProjectCreateDialogPreview() },
		screen("About") { ScreenAboutAppUiTabletPreview() },
		screen("Account settings") { ScreenAccountSettingsUiTabletPreview() },
		screen("Scene list") { ScreenSceneListUiTabletPreview() },
		screen("Scene editor") { ScreenSceneEditorUiTabletPreview() },
		screen("Focus mode") { ScreenFocusModeUiTabletPreview() },
		screen("Drafts list") { ScreenDraftsListUiTabletPreview() },
		screen("Reader mode") { ScreenReaderModeUiTabletPreview() },
		screen("Outline overview") { ScreenOutlineOverviewUiTabletPreview() },
		screen("Move scene dialog", note = "Preview hardcodes the dark theme.") { ScreenMoveSceneDialogPreview() },
		screen("Browse notes") { ScreenBrowseNotesUiTabletPreview() },
		screen("View note") { ScreenViewNoteUiTabletPreview() },
		surfaced("Create note") { ScreenCreateNoteUiPreview() },
		screen("Browse encyclopedia") { ScreenBrowseEntriesUiTabletPreview() },
		screen("View encyclopedia entry") { ScreenViewEntryUiTabletPreview() },
		surfaced("Create encyclopedia entry") { ScreenCreateEntryPreview() },
		screen("Timeline") { ScreenTimeLineOverviewUiTabletPreview() },
		screen("View timeline event") { ScreenViewTimeLineEventUiTabletPreview() },
		surfaced("Create timeline event") { ScreenCreateTimeLineEventUiPreview() },
		screen("Story ideas") { ScreenStoryIdeasUiTabletPreview() },
		screen("Story idea") { ScreenStoryIdeasViewTabletPreview() },
		screen("Project stats") { ScreenProjectStatsUiTabletPreview() },
		screen("Project settings") { ScreenProjectSettingsUiTabletPreview() },
		surfaced("Export options dialog") { ExportOptionsDialogPreview() },
		Screen("Sync conflict", note = "Preview hardcodes a white backdrop and its own size class.") { formFactor ->
			TabletPreviewSurface {
				if (formFactor == FormFactor.Phone) SceneConflictCompactPreview() else SceneConflictPreview()
			}
		},
	)

	private var monoFamily: FontFamily? = null

	private val variants = FormFactor.entries.flatMap { ff -> listOf(Variant(ff, false), Variant(ff, true)) }

	@Test
	fun renderGallery() {
		assumeTrue(System.getProperty(ENABLED_PROPERTY) == "true", "Run via :composeUi:renderScreenGallery")
		val outDir = File(System.getProperty(OUT_DIR_PROPERTY) ?: "build/screen-gallery")
		outDir.deleteRecursively()

		val failures = mutableListOf<String>()
		val rendered = mutableMapOf<Pair<String, String>, Shot>()
		for (screen in screens) {
			for (variant in variants) {
				runCatching { renderShot(screen, variant, outDir) }
					.onSuccess { rendered[screen.name to variant.dir] = it }
					.onFailure { failures += "${screen.name} (${variant.label}): ${it.message}" }
			}
		}

		val study = mutableMapOf<Triple<String, String, StudyStyle>, Shot>()
		for (screen in screens.filter { GROUPING_STUDY?.contains(it.name) ?: true }) {
			for (variant in variants) {
				rendered[screen.name to variant.dir]?.let { study[Triple(screen.name, variant.dir, STUDY_STYLES.first())] = it }
				for (style in STUDY_STYLES.drop(1)) {
					val prefix = "grouping/${slug(style.label)}/"
					runCatching { renderShot(screen, variant, outDir, style, prefix) }
						.onSuccess { study[Triple(screen.name, variant.dir, style)] = it }
						.onFailure { failures += "${screen.name} (${variant.label}, ${style.label}): ${it.message}" }
				}
			}
		}

		File(outDir, "index.html").writeText(indexHtml(rendered, failures))
		File(outDir, "grouping.html").writeText(groupingHtml(study))
		val fullCount = rendered.values.count { it.full != null }
		println("Screen gallery: ${rendered.size} viewports, $fullCount full-length, ${study.size} grouping shots, ${failures.size} failures -> ${outDir.absolutePath}/index.html")
		failures.forEach { println("  FAILED $it") }
		assertTrue(rendered.isNotEmpty(), "no screens rendered")
	}

	/**
	 * Captures the viewport, then, if anything scrolls vertically, re-renders on a canvas
	 * grown by the remaining scroll range until nothing scrolls. Lazy lists only estimate
	 * their range, hence the passes. A scroller whose range doesn't shrink as the canvas
	 * grows has a fixed height, so growing stops there.
	 */
	private fun renderShot(
		screen: Screen,
		variant: Variant,
		outDir: File,
		style: StudyStyle = STUDY_STYLES.first(),
		dirPrefix: String = "",
	): Shot {
		val base = "$dirPrefix${variant.dir}/${slug(screen.name)}"
		val viewportHeight = variant.formFactor.heightDp * SCALE
		val viewport = capture(screen, variant, viewportHeight, style)
		write(viewport.image, File(outDir, "$base.png"))

		var height = viewportHeight
		var overflow = viewport.overflowPx
		var fullCapture: Capture? = null
		for (pass in 0 until MAX_GROW_PASSES) {
			if (overflow <= 0) break
			val nextHeight = minOf(height + overflow, MAX_HEIGHT_DP * SCALE)
			if (nextHeight == height) break
			val grown = capture(screen, variant, nextHeight, style)
			if (grown.overflowPx >= overflow) break
			fullCapture = grown
			height = nextHeight
			overflow = grown.overflowPx
		}

		val full = fullCapture?.let { "$base.full.png".also { path -> write(it.image, File(outDir, path)) } }
		File(outDir, "$base.text.tsv").writeText((fullCapture ?: viewport).textInventory)
		return Shot("$base.png", full, fullComplete = overflow <= 0)
	}

	/**
	 * Captures the image, the largest remaining vertical scroll range in px, and a TSV of
	 * every text node's position and style (for auditing emphasis levels and greebles).
	 */
	private fun capture(screen: Screen, variant: Variant, heightPx: Int, style: StudyStyle): Capture {
		val ff = variant.formFactor
		lateinit var result: Capture
		runDesktopComposeUiTest(width = ff.widthDp * SCALE, height = heightPx) {
			// Infinite animations (cursor blink, spinners) never let the clock idle.
			mainClock.autoAdvance = false
			setContent {
				monoFamily = hammerMonoFontFamily()
				CompositionLocalProvider(
					LocalDensity provides Density(SCALE.toFloat()),
					LocalSystemTheme provides if (variant.dark) SystemTheme.Dark else SystemTheme.Light,
					LocalHdGrouping provides style.grouping,
					LocalHdTexture provides style.texture,
				) {
					screen.content(ff)
				}
			}
			waitForIdle()
			// Let enter transitions finish so content isn't captured mid-fade.
			mainClock.advanceTimeBy(1_000)
			waitForIdle()

			val overflow = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange), useUnmergedTree = true)
				.fetchSemanticsNodes()
				.maxOfOrNull { node ->
					val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
					val remaining = range.maxValue() - range.value()
					if (remaining.isFinite()) ceil(remaining).toInt() else heightPx
				} ?: 0
			result = Capture(onRoot().captureToImage().toAwtImage(), overflow, textInventory())
		}
		return result
	}

	private fun ComposeUiTest.textInventory(): String = buildString {
		appendLine("top_dp\tleft_dp\tfamily\tsize_sp\tweight\tletter_spacing\ttext")
		onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
			.fetchSemanticsNodes()
			.sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
			.forEach { node ->
				val text = node.config[SemanticsProperties.Text].joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
				if (text.isEmpty()) return@forEach
				val layouts = mutableListOf<TextLayoutResult>()
				node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
				val style = layouts.firstOrNull()?.layoutInput?.style
				val family = when (style?.fontFamily) {
					null -> "?"
					monoFamily -> "mono"
					else -> if ("Mono" in style.fontFamily.toString()) "mono" else "sans"
				}
				val b = node.boundsInRoot
				appendLine(
					listOf(
						(b.top / SCALE).roundToInt(), (b.left / SCALE).roundToInt(), family,
						style?.fontSize, style?.fontWeight?.weight, style?.letterSpacing, text,
					).joinToString("\t")
				)
			}
	}

	private fun write(image: BufferedImage, file: File) {
		file.parentFile.mkdirs()
		ImageIO.write(image, "png", file)
	}

	private fun indexHtml(rendered: Map<Pair<String, String>, Shot>, failures: List<String>): String = buildString {
		appendLine("<!doctype html><html><head><meta charset=\"utf-8\"><title>Hammer screen gallery</title><style>")
		appendLine(
			"""
			body{font:14px/1.4 system-ui,sans-serif;margin:24px;background:#f4f4f2;color:#222}
			nav a{margin-right:12px}
			section{margin:40px 0;border-top:1px solid #ccc;padding-top:16px}
			h3{font-size:13px;font-weight:600;color:#555;margin:16px 0 8px}
			.row{display:flex;gap:16px;align-items:flex-start;overflow-x:auto}
			figure{margin:0}figcaption{font-size:12px;color:#666;margin-bottom:4px}
			img{display:block;border:1px solid #bbb;background:#fff}
			img.phone{width:206px}img.tablet{width:640px}
			.note{color:#8a5a00}
			""".trimIndent()
		)
		appendLine("</style></head><body><h1>Hammer screen gallery</h1>")
		appendLine("<p><a href=\"grouping.html\">Grouping study</a></p>")
		appendLine("<p>Rendered from fake preview data at ${SCALE}x. Viewport shows what fits on screen; full length grows the canvas until nothing scrolls. Click an image for full size.</p>")
		if (failures.isNotEmpty()) {
			appendLine("<h2>Failed renders</h2><ul>")
			failures.forEach { appendLine("<li>${esc(it)}</li>") }
			appendLine("</ul>")
		}
		appendLine("<nav>")
		screens.forEach { appendLine("<a href=\"#${slug(it.name)}\">${esc(it.name)}</a>") }
		appendLine("</nav>")
		for (screen in screens) {
			appendLine("<section id=\"${slug(screen.name)}\"><h2>${esc(screen.name)}</h2>")
			screen.note?.let { appendLine("<p class=\"note\">${esc(it)}</p>") }
			appendLine("<h3>Viewport</h3>")
			appendFigures(screen, rendered) { it.viewport }
			if (variants.any { rendered[screen.name to it.dir]?.full != null }) {
				appendLine("<h3>Full length</h3>")
				appendFigures(screen, rendered, fullLength = true) { it.full }
			}
			appendLine("</section>")
		}
		appendLine("</body></html>")
	}

	private fun groupingHtml(study: Map<Triple<String, String, StudyStyle>, Shot>): String = buildString {
		appendLine("<!doctype html><html><head><meta charset=\"utf-8\"><title>Hammer grouping study</title><style>")
		appendLine(
			"""
			body{font:14px/1.4 system-ui,sans-serif;margin:24px;background:#f4f4f2;color:#222}
			nav a{margin-right:12px}
			section{margin:40px 0;border-top:1px solid #ccc;padding-top:16px}
			h3{font-size:13px;font-weight:600;color:#555;margin:20px 0 8px}
			.row{display:flex;gap:16px;align-items:flex-start;overflow-x:auto}
			figure{margin:0}figcaption{font-size:12px;color:#666;margin-bottom:4px}
			img{display:block;border:1px solid #bbb;background:#fff}
			img.phone{width:260px}img.tablet{width:480px}
			""".trimIndent()
		)
		appendLine("</style></head><body><h1>Grouping study</h1>")
		appendLine("<p>Study styles: <b>Rules</b> (current); <b>HangingSpine</b> (cool-tinted regions with an ink spine, titles hung on wide screens, controls as wells); and HangingSpine with a faint texture on regions and cards: <b>Grain</b> (paper noise), <b>Laid</b> (laid and chain lines), <b>Dots</b> (fine halftone). Wells stay plain. Earlier rounds are in ui-research/gallery-history. <a href=\"index.html\">All screens</a></p>")
		val names = screens.map { it.name }.filter { GROUPING_STUDY?.contains(it) ?: true }
		appendLine("<nav>")
		names.forEach { appendLine("<a href=\"#${slug(it)}\">${esc(it)}</a>") }
		appendLine("</nav>")
		for (name in names) {
			appendLine("<section id=\"${slug(name)}\"><h2>${esc(name)}</h2>")
			for (variant in variants) {
				appendLine("<h3>${esc(variant.label)}</h3><div class=\"row\">")
				for (style in STUDY_STYLES) {
					val shot = study[Triple(name, variant.dir, style)] ?: continue
					val src = shot.full ?: shot.viewport
					val cls = variant.formFactor.name.lowercase()
					appendLine("<figure><figcaption>${style.label}</figcaption><a href=\"$src\"><img class=\"$cls\" loading=\"lazy\" src=\"$src\"></a></figure>")
				}
				appendLine("</div>")
			}
			appendLine("</section>")
		}
		appendLine("</body></html>")
	}

	private fun StringBuilder.appendFigures(
		screen: Screen,
		rendered: Map<Pair<String, String>, Shot>,
		fullLength: Boolean = false,
		path: (Shot) -> String?,
	) {
		appendLine("<div class=\"row\">")
		for (variant in variants) {
			val shot = rendered[screen.name to variant.dir] ?: continue
			val src = path(shot) ?: continue
			val caption = if (fullLength && !shot.fullComplete) "${variant.label} (still scrolls)" else variant.label
			val cls = variant.formFactor.name.lowercase()
			appendLine("<figure><figcaption>${esc(caption)}</figcaption><a href=\"$src\"><img class=\"$cls\" loading=\"lazy\" src=\"$src\"></a></figure>")
		}
		appendLine("</div>")
	}

	private fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

	private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

	companion object {
		const val ENABLED_PROPERTY = "hammer.screenGallery"
		const val OUT_DIR_PROPERTY = "hammer.screenGallery.outDir"
		private const val SCALE = 2
		/** The first entry is the baseline, reused from the main gallery renders. */
		private val STUDY_STYLES = listOf(
			StudyStyle("Rules", HdGrouping.Rules),
			StudyStyle("HangingSpine", HdGrouping.HangingSpine),
			StudyStyle("Spine + Grain", HdGrouping.HangingSpine, HdTexture.Grain),
			StudyStyle("Spine + Laid", HdGrouping.HangingSpine, HdTexture.Laid),
			StudyStyle("Spine + Dots", HdGrouping.HangingSpine, HdTexture.Dots),
		)
		private val GROUPING_STUDY: Set<String>? = setOf(
			"Project settings", "Account settings", "Project stats", "View encyclopedia entry",
			"Browse notes", "Scene editor", "Timeline",
		)
		private const val MAX_GROW_PASSES = 4
		private const val MAX_HEIGHT_DP = 8000
	}
}
