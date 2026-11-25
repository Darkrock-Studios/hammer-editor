package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
 * [isSelected] marks the current item, and [clearSelected] the clear row when nothing is set;
 * the list opens scrolled to the current item.
 *
 * [filter] replaces the built-in match (it also sees a blank query). With [keyboardSelection],
 * Up and Down in the search field move a highlight that starts on the first match, and Enter
 * picks it. [autoFocusSearch] puts the caret in the search field on open. [itemTrailingContent]
 * draws a row's trailing end in place of the [itemTrailing] greeble.
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
	isSelected: (T) -> Boolean = { false },
	clearSelected: Boolean = false,
	blocksActions: Boolean = true,
	filter: ((query: String, items: List<T>) -> List<T>)? = null,
	keyboardSelection: Boolean = false,
	autoFocusSearch: Boolean = false,
	itemTrailingContent: (@Composable (T) -> Unit)? = null,
) {
	var query by rememberSaveable { mutableStateOf("") }

	AnimatedDialog(
		visible = visible,
		onCloseRequest = onDismiss,
		dismissOnTapOutside = true,
		onDismissed = { query = "" },
		blocksActions = blocksActions,
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
			isSelected = isSelected,
			clearSelected = clearSelected,
			filter = filter,
			keyboardSelection = keyboardSelection,
			autoFocusSearch = autoFocusSearch,
			itemTrailingContent = itemTrailingContent,
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
	isSelected: (T) -> Boolean = { false },
	clearSelected: Boolean = false,
	filter: ((query: String, items: List<T>) -> List<T>)? = null,
	keyboardSelection: Boolean = false,
	autoFocusSearch: Boolean = false,
	itemTrailingContent: (@Composable (T) -> Unit)? = null,
) {
	val filtered = remember(items, query) {
		when {
			filter != null -> filter(query, items)
			query.isBlank() -> items
			else -> items.filter {
				itemLabel(it).contains(query, ignoreCase = true) ||
					itemTrailing?.invoke(it)?.contains(query, ignoreCase = true) == true
			}
		}
	}
	var highlighted by remember(filtered) { mutableStateOf(0) }
	val pick = { item: T ->
		onSelect(item)
		onDismissRequest()
	}
	val pickHighlighted = { filtered.getOrNull(highlighted)?.let(pick) }
	val focusRequester = remember { FocusRequester() }
	if (autoFocusSearch) {
		LaunchedEffect(Unit) { focusRequester.requestFocus() }
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
			onSearch = if (keyboardSelection) ({ pickHighlighted() }) else null,
			onClear = { onQueryChange("") },
			focusRequester = focusRequester,
			modifier = Modifier
				.fillMaxWidth()
				.then(
					if (keyboardSelection) {
						Modifier.onPreviewKeyEvent { event ->
							if (event.type != KeyEventType.KeyDown || event.hasModifier()) return@onPreviewKeyEvent false
							when (event.key) {
								Key.DirectionDown -> {
									highlighted = (highlighted + 1).coerceAtMost(filtered.lastIndex.coerceAtLeast(0))
									true
								}

								Key.DirectionUp -> {
									highlighted = (highlighted - 1).coerceAtLeast(0)
									true
								}

								Key.Enter, Key.NumPadEnter -> {
									pickHighlighted()
									true
								}

								else -> false
							}
						}
					} else {
						Modifier
					}
				),
		)

		SpacerL()

		// Pinned above the scrolling list so it stays reachable even when the
		// search matches nothing.
		if (clearLabel != null && onClear != null) {
			PickerRow(
				label = clearLabel,
				trailing = null,
				muted = !clearSelected,
				selected = clearSelected,
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
				val initialIndex = remember { filtered.indexOfFirst(isSelected).coerceAtLeast(0) }
				val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
				// The keyed list would otherwise stay anchored on the selected item while filtering.
				LaunchedEffect(query) { if (query.isNotBlank()) listState.scrollToItem(0) }
				if (keyboardSelection) {
					LaunchedEffect(highlighted) { listState.reveal(highlighted) }
				}
				LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
					itemsIndexed(filtered, key = itemKey?.let { key -> { _, item -> key(item) } }) { index, item ->
						PickerRow(
							label = itemLabel(item),
							trailing = itemTrailing?.invoke(item),
							trailingContent = itemTrailingContent?.let { content -> { content(item) } },
							muted = false,
							selected = isSelected(item),
							highlighted = keyboardSelection && index == highlighted,
							onClick = { pick(item) },
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

/** Scrolls the least that shows all of row [index]. */
private suspend fun LazyListState.reveal(index: Int) {
	val info = layoutInfo
	val row = info.visibleItemsInfo.firstOrNull { it.index == index }
	when {
		row == null && index < firstVisibleItemIndex -> scrollToItem(index)
		row == null -> scrollToItem((index - info.visibleItemsInfo.size + 1).coerceAtLeast(0))
		row.offset < info.viewportStartOffset -> scrollBy((row.offset - info.viewportStartOffset).toFloat())
		row.offset + row.size > info.viewportEndOffset ->
			scrollBy((row.offset + row.size - info.viewportEndOffset).toFloat())
	}
}

private fun KeyEvent.hasModifier(): Boolean = isCtrlPressed || isMetaPressed || isAltPressed || isShiftPressed

@Composable
private fun PickerRow(
	label: String,
	trailing: String?,
	muted: Boolean,
	selected: Boolean,
	onClick: () -> Unit,
	highlighted: Boolean = false,
	trailingContent: (@Composable () -> Unit)? = null,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
			.clickable(onClick = onClick)
			.padding(horizontal = Ui.Padding.M, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = when {
				selected -> MaterialTheme.colorScheme.primary
				muted -> MaterialTheme.colorScheme.onSurfaceVariant
				else -> MaterialTheme.colorScheme.onSurface
			},
			modifier = Modifier.weight(1f),
		)
		if (selected) {
			Icon(
				imageVector = Icons.Filled.Check,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(18.dp),
			)
		}
		if (trailingContent != null) {
			trailingContent()
		} else if (trailing != null) {
			HdMonoLabel(
				text = trailing,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
