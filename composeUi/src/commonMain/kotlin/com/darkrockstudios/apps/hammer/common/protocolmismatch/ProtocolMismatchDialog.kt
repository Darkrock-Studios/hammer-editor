package com.darkrockstudios.apps.hammer.common.protocolmismatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.protocolmismatch.ProtocolMismatch
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.protocol_mismatch_body_client
import com.darkrockstudios.apps.hammer.protocol_mismatch_body_server
import com.darkrockstudios.apps.hammer.protocol_mismatch_current_version
import com.darkrockstudios.apps.hammer.protocol_mismatch_dismiss_button
import com.darkrockstudios.apps.hammer.protocol_mismatch_latest_version
import com.darkrockstudios.apps.hammer.protocol_mismatch_open_release_button
import com.darkrockstudios.apps.hammer.protocol_mismatch_title_client
import com.darkrockstudios.apps.hammer.protocol_mismatch_title_server

@Composable
fun ProtocolMismatchDialog(component: ProtocolMismatch) {
	val state by component.state.subscribeAsState()
	AnimatedDialog(
		visible = true,
		onCloseRequest = { component.dismiss() },
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		ProtocolMismatchContent(
			state = state,
			onOpenRelease = component::openReleaseUrl,
			onDismiss = component::dismiss,
		)
	}
}

/**
 * The dialog without the [AnimatedDialog] window wrapper, so a preview can render it settled.
 */
@Composable
fun ProtocolMismatchContent(
	state: ProtocolMismatch.State,
	onOpenRelease: () -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
) {
	FormDialogScaffold(
		title = if (state.clientIsBehind) {
			Res.string.protocol_mismatch_title_client.get()
		} else {
			Res.string.protocol_mismatch_title_server.get()
		},
		confirmLabel = Res.string.protocol_mismatch_open_release_button.get(),
		cancelLabel = Res.string.protocol_mismatch_dismiss_button.get(),
		onConfirm = onOpenRelease,
		onCancel = onDismiss,
		modifier = modifier,
	) {
		Text(
			text = if (state.clientIsBehind) {
				Res.string.protocol_mismatch_body_client.get()
			} else {
				Res.string.protocol_mismatch_body_server.get()
			},
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			HdMetaText(text = Res.string.protocol_mismatch_current_version.get(state.currentVersion))
			val latest = state.latestVersionTag
			if (state.isNewVersionAvailable && latest != null) {
				HdMetaText(
					text = Res.string.protocol_mismatch_latest_version.get(latest),
					color = MaterialTheme.colorScheme.primary,
				)
			}
		}
	}
}
