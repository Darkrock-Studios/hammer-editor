package com.darkrockstudios.apps.hammer.common.intro

import androidx.compose.animation.core.Animatable
import com.darkrockstudios.apps.hammer.hammer_icon
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.app_name
import com.darkrockstudios.apps.hammer.encyclopedia_category_person
import com.darkrockstudios.apps.hammer.encyclopedia_category_place
import com.darkrockstudios.apps.hammer.intro_sketch_desktop
import com.darkrockstudios.apps.hammer.intro_sketch_note
import com.darkrockstudios.apps.hammer.notes_word_count_short
import com.darkrockstudios.apps.hammer.scene_group_scene_count_format
import com.darkrockstudios.apps.hammer.intro_welcome_subtitle
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.components.intro.Intro
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Every feature sketch is drawn in this many units and scaled to fit its box. */
internal const val SketchWidth = 320f
internal const val SketchHeight = 200f

internal val IllustrationHeight = SketchHeight.dp
internal val IllustrationHeightShort = (SketchHeight * 0.75f).dp

/** Short enough that a still taken a second in shows each sketch settled. */
private const val SettleMillis = 900
private const val LoopPauseMillis = 1600L

/** A sketch's drawing area in units, and the size its mono labels are set at. */
internal data class SketchBox(val width: Float, val height: Float, val labelSize: Float = 8.5f) {
	val aspect: Float get() = width / height
	val portrait: Boolean get() = height > width
}

internal val LandscapeBox = SketchBox(SketchWidth, SketchHeight)

/** The welcome's upright title page, a book page's 5:7. */
private val TitlePageBox = SketchBox(200f, 280f, labelSize = 11f)

/** The boxes a slide's sketch can be drawn in, and how wide it may grow. */
private class SketchSpec(val landscape: SketchBox, val portrait: SketchBox?, val maxWidth: Dp)

private val Intro.Slide.sketchSpec: SketchSpec
	get() = when (this) {
		Intro.Slide.Welcome -> SketchSpec(LandscapeBox, portrait = TitlePageBox, maxWidth = 880.dp)
		else -> SketchSpec(LandscapeBox, portrait = null, maxWidth = 760.dp)
	}

/** The box a slide's sketch is drawn in, and its size on screen. */
internal class SketchLayout(val box: SketchBox, val size: DpSize)

/** [slide]'s sketch as large as fits the room, drawn upright when it can be and the room is taller than wide. */
internal fun layoutSketch(slide: Intro.Slide, roomWidth: Dp, roomHeight: Dp): SketchLayout {
	val spec = slide.sketchSpec
	val box = spec.portrait?.takeIf { roomHeight > roomWidth } ?: spec.landscape
	val width = minOf(roomWidth, roomHeight * box.aspect, spec.maxWidth).coerceAtLeast(0.dp)
	return SketchLayout(box, DpSize(width, width / box.aspect))
}

/**
 * The drawing for [slide] in [box]. It builds itself once [active], then holds, some with a quiet
 * loop. Pages the pager drops and brings back build again.
 */
@Composable
internal fun IntroIllustration(slide: Intro.Slide, active: Boolean, box: SketchBox, modifier: Modifier = Modifier) {
	when (slide) {
		Intro.Slide.Welcome -> WelcomeSketch(active, box, modifier)
		Intro.Slide.Structure -> StructureSketch(active, box, modifier)
		Intro.Slide.World -> WorldSketch(active, box, modifier)
		Intro.Slide.Files -> FilesSketch(active, box, modifier)
		Intro.Slide.Anywhere -> AnywhereSketch(active, box, modifier)
		Intro.Slide.Example -> BookSketch(active, box, modifier)
	}
}

/**
 * A title page: the app's hammer, as a printer's mark, swings down and strikes; the wordmark
 * presses into the page, the rules either side run out from the blow, and the subtitle inks in
 * after. Then it rests. The drawn wordmark is the slide's heading for screen readers.
 */
