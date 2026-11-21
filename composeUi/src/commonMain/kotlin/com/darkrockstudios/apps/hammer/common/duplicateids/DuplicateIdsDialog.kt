package com.darkrockstudios.apps.hammer.common.duplicateids

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.duplicateids.DuplicateIds
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineCheckbox
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPrimaryAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdUnsavedBadge
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdWarningNotice
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateCopy
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdKind
import com.darkrockstudios.apps.hammer.common.data.duplicateids.DuplicateIdSet
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.projectsync.diffHighlightedString
import com.darkrockstudios.apps.hammer.common.projectsync.diffInsertedStyle
import com.darkrockstudios.apps.hammer.common.projectsync.diffMovedStyle
import com.darkrockstudios.apps.hammer.common.projectsync.rememberContentDiff
import com.darkrockstudios.apps.hammer.common.util.format
import com.darkrockstudios.apps.hammer.duplicate_ids_archived
import com.darkrockstudios.apps.hammer.duplicate_ids_body_groups
import com.darkrockstudios.apps.hammer.duplicate_ids_body_several
import com.darkrockstudios.apps.hammer.duplicate_ids_body_single
import com.darkrockstudios.apps.hammer.duplicate_ids_close
import com.darkrockstudios.apps.hammer.duplicate_ids_decide_later
import com.darkrockstudios.apps.hammer.duplicate_ids_error_backup
import com.darkrockstudios.apps.hammer.duplicate_ids_error_backup_label
import com.darkrockstudios.apps.hammer.duplicate_ids_error_stale
import com.darkrockstudios.apps.hammer.duplicate_ids_error_stale_label
import com.darkrockstudios.apps.hammer.duplicate_ids_group
import com.darkrockstudios.apps.hammer.duplicate_ids_hide_differences
import com.darkrockstudios.apps.hammer.duplicate_ids_identical
import com.darkrockstudios.apps.hammer.duplicate_ids_in_use
import com.darkrockstudios.apps.hammer.duplicate_ids_keep_many
import com.darkrockstudios.apps.hammer.duplicate_ids_keep_one
import com.darkrockstudios.apps.hammer.duplicate_ids_keep_prefix
import com.darkrockstudios.apps.hammer.duplicate_ids_modified
import com.darkrockstudios.apps.hammer.duplicate_ids_remaining
import com.darkrockstudios.apps.hammer.duplicate_ids_section
import com.darkrockstudios.apps.hammer.duplicate_ids_show_differences
import com.darkrockstudios.apps.hammer.duplicate_ids_title_draft
import com.darkrockstudios.apps.hammer.duplicate_ids_title_entry
import com.darkrockstudios.apps.hammer.duplicate_ids_title_entry_image
import com.darkrockstudios.apps.hammer.duplicate_ids_title_note
import com.darkrockstudios.apps.hammer.duplicate_ids_title_scene
import com.darkrockstudios.apps.hammer.duplicate_ids_working
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun DuplicateIdsDialog(component: DuplicateIds) {
	val state by component.state.subscribeAsState()
	AnimatedDialog(
		visible = true,
		onCloseRequest = { component.decideLater() },
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		DuplicateIdsContent(
			state = state,
			onToggleKeep = component::toggleKeep,
			onKeep = component::keepSelected,
			onDecideLater = component::decideLater,
		)
	}
}

/** The dialog without its [AnimatedDialog] window, so previews can render it. */
@Composable
fun DuplicateIdsContent(
	state: DuplicateIds.State,
	onToggleKeep: (HPath) -> Unit,
	onKeep: () -> Unit,
	onDecideLater: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val set = state.current ?: return
	BoxWithConstraints(modifier = modifier) {
		// The metadata greebles truncate to nothing beside the section on a phone.
		val meta = if (maxWidth < COMPACT_WIDTH) emptyList() else listOf(
			entityCode(set),
			Res.string.duplicate_ids_remaining.get(state.sets.size),
		)
		DuplicateIdsSurface(state, set, meta, onToggleKeep, onKeep, onDecideLater)
	}
}

