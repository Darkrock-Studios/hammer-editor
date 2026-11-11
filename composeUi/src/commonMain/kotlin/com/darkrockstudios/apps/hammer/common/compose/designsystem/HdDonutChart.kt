package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
data class HdDonutSlice(
	val value: Float,
	val color: Color,
)

private const val START_ANGLE = -90f

// Ring outer edge as a fraction of the pie radius; the rest is headroom for
// the hover expansion.
private const val OUTER_RADIUS = 0.95f

// Labels sit on a circle this much wider than the pie.
private const val LABEL_RADIUS = 1.1f

private const val SEARCH_STEPS = 20

/**
 * Donut chart with labels outside the ring joined by curved connectors.
 *
 * The ring sweeps in clockwise from 12 o'clock, then the labels fade in.
 * On desktop the slice under the pointer grows slightly and shows [tooltip]
 * above the pointer. A tap reports the slice under it through [onSliceClick],
 * or [onBackgroundClick] when it lands anywhere else in the chart.
 *
 * The pie is sized to the largest diameter (between [minDiameter] and
 * [maxDiameter]) that fits alongside the labels, and the whole arrangement is
 * centred in the available space.
 */
@Composable
fun HdDonutChart(
	slices: List<HdDonutSlice>,
	modifier: Modifier = Modifier,
	holeSize: Float = 0.55f,
	hoverExpand: Float = 1.05f,
	minDiameter: Dp = 100.dp,
	maxDiameter: Dp = 300.dp,
	connectorWidth: Dp = 2.dp,
	animate: Boolean = true,
	sweepAnimation: AnimationSpec<Float> = tween(800, easing = LinearOutSlowInEasing),
	labelAnimation: AnimationSpec<Float> = tween(1000, easing = LinearOutSlowInEasing),
	onSliceClick: (Int) -> Unit = {},
	onBackgroundClick: () -> Unit = {},
	tooltip: (@Composable (Int) -> Unit)? = null,
	label: @Composable (Int) -> Unit = {},
	holeContent: @Composable BoxScope.() -> Unit = {},
) {
	require(holeSize in 0f..1f) { "holeSize must be between 0 and 1" }

	val progress = remember { Animatable(if (animate) 0f else 1f) }
	val labelAlpha = remember { Animatable(if (animate) 0f else 1f) }
	LaunchedEffect(Unit) {
		progress.animateTo(1f, sweepAnimation)
		labelAlpha.animateTo(1f, labelAnimation)
	}

	var geometry by remember { mutableStateOf<DonutGeometry?>(null) }
	var hovered by remember { mutableStateOf<Int?>(null) }
	var lastHovered by remember { mutableStateOf<Int?>(null) }
	var pointer by remember { mutableStateOf(Offset.Zero) }

	val expansion = remember(slices.size) { List(slices.size) { Animatable(1f) } }
	LaunchedEffect(hovered, expansion) {
		expansion.forEachIndexed { index, anim ->
			launch { anim.animateTo(if (index == hovered) hoverExpand else 1f) }
		}
	}

	val currentSlices by rememberUpdatedState(slices)
	val currentOnSliceClick by rememberUpdatedState(onSliceClick)
	val currentOnBackgroundClick by rememberUpdatedState(onBackgroundClick)
	fun hitTest(position: Offset): Int? {
		val g = geometry ?: return null
		return hitSlice(position, g, sliceSweeps(currentSlices, progress.value), holeSize, hoverExpand)
	}

	Layout(
		contents = listOf(
			{
				slices.indices.forEach { index ->
					Box(Modifier.graphicsLayer { alpha = labelAlpha.value }) { label(index) }
				}
			},
			{
				Box(Modifier.clip(CircleShape), contentAlignment = Alignment.Center) { holeContent() }
			},
			{
				if (tooltip != null) {
					AnimatedVisibility(visible = hovered != null, enter = fadeIn(), exit = fadeOut()) {
						Crossfade(targetState = lastHovered) { index ->
							if (index != null && index < slices.size) tooltip(index)
						}
					}
				}
			},
		),
		modifier = modifier
			.pointerInput(holeSize, hoverExpand) {
				awaitPointerEventScope {
					while (true) {
						val event = awaitPointerEvent()
						val change = event.changes.firstOrNull() ?: continue
						if (change.type != PointerType.Mouse) continue
						val hit = if (event.type == PointerEventType.Exit) null else hitTest(change.position)
						hovered = hit
						if (hit != null) {
							lastHovered = hit
							pointer = change.position
						}
					}
				}
			}
			.pointerInput(holeSize, hoverExpand) {
				detectTapGestures { position ->
					val hit = hitTest(position)
					if (hit != null) currentOnSliceClick(hit) else currentOnBackgroundClick()
				}
			}
			.drawWithCache {
				val g = geometry
				val connectors = g?.let { connectorPaths(it) }.orEmpty()
				val connectorStroke = Stroke(width = connectorWidth.toPx())
				onDrawBehind {
					if (g == null) return@onDrawBehind
					val half = g.diameter / 2f
					val inner = half * holeSize
					val sweeps = sliceSweeps(slices, progress.value)
					var start = START_ANGLE
					sweeps.forEachIndexed { index, sweep ->
						if (sweep > 0f) {
							val outer = half * OUTER_RADIUS * (expansion.getOrNull(index)?.value ?: 1f)
							val thickness = outer - inner
							val radius = inner + thickness / 2f
							drawArc(
								color = slices[index].color,
								startAngle = start,
								sweepAngle = sweep,
								useCenter = false,
								topLeft = g.center - Offset(radius, radius),
								size = Size(radius * 2f, radius * 2f),
								style = Stroke(width = thickness),
							)
						}
						start += sweep
					}

					val alpha = labelAlpha.value
					if (alpha > 0f) {
						connectors.forEachIndexed { index, path ->
							drawPath(path, color = slices[index].color, alpha = alpha, style = connectorStroke)
						}
					}
				}
			},
	) { (labelMeasurables, holeMeasurables, tooltipMeasurables), constraints ->
		val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
		val maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.POSITIVE_INFINITY
		val minPx = minDiameter.toPx()
		val maxPx = maxDiameter.toPx()

		val labelMaxWidth = ((maxWidth - minPx) / 2f).let { if (it.isInfinite()) Constraints.Infinity else it.toInt().coerceAtLeast(0) }
		val labelPlaceables = labelMeasurables.map { it.measure(Constraints(maxWidth = labelMaxWidth)) }
		val midAngles = midAngles(sliceSweeps(slices, 1f))

		fun arrange(diameter: Float): Arrangement {
			val labels = placeLabels(diameter, midAngles, labelPlaceables)
			return Arrangement(diameter, labels, labelBounds(diameter, labels, labelPlaceables))
		}

		val upper = min(maxPx, max(min(maxWidth, maxHeight), minPx))
		var lo = minPx
		var hi = upper
		if (arrange(hi).fits(maxWidth, maxHeight)) {
			lo = hi
		} else {
			repeat(SEARCH_STEPS) {
				val mid = (lo + hi) / 2f
				if (arrange(mid).fits(maxWidth, maxHeight)) lo = mid else hi = mid
			}
		}
		val arrangement = arrange(floor(lo))
		val bounds = arrangement.bounds

		val width = constraints.constrainWidth(ceil(bounds.width).toInt())
		val height = constraints.constrainHeight(ceil(bounds.height).toInt())
		val center = Offset(
			(width - bounds.width) / 2f - bounds.minX,
			(height - bounds.height) / 2f - bounds.minY,
		)
		// Written without reading it, so measuring does not invalidate itself. An
		// equal value is not a change, so draw and hit testing only update on a
		// real layout change.
		geometry = DonutGeometry(center, arrangement.diameter, midAngles, arrangement.labels)

		val holeDiameter = floor(arrangement.diameter * holeSize).toInt()
		val holePlaceable = holeMeasurables.firstOrNull()?.measure(Constraints.fixed(holeDiameter, holeDiameter))
		val tooltipPlaceable = tooltipMeasurables.firstOrNull()?.measure(Constraints(maxWidth = width, maxHeight = height))

		layout(width, height) {
			labelPlaceables.forEachIndexed { index, placeable ->
				val topLeft = center + arrangement.labels[index].topLeft
				placeable.place(topLeft.x.toInt(), topLeft.y.toInt())
			}
			holePlaceable?.place(
				(center.x - holeDiameter / 2f).toInt(),
				(center.y - holeDiameter / 2f).toInt(),
			)
			// The pointer is read in the layer block so following it only moves the
			// tooltip's layer, without placing or redrawing the rest of the chart.
			tooltipPlaceable?.let {
				val maxX = (width - it.width).coerceAtLeast(0).toFloat()
				it.placeWithLayer(0, 0, zIndex = 1f) {
					translationX = (pointer.x - it.width / 2f).coerceIn(0f, maxX)
					translationY = (pointer.y - it.height).coerceAtLeast(0f)
				}
			}
		}
	}
}

