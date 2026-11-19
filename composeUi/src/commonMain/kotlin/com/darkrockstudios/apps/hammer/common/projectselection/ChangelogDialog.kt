package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.about_version_github_button
import com.darkrockstudios.apps.hammer.changelog_dialog_close
import com.darkrockstudios.apps.hammer.changelog_dialog_title
import com.darkrockstudios.apps.hammer.changelog_dialog_title_version
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetaText
import com.darkrockstudios.apps.hammer.common.compose.linkify
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.util.format
import com.darkrockstudios.apps.hammer.update_dialog_release_notes_empty
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * The release notes baked into the app by `prepareForRelease`. Never hits the network.
 * Notes are read line by line, not as markdown: see [linkify] for why.
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
		ChangelogContent(
			state = state,
			onDismiss = component::dismissChangelog,
			onOpenRelease = component::openLatestRelease,
		)
	}
}

/**
 *     What's new in 3.12.0                     ×
 *     5 Oct 2026
 *     ───────────────────────────────────────────
 *     New features
 *     •  Reader Mode: read your story as prose…
 *     ───────────────────────────────────────────
 *                              [Release on GitHub]
 */
@Composable
fun ChangelogContent(
	state: ProjectSelection.ChangelogState,
	onDismiss: () -> Unit,
	onOpenRelease: () -> Unit,
) {
	val title = state.version?.let { Res.string.changelog_dialog_title_version.get(it) }
		?: Res.string.changelog_dialog_title.get()
	HdHairlineDialogShell(
		title = title,
		onClose = onDismiss,
		closeContentDescription = Res.string.changelog_dialog_close.get(),
	) {
		state.date?.let { date ->
			HdMetaText(text = remember(date) { releaseDateLabel(date) })
			Spacer(Modifier.height(16.dp))
		}
		HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
		Column(
			modifier = Modifier
				.weight(1f, fill = false)
				.verticalScroll(rememberScrollState())
				.padding(vertical = 20.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			ReleaseNotes(notes = state.notes)
		}
		HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
		Spacer(Modifier.height(16.dp))
		Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
			HdHairlineButton(label = Res.string.about_version_github_button.get(), onClick = onOpenRelease)
		}
	}
}

/** "- " lines are entries; any other line heads the entries under it ("New features"). */
@Composable
private fun ReleaseNotes(notes: String?) {
	val link = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
	val lines = notes?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
	if (lines.isEmpty()) {
		Text(
			text = Res.string.update_dialog_release_notes_empty.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		return
	}
	lines.forEachIndexed { index, line ->
		val entry = line.removePrefix("- ").removePrefix("* ").takeIf { it != line }
		if (entry != null) {
			Row {
				Text(
					text = "•",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.width(18.dp),
				)
				Text(
					text = linkify(entry, link),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier.weight(1f),
				)
			}
		} else {
			if (index > 0) Spacer(Modifier.height(8.dp))
			Text(
				text = linkify(line, link),
				style = MaterialTheme.typography.titleSmall,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
	}
}

/** The baked date is `yyyy-M-d`; shown the way the project list shows dates. Anything else passes through. */
private fun releaseDateLabel(date: String): String {
	val parts = date.split('-').mapNotNull { it.toIntOrNull() }
	if (parts.size != 3) return date
	return runCatching {
		LocalDateTime(LocalDate(parts[0], parts[1], parts[2]), LocalTime(0, 0)).format("d MMM yyyy")
	}.getOrDefault(date)
}
