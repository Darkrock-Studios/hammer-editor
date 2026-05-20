package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.hammerMonoFontFamily
import korlibs.io.lang.format

/**
 * GitHub release `body` is rendered verbatim as plain text — if a future
 * contributor wants markdown rendering, add a real renderer rather than
 * swapping in an unstyled drop-in.
 */
@Composable
fun UpdateAvailableDialog(component: ProjectSelection) {
	val state by component.updateNotification.subscribeAsState()
	val tag = state.latestVersionTag
	AnimatedDialog(
		visible = state.visible && tag != null,
		onCloseRequest = { component.dismissUpdateNotification(remember = false) },
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		Surface(
			shape = RectangleShape,
			color = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface,
			shadowElevation = Ui.Elevation.LARGE,
			modifier = Modifier
				.padding(horizontal = Ui.Padding.XL)
				.widthIn(max = 560.dp)
				.fillMaxWidth(),
		) {
			Column {
				HdMasthead(
					section = "UPDATE",
					leadingMeta = listOfNotNull(tag),
					trailing = {
						HdMastheadAction(
							label = "× CLOSE",
							onClick = { component.dismissUpdateNotification(remember = false) },
						)
					},
				)
				HdFolioDivider()
				Body(
					isNewVersionAvailable = state.isNewVersionAvailable,
					tag = tag.orEmpty(),
					releaseName = state.releaseName,
					releaseBody = state.releaseBody,
				)
				HorizontalDivider(
					color = MaterialTheme.colorScheme.outlineVariant,
					thickness = 1.dp,
				)
				Footer(
					onOpenRelease = component::openReleaseUrl,
					onDismiss = { component.dismissUpdateNotification(remember = true) },
					manuallyTriggered = state.manuallyTriggered,
				)
			}
		}
	}
}

@Composable
private fun Body(
	isNewVersionAvailable: Boolean,
	tag: String,
	releaseName: String?,
	releaseBody: String?,
) {
	val title = if (isNewVersionAvailable) {
		Res.string.update_dialog_title.get()
	} else {
		Res.string.update_dialog_title_current.get().format(tag)
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
		if (!releaseName.isNullOrBlank()) {
			Text(
				text = releaseName,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		ReleaseNotesSection(releaseBody = releaseBody)
	}
}

@Composable
private fun ReleaseNotesSection(releaseBody: String?) {
	var expanded by remember { mutableStateOf(true) }
	val scroll = rememberScrollState()
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clickable { expanded = !expanded }
				.padding(vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			HdMonoLabel(text = if (expanded) "▾" else "▸")
			HdMonoLabel(text = Res.string.update_dialog_release_notes_section.get())
		}
		AnimatedVisibility(visible = expanded) {
			val text = releaseBody?.takeIf { it.isNotBlank() }
				?: Res.string.update_dialog_release_notes_empty.get()
			Text(
				text = text,
				fontFamily = hammerMonoFontFamily(),
				fontSize = 12.sp,
				lineHeight = 18.sp,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 280.dp)
					.background(MaterialTheme.colorScheme.surfaceContainerLow)
					.padding(12.dp)
					.verticalScroll(scroll),
			)
		}
	}
}

@Composable
private fun Footer(
	onOpenRelease: () -> Unit,
	onDismiss: () -> Unit,
	manuallyTriggered: Boolean,
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
		if (!manuallyTriggered) {
			TextButton(
				onClick = onDismiss,
				shape = RoundedCornerShape(4.dp),
			) {
				Text(Res.string.update_dialog_dismiss_button.get())
			}
		}
		OutlinedButton(
			onClick = onOpenRelease,
			shape = RoundedCornerShape(4.dp),
			colors = ButtonDefaults.outlinedButtonColors(
				contentColor = MaterialTheme.colorScheme.primary,
			),
		) {
			Text(Res.string.update_dialog_open_release_button.get())
		}
	}
}