private data class LabelPlacement(
	/** Relative to the pie centre. */
	val topLeft: Offset,
	/** Where the connector meets the label, relative to the pie centre. */
	val anchor: Offset,
	val isLeft: Boolean,
)

private data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
	val width get() = maxX - minX
	val height get() = maxY - minY
}

private class Arrangement(
	val diameter: Float,
	val labels: List<LabelPlacement>,
	val bounds: Bounds,
) {
	fun fits(maxWidth: Float, maxHeight: Float) = bounds.width < maxWidth && bounds.height < maxHeight
}

private data class DonutGeometry(
	val center: Offset,
	val diameter: Float,
	val midAngles: List<Float>,
	val labels: List<LabelPlacement>,
)

private fun connectorPaths(geometry: DonutGeometry): List<Path> {
	val half = geometry.diameter / 2f
	return geometry.labels.mapIndexed { index, placement ->
		val midAngle = geometry.midAngles[index]
		val from = geometry.center + polar(half * OUTER_RADIUS, midAngle)
		val to = geometry.center + placement.anchor
		val reach = (to - from).getDistance() / 2f
		val c1 = from + polar(reach, midAngle)
		val c2 = to + polar(reach, if (placement.isLeft) 0f else 180f)
		Path().apply {
			moveTo(from.x, from.y)
			cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)
		}
	}
}

