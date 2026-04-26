package com.darkrockstudios.apps.hammer.common.globalsearch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.global_search_button

@Composable
fun GlobalSearchFab(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	FloatingActionButton(
		onClick = onClick,
		modifier = modifier,
	) {
		Icon(
			imageVector = Icons.Default.Search,
			contentDescription = Res.string.global_search_button.get(),
		)
	}
}
