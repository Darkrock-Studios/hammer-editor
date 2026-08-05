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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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

/**
 * The release notes baked into the app by `prepareForRelease`. Never hits the network.
 * Notes render as plain monospace text apart from links, which [linkifyChangelog] makes
 * clickable — see that function for why this isn't a markdown renderer.
 */
@Composable
fun ChangelogDialog(component: ProjectSelection) {
	val state by component.changelog.subscribeAsState()
	AnimatedDialog(
		visible = state.visible && state.notes != null,
		onCloseRequest = component::dismissChangelog,
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
					section = "CHANGES",
					leadingMeta = listOfNotNull(state.version, state.date),
					trailing = {
						HdMastheadAction(
							label = "× CLOSE",
							onClick = component::dismissChangelog,
						)
					},
				)
				HdFolioDivider()
				Body(notes = state.notes)
				HorizontalDivider(
					color = MaterialTheme.colorScheme.outlineVariant,
					thickness = 1.dp,
				)
				Footer(onOpenRelease = component::openLatestRelease)
			}
		}
	}
}

@Composable
private fun Body(notes: String?) {
	Column(
		modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 22.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Text(
			text = Res.string.changelog_dialog_title.get(),
			style = MaterialTheme.typography.headlineSmall.copy(
				fontWeight = FontWeight.Normal,
				letterSpacing = (-0.26).sp,
				lineHeight = 30.sp,
			),
			color = MaterialTheme.colorScheme.onSurface,
		)
		ReleaseNotesSection(notes = notes)
	}
}

@Composable
private fun ReleaseNotesSection(notes: String?) {
	var expanded by remember { mutableStateOf(true) }
	val scroll = rememberScrollState()
	val linkStyle = SpanStyle(
		color = MaterialTheme.colorScheme.primary,
		textDecoration = TextDecoration.Underline,
	)
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
			val text = notes?.takeIf { it.isNotBlank() }
				?: Res.string.update_dialog_release_notes_empty.get()
			Text(
				text = linkifyChangelog(text, linkStyle),
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
private fun Footer(onOpenRelease: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(start = 22.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Spacer(modifier = Modifier.weight(1f))
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
