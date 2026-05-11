package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors

enum class HdStatus {
	Pending,
	Syncing,
	Complete,
	Failed,
	Canceled,
}

/** Semantic accent color for a status — used by the glyph fill and any sibling indicator (progress bar, label tint). */
@Composable
@ReadOnlyComposable
fun HdStatus.accentColor(): Color {
	val hammer = LocalHammerColors.current
	return when (this) {
		HdStatus.Pending -> MaterialTheme.colorScheme.outline
		HdStatus.Syncing -> MaterialTheme.colorScheme.primary
		HdStatus.Complete -> hammer.success
		HdStatus.Failed -> hammer.danger
		HdStatus.Canceled -> MaterialTheme.colorScheme.onSurfaceVariant
	}
}

@Composable
fun HdStatusGlyph(
	status: HdStatus,
	modifier: Modifier = Modifier,
	size: Dp = 18.dp,
) {
	val accent = status.accentColor()
	val ink = MaterialTheme.colorScheme.surface
	val base = modifier.size(size)

	when (status) {
		HdStatus.Pending -> Box(
			modifier = base.border(width = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant, shape = RectangleShape),
		)
		HdStatus.Syncing -> Box(
			modifier = base.border(width = Dp.Hairline, color = accent, shape = RectangleShape),
			contentAlignment = Alignment.Center,
		) {
			Box(modifier = Modifier.size(size * 0.45f).background(accent))
		}
		HdStatus.Complete -> Box(modifier = base.background(accent).drawCheck(ink))
		HdStatus.Failed -> Box(modifier = base.background(accent).drawCross(ink))
		HdStatus.Canceled -> Box(
			modifier = base
				.border(width = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant, shape = RectangleShape)
				.drawCross(accent),
		)
	}
}

private fun Modifier.drawCross(color: Color): Modifier = this
	.fillMaxSize()
	.drawBehind {
		val inset = size.minDimension * 0.28f
		val stroke = size.minDimension * 0.12f
		drawLine(color, Offset(inset, inset), Offset(size.width - inset, size.height - inset), stroke, StrokeCap.Square)
		drawLine(color, Offset(size.width - inset, inset), Offset(inset, size.height - inset), stroke, StrokeCap.Square)
	}

private fun Modifier.drawCheck(color: Color): Modifier = this
	.fillMaxSize()
	.drawBehind {
		val w = size.width
		val h = size.height
		val strokeWidth = size.minDimension * 0.14f
		val path = Path().apply {
			moveTo(w * 0.22f, h * 0.52f)
			lineTo(w * 0.44f, h * 0.72f)
			lineTo(w * 0.78f, h * 0.32f)
		}
		drawPath(
			path = path,
			color = color,
			style = Stroke(width = strokeWidth, cap = StrokeCap.Square),
		)
	}
