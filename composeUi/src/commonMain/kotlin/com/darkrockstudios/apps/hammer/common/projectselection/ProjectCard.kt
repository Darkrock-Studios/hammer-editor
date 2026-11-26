package com.darkrockstudios.apps.hammer.common.projectselection

import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDropdownMenu
import com.darkrockstudios.apps.hammer.common.compose.stampDate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdIndentStep
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdMetaStyle
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdIndentFor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdIndentRails
import com.darkrockstudios.apps.hammer.common.compose.images.CoverThumbnail
import com.darkrockstudios.apps.hammer.common.compose.images.coverSize
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.parseHexColor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator
import com.darkrockstudios.apps.hammer.project_select_card_delete_button
import com.darkrockstudios.apps.hammer.project_select_card_leave_button
import com.darkrockstudios.apps.hammer.project_select_card_move_button
import com.darkrockstudios.apps.hammer.project_select_card_rename_button
import com.darkrockstudios.apps.hammer.project_select_card_sync_button
import com.darkrockstudios.apps.hammer.projects_list_card_by_author
import com.darkrockstudios.apps.hammer.projects_list_card_content_description
import com.darkrockstudios.apps.hammer.projects_list_card_shared
import com.darkrockstudios.apps.hammer.projects_list_card_shared_by
import com.darkrockstudios.apps.hammer.projects_list_card_sync_off
import com.darkrockstudios.apps.hammer.projects_list_card_words
import com.darkrockstudios.apps.hammer.projects_list_item_more_button
import org.jetbrains.compose.resources.StringResource
import com.darkrockstudios.apps.hammer.projects_list_opened_format
import com.darkrockstudios.apps.hammer.projects_list_opened_inline_format

val ProjectCardTestTag = "project-card"
const val ProjectCardCoverTestTag = "project-card-cover"
const val ProjectCardSyncOffTestTag = "project-card-sync-off"
const val ProjectCardSharedByTestTag = "project-card-shared-by"

private val StripeWidth: Dp = 4.dp
private val CoverHeight: Dp = 56.dp
private val CompactCoverHeight: Dp = 48.dp

