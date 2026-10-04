package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.scene_list_outline_overview_chapter_eyebrow
import com.darkrockstudios.apps.hammer.scene_list_outline_overview_jump_to
import com.darkrockstudios.apps.hammer.scene_list_outline_overview_position
import com.darkrockstudios.apps.hammer.scene_list_outline_overview_scene_count

/** One chapter as listed by [HdChapterRailItem] and [HdChapterDropdown]. */
@Immutable
data class HdChapterRow(val title: String, val sceneCount: Int)

/**
 * A chapter entry for a jump list: roman numeral, title and scene count,
 * with a primary-colored edge bar when [selected].
 */
@Composable
fun HdChapterRailItem(
	index: Int,
	row: HdChapterRow,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val accent = MaterialTheme.colorScheme.primary
	val rowBg = if (selected) {
		MaterialTheme.colorScheme.surfaceContainer
	} else {
		MaterialTheme.colorScheme.surfaceContainerLow
	}

	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(IntrinsicSize.Min)
			.background(rowBg)
			.clickable(onClick = onClick),
		verticalAlignment = Alignment.Top,
	) {
		Box(
			modifier = Modifier
				.width(2.dp)
				.fillMaxHeight()
				.background(if (selected) accent else Color.Transparent),
		)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					start = Ui.Padding.L,
					end = Ui.Padding.XL,
					top = Ui.Padding.L,
					bottom = Ui.Padding.L,
				),
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			verticalAlignment = Alignment.Top,
		) {
			Text(
				text = romanNumeral(index + 1),
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Light,
				color = if (selected) {
					MaterialTheme.colorScheme.onSurface
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
				modifier = Modifier.widthIn(min = 24.dp),
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = row.title,
					style = MaterialTheme.typography.titleSmall,
					fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				HdMonoLabel(
					text = Res.string.scene_list_outline_overview_scene_count.get(row.sceneCount),
				)
			}
		}
	}
}

/**
 * Collapsed chapter picker for narrow layouts: a full-width bar showing the
 * selected chapter and its position, expanding in place to the chapter list.
 */
@Composable
fun HdChapterDropdown(
	rows: List<HdChapterRow>,
	selectedIndex: Int,
	onSelect: (Int) -> Unit,
	modifier: Modifier = Modifier,
) {
	var expanded by remember { mutableStateOf(false) }
	val rotation by animateFloatAsState(
		targetValue = if (expanded) 180f else 0f,
		label = "chapterDropdownRotation",
	)
	val current = rows.getOrNull(selectedIndex)

	Column(modifier = modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.background(MaterialTheme.colorScheme.surfaceContainerLow)
				.clickable { expanded = !expanded }
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			HdMonoLabel(text = Res.string.scene_list_outline_overview_jump_to.get())
			Row(
				modifier = Modifier.weight(1f),
				verticalAlignment = Alignment.Bottom,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				Text(
					text = romanNumeral(selectedIndex + 1),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Light,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Text(
					text = current?.title.orEmpty(),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			HdMonoLabel(
				text = Res.string.scene_list_outline_overview_position.get(
					selectedIndex + 1,
					rows.size,
				),
			)
			Icon(
				imageVector = Icons.Default.KeyboardArrowDown,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier
					.size(20.dp)
					.rotate(rotation),
			)
		}
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)

		AnimatedVisibility(
			visible = expanded,
			enter = expandVertically() + fadeIn(),
			exit = shrinkVertically() + fadeOut(),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 320.dp)
					.background(MaterialTheme.colorScheme.surface),
			) {
				LazyColumn(modifier = Modifier.fillMaxWidth()) {
					itemsIndexed(rows, key = { idx, _ -> "drop-$idx" }) { idx, row ->
						HdChapterRailItem(
							index = idx,
							row = row,
							selected = idx == selectedIndex,
							onClick = {
								onSelect(idx)
								expanded = false
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

/** Chapter opener: mono eyebrow with numeral and scene count, light title, hairline rule. */
@Composable
fun HdChapterHeader(
	index: Int,
	title: String,
	sceneCount: Int,
	wide: Boolean,
	modifier: Modifier = Modifier,
) {
	val titleStyle = if (wide) {
		MaterialTheme.typography.headlineLarge
	} else {
		MaterialTheme.typography.headlineMedium
	}
	Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		HdMonoLabel(
			text = Res.string.scene_list_outline_overview_chapter_eyebrow.get(
				romanNumeral(index + 1),
				sceneCount,
			),
		)
		Text(
			text = title,
			style = titleStyle,
			fontWeight = FontWeight.Light,
			color = MaterialTheme.colorScheme.onSurface,
		)
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
			modifier = Modifier.padding(top = Ui.Padding.M),
		)
	}
}

/** Centered `· · ·` ornament marking a break between passages. */
@Composable
fun HdSceneBreak(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier.fillMaxWidth(),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = "· · ·",
			style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 8.sp),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

/** Closing mark for a long read: a hairline rule over a centered mono [label]. */
@Composable
fun HdEndMark(label: String, modifier: Modifier = Modifier) {
	Column(
		modifier = modifier.fillMaxWidth().padding(top = Ui.Padding.L),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		HdMonoLabel(text = label)
	}
}
