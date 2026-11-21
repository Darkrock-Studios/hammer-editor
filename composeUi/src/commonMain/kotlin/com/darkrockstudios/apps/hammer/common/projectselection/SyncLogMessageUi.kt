package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogLevel
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.SyncLogMessage
import com.darkrockstudios.apps.hammer.common.util.formatLocal

@Composable
fun SyncLogMessageUi(logMsg: SyncLogMessage, showProjectName: Boolean = true) {
	val accent = logMsg.level.accentColor()
	val timestamp = remember(logMsg.timestamp) {
		logMsg.timestamp.formatLocal("HH:mm:ss")
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = Ui.Padding.S)
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				shape = RectangleShape,
			)
			.height(IntrinsicSize.Min),
	) {
		Box(
			modifier = Modifier
				.width(3.dp)
				.fillMaxHeight()
				.background(accent),
		)

		Column(
			modifier = Modifier
				.weight(1f)
				.padding(horizontal = Ui.Padding.M, vertical = Ui.Padding.S),
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = timestamp,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				if (showProjectName) {
					logMsg.projectName?.let { name ->
						Spacer(modifier = Modifier.width(Ui.Padding.M))
						HdMonoLabel(text = "·", color = MaterialTheme.colorScheme.outlineVariant)
						Spacer(modifier = Modifier.width(Ui.Padding.M))
						Text(
							text = name,
							style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
							color = MaterialTheme.colorScheme.onSurface,
						)
					}
				}

				Spacer(modifier = Modifier.weight(1f))

				HdMonoLabel(text = logMsg.level.name, color = accent)
			}

			Spacer(modifier = Modifier.height(2.dp))

			SelectionContainer {
				Text(
					text = logMsg.message,
					style = MaterialTheme.typography.bodySmall,
					color = accent,
				)
			}
		}
	}
}

@Composable
@ReadOnlyComposable
fun SyncLogLevel.accentColor(): Color {
	val hammer = LocalHammerColors.current
	return when (this) {
		SyncLogLevel.DEBUG, SyncLogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
		SyncLogLevel.WARN -> hammer.warning
		SyncLogLevel.ERROR -> hammer.danger
	}
}

/** A sync log that stays pinned to its newest message while the user is at the tail. */
@Composable
fun SyncLogList(
	log: List<SyncLogMessage>,
	showProjectName: Boolean,
	horizontalPadding: Dp,
	modifier: Modifier = Modifier,
) {
	val listState: LazyListState = rememberLazyListState()

	LazyColumn(
		state = listState,
		modifier = modifier.fillMaxWidth(),
		contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = Ui.Padding.M),
	) {
		items(
			count = log.size,
			key = { log[it].timestamp.toEpochMilliseconds() to it },
		) { index ->
			SyncLogMessageUi(log[index], showProjectName = showProjectName)
		}
	}

	val pinnedToTail by remember {
		derivedStateOf {
			val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
			last < 0 || last >= log.size - 2
		}
	}
	LaunchedEffect(log.size) {
		if (log.isNotEmpty() && pinnedToTail) {
			listState.scrollToItem(log.size - 1)
		}
	}
}
