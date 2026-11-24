package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

const val HD_COACH_MARK_TAG = "HdCoachMark"

/** Where the elements a coach mark can point at currently sit, keyed by whatever the caller likes. */
@Stable
class HdCoachMarkAnchors {
	internal val bounds = mutableStateMapOf<Any, Rect>()
}

@Composable
fun rememberHdCoachMarkAnchors(): HdCoachMarkAnchors = remember { HdCoachMarkAnchors() }

/**
 * Registers this element as the target for the coach mark shown under [key]. The anchor is
 * forgotten when the element leaves composition, so a recycled list row never leaves stale bounds.
 */
fun Modifier.hdCoachMarkAnchor(anchors: HdCoachMarkAnchors, key: Any): Modifier =
	this then CoachMarkAnchorElement(anchors, key)

private data class CoachMarkAnchorElement(
	val anchors: HdCoachMarkAnchors,
	val key: Any,
) : ModifierNodeElement<CoachMarkAnchorNode>() {
	override fun create() = CoachMarkAnchorNode(anchors, key)

	override fun update(node: CoachMarkAnchorNode) = node.retarget(anchors, key)
}

private class CoachMarkAnchorNode(
	private var anchors: HdCoachMarkAnchors,
	private var key: Any,
) : Modifier.Node(), GlobalPositionAwareModifierNode {
	private var written: Rect? = null

	override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
		val bounds = coordinates.boundsInRoot()
		if (bounds != written) {
			anchors.bounds[key] = bounds
			written = bounds
		}
	}

	override fun onDetach() = forget()

	fun retarget(anchors: HdCoachMarkAnchors, key: Any) {
		if (anchors === this.anchors && key == this.key) return
		forget()
		this.anchors = anchors
		this.key = key
	}

	/** Only clears what this node wrote, in case another element has since claimed the key. */
	private fun forget() {
		if (written != null && anchors.bounds[key] == written) anchors.bounds.remove(key)
		written = null
	}
}

/**
 * Dims [content] except the element anchored under [activeKey], and sets a callout beside it.
 *
 * ```
 *   ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
 *   ░┌─────────────────────────┐░
 *   ░│ the anchored element    │░
 *   ░└─────────────────────────┘░
 *   ░ ┌───────────────────────┐ ░
 *   ░ │ Title                 │ ░
 *   ░ │ Body.                 │ ░
 *   ░ │    [Turn off] [Got it]│ ░
 *   ░ └───────────────────────┘ ░
 *   ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
 * ```
 *
 * Shows nothing until the anchor has been laid out, so a mark for an element that is
 * off screen or absent simply waits. Taps on the dimmed area call [onDismiss]; the element
 * itself stays interactive.
 */
@Composable
fun HdCoachMarkHost(
	anchors: HdCoachMarkAnchors,
	activeKey: Any?,
	title: String,
	body: String,
	dismissLabel: String,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
	/** A quieter second action beside the dismiss button, such as turning tips off. */
	secondaryLabel: String? = null,
	onSecondary: () -> Unit = {},
	content: @Composable () -> Unit,
) {
	var hostOrigin by remember { mutableStateOf(Offset.Zero) }

	Box(modifier = modifier.onGloballyPositioned { hostOrigin = it.positionInRoot() }) {
		content()

		val target = activeKey?.let { anchors.bounds[it] }?.translate(-hostOrigin)
		if (target != null && !target.isEmpty) {
			val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)
			val accent = MaterialTheme.colorScheme.primary

			Box(
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
					.drawBehind {
						drawRect(scrim)
						drawRect(Color.Transparent, target.topLeft, target.size, blendMode = BlendMode.Clear)
						drawRect(accent, target.topLeft, target.size, style = Stroke(width = 1.dp.toPx()))
					}
			)

			ScrimTapZones(target = target, onTap = onDismiss)

			CalloutPlacement(target = target) {
				Callout(
					title = title,
					body = body,
					dismissLabel = dismissLabel,
					onDismiss = onDismiss,
					secondaryLabel = secondaryLabel,
					onSecondary = onSecondary,
				)
			}
		}
	}
}

/** Catches taps on the dimmed area only; nothing covers [target], so it keeps taking its own gestures. */
@Composable
private fun ScrimTapZones(target: Rect, onTap: () -> Unit) {
	val currentOnTap by rememberUpdatedState(onTap)
	Layout(
		content = {
			repeat(4) {
				Box(Modifier.pointerInput(Unit) { detectTapGestures { currentOnTap() } })
			}
		},
		modifier = Modifier.fillMaxSize(),
	) { measurables, constraints ->
		val width = constraints.maxWidth
		val height = constraints.maxHeight
		val left = target.left.roundToInt().coerceIn(0, width)
		val right = target.right.roundToInt().coerceIn(left, width)
		val top = target.top.roundToInt().coerceIn(0, height)
		val bottom = target.bottom.roundToInt().coerceIn(top, height)
		val zones = listOf(
			IntRect(0, 0, width, top),
			IntRect(0, bottom, width, height),
			IntRect(0, top, left, bottom),
			IntRect(right, top, width, bottom),
		)
		val placeables = measurables.zip(zones) { measurable, zone ->
			measurable.measure(Constraints.fixed(zone.width, zone.height)) to zone.topLeft
		}
		layout(width, height) { placeables.forEach { (placeable, at) -> placeable.place(at) } }
	}
}

/**
 * Sets the callout under the target, or above it when it only fits there. When it fits on neither
 * side it takes the roomier one and is held to that height, so it never covers the target.
 */
@Composable
private fun CalloutPlacement(target: Rect, callout: @Composable () -> Unit) {
	Layout(content = callout, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
		val margin = CalloutMargin.roundToPx()
		val gap = CalloutGap.roundToPx()
		val maxWidth = minOf(constraints.maxWidth - margin * 2, CalloutMaxWidth.roundToPx()).coerceAtLeast(0)
		val below = target.bottom.roundToInt() + gap
		val roomBelow = (constraints.maxHeight - margin - below).coerceAtLeast(0)
		val roomAbove = (target.top.roundToInt() - gap - margin).coerceAtLeast(0)
		val placeable = measurables.single().measure(
			Constraints(maxWidth = maxWidth, maxHeight = maxOf(roomBelow, roomAbove))
		)

		val x = (target.center.x - placeable.width / 2f).roundToInt()
			.coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
		val y = if (placeable.height <= roomBelow) below else target.top.roundToInt() - gap - placeable.height

		layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
	}
}

@Composable
private fun Callout(
	title: String,
	body: String,
	dismissLabel: String,
	onDismiss: () -> Unit,
	secondaryLabel: String?,
	onSecondary: () -> Unit,
) {
	Column(
		modifier = Modifier
			.testTag(HD_COACH_MARK_TAG)
			.background(MaterialTheme.colorScheme.surface)
			.border(Dp.Hairline, MaterialTheme.colorScheme.primary, RectangleShape)
			// Taps on the callout itself must not fall through to the scrim and dismiss it.
			.pointerInput(Unit) { detectTapGestures { } }
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 14.dp, vertical = 12.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
		Text(
			text = body,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Row(
			modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			if (secondaryLabel != null) HdHairlineButton(label = secondaryLabel, onClick = onSecondary)
			HdHairlineButton(label = dismissLabel, onClick = onDismiss, emphasised = true)
		}
	}
}

private val CalloutMaxWidth = 320.dp
private val CalloutMargin = 16.dp
private val CalloutGap = 8.dp
