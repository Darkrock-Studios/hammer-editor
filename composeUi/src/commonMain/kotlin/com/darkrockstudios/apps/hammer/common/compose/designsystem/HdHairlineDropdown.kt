package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.WindowHeightSizeClass
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.hd_dropdown_no_match
import com.darkrockstudios.apps.hammer.hd_dropdown_search

/**
 * Generic hairline dropdown — a full-width square-cornered pill showing the
 * selected label and a `▾` chevron that opens a [DropdownMenu] of [options].
 *
 *     ┌─────────────────────────────────┐
 *     │ Markdown                      ▾ │
 *     └─────────────────────────────────┘
 *
 * The resource-label sibling of [HdHairlineSegmentedPicker] — same signature, so
 * swapping between them is one-for-one. Use for enums of 4+ values where a
 * segmented row would crowd; the optional [title] renders as an [HdMonoLabel]
 * above the pill with 8dp spacing.
 *
 * With more than [SEARCHABLE_ABOVE] options the open menu starts with a field that
 * narrows them to the labels containing what is typed, whatever its case or accents.
 * Up and Down move through the matches and Enter takes the one they are on.
 */
@Composable
fun <T> HdHairlineDropdown(
	options: List<T>,
	selected: T,
	onSelect: (T) -> Unit,
	label: @Composable (T) -> String,
	modifier: Modifier = Modifier,
	title: String? = null,
	enabled: Boolean = true,
) {
	var expanded by remember { mutableStateOf(false) }
	var query by remember { mutableStateOf("") }
	Column(
		modifier = modifier.alpha(if (enabled) 1f else 0.45f),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		if (title != null) {
			HdMonoLabel(text = title)
		}
		Box {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 36.dp)
					.hdControlFrame(MaterialTheme.colorScheme.outlineVariant)
					.clickable(enabled = enabled) {
						query = ""
						expanded = true
					}
					.padding(horizontal = Ui.Padding.M, vertical = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
			) {
				Text(
					text = label(selected),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					softWrap = false,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				HdMonoLabel(
					text = "▾",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			DropdownMenu(
				expanded = expanded && enabled,
				onDismissRequest = { expanded = false },
			) {
				val labelled = options.map { it to label(it) }
				val needle = query.trim().folded()
				val shown = if (needle.isEmpty()) labelled else labelled.filter { needle in it.second.folded() }
				val pick: (T) -> Unit = {
					onSelect(it)
					expanded = false
				}
				if (options.size > SEARCHABLE_ABOVE) {
					// The row Enter takes: the first match while typing; none before that, until an arrow key.
					var highlight by remember(needle) { mutableStateOf(if (needle.isEmpty()) -1 else 0) }
					DropdownSearch(
						query = query,
						onQueryChange = { query = it },
						onMove = { step -> if (shown.isNotEmpty()) highlight = (highlight + step).coerceIn(0, shown.lastIndex) },
						onEnter = {
							val match = shown.getOrNull(highlight)
							if (match != null) pick(match.first) else if (needle.isEmpty()) expanded = false
						},
					)
					val revealed = if (highlight >= 0 || needle.isNotEmpty()) highlight else shown.indexOfFirst { it.first == selected }
					val compact = LocalScreenCharacteristic.current.windowHeightClass == WindowHeightSizeClass.Compact
					val scroll = rememberScrollState()
					var rowHeight by remember { mutableStateOf(0) }
					LaunchedEffect(revealed, needle, rowHeight) {
						if (revealed < 0 || rowHeight == 0) return@LaunchedEffect
						val top = revealed * rowHeight
						val bottom = top + rowHeight
						if (top < scroll.value) {
							scroll.scrollTo(top)
						} else if (bottom > scroll.value + scroll.viewportSize) {
							scroll.scrollTo(bottom - scroll.viewportSize)
						}
					}
					// The menu scrolls as a whole, so the options scroll on their own to keep the field in view.
					Column(
						modifier = Modifier
							.heightIn(max = if (compact) 160.dp else 320.dp)
							.verticalScroll(scroll),
					) {
						shown.forEachIndexed { i, (option, text) ->
							DropdownOption(
								text = text,
								selected = option == selected,
								highlighted = i == highlight,
								// Every row is the same height, so the first one's places the rest.
								modifier = if (i == 0) Modifier.onSizeChanged { rowHeight = it.height } else Modifier,
							) { pick(option) }
						}
						if (shown.isEmpty()) {
							Text(
								text = Res.string.hd_dropdown_no_match.get(),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.M),
							)
						}
					}
				} else {
					shown.forEach { (option, text) -> DropdownOption(text, option == selected) { pick(option) } }
				}
			}
		}
	}
}

@Composable
private fun DropdownOption(
	text: String,
	selected: Boolean,
	highlighted: Boolean = false,
	modifier: Modifier = Modifier,
	onClick: () -> Unit,
) {
	DropdownMenuItem(
		modifier = modifier
			.then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) else Modifier),
		text = {
			Text(
				text = text,
				style = MaterialTheme.typography.bodyMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				color = if (selected) {
					MaterialTheme.colorScheme.onSurface
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
			)
		},
		onClick = onClick,
	)
}

