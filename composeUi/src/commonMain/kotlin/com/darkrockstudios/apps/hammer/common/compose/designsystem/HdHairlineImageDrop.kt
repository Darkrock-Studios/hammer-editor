package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.fileDropTarget
import io.github.vinceglb.filekit.PlatformFile
import kotlin.math.ceil

/**
 * Image drop / preview affordance for the create-entry vocabulary.
 *
 * Empty state: a 135° striped surface with a dashed hairline border, a
 * centered + glyph tile, a mono "drop or" hint, and a hairline-bordered
 * "browse" button. Click anywhere to launch the picker.
 *
 * Populated state: the same height with [image] rendered inside; a
 * trailing "remove" affordance overlays the top-right corner.
 *
 *     COVER ART                                    1 ATTACHED   REPLACE ↗
 *     ╔═══════════════════════════════════════════════════════╗
 *     ║ ░░░░░░░░░░░░░ [+] ░░░░░░░░░░░░░░ DROP IMAGE · OR ░░░ ║
 *     ╚═══════════════════════════════════════════════════════╝
 */
@Composable
fun HdHairlineImageDrop(
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	dropHint: String = "Drop image · or",
	browseLabel: String = "Browse files",
	height: Dp = 160.dp,
	image: (@Composable BoxScope.() -> Unit)? = null,
	onRemove: (() -> Unit)? = null,
	attachedLabel: String? = null,
	replaceLabel: String? = null,
	onFilesDropped: ((List<PlatformFile>) -> Unit)? = null,
	dropExtensions: Set<String> = emptySet(),
) {
	var dragActive by remember { mutableStateOf(false) }
	val dropModifier = Modifier.fileDropTarget(
		enabled = onFilesDropped != null,
		extensions = dropExtensions,
		onDragChange = { dragActive = it },
		onFilesDropped = { onFilesDropped?.invoke(it) },
	)

	Column(modifier = modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.Bottom,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			HdMonoLabel(text = label)
			if (image != null && attachedLabel != null) {
				HdMonoLabel(
					text = attachedLabel,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Box(modifier = Modifier.weight(1f))
			if (image != null && replaceLabel != null) {
				HdMonoLabel(
					text = "$replaceLabel ↗",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.clickable(onClick = onClick),
				)
			}
		}

		if (image != null) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(height)
					.padding(top = 8.dp)
					.border(
						width = if (dragActive) 1.dp else Dp.Hairline,
						color = if (dragActive) {
							MaterialTheme.colorScheme.primary
						} else {
							MaterialTheme.colorScheme.outlineVariant
						},
						shape = RectangleShape,
					)
					.then(dropModifier),
			) {
				image()
				if (onRemove != null) {
					HdClearGlyph(
						onClick = onRemove,
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(8.dp)
							.background(MaterialTheme.colorScheme.surface, RectangleShape)
							.border(
								width = Dp.Hairline,
								color = MaterialTheme.colorScheme.outlineVariant,
								shape = RectangleShape,
							),
						boxSize = 24.dp,
						glyphSize = 8.dp,
						color = MaterialTheme.colorScheme.onSurface,
					)
				}
			}
		} else {
			val stripeA = MaterialTheme.colorScheme.surfaceContainerLow
			val stripeB = MaterialTheme.colorScheme.surfaceContainer
			val dashColor = if (dragActive) {
				MaterialTheme.colorScheme.primary
			} else {
				MaterialTheme.colorScheme.outlineVariant
			}
			val tileColor = MaterialTheme.colorScheme.surfaceContainer
			val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

			Box(
				modifier = Modifier
					.fillMaxWidth()
					.height(height)
					.padding(top = 8.dp)
					.diagonalStripes(stripeA, stripeB)
					.dashedHairlineBorder(dashColor)
					.then(dropModifier)
					.clickable(onClick = onClick),
				contentAlignment = Alignment.Center,
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Box(
						modifier = Modifier
							.size(40.dp)
							.background(tileColor, RectangleShape)
							.border(
								width = Dp.Hairline,
								color = dashColor,
								shape = RectangleShape,
							),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = "+",
							style = MaterialTheme.typography.headlineSmall,
							color = onSurfaceVariant,
							fontWeight = FontWeight.Light,
						)
					}
					HdMonoLabel(
						text = dropHint,
						color = onSurfaceVariant,
					)
					Box(
						modifier = Modifier
							.background(MaterialTheme.colorScheme.surface, RectangleShape)
							.border(
								width = Dp.Hairline,
								color = MaterialTheme.colorScheme.primary,
								shape = RectangleShape,
							)
							.clickable(onClick = onClick)
							.padding(horizontal = 12.dp, vertical = 6.dp),
					) {
						HdMonoLabel(
							text = browseLabel,
							color = MaterialTheme.colorScheme.primary,
						)
					}
				}
			}
		}
	}
}

/** 135° repeating diagonal stripe fill — cached per size. */
private fun Modifier.diagonalStripes(
	stripeA: Color,
	stripeB: Color,
	period: Dp = 28.dp,
): Modifier = drawWithCache {
	val periodPx = period.toPx().coerceAtLeast(1f)
	val stripeWidth = periodPx / 2f
	val coverage = size.width + size.height
	val count = ceil(coverage / periodPx).toInt() + 2
	val startX = -size.height
	onDrawBehind {
		drawRect(stripeA, size = size)
		clipRect(0f, 0f, size.width, size.height) {
			var x = startX
			repeat(count) {
				drawLine(
					color = stripeB,
					start = Offset(x, 0f),
					end = Offset(x + size.height, size.height),
					strokeWidth = stripeWidth,
				)
				x += periodPx
			}
		}
	}
}

private fun Modifier.dashedHairlineBorder(
	color: Color,
	dash: Dp = 4.dp,
	gap: Dp = 4.dp,
): Modifier = drawWithCache {
	val stroke = Stroke(
		width = 1f,
		pathEffect = PathEffect.dashPathEffect(
			intervals = floatArrayOf(dash.toPx(), gap.toPx()),
			phase = 0f,
		),
	)
	val rectSize = Size(size.width - 1f, size.height - 1f)
	onDrawBehind {
		drawRect(
			color = color,
			topLeft = Offset(0.5f, 0.5f),
			size = rectSize,
			style = stroke,
		)
	}
}
