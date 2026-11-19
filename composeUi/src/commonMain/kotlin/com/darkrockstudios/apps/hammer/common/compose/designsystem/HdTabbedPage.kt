package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.darkrockstudios.apps.hammer.common.compose.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn

/**
 * A page of tabs (settings screens). On wide screens the tabs run down a side column under [back],
 * and the title and content share the next edge, so nothing is centered against a left-pinned
 * masthead. Phones put the tabs in an even row under the masthead, seated on its rule.
 *
 *     ←  Home      Project settings                    ■ Autosaved
 *     ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *        Project      Word-count goal
 *     ┃  Writing      ─────────────────────────────────────────────
 *        Timelines    ☐ Track a writing goal
 */
@Composable
fun <T> HdTabbedPage(
	title: String,
	tabs: List<T>,
	selected: T,
	label: @Composable (T) -> String,
	onSelect: (T) -> Unit,
	maxWidth: Dp,
	modifier: Modifier = Modifier,
	back: (@Composable () -> Unit)? = null,
	trailing: (@Composable () -> Unit)? = null,
	content: @Composable ColumnScope.(T) -> Unit,
) {
	val compact = LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact
	val horizontal = if (compact) Ui.Padding.XL else 56.dp
	val side = !compact

	Column(modifier = modifier.fillMaxSize()) {
		PageMasthead(title, maxWidth, horizontal, sideColumn = side, back = back, trailing = trailing)

		if (side) {
			HdFolioDivider()
			Row(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = horizontal)) {
				Column(
					modifier = Modifier.width(SideNavWidth).padding(top = 28.dp),
					verticalArrangement = Arrangement.spacedBy(2.dp),
				) {
					tabs.forEach { option ->
						SideTab(label(option), option == selected) { onSelect(option) }
					}
				}
				Spacer(Modifier.width(SideNavGap))
				TabBody(selected, maxWidth, endPadding = horizontal, top = 32.dp, content = content)
			}
		} else {
			TopTabs(tabs, selected, label, onSelect, horizontal)
			Box(Modifier.weight(1f).fillMaxWidth().padding(start = horizontal)) {
				TabBody(selected, maxWidth, endPadding = horizontal, top = Ui.Padding.XL, content = content)
			}
		}
	}
}

/**
 * A page without tabs (About): the same masthead as [HdTabbedPage]. On wide screens the title
 * and the content column are centered together, so they still share one edge.
 */
@Composable
fun HdPage(
	title: String,
	maxWidth: Dp,
	modifier: Modifier = Modifier,
	back: (@Composable () -> Unit)? = null,
	trailing: (@Composable () -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	val compact = LocalScreenCharacteristic.current.windowWidthClass == WindowWidthSizeClass.Compact
	val horizontal = if (compact) Ui.Padding.XL else 56.dp
	Column(modifier = modifier.fillMaxSize()) {
		PageMasthead(title, maxWidth, horizontal, sideColumn = false, back = back, trailing = trailing, centered = !compact)
		HdFolioDivider()
		Box(Modifier.weight(1f).fillMaxWidth().padding(start = horizontal)) {
			TabBody(Unit, maxWidth, endPadding = horizontal, top = if (compact) Ui.Padding.XL else 32.dp, centered = !compact) { content() }
		}
	}
}

@Composable
private fun PageMasthead(
	title: String,
	maxWidth: Dp,
	horizontal: Dp,
	sideColumn: Boolean,
	back: (@Composable () -> Unit)?,
	trailing: (@Composable () -> Unit)?,
	centered: Boolean = false,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(Ui.TOP_BAR_HEIGHT)
			.padding(horizontal = horizontal),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
	) {
		if (sideColumn) {
			Box(Modifier.width(SideNavWidth)) { back?.invoke() }
			Spacer(Modifier.width(SideNavGap))
		} else if (back != null) {
			back()
			Spacer(Modifier.width(Ui.Padding.L))
		}
		Row(
			modifier = Modifier.weight(1f, fill = false).widthIn(max = maxWidth).fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			trailing?.invoke()
		}
	}
}

@Composable
private fun <T> TabBody(
	selected: T,
	maxWidth: Dp,
	endPadding: Dp,
	top: Dp,
	centered: Boolean = false,
	content: @Composable ColumnScope.(T) -> Unit,
) {
	// A fresh scroll position per tab, so switching tabs always starts at the top.
	key(selected) {
		val scrollState = rememberScrollState()
		Box(modifier = Modifier.fillMaxSize()) {
			Column(
				modifier = Modifier
					.align(if (centered) Alignment.TopCenter else Alignment.TopStart)
					.fillMaxHeight()
					.verticalScroll(scrollState)
					.padding(end = endPadding, top = top, bottom = 40.dp)
					.widthIn(max = maxWidth)
					.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(56.dp),
			) {
				content(selected)
			}
			MpScrollBarColumn(modifier = scrollBarOverlay(), state = scrollState)
		}
	}
}

/** Phone tabs: an even row on the folio rule, the active tab's accent bar lying on top of it. */
@Composable
private fun <T> TopTabs(
	tabs: List<T>,
	selected: T,
	label: @Composable (T) -> String,
	onSelect: (T) -> Unit,
	horizontal: Dp,
) {
	val accent = MaterialTheme.colorScheme.primary
	Column(modifier = Modifier.fillMaxWidth()) {
		// Above the rule so the active bar can paint over it.
		Row(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontal).zIndex(1f)) {
			tabs.forEach { option ->
				val active = option == selected
				Box(
					modifier = Modifier
						.weight(1f)
						.drawWithContent {
							drawContent()
							if (active) {
								drawRect(accent, topLeft = Offset(0f, size.height), size = Size(size.width, 2.dp.toPx()))
							}
						}
						.clickable(role = Role.Tab) { onSelect(option) }
						.semantics { this.selected = active }
						.padding(top = 4.dp, bottom = 12.dp),
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = label(option),
						style = MaterialTheme.typography.labelLarge,
						color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}
		HdFolioDivider()
	}
}

/** A side-column tab: the label, with an accent bar down its start edge when active. */
@Composable
private fun SideTab(label: String, active: Boolean, onClick: () -> Unit) {
	val accent = MaterialTheme.colorScheme.primary
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.drawWithContent {
				drawContent()
				if (active) drawRect(accent, topLeft = Offset.Zero, size = Size(2.dp.toPx(), size.height))
			}
			.clickable(role = Role.Tab, onClick = onClick)
			.semantics { selected = active }
			.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyLarge,
			color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

private val SideNavWidth = 180.dp
private val SideNavGap = 48.dp