@Composable
private fun WelcomeSketch(active: Boolean, box: SketchBox, modifier: Modifier) {
	val settle = rememberSettle(active)
	val appName = Res.string.app_name.get()
	val subtitle = Res.string.intro_welcome_subtitle.get()
	val display = MaterialTheme.typography.displayLarge
	val titleMedium = MaterialTheme.typography.titleMedium
	val italic = remember(titleMedium) { titleMedium.copy(fontStyle = FontStyle.Italic, textAlign = TextAlign.Center) }
	val hammer = rememberHammerMark()
	val page = if (box.portrait) PortraitTitlePage else LandscapeTitlePage

	Box(modifier) {
		SketchCanvas(Modifier.matchParentSize().semantics { heading(); contentDescription = appName }, box) {
			val wordmarkStyle = scaled(display, page.wordmarkSize)
			val captionStyle = scaled(italic, page.captionSize)
			val t = settle.value
			val centerX = box.width / 2f
			val shown = t.phase(0f, 0.2f)
			frame(page.insetX, page.insetY, box.width - page.insetX * 2f, box.height - page.insetY * 2f, palette.rule.copy(alpha = shown))
			val innerX = page.insetX + 4f
			val innerY = page.insetY + 4f
			frame(innerX, innerY, box.width - innerX * 2f, box.height - innerY * 2f, palette.rule.copy(alpha = shown * 0.6f))

			val swing = FastOutLinearInEasing.transform(((t - 0.05f) / (StrikeAt - 0.05f)).coerceIn(0f, 1f))
			val recoil = ((t - StrikeAt) / 0.25f).coerceIn(0f, 1f)
			val degrees = -38f * (1f - swing) - 5f * sin(PI.toFloat() * recoil)
			val markAlpha = t.phase(0f, 0.12f)
			hammer.parts.forEach { part ->
				path(part.path, part.fill, markAlpha, centerX - page.markSize / 2f, page.markTop, page.markSize, hammer.viewport, degrees, HammerGrip)
			}

			// The press: overshoots small on the blow, then eases back to size.
			val ink = ((t - StrikeAt) / 0.3f).coerceIn(0f, 1f)
			if (ink > 0f) {
				val scale = if (ink < 0.5f) 1.14f - 0.18f * (ink / 0.5f) else 0.96f + 0.04f * FastOutSlowInEasing.transform((ink - 0.5f) / 0.5f)
				wordmark(appName, wordmarkStyle, centerX, page.wordmarkCenterY, palette.ink.copy(alpha = min(1f, ink * 2f)), scale)
			}

			val wordHalf = textWidth(appName, wordmarkStyle) / 2f + 8f
			val run = t.phase(StrikeAt, 0.85f)
			if (run > 0f) {
				val half = wordHalf + (page.ruleHalfWidth - wordHalf) * run
				line(centerX - half, page.ruleAboveY, centerX + half, page.ruleAboveY, palette.muted)
				line(centerX - half, page.ruleBelowY, centerX + half, page.ruleBelowY, palette.muted)
			}

			val spark = ((t - StrikeAt) / 0.3f).coerceIn(0f, 1f)
			if (spark > 0f && spark < 1f) {
				val sparkColor = palette.ink.copy(alpha = 1f - spark)
				for (side in floatArrayOf(-1f, 1f)) {
					val x = centerX + side * (wordHalf - 4f)
					SparkRises.forEach { rise ->
						val inner = 3f + 8f * spark
						val outer = 8f + 14f * spark
						line(x + side * inner, page.wordmarkCenterY + rise * inner, x + side * outer, page.wordmarkCenterY + rise * outer, sparkColor)
					}
				}
			}

			val subtitleColor = palette.muted.copy(alpha = t.phase(0.6f, 0.9f))
			val subtitleBottom = caption(subtitle, captionStyle, centerX, page.subtitleTop, page.subtitleWidth, subtitleColor)
			val colophonColor = palette.muted.copy(alpha = t.phase(0.75f, 1f))
			text(Colophon, centerX, subtitleBottom + page.colophonGap, colophonColor, align = LabelAlign.Center)
		}
		// The subtitle is drawn too, so it gets a node of its own.
		Box(Modifier.matchParentSize().semantics { contentDescription = subtitle })
	}
}

private const val StrikeAt = 0.45f

/** Where the welcome's title page sets each piece, in its box's units. */
private class TitlePage(
	val insetX: Float,
	val insetY: Float,
	val markSize: Float,
	val markTop: Float,
	val ruleAboveY: Float,
	val wordmarkSize: Float,
	val wordmarkCenterY: Float,
	val ruleBelowY: Float,
	val ruleHalfWidth: Float,
	val captionSize: Float,
	val subtitleTop: Float,
	val subtitleWidth: Float,
	val colophonGap: Float,
)

