package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdConflictFieldSpacing
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
import com.darkrockstudios.apps.hammer.ideas_conflict_field_content
import com.darkrockstudios.apps.hammer.ideas_conflict_field_tags
import com.darkrockstudios.apps.hammer.ideas_conflict_field_title

/**
 * Local/Remote resolution panes for a story-idea sync conflict, shown inside the account sync
 * dialog. The local pane is editable so the resolution can be a manual merge.
 */
@Composable
fun IdeaConflictUi(
	conflict: IdeaConflict,
	compact: Boolean,
	onResolve: (StoryIdea) -> Unit,
	modifier: Modifier = Modifier,
) {
	val local = conflict.local
	val server = conflict.server
	var titleText by rememberSaveable(local) { mutableStateOf(local.title ?: "") }
	var contentText by rememberSaveable(local) { mutableStateOf(local.content) }

	ConflictSplit(
		compact = compact,
		onUseLocal = {
			onResolve(
				local.copy(
					title = titleText.trim().ifEmpty { null },
					content = contentText,
				)
			)
		},
		onUseRemote = { onResolve(server) },
		LocalBody = { paneModifier ->
			Column(
				modifier = paneModifier
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
			) {
				HdConflictField(
					label = Res.string.ideas_conflict_field_title.get(),
					conflict = server.title != local.title,
				) {
					HdHairlineField(
						label = "",
						value = titleText,
						onValueChange = { titleText = it },
						singleLine = true,
						modifier = Modifier.fillMaxWidth(),
					)
				}
				HdConflictField(
					label = Res.string.ideas_conflict_field_tags.get(),
					conflict = server.tags != local.tags,
				) {
					ReadOnlyLine(formatTags(local.tags))
				}
				HdConflictField(
					label = Res.string.ideas_conflict_field_content.get(),
					conflict = server.content != local.content,
				) {
					HdHairlineField(
						label = "",
						value = contentText,
						onValueChange = { contentText = it },
						singleLine = false,
						minLines = 6,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
		},
		RemoteBody = { paneModifier ->
			Column(
				modifier = paneModifier
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(HdConflictFieldSpacing),
			) {
				HdConflictField(
					label = Res.string.ideas_conflict_field_title.get(),
					conflict = server.title != local.title,
				) {
					ReadOnlyLine(server.title ?: "—")
				}
				HdConflictField(
					label = Res.string.ideas_conflict_field_tags.get(),
					conflict = server.tags != local.tags,
				) {
					ReadOnlyLine(formatTags(server.tags))
				}
				HdConflictField(
					label = Res.string.ideas_conflict_field_content.get(),
					conflict = server.content != local.content,
				) {
					ReadOnlyBlock(server.content)
				}
			}
		},
	)
}

private fun formatTags(tags: Set<String>): String =
	if (tags.isEmpty()) "—" else tags.sorted().joinToString("  ") { "#$it" }
