package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator
import com.darkrockstudios.apps.hammer.project_home_stat_activity_tooltip
import com.darkrockstudios.apps.hammer.project_home_stat_activity_tooltip_empty
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

const val DEFAULT_HEATMAP_WEEKS = 12

/**
 * GitHub-style activity heatmap. Renders [weekCount] weeks of writing-activity
 * cells, one column per week (oldest left, current week right), 7 rows per
 * column (Monday top → Sunday bottom). Cell intensity is a linear ramp on
 * the maximum word count seen in the window; days with no activity are drawn
 * as a dim placeholder.
 *
 * Each populated cell surfaces its date and word count on hover (desktop) or
 * tap (touch); a tapped tooltip persists until the user taps elsewhere.
 */
@Composable
fun ActivityHeatmap(
	dailyTotals: Map<LocalDate, Int>,
	today: LocalDate,
	modifier: Modifier = Modifier,
	weekCount: Int = DEFAULT_HEATMAP_WEEKS,
) {
	val grid = remember(dailyTotals, today, weekCount) {
		buildHeatmapGrid(dailyTotals, today, weekCount)
	}
	val baseColor = MaterialTheme.colorScheme.primary
	val emptyColor = MaterialTheme.colorScheme.surfaceVariant
	val maxCount = grid.maxValue
	val cellSpacing = 2.dp

	BoxWithConstraints(modifier = modifier) {
		val cellSize = (maxWidth - cellSpacing * (weekCount - 1)) / weekCount
		Row(horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
			grid.weeks.forEach { week ->
				Column(verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
					week.forEach { cell ->
						val color = if (cell == null || cell.words <= 0) {
							emptyColor
						} else {
							val ratio = if (maxCount <= 0) 0f
							else (cell.words.toFloat() / maxCount).coerceIn(0.15f, 1f)
							baseColor.copy(alpha = ratio)
						}
						if (cell == null) {
							HeatmapCell(color = color, size = cellSize)
						} else {
							HeatmapCellWithTooltip(
								color = color,
								size = cellSize,
								text = cell.tooltipText(),
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun HeatmapCellData.tooltipText(): String {
	val date = date.toString()
	return if (words > 0) {
		stringResource(
			Res.string.project_home_stat_activity_tooltip,
			date,
			words.formatDecimalSeparator()
		)
	} else {
		stringResource(Res.string.project_home_stat_activity_tooltip_empty, date)
	}
}

@Composable
private fun HeatmapCell(color: Color, size: Dp) {
	Spacer(
		modifier = Modifier
			.size(size)
			.clip(RoundedCornerShape(2.dp))
			.background(color)
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeatmapCellWithTooltip(color: Color, size: Dp, text: String) {
	val state = rememberTooltipState(isPersistent = true)
	val scope = rememberCoroutineScope()
	TooltipBox(
		positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
		tooltip = { PlainTooltip { Text(text) } },
		state = state,
	) {
		Spacer(
			modifier = Modifier
				.size(size)
				.clip(RoundedCornerShape(2.dp))
				.background(color)
				.clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
				) { scope.launch { state.show() } },
		)
	}
}

internal data class HeatmapCellData(val date: LocalDate, val words: Int)

internal data class HeatmapGrid(
	val weeks: List<List<HeatmapCellData?>>,
	val maxValue: Int,
)

internal fun buildHeatmapGrid(
	dailyTotals: Map<LocalDate, Int>,
	today: LocalDate,
	weekCount: Int,
): HeatmapGrid {
	// Anchor each column on a Monday so weeks align consistently.
	val daysSinceMonday = (today.dayOfWeek.isoDayNumber - 1)
	val currentWeekStart = today.minus(daysSinceMonday, DateTimeUnit.DAY)
	val firstWeekStart = currentWeekStart.minus((weekCount - 1) * 7, DateTimeUnit.DAY)

	var max = 0
	val weeks = (0 until weekCount).map { weekIndex ->
		val weekStart = firstWeekStart.plus(weekIndex * 7, DateTimeUnit.DAY)
		(0 until 7).map { dayIndex ->
			val date = weekStart.plus(dayIndex, DateTimeUnit.DAY)
			if (date > today) {
				null
			} else {
				val words = dailyTotals[date] ?: 0
				if (words > max) max = words
				HeatmapCellData(date, words)
			}
		}
	}
	return HeatmapGrid(weeks = weeks, maxValue = max)
}