private val LandscapeTitlePage = TitlePage(
	insetX = 24f,
	insetY = 4f,
	markSize = 40f,
	markTop = 18f,
	ruleAboveY = 64f,
	wordmarkSize = 44f,
	wordmarkCenterY = 96f,
	ruleBelowY = 128f,
	ruleHalfWidth = 112f,
	captionSize = 10f,
	subtitleTop = 138f,
	subtitleWidth = 140f,
	colophonGap = 8f,
)

private val PortraitTitlePage = TitlePage(
	insetX = 4f,
	insetY = 4f,
	markSize = 56f,
	markTop = 30f,
	ruleAboveY = 112f,
	wordmarkSize = 38f,
	wordmarkCenterY = 146f,
	ruleBelowY = 180f,
	ruleHalfWidth = 80f,
	captionSize = 13f,
	subtitleTop = 194f,
	subtitleWidth = 170f,
	colophonGap = 14f,
)

/** Slope of each spark off the wordmark's edge: up, level, down. */
private val SparkRises = floatArrayOf(-0.6f, 0f, 0.6f)

/** The release the build was cut from, as major.minor. */
private val Colophon = BuildMetadata.APP_VERSION.split('.').take(2).joinToString(".")

/** The end of the handle the hammer swings about, in the icon's own units. */
private val HammerGrip = Offset(140f, 365f)

private class HammerPart(val path: Path, val fill: Brush)

private class HammerMark(val parts: List<HammerPart>, val viewport: Float)

/** The hammer from the app icon, without the icon's tile, which is its first path. */
@Composable
private fun rememberHammerMark(): HammerMark {
	val icon = vectorResource(Res.drawable.hammer_icon)
	return remember(icon) {
		val parts = icon.root.filterIsInstance<VectorPath>().drop(1).mapNotNull { vector ->
			vector.fill?.let { HammerPart(vector.pathData.toPath(), it) }
		}
		HammerMark(parts, icon.viewportWidth)
	}
}

/** Scene rows slide into two chapters; then two scenes trade places now and then. */
@Composable
private fun StructureSketch(active: Boolean, box: SketchBox, modifier: Modifier) {
	val swap = remember { Animatable(0f) }
	val settle = rememberSettle(active) {
		while (true) {
			delay(LoopPauseMillis)
			swap.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
			delay(LoopPauseMillis)
			swap.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
		}
	}

	val firstChapter = Res.string.scene_group_scene_count_format.get(3).uppercase()
	val secondChapter = Res.string.scene_group_scene_count_format.get(2).uppercase()
	val counts = SceneWords.map { Res.string.notes_word_count_short.get(it).uppercase() }
	// Kept as one instance while equal, so the draw cache is not rebuilt on every recomposition.
	val words = remember(counts) { counts }

	SketchCanvas(modifier, box) {
		val t = settle.value
		val s = swap.value

		chapter(y = 10f, count = firstChapter, appear = t.phase(0f, 0.3f), rows = 3, rails = t.phase(0.1f, 0.6f))
		scene(y = 36f, titleWidth = 96f, words = words[0], appear = t.phase(0.12f, 0.42f))
		scene(y = 58f + s * 22f, titleWidth = 120f, words = words[1], appear = t.phase(0.22f, 0.52f))
		scene(
			y = 80f - s * 22f,
			titleWidth = 78f,
			words = words[2],
			appear = t.phase(0.32f, 0.62f),
			highlighted = true,
			lift = sin(PI.toFloat() * s) * 6f,
		)

		chapter(y = 110f, count = secondChapter, appear = t.phase(0.3f, 0.6f), rows = 2, rails = t.phase(0.4f, 0.8f))
		scene(y = 136f, titleWidth = 110f, words = words[3], appear = t.phase(0.45f, 0.75f))
		scene(y = 158f, titleWidth = 88f, words = words[4], appear = t.phase(0.55f, 0.85f))
	}
}

/** Word counts for the sketch's five scenes, in the order they are drawn. */
private val SceneWords = listOf(1204, 2310, 860, 1876, 1412)

private fun Sketch.chapter(y: Float, count: String, appear: Float, rows: Int, rails: Float) {
	if (appear <= 0f) return
	val ink = palette.ink.copy(alpha = appear)
	frame(40f, y + 5f, 8f, 10f, ink)
	line(42.5f, y + 5f, 42.5f, y + 15f, ink)
	fill(58f, y + 8f, 84f, 4f, ink)
	text(count, 284f, y + 5f, palette.muted.copy(alpha = appear), align = LabelAlign.End)

	val railTop = y + 22f
	val railBottom = railTop + rows * 22f - 6f
	line(44f, railTop, 44f, railTop + (railBottom - railTop) * rails, palette.rule)
}

