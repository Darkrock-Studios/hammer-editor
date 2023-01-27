package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagRow(
	tags: Collection<String>,
	modifier: Modifier = Modifier,
) {
	SimpleFlowRow(
		verticalGap = 8.dp,
		horizontalGap = 8.dp,
		alignment = Alignment.CenterHorizontally,
		modifier = modifier
	) {
		for (tag in tags) {
			SuggestionChip(
				onClick = {},
				label = { Text(tag) },
				enabled = false
			)
		}
	}
}