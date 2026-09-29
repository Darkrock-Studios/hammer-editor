package com.darkrockstudios.apps.hammer.common.encyclopedia.rename

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry
import com.darkrockstudios.apps.hammer.common.components.encyclopedia.rename.RenameEntry.Step
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EntryError
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.rename.*
import com.darkrockstudios.apps.hammer.common.globalsearch.annotatedSnippet
import org.jetbrains.compose.resources.StringResource

private val DialogMaxWidth = 560.dp
private val DialogMaxHeight = 760.dp
private val BodyPadding = PaddingValues(start = 26.dp, end = 26.dp, top = 22.dp, bottom = 22.dp)

@Composable
internal fun RenameEntryDialog(component: RenameEntry) {
	val state by component.state.subscribeAsState()
	var isOpen by remember { mutableStateOf(true) }
	val isCompact = LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact

	AnimatedDialogContainer(
		isOpen = isOpen,
		onDismissRequest = { if (state.canClose) isOpen = false },
		onClosed = component::close,
		properties = DialogProperties(
			dismissOnBackPress = true,
			dismissOnClickOutside = false,
			usePlatformDefaultWidth = false,
		),
	) {
		val frame = if (isCompact) {
			Modifier.fillMaxSize()
		} else {
			Modifier
				.padding(Ui.Padding.XL)
				.widthIn(max = DialogMaxWidth)
				.heightIn(max = DialogMaxHeight)
				.fillMaxWidth()
				.fillMaxHeight(0.9f)
		}
		Surface(
			modifier = frame.predictiveBackTransform(),
			shape = RectangleShape,
			color = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface,
			border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
		) {
			Column(modifier = Modifier.fillMaxSize()) {
				HdMasthead(
					section = Res.string.encyclopedia_entry_rename_marker.get(),
					leadingMeta = mastheadMeta(state),
					trailing = {
						if (state.canClose) {
							HdMastheadAction(
								label = Res.string.encyclopedia_entry_rename_close.get(),
								onClick = ::requestDismiss,
							)
						}
					},
				)
				HdFolioDivider()

				Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
					when (val step = state.step) {
						Step.Loading -> ProgressBody(label = null, progress = null)
						Step.Names -> NamesBody(state, component)
						is Step.Searching -> ProgressBody(
							label = Res.string.encyclopedia_entry_rename_searching.get(),
							progress = step.progress,
						)

						is Step.Summary -> SummaryBody(step, component)
						is Step.Applying -> ProgressBody(
							label = Res.string.encyclopedia_entry_rename_applying.get(),
							progress = step.progress,
						)

						is Step.Done -> DoneBody(step.result)
					}
				}

				Footer(state, component, onDone = ::requestDismiss)
			}
		}
	}
}

@Composable
private fun mastheadMeta(state: RenameEntry.State): List<String> = when (val step = state.step) {
	Step.Names -> listOf(Res.string.encyclopedia_entry_rename_meta_forms.get(1 + state.aliases.size))
	is Step.Summary -> listOf(Res.string.encyclopedia_entry_rename_meta_changes.get(step.pendingCount))
	else -> emptyList()
}

@Composable
private fun NamesBody(state: RenameEntry.State, component: RenameEntry) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(BodyPadding),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Text(
			text = Res.string.encyclopedia_entry_rename_title.get(state.name.from),
			style = MaterialTheme.typography.headlineSmall,
		)
		Text(
			text = Res.string.encyclopedia_entry_rename_intro.get(),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		HdHairlineField(
			label = Res.string.encyclopedia_entry_rename_name_label.get(state.name.from),
			value = state.name.text,
			onValueChange = component::updateName,
			capitalization = KeyboardCapitalization.Words,
		)

		state.aliases.forEachIndexed { index, alias ->
			AliasRow(
				alias = alias,
				onTextChange = { component.updateAlias(index, it) },
				onRemovedChange = { component.setAliasRemoved(index, it) },
			)
		}

		state.addedAliases.forEachIndexed { index, alias ->
			Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
				HdHairlineField(
					label = Res.string.encyclopedia_entry_rename_new_alias_label.get(),
					value = alias,
					onValueChange = { component.updateAddedAlias(index, it) },
					modifier = Modifier.weight(1f),
					capitalization = KeyboardCapitalization.Words,
				)
				HdHairlineButton(
					label = Res.string.encyclopedia_entry_rename_alias_remove.get(),
					onClick = { component.removeAddedAlias(index) },
				)
			}
		}

		HdHairlineButton(
			label = Res.string.encyclopedia_entry_rename_add_alias.get(),
			onClick = component::addAlias,
		)

		HdHairlineToggleRow(
			checked = state.keepOldNames,
			onCheckedChange = component::setKeepOldNames,
			label = Res.string.encyclopedia_entry_rename_keep_old.get(),
			hint = Res.string.encyclopedia_entry_rename_keep_old_hint.get(),
		)

		state.check?.let { CheckNotices(it) }
	}
}