private fun Sketch.scene(
	y: Float,
	titleWidth: Float,
	words: String,
	appear: Float,
	highlighted: Boolean = false,
	lift: Float = 0f,
) {
	if (appear <= 0f) return
	val x = 58f + (1f - appear) * 40f + lift
	if (highlighted) {
		fill(x, y, 226f, 18f, palette.raised.copy(alpha = appear))
		fill(x, y, 2f, 18f, palette.accent.copy(alpha = appear))
	}
	fill(x + 10f, y + 7.5f, titleWidth, 3f, palette.muted.copy(alpha = appear * 0.7f))
	text(words, x + 222f, y + 4f, palette.muted.copy(alpha = appear), align = LabelAlign.End)
}

/** Entry cards and a note draw hairline links to the places a scene mentions them, over a timeline. */
@Composable
private fun WorldSketch(active: Boolean, box: SketchBox, modifier: Modifier) {
	val settle = rememberSettle(active)

	val hammerColors = LocalHammerColors.current
	val noteColor = MaterialTheme.colorScheme.onSurfaceVariant
	val person = Res.string.encyclopedia_category_person.get().uppercase()
	val place = Res.string.encyclopedia_category_place.get().uppercase()
	val note = Res.string.intro_sketch_note.get().uppercase()
	val cards = remember(hammerColors, noteColor, person, place, note) {
		listOf(
			WorldCard(y = 18f, stamp = person, color = hammerColors.person, mentionLine = 1, mentionX = 214f, mentionWidth = 26f),
			WorldCard(y = 76f, stamp = place, color = hammerColors.place, mentionLine = 4, mentionX = 238f, mentionWidth = 34f),
			WorldCard(y = 134f, stamp = note, color = noteColor, mentionLine = 8, mentionX = 196f, mentionWidth = 24f),
		)
	}
	SketchCanvas(modifier, box) {
		val t = settle.value

		val page = t.phase(0f, 0.3f)
		if (page > 0f) {
			fill(178f, 14f, 114f, 160f, palette.paper.copy(alpha = page))
			frame(178f, 14f, 114f, 160f, palette.rule.copy(alpha = page))
			fill(190f, 26f, 54f, 5f, palette.ink.copy(alpha = page))
			PageLines.forEachIndexed { index, width ->
				fill(190f, 44f + index * 13f, width, 3f, palette.muted.copy(alpha = page * 0.3f))
			}
		}

		cards.forEachIndexed { index, card ->
			val appear = t.phase(index * 0.1f, 0.3f + index * 0.1f)
			if (appear <= 0f) return@forEachIndexed
			val x = 28f - (1f - appear) * 16f
			fill(x, card.y, 96f, 42f, palette.raised.copy(alpha = appear))
			frame(x, card.y, 96f, 42f, palette.rule.copy(alpha = appear))
			fill(x, card.y, 96f, 3f, card.color.copy(alpha = appear))
			text(card.stamp, x + 8f, card.y + 10f, card.color.copy(alpha = appear))
			fill(x + 8f, card.y + 28f, 52f, 4f, palette.ink.copy(alpha = appear))

			val link = t.phase(0.35f + index * 0.12f, 0.7f + index * 0.12f)
			if (link <= 0f) return@forEachIndexed
			val mentionY = 44f + card.mentionLine * 13f + 1.5f
			curve(
				from = Offset(124f, card.y + 21f),
				control1 = Offset(152f, card.y + 21f),
				control2 = Offset(card.mentionX - 28f, mentionY),
				to = Offset(card.mentionX, mentionY),
				fraction = link,
				color = card.color,
			)
			fill(card.mentionX, mentionY - 1.5f, card.mentionWidth, 3f, card.color.copy(alpha = link))
			fill(card.mentionX, mentionY + 3f, card.mentionWidth, 1f, card.color.copy(alpha = link))
		}

		val timeline = t.phase(0.55f, 0.9f)
		if (timeline > 0f) {
			line(28f, 190f, 28f + 264f * timeline, 190f, palette.rule)
			TimelineTicks.forEach { x ->
				if (x < 28f + 264f * timeline) frame(x - 2.5f, 187.5f, 5f, 5f, palette.muted.copy(alpha = timeline))
			}
			if (timeline >= 0.75f) {
				val linked = (timeline - 0.75f) / 0.25f
				fill(232.5f, 187.5f, 5f, 5f, hammerColors.event.copy(alpha = linked))
				line(235f, 174f, 235f, 187.5f, hammerColors.event.copy(alpha = linked))
			}
		}
	}
}

