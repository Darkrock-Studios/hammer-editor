package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.wear.R
import com.darkrockstudios.apps.hammer.wear.components.synclog.SyncLog

@Composable
fun SyncLogUi(component: SyncLog) {
	val state by component.state.subscribeAsState()
	SyncLogScreen(state)
}

@Composable
fun SyncLogScreen(state: SyncLog.State) {
	val listState = rememberTransformingLazyColumnState()
	ScreenScaffold(scrollState = listState) { contentPadding ->
		TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
			item {
				ListHeader { Text(stringResource(R.string.sync_log_title)) }
			}
			if (state.entries.isEmpty()) {
				item {
					Text(
						text = stringResource(R.string.sync_log_empty),
						textAlign = TextAlign.Center,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
			items(count = state.entries.size) { index ->
				val entry = state.entries[index]
				val prefix = entry.projectName?.let { "$it: " } ?: ""
				Text(
					text = prefix + entry.message,
					style = MaterialTheme.typography.bodySmall,
					color = when (entry.level) {
						SyncLogLevel.ERROR -> MaterialTheme.colorScheme.error
						SyncLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
						SyncLogLevel.INFO, SyncLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurface
					},
					modifier = Modifier.fillMaxWidth(),
				)
			}
		}
	}
}
