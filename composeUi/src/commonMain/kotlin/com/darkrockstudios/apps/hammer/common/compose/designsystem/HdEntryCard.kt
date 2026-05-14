package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hairline-bordered, square-cornered entry card. Composes:
 *  1. A 200dp [hero] zone (image or [HdTypographicHero])
 *  2. The [stamp] postage tag overlaying the top-left of the hero
 *  3. An optional [title] (only when the hero is an image — typographic
 *     heroes carry the name themselves)
 *  4. A [description] body
 *  5. An optional row of [tags]
 *  6. A hairline footer with [meta] on the left and an [openAffordance]
 *     on the right
 *
 * The card itself is the click target — pass [onClick] to open the
 * entry. Stamp and tag affordances stop propagation so they only fire
 * the filter, not the open action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdEntryCard(
	onClick: () -> Unit,
	hero: @Composable () -> Unit,
	stamp: @Composable () -> Unit,
	description: String,
	meta: String,
	modifier: Modifier = Modifier,
	title: String? = null,
	tags: List<String> = emptyList(),
	activeTag: String? = null,
	onTagClick: (String) -> Unit = {},
	tagsScrollHorizontally: Boolean = false,
	openAffordance: String = "↗ OPEN",
) {
	val ruleColor = MaterialTheme.colorScheme.outlineVariant
	val ruleSoft = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

	Column(
		modifier = modifier
			.fillMaxWidth()
			.clip(RectangleShape)
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.border(width = Dp.Hairline, color = ruleColor, shape = RectangleShape)
			.clickable(onClick = onClick),
	) {
		// Hero with stamp overlay.
		Box(modifier = Modifier.fillMaxWidth()) {
			hero()
			Box(
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(10.dp),
			) {
				stamp()
			}
		}

		HorizontalDivider(thickness = Dp.Hairline, color = ruleSoft)

		// Title (only when image hero).
		if (title != null) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Normal,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(
					start = 16.dp,
					end = 16.dp,
					top = 14.dp,
					bottom = 6.dp,
				),
			)
		}

		// Description body.
		Text(
			text = description,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = if (title == null) 5 else 4,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(
				start = 16.dp,
				end = 16.dp,
				top = if (title == null) 14.dp else 0.dp,
				bottom = 12.dp,
			),
		)

		// Tag chips — wrapped row by default, single horizontally
		// scrolling row on narrow / mobile.
		if (tags.isNotEmpty()) {
			val tagPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp)
			if (tagsScrollHorizontally) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.horizontalScroll(rememberScrollState())
						.padding(tagPadding),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					tags.forEach { tag ->
						HdTagChip(
							label = tag,
							active = tag == activeTag,
							onClick = { onTagClick(tag) },
						)
					}
				}
			} else {
				HdTagFlowRow(
					tags = tags,
					activeTag = activeTag,
					onTagClick = onTagClick,
					modifier = Modifier.padding(tagPadding),
				)
			}
		}

		HorizontalDivider(thickness = Dp.Hairline, color = ruleSoft)

		// Footer: scenes / refs / words on the left, OPEN affordance on
		// the right. Both mono labels for editorial feel.
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			HdMonoLabel(
				text = meta,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			HdMonoLabel(
				text = openAffordance,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HdTagFlowRow(
	tags: List<String>,
	activeTag: String?,
	onTagClick: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	FlowRow(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		tags.forEach { tag ->
			HdTagChip(
				label = tag,
				active = tag == activeTag,
				onClick = { onTagClick(tag) },
			)
		}
	}
}
