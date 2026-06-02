package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.parseHexColor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.util.format
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator
import com.darkrockstudios.apps.hammer.project_select_card_delete_button
import com.darkrockstudios.apps.hammer.project_select_card_rename_button
import com.darkrockstudios.apps.hammer.projects_list_card_by_author
import com.darkrockstudios.apps.hammer.projects_list_card_content_description
import com.darkrockstudios.apps.hammer.projects_list_card_created
import com.darkrockstudios.apps.hammer.projects_list_card_words
import com.darkrockstudios.apps.hammer.projects_list_item_more_button
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

val ProjectCardTestTag = "project-card"

private val StripeWidth: Dp = 4.dp
private val NumberColumnWidth: Dp = 56.dp
private val CompactNumberColumnWidth: Dp = 44.dp

/**
 * Editorial-index row for a single project. Reflows between Wide and Compact:
 *
 * Wide:
 *     │ 01   Alice In Wonderland          29 JAN ’23   ⋮
 *     │      by Lewis Carroll · Created 29 JAN ’23
 *
 * Compact (drops the trailing mono cell, lays the dates + word count out as
 * an aligned label/value greeble grid instead of one flowing mono line):
 *     │ 01  Alice In Wonderland           ⋮
 *     │     by Lewis Carroll
 *     │     CREATED      OPENED      WORDS
 *     │     29 JAN ’23   04 MAR ’24  26,351
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectIndexRow(
	isWide: Boolean,
	index: Int,
	projectData: ProjectData,
	onProjectClick: (projectDef: ProjectDef) -> Unit,
	onProjectAltClick: (projectDef: ProjectDef) -> Unit,
	onProjectRenameClick: (projectDef: ProjectDef) -> Unit,
	modifier: Modifier = Modifier,
) {
	val storedData = projectData.storedData
	val author = storedData.authorName?.takeIf { it.isNotBlank() }
	val themePrimary = remember(storedData.theme?.primary) {
		storedData.theme?.primary?.let { parseHexColor(it) }
	}
	val unsetStripeColor = MaterialTheme.colorScheme.outlineVariant

	val createdDate = remember(projectData.metadata.info) {
		formatStamp(projectData.metadata.info.created)
	}
	val lastAccessedDate = remember(projectData.metadata.info) {
		projectData.metadata.info.lastAccessed?.let { formatStamp(it) }
	}
	val wordsLabel = projectData.totalWords?.let {
		Res.string.projects_list_card_words.get(it.formatDecimalSeparator())
	}
	val wordsValue = projectData.totalWords?.formatDecimalSeparator()
	val byAuthor = author?.let { Res.string.projects_list_card_by_author.get(it) }
	val createdText = Res.string.projects_list_card_created.get(createdDate)
	val cardContentDescription =
		Res.string.projects_list_card_content_description.get(projectData.definition.name)
	val rowNumber = remember(index) { (index + 1).toString().padStart(2, '0') }
	// Wide keeps the single italic subline; Compact reflows the same facts into
	// the aligned greeble grid below, so the subline is wide-only now.
	val subline = remember(byAuthor, createdText) {
		listOfNotNull(byAuthor, createdText).joinToString(" · ")
	}

	Row(
		modifier = modifier
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
			unsetColor = unsetStripeColor,
		)

		Text(
			text = rowNumber,
			modifier = Modifier
				.width(if (isWide) NumberColumnWidth else CompactNumberColumnWidth)
				.padding(start = Ui.Padding.XL),
			style = if (isWide) {
				MaterialTheme.typography.headlineMedium
			} else {
				MaterialTheme.typography.titleLarge
			},
			fontWeight = FontWeight.Light,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		Column(
			modifier = Modifier
				.weight(1f)
				.padding(vertical = Ui.Padding.XL),
			verticalArrangement = Arrangement.spacedBy(2.dp),
		) {
			Text(
				text = projectData.definition.name,
				style = if (isWide) {
					MaterialTheme.typography.headlineSmall
				} else {
					MaterialTheme.typography.titleMedium
				},
				color = MaterialTheme.colorScheme.onSurface,
			)
			if (isWide) {
				Text(
					text = subline,
					style = MaterialTheme.typography.bodyMedium,
					fontStyle = FontStyle.Italic,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			} else {
				if (byAuthor != null) {
					Text(
						text = byAuthor,
						style = MaterialTheme.typography.bodyMedium,
						fontStyle = FontStyle.Italic,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				Spacer(modifier = Modifier.height(8.dp))
				CompactMetaGrid(
					createdValue = createdDate,
					openedValue = lastAccessedDate,
					wordsValue = wordsValue,
				)
			}
		}

		if (isWide) {
			Column(
				modifier = Modifier.padding(horizontal = Ui.Padding.L),
				horizontalAlignment = Alignment.End,
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				HdMonoLabel(
					text = lastAccessedDate ?: "—",
					color = if (lastAccessedDate != null) {
						MaterialTheme.colorScheme.onSurfaceVariant
					} else {
						MaterialTheme.colorScheme.outline
					},
				)
				if (wordsLabel != null) {
					HdMonoLabel(
						text = wordsLabel,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}

		ProjectOptionsMenu(
			items = listOf(
				Res.string.project_select_card_delete_button,
				Res.string.project_select_card_rename_button,
			),
		) {
			when (it) {
				Res.string.project_select_card_delete_button ->
					onProjectAltClick(projectData.definition)

				Res.string.project_select_card_rename_button ->
					onProjectRenameClick(projectData.definition)
			}
		}
	}

	HorizontalDivider(
		modifier = Modifier.fillMaxWidth(),
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
}

/**
 * Compact label-over-value greeble grid. Equal-weight columns keep the dates
 * and word count aligned down the whole list instead of flowing and wrapping
 * at arbitrary points. The labels are faint captions; values stay at the same
 * muted weight as the rest of the row so the project title is the only bright
 * anchor per row.
 *
 *     CREATED      OPENED      WORDS
 *     29 JAN ’23   04 MAR ’24  26,351
 */
