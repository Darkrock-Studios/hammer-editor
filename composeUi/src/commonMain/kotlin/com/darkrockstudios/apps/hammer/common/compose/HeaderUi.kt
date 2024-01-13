package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import dev.icerock.moko.resources.StringResource

@Composable
fun HeaderUi(
	title: StringResource,
	emoji: String,
	modifier: Modifier = Modifier,
) {
	Row(modifier = modifier.wrapContentWidth()) {
		Text(
			emoji,
			style = MaterialTheme.typography.headlineMedium,
			color = MaterialTheme.colorScheme.onBackground,
			modifier = Modifier.padding(end = Ui.Padding.XL)
		)
		Text(
			title.get(),
			style = MaterialTheme.typography.headlineMedium,
			color = MaterialTheme.colorScheme.onBackground,
		)
	}
}