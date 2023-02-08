package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KFunction0

@Composable
fun ViewTimeLineEventUi(
	component: ViewTimeLineEvent,
	modifier: Modifier = Modifier,
	scope: CoroutineScope,
	snackbarHostState: SnackbarHostState,
	closeEvent: KFunction0<Unit>
) {
	val state by component.state.subscribeAsState()

	val screen = LocalScreenCharacteristic.current
	val needsExplicitClose = remember { screen.needsExplicitClose }

	state.event?.let { event ->
		Box(modifier = modifier.fillMaxSize()) {
			Column(modifier = Modifier.align(Alignment.Center).padding(Ui.Padding.XL)) {
				if (needsExplicitClose) {
					IconButton(
						onClick = closeEvent,
						modifier = Modifier.align(Alignment.End).padding(Ui.Padding.XL),
					) {
						Icon(
							Icons.Default.Close,
							"Close",
							tint = MaterialTheme.colorScheme.onBackground
						)
					}
				}

				event.date?.let { date ->
					Text(
						date,
						style = MaterialTheme.typography.displayMedium,
						color = MaterialTheme.colorScheme.onBackground
					)
				}
				Text(
					event.content,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onBackground,
					modifier = Modifier.verticalScroll(rememberScrollState(0))
				)
			}
		}
	}
}