private fun sliceSweeps(slices: List<HdDonutSlice>, progress: Float): List<Float> {
	val total = slices.sumOf { it.value.coerceAtLeast(0f).toDouble() }.toFloat()
	if (total <= 0f) return slices.map { 0f }
	return slices.map { it.value.coerceAtLeast(0f) / total * 360f * progress }
}

private fun midAngles(sweeps: List<Float>): List<Float> {
	var start = START_ANGLE
	return sweeps.map { sweep ->
		(start + sweep / 2f).also { start += sweep }
	}
}

private fun polar(radius: Float, degrees: Float): Offset {
	val radians = degrees * PI.toFloat() / 180f
	return Offset(radius * cos(radians), radius * sin(radians))
}

private fun hitSlice(
	position: Offset,
	geometry: DonutGeometry,
	sweeps: List<Float>,
	holeSize: Float,
	hoverExpand: Float,
): Int? {
	val delta = position - geometry.center
	val radius = delta.getDistance()
	val half = geometry.diameter / 2f
	if (radius < half * holeSize || radius > half * OUTER_RADIUS * hoverExpand) return null

	val degrees = atan2(delta.y, delta.x) * 180f / PI.toFloat()
	val fromStart = ((degrees - START_ANGLE) % 360f + 360f) % 360f
	var end = 0f
	sweeps.forEachIndexed { index, sweep ->
		end += sweep
		if (sweep > 0f && fromStart < end) return index
	}
	return null
}

private enum class Quadrant { NorthEast, SouthEast, SouthWest, NorthWest }