@Composable
private fun DropdownSearch(
	query: String,
	onQueryChange: (String) -> Unit,
	onMove: (step: Int) -> Unit,
	onEnter: () -> Unit,
) {
	val focus = remember { FocusRequester() }
	if (hdDropdownSearchTakesFocus) {
		LaunchedEffect(Unit) { focus.requestFocus() }
	}
	val description = Res.string.hd_dropdown_search.get()
	BasicTextField(
		value = query,
		onValueChange = onQueryChange,
		singleLine = true,
		textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
		cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
		keyboardActions = KeyboardActions(onDone = { onEnter() }),
		modifier = Modifier
			// Wide enough that the menu doesn't shrink as the options narrow.
			.widthIn(min = 220.dp)
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.M)
			.focusRequester(focus)
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				when (event.key) {
					Key.DirectionDown -> onMove(1)
					Key.DirectionUp -> onMove(-1)
					else -> return@onPreviewKeyEvent false
				}
				true
			}
			.semantics { contentDescription = description }
			.testTag(HD_DROPDOWN_SEARCH_TAG),
		decorationBox = { field ->
			Box {
				if (query.isEmpty()) {
					Text(
						text = description,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				field()
			}
		},
	)
}

/** Lower case, with the accents of Latin letters dropped, so "sao" finds "São". */
private fun String.folded(): String = buildString(length) {
	for (c in this@folded.lowercase()) {
		val at = ACCENTED.indexOf(c)
		append(if (at < 0) c else UNACCENTED[at])
	}
}

private const val ACCENTED = "àáâãäåçèéêëìíîïñòóôõöùúûüýÿāăąćĉċčďēĕėęěĝğġģĥĩīĭįĵķĺļľńņňōŏőŕŗřśŝşšţťũūŭůűųŵŷźżžơưǎǐǒǔǖǘǚǜǟǡǧǩǫǭǰǵǹǻȁȃȅȇȉȋȍȏȑȓȕȗșțȟȧȩȫȭȯȱȳøłđıß"
private const val UNACCENTED = "aaaaaaceeeeiiiinooooouuuuyyaaaccccdeeeeegggghiiiijklllnnnooorrrssssttuuuuuuwyzzzouaiouuuuuaagkoojgnaaaeeiioorruusthaeooooyoldis"

/** A dropdown with more options than this can be searched. */
private const val SEARCHABLE_ABOVE = 10

const val HD_DROPDOWN_SEARCH_TAG = "hd-dropdown-search"

/** Whether the search field takes focus as the menu opens. Not where that would raise an on-screen keyboard. */
internal expect val hdDropdownSearchTakesFocus: Boolean