@Composable
private fun AliasRow(
	alias: RenameEntry.FormEdit,
	onTextChange: (String) -> Unit,
	onRemovedChange: (Boolean) -> Unit,
) {
	Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		if (alias.removed) {
			Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
				HdMonoLabel(text = Res.string.encyclopedia_entry_rename_alias_label.get(alias.from))
				Text(
					text = alias.from,
					style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.LineThrough),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				HdMonoLabel(text = Res.string.encyclopedia_entry_rename_alias_removed.get())
			}
			HdHairlineButton(
				label = Res.string.encyclopedia_entry_rename_alias_restore.get(),
				onClick = { onRemovedChange(false) },
			)
		} else {
			HdHairlineField(
				label = Res.string.encyclopedia_entry_rename_alias_label.get(alias.from),
				value = alias.text,
				onValueChange = onTextChange,
				modifier = Modifier.weight(1f),
				capitalization = KeyboardCapitalization.Words,
			)
			HdHairlineButton(
				label = Res.string.encyclopedia_entry_rename_alias_remove.get(),
				onClick = { onRemovedChange(true) },
			)
		}
	}
}

@Composable
private fun CheckNotices(check: RenameCheck) {
	val error = when {
		check.hasBlankForm -> Res.string.encyclopedia_entry_rename_blank_form.get()
		check.entryError != EntryError.NONE -> entryErrorText(check.entryError)
		else -> null
	}
	if (error != null) {
		Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
	} else if (!check.hasChanges) {
		HdMonoLabel(text = Res.string.encyclopedia_entry_rename_no_changes.get())
	}
	if (check.collisions.isNotEmpty()) {
		HdWarningNotice(
			label = Res.string.encyclopedia_entry_rename_collision_label.get(),
			message = check.collisions.map {
				Res.string.encyclopedia_entry_rename_collision.get(it.form, it.otherEntryName)
			}.joinToString("\n"),
		)
	}
}

@Composable
private fun entryErrorText(error: EntryError): String? = when (error) {
	EntryError.NONE -> null
	EntryError.NAME_TOO_SHORT -> Res.string.encyclopedia_create_entry_toast_name_too_short.get()
	EntryError.NAME_TOO_LONG -> Res.string.encyclopedia_create_entry_toast_too_long.get(EncyclopediaRepository.MAX_NAME_SIZE)
	EntryError.NAME_INVALID_CHARACTERS -> Res.string.encyclopedia_create_entry_toast_invalid_name.get()
	EntryError.TAG_TOO_LONG -> Res.string.encyclopedia_create_entry_toast_tag_too_long.get(EncyclopediaRepository.MAX_TAG_SIZE)
	EntryError.ALIAS_TOO_LONG -> Res.string.encyclopedia_create_entry_toast_alias_too_long.get(EncyclopediaRepository.MAX_NAME_SIZE)
}

@Composable
private fun ProgressBody(label: String?, progress: RenameProgress?) {
	Column(
		modifier = Modifier.fillMaxSize().padding(BodyPadding),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.CenterVertically),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		if (label != null) HdMonoLabel(text = label)
		val fraction = progress?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total } ?: 0f
		HdHairlineProgressBar(progress = fraction, modifier = Modifier.widthIn(max = 320.dp))
		if (progress != null) {
			HdMonoLabel(text = Res.string.encyclopedia_entry_rename_progress.get(progress.done, progress.total))
		}
	}
}