private val PageLines = floatArrayOf(92f, 84f, 92f, 70f, 92f, 88f, 60f, 92f, 76f)
private val TimelineTicks = floatArrayOf(60f, 120f, 270f)

private class WorldCard(
	val y: Float,
	val stamp: String,
	val color: Color,
	val mentionLine: Int,
	val mentionX: Float,
	val mentionWidth: Float,
)

/** The example project's real layout on disk unfolds a row at a time. */
@Composable
private fun FilesSketch(active: Boolean, box: SketchBox, modifier: Modifier) {
	val settle = rememberSettle(active)

	SketchCanvas(modifier, box) {
		val t = settle.value
		val rowHeight = 19f
		FileRows.forEachIndexed { index, row ->
			val appear = t.phase(index * 0.075f, index * 0.075f + 0.28f)
			if (appear <= 0f) return@forEachIndexed
			val y = 12f + index * rowHeight - (1f - appear) * 10f
			for (level in 0 until row.depth) {
				line(31f + level * 14f, y, 31f + level * 14f, y + rowHeight, palette.rule.copy(alpha = appear))
			}
			val x = row.labelX - 16f
			val ink = (if (row.folder) palette.ink else palette.muted).copy(alpha = appear)
			if (row.folder) {
				fill(x, y + 4f, 4f, 2f, ink)
				frame(x, y + 6f, 10f, 7f, ink)
			} else {
				frame(x + 1f, y + 3f, 8f, 10f, ink)
				line(x + 3f, y + 7f, x + 7f, y + 7f, ink)
				line(x + 3f, y + 10f, x + 7f, y + 10f, ink)
			}
			text(row.name, x + 16f, y + 3f, ink)
		}
	}
}

internal class FileRow(val depth: Int, val name: String, val folder: Boolean) {
	/** Where the row's label starts, in sketch units. */
	val labelX: Float get() = 42f + depth * 14f
}

internal val FileRows = listOf(
	FileRow(0, "${ExampleProjectRepository.PROJECT_NAME}/", folder = true),
	FileRow(1, "scenes/", folder = true),
	FileRow(2, "01~I. Down the Rabbit-Hole~2/", folder = true),
	FileRow(3, "0~The Riverbank~3.md", folder = false),
	FileRow(3, "1~Down the Rabbit-Hole~129.md", folder = false),
	FileRow(1, "encyclopedia/", folder = true),
	FileRow(1, "notes/", folder = true),
	FileRow(1, "timeline/", folder = true),
	FileRow(1, "project.toml", folder = false),
)

/** A phone and a laptop fill with the same writing, with a sync pulse running between them. */
@Composable
private fun AnywhereSketch(active: Boolean, box: SketchBox, modifier: Modifier) {
	val pulse = remember { Animatable(0.5f) }
	val settle = rememberSettle(active) {
		while (true) {
			pulse.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
			delay(LoopPauseMillis / 2)
			pulse.animateTo(0f, tween(1000, easing = FastOutSlowInEasing))
			delay(LoopPauseMillis / 2)
		}
	}

	val desktop = Res.string.intro_sketch_desktop.get().uppercase()

	SketchCanvas(modifier, box) {
		val t = settle.value

		val phone = t.phase(0f, 0.35f)
		if (phone > 0f) {
			val dx = -(1f - phone) * 20f
			val color = palette.muted.copy(alpha = phone)
			fill(44f + dx, 42f, 54f, 100f, palette.paper.copy(alpha = phone))
			frame(44f + dx, 42f, 54f, 100f, color, heavy = true)
			fill(63f + dx, 46f, 16f, 2f, color)
			PhoneLines.forEachIndexed { index, width ->
				val line = t.phase(0.38f + index * 0.06f, 0.53f + index * 0.06f)
				if (line > 0f) fill(52f + dx, 58f + index * 10f, width * line, 3f, palette.muted.copy(alpha = 0.45f * phone))
			}
			text("ANDROID · IOS", 71f + dx, 150f, palette.muted.copy(alpha = phone), align = LabelAlign.Center)
		}

		val laptop = t.phase(0.05f, 0.4f)
		if (laptop > 0f) {
			val dx = (1f - laptop) * 20f
			val color = palette.muted.copy(alpha = laptop)
			fill(150f + dx, 32f, 140f, 92f, palette.paper.copy(alpha = laptop))
			frame(150f + dx, 32f, 140f, 92f, color, heavy = true)
			fill(136f + dx, 126f, 168f, 4f, color)
			line(184f + dx, 36f, 184f + dx, 120f, palette.rule.copy(alpha = laptop))
			repeat(5) { index ->
				fill(156f + dx, 42f + index * 10f, 20f, 3f, palette.muted.copy(alpha = 0.3f * laptop))
			}
			LaptopLines.forEachIndexed { index, width ->
				val line = t.phase(0.3f + index * 0.06f, 0.45f + index * 0.06f)
				if (line > 0f) fill(192f + dx, 44f + index * 10f, width * line, 3f, palette.muted.copy(alpha = 0.45f * laptop))
			}
			text(desktop, 220f + dx, 138f, palette.muted.copy(alpha = laptop), align = LabelAlign.Center)
		}

		val link = t.phase(0.4f, 0.7f)
		if (link > 0f) {
			line(104f, 92f, 144f, 92f, palette.rule.copy(alpha = link), dashed = true)
			val x = 104f + 40f * pulse.value
			fill(x - 3f, 89f, 6f, 6f, palette.accent.copy(alpha = link))
		}
	}
}

