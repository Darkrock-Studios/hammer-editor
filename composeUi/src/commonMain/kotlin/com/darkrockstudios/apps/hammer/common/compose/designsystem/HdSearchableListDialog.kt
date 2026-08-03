package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Hairline dialog for picking one item from a long list, narrowed by a search field.
 * Use it when a [HdHairlineDropdown] would be unwieldy (dozens to hundreds of options,
 * e.g. every locale the platform knows).
 *
 *     ┌───────────────────────────────────────────┐
 *     │ Choose a language                       × │
 *     │ ┌───────────────────────────────────────┐ │
 *     │ │ ⌕  search                             │ │
 *     │ └───────────────────────────────────────┘ │
 *     │  Clear selection                          │
 *     │ ──────────────────────────────────────────│
 *     │  English (United States)          EN-US   │
 *     │ ──────────────────────────────────────────│
 *     │  Français (France)                FR-FR   │
 *     └───────────────────────────────────────────┘
 *
 * The search matches [itemLabel] and [itemTrailing] case-insensitively. [itemTrailing]
 * renders as a mono greeble on the right; anchor it to a real value (a locale code,
 * a count), never decoration. [onClear], when provided, shows a muted row above the
 * list that clears the current selection. Selection and clear both dismiss.
 */
@Composable
fun <T> HdSearchableListDialog(
	visible: Boolean,
	title: String,
	items: List<T>,
	itemLabel: (T) -> String,
	onSelect: (T) -> Unit,
	onDismiss: () -> Unit,
	closeContentDescription: String,
	searchPlaceholder: String = "",
	itemTrailing: ((T) -> String)? = null,
	itemKey: ((T) -> Any)? = null,
	clearLabel: String? = null,
	onClear: (() -> Unit)? = null,
	emptyLabel: String = "",
) {
	var query by rememberSaveable { mutableStateOf("") }

	AnimatedDialog(
		visible = visible,
		onCloseRequest = onDismiss,
		dismissOnTapOutside = true,
		onDismissed = { query = "" },
	) {
		HdSearchableListDialogContent(
			title = title,
			query = query,
			onQueryChange = { query = it },
			items = items,
			itemLabel = itemLabel,
			onSelect = onSelect,
			onDismissRequest = { requestDismiss() },
			closeContentDescription = closeContentDescription,
			searchPlaceholder = searchPlaceholder,
			itemTrailing = itemTrailing,
			itemKey = itemKey,
			clearLabel = clearLabel,
			onClear = onClear,
			emptyLabel = emptyLabel,
		)
	}
}

/** The dialog body without the [AnimatedDialog] host, so previews can render it directly. */
@Composable
internal fun <T> HdSearchableListDialogContent(
	title: String,
	query: String,
	onQueryChange: (String) -> Unit,
	items: List<T>,
	itemLabel: (T) -> String,
	onSelect: (T) -> Unit,
	onDismissRequest: () -> Unit,
	closeContentDescription: String,
	searchPlaceholder: String = "",
	itemTrailing: ((T) -> String)? = null,
	itemKey: ((T) -> Any)? = null,
	clearLabel: String? = null,
	onClear: (() -> Unit)? = null,
	emptyLabel: String = "",
) {
	val filtered = remember(items, query) {
		if (query.isBlank()) items
		else items.filter {
			itemLabel(it).contains(query, ignoreCase = true) ||
				itemTrailing?.invoke(it)?.contains(query, ignoreCase = true) == true
		}
	}

	HdHairlineDialogShell(
		title = title,
		onClose = onDismissRequest,
		closeContentDescription = closeContentDescription,
	) {
		HdSearchField(
			value = query,
			onValueChange = onQueryChange,
			placeholder = searchPlaceholder,
			onClear = { onQueryChange("") },
			modifier = Modifier.fillMaxWidth(),
		)

		SpacerL()

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 120.dp, max = 360.dp),
		) {
			if (filtered.isEmpty()) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = Ui.Padding.XL),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = emptyLabel,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			} else {
				LazyColumn(modifier = Modifier.fillMaxWidth()) {
					if (clearLabel != null && onClear != null) {
						item {
							PickerRow(
								label = clearLabel,
								trailing = null,
								muted = true,
								onClick = {
									onClear()
									onDismissRequest()
								},
							)
							HorizontalDivider(
								thickness = Dp.Hairline,
								color = MaterialTheme.colorScheme.outlineVariant,
							)
						}
					}
					items(filtered, key = itemKey) { item ->
						PickerRow(
							label = itemLabel(item),
							trailing = itemTrailing?.invoke(item),
							muted = false,
							onClick = {
								onSelect(item)
								onDismissRequest()
							},
						)
						HorizontalDivider(
							thickness = Dp.Hairline,
							color = MaterialTheme.colorScheme.outlineVariant,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun PickerRow(
	label: String,
	trailing: String?,
	muted: Boolean,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = Ui.Padding.M, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = if (muted) {
				MaterialTheme.colorScheme.onSurfaceVariant
			} else {
				MaterialTheme.colorScheme.onSurface
			},
		)
		if (trailing != null) {
			HdMonoLabel(
				text = trailing,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
