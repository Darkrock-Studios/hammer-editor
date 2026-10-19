package com.darkrockstudios.apps.hammer.common.compose.entitypicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineCheckbox
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdReferenceChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTabPill
import com.darkrockstudios.apps.hammer.common.compose.designsystem.glyph
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.filterCatalog
import com.darkrockstudios.apps.hammer.entity_picker_close
import com.darkrockstudios.apps.hammer.entity_picker_done
import com.darkrockstudios.apps.hammer.entity_picker_empty
import com.darkrockstudios.apps.hammer.entity_picker_more
import com.darkrockstudios.apps.hammer.entity_picker_none
import com.darkrockstudios.apps.hammer.entity_picker_remove
import com.darkrockstudios.apps.hammer.entity_picker_search
import com.darkrockstudios.apps.hammer.global_search_filter_encyclopedia
import com.darkrockstudios.apps.hammer.global_search_filter_notes
import com.darkrockstudios.apps.hammer.global_search_filter_scenes
import com.darkrockstudios.apps.hammer.global_search_filter_timeline

/**
 * Picks items of a project from [items] by name or alias, as they are typed:
 *
 *     ┌───────────────────────────────────────────┐
 *     │ First character                         × │
 *     │   Scenes       Encyclopedia               │
 *     │ ──────────── ━━━━━━━━━━━━━━               │
 *     │ ┌───────────────────────────────────────┐ │
 *     │ │ ⌕  ali                                │ │
 *     │ └───────────────────────────────────────┘ │
 *     │ [☉] Alice                             [ ] │
 *     │     Person · Alys                         │
 *     └───────────────────────────────────────────┘
 *
 * Only items [filter] accepts are offered, with a tab for each of its kinds when it has more than
 * one. With [multiple], picking a row toggles it in [selected] and Done closes; otherwise picking one
 * replaces the selection and closes. Not tied to plugins: it takes the catalog and reports picks.
 */
@Composable
fun EntityPickerDialog(
	visible: Boolean,
	title: String,
	items: List<CatalogItem>,
	filter: CatalogFilter,
	multiple: Boolean,
	selected: List<CatalogRef>,
	onSelectedChange: (List<CatalogRef>) -> Unit,
	onDismiss: () -> Unit,
) {
	var query by rememberSaveable { mutableStateOf("") }
	val tabs = CatalogKind.entries.filter { it in filter.kinds }
	// Opens on the kind already picked, if any.
	var tab by remember(visible) { mutableStateOf(selected.firstOrNull()?.kind?.takeIf { it in tabs } ?: tabs.firstOrNull()) }

	AnimatedDialog(
		visible = visible,
		onCloseRequest = onDismiss,
		dismissOnTapOutside = true,
		onDismissed = { query = "" },
	) {
		val matches = remember(items, query, filter, tab) {
			filterCatalog(items, query, filter.copy(kinds = setOfNotNull(tab)))
		}
		HdHairlineDialogShell(
			title = title,
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.entity_picker_close.get(),
		) {
			if (tabs.size > 1) {
				Row(modifier = Modifier.fillMaxWidth()) {
					tabs.forEach { kind ->
						HdTabPill(
							label = kind.label(),
							active = tab == kind,
							onClick = { tab = kind },
							modifier = Modifier.weight(1f),
						)
					}
				}
				SpacerL()
			}

			HdSearchField(
				value = query,
				onValueChange = { query = it },
				placeholder = Res.string.entity_picker_search.get(),
				onClear = { query = "" },
				modifier = Modifier.fillMaxWidth(),
			)

			SpacerL()

			Box(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 120.dp, max = 320.dp),
			) {
				if (matches.isEmpty()) {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = Ui.Padding.XL),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = (if (query.isBlank()) Res.string.entity_picker_none else Res.string.entity_picker_empty).get(),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				} else {
					LazyColumn(modifier = Modifier.fillMaxWidth()) {
						items(matches.take(MAX_ROWS), key = { "${it.kind.id}:${it.id}" }) { item ->
							val picked = item.ref in selected
							PickerRow(
								item = item,
								picked = picked,
								multiple = multiple,
								onToggle = {
									if (multiple) {
										onSelectedChange(if (picked) selected - item.ref else selected + item.ref)
									} else {
										onSelectedChange(listOf(item.ref))
										requestDismiss()
									}
								},
							)
							HorizontalDivider(
								thickness = Dp.Hairline,
								color = MaterialTheme.colorScheme.outlineVariant,
							)
						}
						if (matches.size > MAX_ROWS) {
							item(key = "more") {
								HdMonoLabel(
									text = Res.string.entity_picker_more.get(matches.size - MAX_ROWS),
									modifier = Modifier.padding(Ui.Padding.L),
								)
							}
						}
					}
				}
			}

			if (multiple) {
				SpacerL()
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End,
				) {
					HdHairlineButton(
						label = Res.string.entity_picker_done.get(),
						onClick = { requestDismiss() },
						emphasised = true,
					)
				}
			}
		}
	}
}

/** A picked [item] as a chip, its glyph on its kind's swatch, with × calling [onRemove]. */
@Composable
fun EntityChip(item: CatalogItem, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
	val mark = item.mark()
	HdReferenceChip(
		glyph = mark.glyph,
		swatchColor = mark.swatch,
		glyphColor = mark.onSwatch,
		name = item.name,
		onClick = onClick,
		onAction = onRemove,
		actionContentDescription = Res.string.entity_picker_remove.get(item.name),
		modifier = modifier,
	)
}

@Composable
private fun PickerRow(item: CatalogItem, picked: Boolean, multiple: Boolean, onToggle: () -> Unit) {
	val mark = item.mark()
	val detail = listOfNotNull(
		item.entryType?.toStringResource()?.get(),
		item.detail,
		item.aliases.joinToString(", ").ifEmpty { null },
	).joinToString(" · ")
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.toggleable(value = picked, role = if (multiple) Role.Checkbox else Role.RadioButton, onValueChange = { onToggle() })
			.padding(horizontal = Ui.Padding.M, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Box(
			modifier = Modifier.size(22.dp).background(mark.swatch),
			contentAlignment = Alignment.Center,
		) {
			Text(text = mark.glyph, style = MaterialTheme.typography.labelMedium, color = mark.onSwatch)
		}
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = item.name,
				style = MaterialTheme.typography.bodyMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (detail.isNotEmpty()) {
				HdMonoLabel(text = detail, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
			}
		}
		HdHairlineCheckbox(checked = picked)
	}
}

private class KindMark(val glyph: String, val swatch: Color, val onSwatch: Color)

/** An entry's type glyph on its type's color; the other kinds a glyph of their own on a neutral swatch. */
@Composable
private fun CatalogItem.mark(): KindMark {
	val type = entryType
	if (type != null) return KindMark(type.glyph(), LocalHammerColors.current.colorFor(type), MaterialTheme.colorScheme.onPrimary)
	val glyph = when (kind) {
		CatalogKind.Scene -> "§"
		CatalogKind.Note -> "¶"
		CatalogKind.Entry -> "∗"
		CatalogKind.Event -> "◷"
	}
	return KindMark(glyph, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surface)
}

@Composable
private fun CatalogKind.label(): String = when (this) {
	CatalogKind.Scene -> Res.string.global_search_filter_scenes
	CatalogKind.Note -> Res.string.global_search_filter_notes
	CatalogKind.Entry -> Res.string.global_search_filter_encyclopedia
	CatalogKind.Event -> Res.string.global_search_filter_timeline
}.get()

/** Rows past this are left out until the search narrows them. */
private const val MAX_ROWS = 100