@Composable
private fun CompactMetaGrid(
	createdValue: String,
	openedValue: String?,
	wordsValue: String?,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		MetaColumn(
			label = "Created",
			value = createdValue,
			modifier = Modifier.weight(1f),
		)
		MetaColumn(
			label = "Opened",
			value = openedValue ?: "—",
			muted = openedValue == null,
			modifier = Modifier.weight(1f),
		)
		if (wordsValue != null) {
			MetaColumn(
				label = "Words",
				value = wordsValue,
				modifier = Modifier.weight(1f),
			)
		}
	}
}

@Composable
private fun MetaColumn(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
	muted: Boolean = false,
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(3.dp),
	) {
		HdMonoLabel(
			text = label,
			color = MaterialTheme.colorScheme.outline,
			maxLines = 1,
			softWrap = false,
		)
		HdMonoLabel(
			text = value,
			style = MaterialTheme.typography.labelMedium,
			color = if (muted) {
				MaterialTheme.colorScheme.outline
			} else {
				MaterialTheme.colorScheme.onSurfaceVariant
			},
			maxLines = 1,
			softWrap = false,
		)
	}
}

@Composable
private fun ProjectStripe(themePrimary: Color?, unsetColor: Color) {
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
					} else {
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

private fun formatStamp(instant: Instant): String =
	instant.toLocalDateTime(TimeZone.currentSystemDefault())
		.format("dd MMM `yy")
		.uppercase()

@Composable
fun ProjectOptionsMenu(
	items: List<StringResource>,
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

		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			items.forEach { item ->
				DropdownMenuItem(
					modifier = Modifier.testTag(item.get()),
					text = { Text(text = item.get()) },
					onClick = {
						onItemClick(item)
						expanded = false
					}
				)
			}
		}
	}
}