/**
 * Editorial-index row for a single project. Wide puts the opened date and word count in a
 * trailing column; Compact folds them into one meta line under the tags.
 *
 * Inside a group ([depth] > 0) the row hangs one step right of its group's chevron, with a rail
 * per enclosing group. [groupPath], shown while the list is flattened by a filter, names the
 * groups the project sits in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectIndexRow(
	isWide: Boolean,
	projectData: ProjectData,
	onProjectClick: (projectDef: ProjectDef) -> Unit,
	onProjectAltClick: (projectDef: ProjectDef) -> Unit,
	onProjectRenameClick: (projectDef: ProjectDef) -> Unit,
	modifier: Modifier = Modifier,
	onTagClick: (tag: String) -> Unit = {},
	onProjectMoveClick: (projectDef: ProjectDef) -> Unit = {},
	onProjectSyncClick: (projectDef: ProjectDef, enabled: Boolean) -> Unit = { _, _ -> },
	depth: Int = 0,
	groupPath: List<String> = emptyList(),
	isServerSynced: Boolean = true,
) {
	val storedData = projectData.storedData
	val author = storedData.authorName?.takeIf { it.isNotBlank() }
	val tags = remember(storedData.tags) { storedData.tags.sorted() }
	val themePrimary = remember(storedData.theme?.primary) {
		storedData.theme?.primary?.let { parseHexColor(it) }
	}
	val unsetStripeColor = MaterialTheme.colorScheme.outlineVariant

	val lastAccessedDate = remember(projectData.metadata.info) {
		projectData.metadata.info.lastAccessed?.stampDate()
	}
	val wordsLabel = projectData.totalWords?.let {
		Res.string.projects_list_card_words.get(it.formatDecimalSeparator())
	}
	val byAuthor = author?.let { Res.string.projects_list_card_by_author.get(it) }
	val cardContentDescription =
		Res.string.projects_list_card_content_description.get(projectData.definition.name)

	val breadcrumb = remember(groupPath) { groupPath.takeIf { it.isNotEmpty() }?.joinToString(" › ") }
	val shared = projectData.metadata.info.ownerId != null
	val syncEnabled = projectData.metadata.info.syncEnabled
	val syncOff = isServerSynced && !syncEnabled
	val sharedBy = if (shared) sharedByLabel(projectData.metadata.info.ownerName) else null

	Column(
		modifier = modifier
			.fillMaxWidth()
			.hdIndentRails(levels = depth, color = MaterialTheme.colorScheme.outlineVariant, gutter = ProjectTreeGutter)
			.padding(start = if (depth > 0) hdIndentFor(depth, gutter = ProjectTreeGutter) + HdIndentStep else 0.dp),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(IntrinsicSize.Min)
				.combinedClickable(onClick = { onProjectClick(projectData.definition) })
				.semantics {
					contentDescription = cardContentDescription
				}
				.testTag(ProjectCardTestTag),
			verticalAlignment = Alignment.CenterVertically,
		) {
			ProjectStripe(
				themePrimary = themePrimary,
				// Nested rows already have the group rails; a dashed stripe beside them reads as clutter.
				unsetColor = unsetStripeColor.takeIf { depth == 0 },
			)

			Spacer(modifier = Modifier.width(Ui.Padding.XL))

			val coverPath = projectData.coverImagePath
			if (coverPath != null) {
				CoverThumbnail(
					path = coverPath,
					version = projectData.coverImageVersion,
					contentDescription = null,
					modifier = Modifier
						.padding(end = Ui.Padding.L)
						.coverSize(if (isWide) CoverHeight else CompactCoverHeight)
						.testTag(ProjectCardCoverTestTag),
				)
			}

			Column(
				modifier = Modifier
					.weight(1f)
					.padding(vertical = Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				) {
					Text(
						text = projectData.definition.name,
						style = if (isWide) {
							MaterialTheme.typography.headlineSmall
						} else {
							MaterialTheme.typography.titleMedium
						},
						color = MaterialTheme.colorScheme.onSurface,
						modifier = Modifier.weight(1f, fill = false),
					)
					if (syncOff) {
						Icon(
							imageVector = Icons.Outlined.CloudOff,
							contentDescription = Res.string.projects_list_card_sync_off.get(),
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier
								.size(16.dp)
								.testTag(ProjectCardSyncOffTestTag),
						)
					}
				}
				if (breadcrumb != null) {
					HdMonoLabel(
						text = breadcrumb,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				if (sharedBy != null) {
					// Not a mono label: that uppercases, and a pen name keeps its case.
					Text(
						text = sharedBy,
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.testTag(ProjectCardSharedByTestTag),
					)
				}
				if (byAuthor != null) {
					Text(
						text = byAuthor,
						style = MaterialTheme.typography.bodyMedium,
						fontStyle = FontStyle.Italic,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				TagChipRow(tags = tags, onTagClick = onTagClick)
				if (!isWide) {
					val meta = listOfNotNull(wordsLabel, lastAccessedDate?.let { Res.string.projects_list_opened_inline_format.get(it) }).joinToString(" · ")
					if (meta.isNotEmpty()) {
						Text(
							text = meta,
							style = hdMetaStyle(),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}

			if (isWide) {
				Column(
					modifier = Modifier.padding(horizontal = Ui.Padding.L),
					horizontalAlignment = Alignment.End,
					verticalArrangement = Arrangement.spacedBy(2.dp),
				) {
					// Self-labelled, so the list needs no column header.
					if (lastAccessedDate != null) {
						Text(
							text = Res.string.projects_list_opened_format.get(lastAccessedDate),
							style = hdMetaStyle(),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					if (wordsLabel != null) {
						Text(
							text = wordsLabel,
							style = hdMetaStyle(),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}

			ProjectOptionsMenu(
				items = remember(shared, syncEnabled, isServerSynced) { projectMenuItems(shared, syncEnabled, isServerSynced) },
				toggles = remember(syncEnabled) { mapOf(Res.string.project_select_card_sync_button to syncEnabled) },
			) {
				when (it) {
					Res.string.project_select_card_delete_button, Res.string.project_select_card_leave_button ->
						onProjectAltClick(projectData.definition)

					Res.string.project_select_card_rename_button ->
						onProjectRenameClick(projectData.definition)

					Res.string.project_select_card_move_button ->
						onProjectMoveClick(projectData.definition)

					Res.string.project_select_card_sync_button ->
						onProjectSyncClick(projectData.definition, !syncEnabled)
				}
			}
		}

		HorizontalDivider(
			modifier = Modifier.fillMaxWidth(),
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChipRow(tags: List<String>, onTagClick: (String) -> Unit) {
	if (tags.isEmpty()) return
	FlowRow(
		modifier = Modifier.padding(top = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		tags.forEach { tag ->
			HdTagChip(label = tag, onClick = { onTagClick(tag) })
		}
	}
}

@Composable
private fun ProjectStripe(themePrimary: Color?, unsetColor: Color?) {
	// Single drawWithCache so the modifier graph is stable per-row regardless
	// of whether the project has a theme — switching modifier nodes per row
	// would churn allocations in the LazyColumn.
	Box(
		modifier = Modifier
			.width(StripeWidth)
			.fillMaxHeight()
			.drawWithCache {
				val dashEffect = if (themePrimary == null) {
					PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
				} else null
				onDrawBehind {
					if (themePrimary != null) {
						drawRect(themePrimary)
					} else if (unsetColor != null) {
						drawLine(
							color = unsetColor,
							start = Offset(0f, 0f),
							end = Offset(0f, size.height),
							strokeWidth = 1f,
							cap = StrokeCap.Square,
							pathEffect = dashEffect,
						)
					}
				}
			},
	)
}

/** An item in [toggles] is a switch, checked when its value is true. */
@Composable
fun ProjectOptionsMenu(
	items: List<StringResource>,
	toggles: Map<StringResource, Boolean> = emptyMap(),
	onItemClick: (StringResource) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }

	Box {
		IconButton(
			modifier = Modifier.testTag("More"),
			onClick = { expanded = true },
		) {
			Icon(
				Icons.Default.MoreVert,
				contentDescription = Res.string.projects_list_item_more_button.get(),
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}

		HdDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			items.forEach { item ->
				val on = toggles[item]
				DropdownMenuItem(
					modifier = Modifier
						.testTag(item.get())
						.then(if (on != null) Modifier.semantics { toggleableState = ToggleableState(on) } else Modifier),
					text = { Text(text = item.get()) },
					trailingIcon = if (on == true) {
						{ Icon(Icons.Default.Check, contentDescription = null) }
					} else {
						null
					},
					onClick = {
						onItemClick(item)
						expanded = false
					}
				)
			}
		}
	}
}

/**
 * While a shared project syncs, deleting it is leaving it, and with a server its owner names and
 * places it. Parked, it is this device's copy like any other. Only the account's own projects have
 * a sync switch.
 */
private fun projectMenuItems(shared: Boolean, syncEnabled: Boolean, isServerSynced: Boolean): List<StringResource> {
	val leaves = shared && syncEnabled
	val ownerArranges = leaves && isServerSynced
	return listOfNotNull(
		if (leaves) Res.string.project_select_card_leave_button else Res.string.project_select_card_delete_button,
		Res.string.project_select_card_rename_button.takeUnless { ownerArranges },
		Res.string.project_select_card_move_button.takeUnless { ownerArranges },
		Res.string.project_select_card_sync_button.takeIf { !shared && isServerSynced },
	)
}

/** "Shared by {owner}", or "Shared with you" when the owner has no pen name. */
@Composable
fun sharedByLabel(ownerName: String?): String =
	ownerName?.takeIf { it.isNotBlank() }
		?.let { Res.string.projects_list_card_shared_by.get(it) }
		?: Res.string.projects_list_card_shared.get()