private val PhoneLines = floatArrayOf(30f, 38f, 26f, 38f, 34f, 20f, 36f)
private val LaptopLines = floatArrayOf(80f, 72f, 84f, 60f, 80f, 76f, 40f)

/** The example project as a book that opens to its first chapter. */
@Composable
private fun BookSketch(active: Boolean, box: SketchBox, modifier: Modifier) {
	val settle = rememberSettle(active)

	SketchCanvas(modifier, box) {
		val t = settle.value
		val appear = t.phase(0f, 0.15f)
		if (appear <= 0f) return@SketchCanvas

		val top = 26f
		val height = 140f
		val pageWidth = 94f
		val open = t.phase(0.1f, 0.6f)
		val cover = pageWidth * cos(PI.toFloat() * open)

		fill(162f, top, pageWidth, height, palette.paper.copy(alpha = appear))
		frame(162f, top, pageWidth, height, palette.rule.copy(alpha = appear))
		line(164f, top + height + 2f, 256f, top + height + 2f, palette.rule.copy(alpha = appear))
		line(166f, top + height + 4f, 256f, top + height + 4f, palette.rule.copy(alpha = appear))

		if (cover < 0f) {
			val width = abs(cover)
			fill(158f - width, top, width, height, palette.paper)
			frame(158f - width, top, width, height, palette.rule)
			if (open >= 1f) {
				line(64f, top + height + 2f, 156f, top + height + 2f, palette.rule)
				line(64f, top + height + 4f, 154f, top + height + 4f, palette.rule)
			}
		}

		val words = t.phase(0.55f, 1f)
		if (words > 0f) {
			text("§ I", 176f, top + 12f, palette.muted.copy(alpha = words))
			fill(176f, top + 28f, 50f, 4f, palette.ink.copy(alpha = words))
			repeat(9) { index ->
				val line = t.phase(0.6f + index * 0.035f, 0.7f + index * 0.035f)
				fill(176f, top + 44f + index * 10f, (if (index == 8) 40f else 66f) * line, 3f, palette.muted.copy(alpha = 0.4f))
			}
			hatched(76f, top + 14f, 70f, 64f, palette.rule.copy(alpha = words))
			repeat(5) { index ->
				val line = t.phase(0.65f + index * 0.035f, 0.75f + index * 0.035f)
				fill(76f, top + 90f + index * 10f, 70f * line, 3f, palette.muted.copy(alpha = 0.4f))
			}
		}

		if (cover >= 0f) {
			fill(160f, top, cover, height, palette.raised.copy(alpha = appear))
			frame(160f, top, cover, height, palette.muted.copy(alpha = appear))
			val label = (cover / pageWidth) * appear
			if (label > 0.6f) {
				text("ALICE IN", 160f + cover / 2f, top + 54f, palette.ink.copy(alpha = label), align = LabelAlign.Center)
				text("WONDERLAND", 160f + cover / 2f, top + 68f, palette.ink.copy(alpha = label), align = LabelAlign.Center)
			}
		}

		line(160f, top, 160f, top + height, palette.muted.copy(alpha = appear))
		val caption = palette.muted.copy(alpha = t.phase(0.6f, 0.9f))
		text(ExampleProjectRepository.PROJECT_NAME.uppercase(), 160f, 184f, caption, align = LabelAlign.Center)
	}
}

