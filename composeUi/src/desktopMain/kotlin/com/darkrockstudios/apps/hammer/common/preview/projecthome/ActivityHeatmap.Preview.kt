package com.darkrockstudios.apps.hammer.common.preview.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projecthome.ActivityHeatmap
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

@Preview
@Composable
fun ActivityHeatmapPreview() {
	val today = LocalDate(2026, 7, 7)
	val dailyTotals = buildMap {
		val pattern = listOf(0, 1200, 800, 0, 2400, 300, 0, 1500, 0, 900, 4100, 0)
		(0 until 60).forEach { offset ->
			val words = pattern[offset % pattern.size]
			if (words > 0) put(today.minus(offset, DateTimeUnit.DAY), words)
		}
	}

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.padding(16.dp)
					.width(320.dp),
			) {
				ActivityHeatmap(
					dailyTotals = dailyTotals,
					today = today,
				)
			}
		}
	}
}