@Composable
private fun DuplicateIdsSurface(
	state: DuplicateIds.State,
	set: DuplicateIdSet,
	meta: List<String>,
	onToggleKeep: (HPath) -> Unit,
	onKeep: () -> Unit,
	onDecideLater: () -> Unit,
) {
	Surface(
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		shadowElevation = Ui.Elevation.LARGE,
		modifier = Modifier
			.padding(Ui.Padding.XL)
			.widthIn(max = 640.dp)
			.fillMaxWidth(),
	) {
		Column {
			HdMasthead(
				section = Res.string.duplicate_ids_section.get(),
				leadingMeta = meta,
				trailing = {
					HdMastheadAction(
						label = Res.string.duplicate_ids_close.get(),
						onClick = onDecideLater,
					)
				},
			)
			HdFolioDivider()
			Column(
				modifier = Modifier
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
					.padding(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 22.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp),
			) {
				Explanation(set)
				Column {
					set.copies.forEachIndexed { index, copy ->
						if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = Dp.Hairline)
						CopyRow(
							copy = copy,
							inUse = set.inUse,
							kept = copy.path in state.keep,
							enabled = !state.working,
							onToggle = { onToggleKeep(copy.path) },
						)
					}
				}
				state.error?.let { ErrorNotice(it) }
			}
			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
			Footer(
				keepCount = state.keep.size,
				working = state.working,
				onKeep = onKeep,
				onDecideLater = onDecideLater,
			)
		}
	}
}