/** Progress from 0 to 1 that runs once each time the sketch becomes [active], then hands over to [loop]. */
@Composable
private fun rememberSettle(active: Boolean, loop: suspend () -> Unit = {}): Animatable<Float, AnimationVector1D> {
	val settle = remember { Animatable(0f) }
	LaunchedEffect(active) {
		if (!active) return@LaunchedEffect
		settle.animateTo(1f, tween(SettleMillis, easing = LinearEasing))
		loop()
	}
	return settle
}

/** How far along [start] to [end] of the overall progress this is, eased, from 0 to 1. */
private fun Float.phase(start: Float, end: Float): Float =
	FastOutSlowInEasing.transform(((this - start) / (end - start)).coerceIn(0f, 1f))

private class SketchPalette(
	val ink: Color,
	val muted: Color,
	val rule: Color,
	val accent: Color,
	val paper: Color,
	val raised: Color,
)

/** The mono label style for a sketch in [box] drawn [unit] pixels per sketch unit, so labels keep the sketch's proportions. */
internal fun sketchLabelStyle(base: TextStyle, box: SketchBox, unit: Float, density: Density): TextStyle =
	sketchTextStyle(base, unit, density, size = box.labelSize, tracking = 1f)

/** [base] at [size] sketch units with [tracking] between letters, whatever the density and font scale. */
private fun sketchTextStyle(base: TextStyle, unit: Float, density: Density, size: Float, tracking: Float): TextStyle {
	val spPerUnit = unit / (density.density * density.fontScale)
	return base.copy(fontSize = (size * spPerUnit).sp, letterSpacing = (tracking * spPerUnit).sp, lineHeight = TextUnit.Unspecified)
}

@Composable
private fun SketchCanvas(modifier: Modifier, box: SketchBox, draw: Sketch.() -> Unit) {
	val measurer = rememberTextMeasurer()
	val labelStyle = MaterialTheme.typography.labelSmall
	val colors = MaterialTheme.colorScheme
	val palette = remember(colors) {
		SketchPalette(
			ink = colors.onSurface,
			muted = colors.onSurfaceVariant,
			rule = colors.outlineVariant,
			accent = colors.primary,
			paper = colors.surface,
			raised = colors.surfaceContainerHigh,
		)
	}
	Spacer(
		modifier.drawWithCache {
			val unit = min(size.width / box.width, size.height / box.height)
			val origin = Offset((size.width - box.width * unit) / 2f, (size.height - box.height * unit) / 2f)
			val textDensity = Density(density, fontScale)
			val sketch = Sketch(box, unit, origin, measurer, textDensity, sketchLabelStyle(labelStyle, box, unit, textDensity), palette)
			onDrawBehind { sketch.drawIn(this, draw) }
		}
	)
}

/**
 * Draws in sketch units: its [box], scaled into the canvas. Built once per size, so text is
 * measured once and an animation frame only redraws.
 */
