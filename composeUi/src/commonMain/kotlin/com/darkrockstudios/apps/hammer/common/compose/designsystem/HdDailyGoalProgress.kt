package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator

/** "Daily goal  847 / 1,000" + thin progress bar. Progress clamped to [0,1]. */
@Composable
fun HdDailyGoalProgress(
	current: Int,
	goal: Int,
	modifier: Modifier = Modifier,
	label: String = "Daily goal",
) {
	val fraction = if (goal <= 0) 0f else (current.toFloat() / goal).coerceIn(0f, 1f)

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdMonoLabel(
				text = label,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Text(
				text = "${current.formatDecimalSeparator()} / ${goal.formatDecimalSeparator()}",
				style = MaterialTheme.typography.titleSmall,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
		LinearProgressIndicator(
			progress = { fraction },
			modifier = Modifier
				.fillMaxWidth()
				.height(2.dp),
			color = MaterialTheme.colorScheme.primary,
			trackColor = MaterialTheme.colorScheme.surfaceVariant,
		)
	}
}
