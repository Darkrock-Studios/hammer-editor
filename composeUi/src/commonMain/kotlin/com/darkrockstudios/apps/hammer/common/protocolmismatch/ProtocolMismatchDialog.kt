package com.darkrockstudios.apps.hammer.common.protocolmismatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.protocolmismatch.ProtocolMismatch
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.protocol_mismatch_body_client
import com.darkrockstudios.apps.hammer.protocol_mismatch_body_server
import com.darkrockstudios.apps.hammer.protocol_mismatch_current_version
import com.darkrockstudios.apps.hammer.protocol_mismatch_dismiss_button
import com.darkrockstudios.apps.hammer.protocol_mismatch_latest_version
import com.darkrockstudios.apps.hammer.protocol_mismatch_open_release_button
import com.darkrockstudios.apps.hammer.protocol_mismatch_section
import com.darkrockstudios.apps.hammer.protocol_mismatch_title_client
import com.darkrockstudios.apps.hammer.protocol_mismatch_title_server
import korlibs.io.lang.format

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
 * The dialog's chrome without the animated [AnimatedDialog] window wrapper, so it can be
 * rendered directly in a Compose preview (the live dialog hangs in its own window and won't
 * settle to a static frame).
 */
@Composable
fun ProtocolMismatchContent(
	state: ProtocolMismatch.State,
	onOpenRelease: () -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		shadowElevation = Ui.Elevation.LARGE,
		modifier = modifier
			.padding(horizontal = Ui.Padding.XL)
			.widthIn(max = 560.dp)
			.fillMaxWidth(),
	) {
		Column {
			HdMasthead(
				section = Res.string.protocol_mismatch_section.get(),
				leadingMeta = listOfNotNull(state.latestVersionTag),
				trailing = {
					HdMastheadAction(
						label = "× CLOSE",
						onClick = onDismiss,
					)
				},
			)
			HdFolioDivider()
			Body(
				clientIsBehind = state.clientIsBehind,
				currentVersion = state.currentVersion,
				latestVersionTag = state.latestVersionTag,
				showLatestVersion = state.isNewVersionAvailable,
			)
			HorizontalDivider(
				color = MaterialTheme.colorScheme.outlineVariant,
				thickness = 1.dp,
			)
			Footer(
				onOpenRelease = onOpenRelease,
				onDismiss = onDismiss,
			)
		}
	}
}

@Composable
private fun Body(
	clientIsBehind: Boolean,
	currentVersion: String,
	latestVersionTag: String?,
	showLatestVersion: Boolean,
) {
	val title = if (clientIsBehind) {
		Res.string.protocol_mismatch_title_client.get()
	} else {
		Res.string.protocol_mismatch_title_server.get()
	}
	val body = if (clientIsBehind) {
		Res.string.protocol_mismatch_body_client.get()
	} else {
		Res.string.protocol_mismatch_body_server.get()
	}
	Column(
		modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 22.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.headlineSmall.copy(
				fontWeight = FontWeight.Normal,
				letterSpacing = (-0.26).sp,
				lineHeight = 30.sp,
			),
			color = MaterialTheme.colorScheme.onSurface,
		)
		Text(
			text = body,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
			HdMonoLabel(
				text = Res.string.protocol_mismatch_current_version.get().format(currentVersion),
			)
			if (showLatestVersion && latestVersionTag != null) {
				HdMonoLabel(
					text = Res.string.protocol_mismatch_latest_version.get().format(latestVersionTag),
					color = MaterialTheme.colorScheme.primary,
				)
			}
		}
	}
}

@Composable
private fun Footer(
	onOpenRelease: () -> Unit,
	onDismiss: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(start = 22.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Spacer(modifier = Modifier.weight(1f))
		TextButton(
			onClick = onDismiss,
			shape = RoundedCornerShape(4.dp),
		) {
			Text(Res.string.protocol_mismatch_dismiss_button.get())
		}
		OutlinedButton(
			onClick = onOpenRelease,
			shape = RoundedCornerShape(4.dp),
			colors = ButtonDefaults.outlinedButtonColors(
				contentColor = MaterialTheme.colorScheme.primary,
			),
		) {
			Text(Res.string.protocol_mismatch_open_release_button.get())
		}
	}
}