private class Sketch(
	val box: SketchBox,
	/** One sketch unit in pixels. */
	private val unit: Float,
	private val origin: Offset,
	private val measurer: TextMeasurer,
	private val density: Density,
	private val labelStyle: TextStyle,
	val palette: SketchPalette,
) {
	private lateinit var scope: DrawScope
	private val hairline = Stroke(1f)
	private val device = Stroke(1.2f * unit)
	private val dash = PathEffect.dashPathEffect(floatArrayOf(3f * unit, 3f * unit))

	private data class Measure(val text: String, val style: TextStyle, val maxWidth: Int)

	private val measured = HashMap<Measure, TextLayoutResult>()
	private val styles = HashMap<Triple<TextStyle, Float, Float>, TextStyle>()

	fun drawIn(scope: DrawScope, draw: Sketch.() -> Unit) {
		this.scope = scope
		draw()
	}

	private fun at(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)

	private fun measure(text: String, style: TextStyle, maxWidth: Int = Constraints.Infinity): TextLayoutResult =
		measured.getOrPut(Measure(text, style, maxWidth)) {
			measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth))
		}

	/** [base] set at [size] sketch units, for faces beyond the mono labels. */
	fun scaled(base: TextStyle, size: Float, tracking: Float = 0f): TextStyle =
		styles.getOrPut(Triple(base, size, tracking)) { sketchTextStyle(base, unit, density, size, tracking) }

	fun textWidth(text: String, style: TextStyle): Float = measure(text, style).size.width / unit

	/** [text] in [style], wrapped to [width] units and centred on [centerX], its top at [y]. Returns its bottom. */
	fun caption(text: String, style: TextStyle, centerX: Float, y: Float, width: Float, color: Color): Float {
		val layout = measure(text, style, maxWidth = (width * unit).toInt())
		val point = at(centerX, y)
		scope.drawText(layout, color = color, topLeft = Offset(point.x - layout.size.width / 2f, point.y))
		return y + layout.size.height / unit
	}

	/** [text] in [style], centred on ([centerX], [centerY]) and drawn at [scale]. */
	fun wordmark(text: String, style: TextStyle, centerX: Float, centerY: Float, color: Color, scale: Float) {
		val layout = measure(text, style)
		val center = at(centerX, centerY)
		scope.withTransform({ scale(scale, scale, pivot = center) }) {
			drawText(layout, color = color, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
		}
	}

	fun fill(x: Float, y: Float, width: Float, height: Float, color: Color) {
		if (width <= 0f || height <= 0f) return
		scope.drawRect(color, at(x, y), Size(width * unit, height * unit))
	}

	/** A hairline frame, or the heavier outline of a device when [heavy]. */
	fun frame(x: Float, y: Float, width: Float, height: Float, color: Color, heavy: Boolean = false) {
		if (width <= 0f || height <= 0f) return
		scope.drawRect(color, at(x, y), Size(width * unit, height * unit), style = if (heavy) device else hairline)
	}

	fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, dashed: Boolean = false) {
		scope.drawLine(color, at(x1, y1), at(x2, y2), strokeWidth = hairline.width, pathEffect = if (dashed) dash else null)
	}

	/** A cubic hairline from [from] to [to], drawn only up to [fraction] of the way. */
	fun curve(from: Offset, control1: Offset, control2: Offset, to: Offset, fraction: Float, color: Color) {
		val steps = 32
		val last = (steps * fraction).toInt()
		var previous = from
		for (step in 1..last) {
			val point = cubic(from, control1, control2, to, step / steps.toFloat())
			line(previous.x, previous.y, point.x, point.y, color)
			previous = point
		}
	}

	private fun cubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
		val u = 1f - t
		return p0 * (u * u * u) + p1 * (3f * u * u * t) + p2 * (3f * u * t * t) + p3 * (t * t * t)
	}

	/** Fills [path], drawn on a [viewport]-unit square, [size] units wide at ([x], [y]), turned [degrees] about [pivot] in its own units. */
	fun path(path: Path, fill: Brush, alpha: Float, x: Float, y: Float, size: Float, viewport: Float, degrees: Float, pivot: Offset) {
		val topLeft = at(x, y)
		val scale = size / viewport * unit
		scope.withTransform({
			translate(topLeft.x, topLeft.y)
			scale(scale, scale, pivot = Offset.Zero)
			rotate(degrees, pivot)
		}) {
			drawPath(path, fill, alpha = alpha)
		}
	}

	/** The engraving placeholder's 45 degree hatching, in a hairline frame. */
	fun hatched(x: Float, y: Float, width: Float, height: Float, color: Color) {
		frame(x, y, width, height, color)
		val topLeft = at(x, y)
		val bottomRight = at(x + width, y + height)
		scope.clipRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y) {
			var offset = -height
			while (offset < width) {
				line(x + offset, y + height, x + offset + height, y, color)
				offset += 6f
			}
		}
	}

	/** A mono label whose [align] edge, or centre, sits at [x]; [y] is its top. */
	fun text(text: String, x: Float, y: Float, color: Color, align: LabelAlign = LabelAlign.Start) {
		val layout = measure(text, labelStyle)
		val point = at(x, y)
		val left = when (align) {
			LabelAlign.Start -> point.x
			LabelAlign.Center -> point.x - layout.size.width / 2f
			LabelAlign.End -> point.x - layout.size.width
		}
		scope.drawText(layout, color = color, topLeft = Offset(left, point.y))
	}
}

private enum class LabelAlign { Start, Center, End }