@Composable
private fun Explanation(set: DuplicateIdSet) {
	// Only the id is known to be shared: the copies may be different items with different names.
	val title = when (set.kind) {
		DuplicateIdKind.Entry -> Res.string.duplicate_ids_title_entry
		DuplicateIdKind.EntryImage -> Res.string.duplicate_ids_title_entry_image
		DuplicateIdKind.Scene -> Res.string.duplicate_ids_title_scene
		DuplicateIdKind.Draft -> Res.string.duplicate_ids_title_draft
		DuplicateIdKind.Note -> Res.string.duplicate_ids_title_note
	}.get(set.copies.size, set.id)
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Text(
			text = title,
			style = MaterialTheme.typography.headlineSmall.copy(
				fontWeight = FontWeight.Normal,
				letterSpacing = (-0.26).sp,
				lineHeight = 30.sp,
			),
			color = MaterialTheme.colorScheme.onSurface,
		)
		val body = if (set.canKeepSeveral) Res.string.duplicate_ids_body_several else Res.string.duplicate_ids_body_single
		Text(
			text = body.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		if (set.copies.any { it.isGroup }) {
			Text(
				text = Res.string.duplicate_ids_body_groups.get(),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

@Composable
private fun CopyRow(
	copy: DuplicateCopy,
	inUse: DuplicateCopy,
	kept: Boolean,
	enabled: Boolean,
	onToggle: () -> Unit,
) {
	var showDiff by rememberSaveable(copy.path.path) { mutableStateOf(false) }
	val isInUse = copy == inUse
	val canDiff = !isInUse && !copy.isGroup && !inUse.isGroup && copy.text.isNotEmpty()

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(enabled = enabled, onClick = onToggle)
			.padding(vertical = 12.dp),
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		HdHairlineCheckbox(checked = kept, modifier = Modifier.padding(top = 2.dp))
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				Text(
					text = if (copy.isGroup) "${copy.name} · ${Res.string.duplicate_ids_group.get()}" else copy.name,
					style = MaterialTheme.typography.titleMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false),
				)
				if (isInUse) {
					HdUnsavedBadge(
						text = Res.string.duplicate_ids_in_use.get(),
						color = MaterialTheme.colorScheme.primary,
					)
				}
				if (copy.archived) HdMonoLabel(Res.string.duplicate_ids_archived.get())
			}
			// Filenames are case-sensitive, so not set in the label's capitals.
			Text(
				text = copy.relativePath,
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			HdMonoLabel(text = copyMeta(copy), maxLines = 1)

			val preview = remember(copy.text) { copy.text.trim().take(PREVIEW_LENGTH) }
			if (preview.isNotEmpty()) {
				Text(
					text = preview,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			}

			if (canDiff) {
				if (copy.contentHash == inUse.contentHash) {
					HdMonoLabel(Res.string.duplicate_ids_identical.get())
				} else {
					HdMonoLabel(
						text = if (showDiff) Res.string.duplicate_ids_hide_differences.get() else Res.string.duplicate_ids_show_differences.get(),
						color = MaterialTheme.colorScheme.primary,
						modifier = Modifier.clickable { showDiff = !showDiff }.padding(vertical = 4.dp),
					)
					if (showDiff) Differences(inUse = inUse.text, copy = copy.text)
				}
			}
		}
	}
}

/** The copy's text with what it has beyond the in-use copy highlighted. */
@Composable
private fun Differences(inUse: String, copy: String) {
	val diff = rememberContentDiff(serverContent = inUse, localContent = copy)
	val highlighted = diffHighlightedString(
		text = copy,
		spans = diff?.rightSpans.orEmpty(),
		style = diffInsertedStyle(),
		movedStyle = diffMovedStyle(),
	)
	val rule = MaterialTheme.colorScheme.outlineVariant
	Text(
		text = highlighted,
		style = MaterialTheme.typography.bodySmall,
		modifier = Modifier
			.padding(top = 4.dp)
			.heightIn(max = 240.dp)
			.drawBehind { drawLine(rule, Offset.Zero, Offset(0f, size.height), strokeWidth = 1f) }
			.padding(start = 12.dp)
			.verticalScroll(rememberScrollState()),
	)
}

@Composable
private fun ErrorNotice(error: DuplicateIds.Error) {
	when (error) {
		DuplicateIds.Error.BackupFailed -> HdWarningNotice(
			label = Res.string.duplicate_ids_error_backup_label.get(),
			message = Res.string.duplicate_ids_error_backup.get(),
		)

		DuplicateIds.Error.Stale -> HdWarningNotice(
			label = Res.string.duplicate_ids_error_stale_label.get(),
			message = Res.string.duplicate_ids_error_stale.get(),
		)
	}
}

@Composable
private fun Footer(
	keepCount: Int,
	working: Boolean,
	onKeep: () -> Unit,
	onDecideLater: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(start = 22.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		if (working) HdMonoLabel(Res.string.duplicate_ids_working.get())
		Spacer(modifier = Modifier.weight(1f))
		HdHairlineButton(
			label = Res.string.duplicate_ids_decide_later.get(),
			onClick = onDecideLater,
			enabled = !working,
		)
		HdPrimaryAction(
			prefix = Res.string.duplicate_ids_keep_prefix.get(),
			label = if (keepCount == 1) Res.string.duplicate_ids_keep_one.get() else Res.string.duplicate_ids_keep_many.get(keepCount),
			onClick = onKeep,
			enabled = keepCount > 0 && !working,
		)
	}
}

private val COMPACT_WIDTH = 520.dp

// Plenty for the two lines shown, without laying out a whole scene per row.
private const val PREVIEW_LENGTH = 400

private fun entityCode(set: DuplicateIdSet): String {
	val prefix = when (set.kind) {
		DuplicateIdKind.Entry, DuplicateIdKind.EntryImage -> "ENT"
		DuplicateIdKind.Scene -> "SCN"
		DuplicateIdKind.Draft -> "DRF"
		DuplicateIdKind.Note -> "NOTE"
	}
	return "$prefix-${set.id.toString().padStart(3, '0')}"
}

@Composable
private fun copyMeta(copy: DuplicateCopy): String = listOfNotNull(
	copy.modifiedAtMillis?.let { Res.string.duplicate_ids_modified.get(formatModified(it)) },
	copy.sizeBytes?.takeIf { !copy.isGroup }?.let(::formatSize),
).joinToString(" · ")

private fun formatModified(millis: Long): String =
	Instant.fromEpochMilliseconds(millis)
		.toLocalDateTime(TimeZone.currentSystemDefault())
		.format("d MMM yyyy HH:mm")
		.uppercase()

private fun formatSize(bytes: Long): String = when {
	bytes < 1024 -> "$bytes B"
	bytes < 1024 * 1024 -> "${(bytes * 10 / 1024) / 10.0} KB"
	else -> "${(bytes * 10 / (1024 * 1024)) / 10.0} MB"
}