@Composable
private fun SummaryBody(summary: Step.Summary, component: RenameEntry) {
	val preview = summary.preview
	var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
	fun toggle(group: String) {
		expanded = if (group in expanded) expanded - group else expanded + group
	}

	val groups = preview.items
		.filter { it.sureCount > 0 }
		.groupBy { it.key.place }
	val unsureItems = preview.items.filter { it.unsure.isNotEmpty() }

	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = BodyPadding,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		item {
			Text(
				text = summaryHeadline(summary),
				style = MaterialTheme.typography.titleMedium,
			)
		}
		item {
			if (preview.backupSupported) {
				Text(
					text = Res.string.encyclopedia_entry_rename_backup_note.get(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			} else {
				HdWarningNotice(
					label = Res.string.encyclopedia_entry_rename_no_backup_label.get(),
					message = Res.string.encyclopedia_entry_rename_no_backup_message.get(),
				)
			}
		}

		for ((place, items) in groups) {
			val groupKey = place.name
			val count = items
				.filter { it.key !in summary.excludedItems }
				.sumOf { item -> item.matches.count { it.isSure || it.key in summary.includedUnsure } }
			item(key = "group-$groupKey") {
				Disclosure(
					label = placeLabel(place).get(),
					meta = count.toString(),
					expanded = groupKey in expanded,
					onToggle = { toggle(groupKey) },
				)
			}
			if (groupKey in expanded) {
				items(items, key = { "item-${it.key.place}-${it.key.id}" }) { item ->
					val included = item.key !in summary.excludedItems
					CheckRow(
						checked = included,
						onToggle = { component.setItemExcluded(item.key, included) },
					) {
						Text(
							text = item.title,
							style = MaterialTheme.typography.bodyMedium,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.weight(1f),
						)
						HdMonoLabel(text = item.sureCount.toString())
					}
				}
			}
		}

		if (unsureItems.isNotEmpty()) {
			unsureSection(
				summary = summary,
				items = unsureItems,
				expanded = UNSURE_GROUP in expanded,
				onToggle = { toggle(UNSURE_GROUP) },
				component = component,
			)
		}
	}
}

private const val UNSURE_GROUP = "unsure"

private fun LazyListScope.unsureSection(
	summary: Step.Summary,
	items: List<RenameItem>,
	expanded: Boolean,
	onToggle: () -> Unit,
	component: RenameEntry,
) {
	item(key = "group-unsure") {
		Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
			Disclosure(
				label = countText(
					summary.unsureCount,
					Res.string.encyclopedia_entry_rename_unsure_one,
					Res.string.encyclopedia_entry_rename_unsure_other,
				),
				meta = summary.includedUnsure.size
					.takeIf { it > 0 }
					?.let { Res.string.encyclopedia_entry_rename_unsure_included.get(it) }
					.orEmpty(),
				expanded = expanded,
				onToggle = onToggle,
			)
			AnimatedVisibility(visible = expanded) {
				HdMonoLabel(text = Res.string.encyclopedia_entry_rename_unsure_hint.get())
			}
		}
	}
	if (!expanded) return
	for (item in items) {
		items(item.unsure, key = { "unsure-${it.key.item.place}-${it.key.item.id}-${it.key.offset}" }) { match ->
			val included = match.key in summary.includedUnsure
			CheckRow(
				checked = included,
				onToggle = { component.setUnsureIncluded(match.key, !included) },
			) {
				Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
					HdMonoLabel(
						text = "${placeLabel(item.key.place).get()} · ${item.title}",
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					match.snippet?.let {
						Text(text = annotatedSnippet(it), style = MaterialTheme.typography.bodyMedium)
					}
					match.reason?.let {
						Text(
							text = reasonText(it),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun Disclosure(label: String, meta: String, expanded: Boolean, onToggle: () -> Unit) {
	Column {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = Ui.MIN_TOUCH_SIZE)
				.clickable(onClick = onToggle),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		) {
			HdMonoLabel(text = if (expanded) "▾" else "▸")
			HdMonoLabel(text = label, modifier = Modifier.weight(1f))
			HdMonoLabel(text = meta)
		}
		HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
	}
}

@Composable
private fun CheckRow(checked: Boolean, onToggle: () -> Unit, content: @Composable RowScope.() -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = Ui.MIN_TOUCH_SIZE)
			.clickable(onClick = onToggle)
			.padding(start = Ui.Padding.XL),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdHairlineCheckbox(checked = checked)
		content()
	}
}

@Composable
private fun summaryHeadline(summary: Step.Summary): String {
	val preview = summary.preview
	return when {
		preview.items.isEmpty() -> Res.string.encyclopedia_entry_rename_summary_none.get()
		summary.pendingCount == 1 -> Res.string.encyclopedia_entry_rename_summary_one.get(preview.oldName, preview.newName)
		else -> Res.string.encyclopedia_entry_rename_summary_other.get(
			summary.pendingCount,
			preview.oldName,
			preview.newName,
		)
	}
}

@Composable
private fun DoneBody(result: RenameResult) {
	Column(
		modifier = Modifier.fillMaxSize().padding(BodyPadding),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		if (result.backupFailed) {
			Text(
				text = Res.string.encyclopedia_entry_rename_result_backup_failed.get(),
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.error,
			)
			return@Column
		}
		Text(
			text = if (result.replaced == 1) {
				Res.string.encyclopedia_entry_rename_result_one.get()
			} else {
				Res.string.encyclopedia_entry_rename_result_other.get(result.replaced, result.itemsChanged)
			},
			style = MaterialTheme.typography.titleMedium,
		)
		if (result.skippedStale > 0) {
			Text(text = Res.string.encyclopedia_entry_rename_result_stale.get(result.skippedStale))
		}
		if (result.skippedInvalid > 0) {
			Text(text = Res.string.encyclopedia_entry_rename_result_invalid.get(result.skippedInvalid))
		}
		if (result.entryError != EntryError.NONE) {
			Text(
				text = Res.string.encyclopedia_entry_rename_result_entry_failed.get(),
				color = MaterialTheme.colorScheme.error,
			)
			entryErrorText(result.entryError)?.let {
				Text(text = it, color = MaterialTheme.colorScheme.error)
			}
		}
	}
}

@Composable
private fun Footer(state: RenameEntry.State, component: RenameEntry, onDone: () -> Unit) {
	val step = state.step
	if (step is Step.Loading || step is Step.Applying) return
	HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 26.dp, vertical = Ui.Padding.L),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.End),
		verticalAlignment = Alignment.CenterVertically,
	) {
		when (step) {
			Step.Names -> {
				HdHairlineButton(
					label = Res.string.encyclopedia_entry_rename_cancel_button.get(),
					onClick = onDone,
				)
				HdHairlineButton(
					label = Res.string.encyclopedia_entry_rename_find_button.get(),
					onClick = component::findReferences,
					emphasised = true,
					enabled = state.check?.canProceed == true,
				)
			}

			is Step.Searching -> HdHairlineButton(
				label = Res.string.encyclopedia_entry_rename_back_button.get(),
				onClick = component::backToNames,
			)

			is Step.Summary -> {
				HdHairlineButton(
					label = Res.string.encyclopedia_entry_rename_back_button.get(),
					onClick = component::backToNames,
				)
				HdHairlineButton(
					label = when (step.pendingCount) {
						0 -> Res.string.encyclopedia_entry_rename_apply_none.get()
						1 -> Res.string.encyclopedia_entry_rename_apply_one.get()
						else -> Res.string.encyclopedia_entry_rename_apply_other.get(step.pendingCount)
					},
					onClick = component::apply,
					emphasised = true,
				)
			}

			is Step.Done -> HdHairlineButton(
				label = Res.string.encyclopedia_entry_rename_done_button.get(),
				onClick = onDone,
				emphasised = true,
			)

			Step.Loading, is Step.Applying -> Unit
		}
	}
}