private fun quadrantOf(degrees: Float): Quadrant {
	val normalized = (degrees % 360f + 360f) % 360f
	val adjusted = if (normalized <= 270f) normalized else normalized - 360f
	return when {
		adjusted <= 0f -> Quadrant.NorthEast
		adjusted <= 90f -> Quadrant.SouthEast
		adjusted <= 180f -> Quadrant.SouthWest
		else -> Quadrant.NorthWest
	}
}

/**
 * Places each label just outside the pie at its slice's mid angle. Labels are
 * stacked outward from the horizontal axis on each side so neighbours never
 * overlap, and slid sideways so they clear the label circle.
 */
private fun placeLabels(
	diameter: Float,
	midAngles: List<Float>,
	placeables: List<Placeable>,
): List<LabelPlacement> {
	val radius = diameter / 2f * LABEL_RADIUS
	val byQuadrant = midAngles.indices.groupBy { quadrantOf(midAngles[it]) }
	val result = arrayOfNulls<LabelPlacement>(midAngles.size)

	fun circleX(y: Float): Float? = if (abs(y) <= radius) sqrt(radius * radius - y * y) else null

	fun place(index: Int, quadrant: Quadrant, lastY: Float): LabelPlacement {
		val radians = midAngles[index] * PI.toFloat() / 180f
		val width = placeables[index].width
		val height = placeables[index].height
		val idealTop = radius * sin(radians) - height / 2f
		val south = quadrant == Quadrant.SouthEast || quadrant == Quadrant.SouthWest
		val top = if (south) max(idealTop, lastY) else min(idealTop, lastY - height)
		val bottom = top + height
		val isLeft = quadrant == Quadrant.NorthWest || quadrant == Quadrant.SouthWest
		val xLimit = radius * cos(radians)
		val x = if (isLeft) {
			minOf(xLimit, circleX(top)?.let { -it } ?: Float.POSITIVE_INFINITY, circleX(bottom)?.let { -it } ?: Float.POSITIVE_INFINITY)
		} else {
			maxOf(xLimit, circleX(top) ?: Float.NEGATIVE_INFINITY, circleX(bottom) ?: Float.NEGATIVE_INFINITY)
		}
		val anchor = Offset(x, top + height / 2f)
		val topLeft = if (isLeft) Offset(x - width, top) else Offset(x, top)
		return LabelPlacement(topLeft, anchor, isLeft)
	}

	// Each side stacks upward from the horizontal axis for its north quadrant,
	// then downward for its south quadrant, starting below the lowest north label.
	fun side(north: Quadrant, northOrder: List<Int>, south: Quadrant, southOrder: List<Int>) {
		var lastY = Float.POSITIVE_INFINITY
		var maxY = Float.NEGATIVE_INFINITY
		northOrder.forEach { index ->
			val placement = place(index, north, lastY)
			result[index] = placement
			lastY = placement.topLeft.y
			maxY = max(maxY, placement.topLeft.y + placeables[index].height)
		}
		lastY = maxY
		southOrder.forEach { index ->
			val placement = place(index, south, lastY)
			result[index] = placement
			lastY = placement.topLeft.y + placeables[index].height
		}
	}

	side(
		Quadrant.NorthEast, byQuadrant[Quadrant.NorthEast].orEmpty().reversed(),
		Quadrant.SouthEast, byQuadrant[Quadrant.SouthEast].orEmpty(),
	)
	side(
		Quadrant.NorthWest, byQuadrant[Quadrant.NorthWest].orEmpty(),
		Quadrant.SouthWest, byQuadrant[Quadrant.SouthWest].orEmpty().reversed(),
	)

	return result.map { requireNotNull(it) }
}

private fun labelBounds(
	diameter: Float,
	labels: List<LabelPlacement>,
	placeables: List<Placeable>,
): Bounds {
	val half = diameter / 2f
	var minX = -half
	var minY = -half
	var maxX = half
	var maxY = half
	labels.forEachIndexed { index, placement ->
		minX = min(minX, placement.topLeft.x)
		minY = min(minY, placement.topLeft.y)
		maxX = max(maxX, placement.topLeft.x + placeables[index].width)
		maxY = max(maxY, placement.topLeft.y + placeables[index].height)
	}
	return Bounds(minX, minY, maxX, maxY)
}