@Composable
private fun countText(count: Int, one: StringResource, other: StringResource): String =
	if (count == 1) one.get() else other.get(count)

private fun placeLabel(place: RenamePlace): StringResource = when (place) {
	RenamePlace.SCENE_TEXT -> Res.string.encyclopedia_entry_rename_place_scene_text
	RenamePlace.ARCHIVED_SCENE_TEXT -> Res.string.encyclopedia_entry_rename_place_archived
	RenamePlace.SCENE_TITLE -> Res.string.encyclopedia_entry_rename_place_scene_title
	RenamePlace.SCENE_OUTLINE -> Res.string.encyclopedia_entry_rename_place_scene_outline
	RenamePlace.SCENE_NOTES -> Res.string.encyclopedia_entry_rename_place_scene_notes
	RenamePlace.NOTE -> Res.string.encyclopedia_entry_rename_place_note
	RenamePlace.TIMELINE_EVENT -> Res.string.encyclopedia_entry_rename_place_timeline
	RenamePlace.ENCYCLOPEDIA_ENTRY -> Res.string.encyclopedia_entry_rename_place_entry
}

@Composable
private fun reasonText(reason: UnsureReason): String = when (reason) {
	UnsureReason.Plural -> Res.string.encyclopedia_entry_rename_reason_plural.get()
	is UnsureReason.PartOfName -> Res.string.encyclopedia_entry_rename_reason_part.get(reason.word)
	is UnsureReason.SharedWith -> Res.string.encyclopedia_entry_rename_reason_shared.get(reason.otherEntryNames.joinToString(", "))
	UnsureReason.Dismissed -> Res.string.encyclopedia_entry_rename_reason_dismissed.get()
}
