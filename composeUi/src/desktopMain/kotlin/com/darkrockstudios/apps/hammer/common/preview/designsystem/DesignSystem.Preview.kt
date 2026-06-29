package com.darkrockstudios.apps.hammer.common.preview.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview

private val sampleAttribution
	@Composable get() = LocalHammerColors.current.let { hc ->
		listOf(
			HdAttributionItem("Alice", 15, hc.colorForCharacter(1)),
			HdAttributionItem("The Cheshire Cat", 6, hc.colorForCharacter(2)),
			HdAttributionItem("The Queen of Hearts", 5, hc.colorForCharacter(3)),
			HdAttributionItem("The Mad Hatter", 4, hc.colorForCharacter(4)),
			HdAttributionItem("The White Rabbit", 4, hc.colorForCharacter(5)),
			HdAttributionItem("The Caterpillar", 2, hc.colorForCharacter(6)),
		)
	}

@Preview
@Composable
fun MonoLabelPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdMonoLabel("Total Words")
			HdMonoLabel("scenes · 13 ch")
			HdMonoLabel("Saved · 12:04", style = MaterialTheme.typography.labelMedium)
		}
	}
}

@Preview
@Composable
fun SectionHeaderPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdSectionHeader(
				section = 1,
				title = "Structure",
				modifier = Modifier.fillMaxWidth(),
				trailing = { HdMonoLabel("15 scenes · 13 chapters") },
			)
			HdSectionHeader(
				section = 2,
				title = "Inhabitants",
				modifier = Modifier.fillMaxWidth(),
				trailing = { HdMonoLabel("12 people · 38 connections") },
			)
		}
	}
}

@Preview
@Composable
fun CategorySwatchAndChipPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				EntryType.entries.forEach { type ->
					HdCategorySwatch(type = type, size = 12.dp)
				}
			}
			Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
				EntryType.entries.forEach { HdCategoryChip(it) }
			}
		}
	}
}

@Preview
@Composable
fun DeltaBadgePreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdDeltaBadge(percent = 22f, suffix = "vs last week")
			HdDeltaBadge(percent = -3f, suffix = "vs last week")
			HdDeltaBadge(percent = 0f, suffix = "vs last week")
		}
	}
}

@Preview
@Composable
fun EngravingPlaceholderPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdEngravingPlaceholder(
				label = "Tea Party",
				modifier = Modifier
					.fillMaxWidth()
					.height(120.dp),
			)
		}
	}
}

@Preview
@Composable
fun MetadataItemPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
				HdMetadataItem(label = "Status", value = "Draft")
				HdMetadataItem(label = "POV", value = "3rd · Alice")
				HdMetadataItem(label = "Where", value = "Rabbit-hole")
			}
			Column(modifier = Modifier.fillMaxWidth(0.5f)) {
				HdInlineStat("Today", "847", valueStyle = MaterialTheme.typography.titleSmall)
				HdInlineStat("Daily avg", "590", valueStyle = MaterialTheme.typography.titleSmall)
			}
		}
	}
}

@Preview
@Composable
fun StatBlockPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdStatBlock(
				label = "Total Words",
				value = "23,214",
				subtitle = "≈ 93 min reading · 77 pages",
				modifier = Modifier.fillMaxWidth(),
			)
			HdStatBlock(
				label = "This Week",
				value = "+4,128",
				valueColor = MaterialTheme.colorScheme.primary,
				modifier = Modifier.fillMaxWidth(),
				content = {
					HdDeltaBadge(percent = 22f, suffix = "vs last week")
					HdInlineStat("Today", "847")
					HdInlineStat("Daily avg", "590")
				},
			)
		}
	}
}

@Preview
@Composable
fun DailyGoalProgressPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdDailyGoalProgress(current = 847, goal = 1000, modifier = Modifier.fillMaxWidth())
			HdDailyGoalProgress(current = 1500, goal = 1000, modifier = Modifier.fillMaxWidth())
			HdDailyGoalProgress(current = 0, goal = 1000, modifier = Modifier.fillMaxWidth())
		}
	}
}

@Preview
@Composable
fun AttributionBarPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdMiniBarChart(items = sampleAttribution, modifier = Modifier.fillMaxWidth())
		}
	}
}

@Preview
@Composable
fun HairlineSectionPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdPlainSection {
				Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
					HdStatBlock("Total Words", "23,214", subtitle = "≈ 93 min · 77 pages")
					HdStatBlock("Streak", "11 days", subtitle = "longest 17")
				}
			}
			HdHairlineSection(
				section = 1,
				title = "Structure",
				headerTrailing = { HdMonoLabel("15 scenes · 13 chapters") },
			) {
				Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
					HdStatBlock("Scenes", "15", subtitle = "across 13 chapters")
					HdStatBlock("Avg / Scene", "1,547", subtitle = "median 1,695")
				}
			}
		}
	}
}

@Preview
@Composable
fun HairlineGridPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdHairlineGrid(
				columns = 2,
				cells = listOf<@Composable () -> Unit>(
					{ HdStatBlock("Scenes", "15", subtitle = "across 13 chapters") },
					{ HdStatBlock("Avg / Scene", "1,547", subtitle = "median 1,694") },
					{ HdStatBlock("Notes", "2", valueStyle = MaterialTheme.typography.displayMedium) },
					{ HdStatBlock("Events", "12", valueStyle = MaterialTheme.typography.displayMedium) },
				),
			)
		}
	}
}

private val navRailPreviewDestinations = listOf(
	HdNavRailDestination(id = "home", icon = Icons.Default.Refresh, label = "Home", shortLabel = "HOME"),
	HdNavRailDestination(id = "editor", icon = Icons.Default.MoreVert, label = "Story", shortLabel = "STRY"),
	HdNavRailDestination(id = "notes", icon = Icons.Default.Warning, label = "Notes", shortLabel = "NOTE"),
)

@Preview
@Composable
fun NavRailCollapsedPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		Row(modifier = Modifier.background(MaterialTheme.colorScheme.background).height(500.dp)) {
			HdNavRail(
				destinations = navRailPreviewDestinations,
				selectedId = "home",
				onSelect = {},
				expanded = false,
				onToggleExpanded = {},
				footer = {
					HdMonoLabel(
						text = "v2.2.0",
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				},
			)
			Column(modifier = Modifier.padding(24.dp)) {
				HdMonoLabel(
					text = "Collapsed — sliding indicator on the left edge",
					style = MaterialTheme.typography.labelMedium,
				)
			}
		}
	}
}

@Preview
@Composable
fun NavRailExpandedPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		Row(modifier = Modifier.background(MaterialTheme.colorScheme.background).height(500.dp)) {
			HdNavRail(
				destinations = navRailPreviewDestinations,
				selectedId = "editor",
				onSelect = {},
				expanded = true,
				onToggleExpanded = {},
				footer = {
					HdMonoLabel(
						text = "v2.2.0",
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				},
			)
			Column(modifier = Modifier.padding(24.dp)) {
				HdMonoLabel(
					text = "Expanded — full labels beside icons",
					style = MaterialTheme.typography.labelMedium,
				)
			}
		}
	}
}

@Preview
@Composable
fun LightThemePreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = false) {
		PreviewSurface {
			HdSectionHeader(
				section = 1,
				title = "Structure",
				modifier = Modifier.fillMaxWidth(),
				trailing = { HdMonoLabel("15 scenes · 13 chapters") },
			)
			HdStatBlock("Total Words", "23,214", subtitle = "≈ 93 min reading")
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				EntryType.entries.forEach {
					HdCategorySwatch(it, size = 12.dp)
				}
			}
			HdDeltaBadge(percent = 22f, suffix = "vs last week")
		}
	}
}

@Preview
@Composable
fun TypeStampPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				EntryType.entries.forEach { type ->
					HdTypeStamp(
						type = type,
						label = type.name,
						onClick = {},
					)
				}
			}
		}
	}
}

@Preview
@Composable
fun TypographicHeroPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdTypographicHero(
				name = "The White Rabbit",
				type = EntryType.PERSON,
				modifier = Modifier.fillMaxWidth(),
			)
			HdTypographicHero(
				name = "Pool of Tears",
				type = EntryType.PLACE,
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

@Preview
@Composable
fun TagChipPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				HdTagChip(label = "animal", onClick = {})
				HdTagChip(label = "magical", onClick = {}, active = true)
				HdTagChip(label = "guide", onClick = {})
			}
		}
	}
}

@Preview
@Composable
fun EntryFilterBarPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdEntryFilterBar(
				options = listOf(
					HdEntryFilterOption(type = null, label = "ALL", count = 37),
					HdEntryFilterOption(type = EntryType.PERSON, label = "PEOPLE", count = 12),
					HdEntryFilterOption(type = EntryType.PLACE, label = "PLACES", count = 18),
					HdEntryFilterOption(type = EntryType.THING, label = "THINGS", count = 7),
				),
				selected = EntryType.PLACE,
				onSelect = {},
			)
		}
	}
}

@Preview
@Composable
fun EntryCardPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdEntryCard(
				onClick = {},
				hero = {
					HdTypographicHero(
						name = "The White Rabbit",
						type = EntryType.PERSON,
						modifier = Modifier.fillMaxWidth(),
					)
				},
				stamp = {
					HdTypeStamp(
						type = EntryType.PERSON,
						label = "PERSON",
						onClick = {},
					)
				},
				description = "A nervous, well-dressed rabbit consulting a pocket watch. " +
					"The first transgression of natural law in the book; functions as a " +
					"guide and a panic.",
				meta = "322 W · 3 TAGS",
				tags = listOf("animal", "guide", "panic"),
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

private val themePreviewTags = listOf(
	HdThemeTagPreview("ch1", scene = 4, note = 2, ency = 2, event = 3),
	HdThemeTagPreview("scale", scene = 2, note = 1, ency = 2, event = 2),
	HdThemeTagPreview("magic", scene = 1, note = 0, ency = 4, event = 1),
	HdThemeTagPreview("dream", scene = 3, note = 1, ency = 0, event = 1),
	HdThemeTagPreview("carroll", scene = 0, note = 4, ency = 0, event = 0),
	HdThemeTagPreview("royal", scene = 1, note = 0, ency = 2, event = 1),
	HdThemeTagPreview("guide", scene = 0, note = 0, ency = 3, event = 0),
	HdThemeTagPreview("language", scene = 0, note = 3, ency = 0, event = 0),
	HdThemeTagPreview("panic", scene = 0, note = 0, ency = 2, event = 1),
	HdThemeTagPreview("animal", scene = 0, note = 0, ency = 3, event = 0),
)

private data class HdThemeTagPreview(
	val name: String,
	val scene: Int,
	val note: Int,
	val ency: Int,
	val event: Int,
) {
	val total: Int get() = scene + note + ency + event
	val breadth: Int get() = listOf(scene, note, ency, event).count { it > 0 }
}

@Preview
@Composable
fun ThemesRankedRowPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			val hc = LocalHammerColors.current
			val max = themePreviewTags.maxOf { it.total }
			Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
				themePreviewTags.take(6).forEachIndexed { index, tag ->
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(14.dp),
					) {
						HdMonoLabel(
							text = (index + 1).toString().padStart(2, '0'),
							modifier = Modifier.width(24.dp),
						)
						Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
							androidx.compose.material3.Text(
								"#",
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
							androidx.compose.material3.Text(
								tag.name,
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurface
							)
						}
						HdMonoLabel("${tag.breadth}/4", modifier = Modifier.width(36.dp))
						Box(
							modifier = Modifier
								.weight(2f)
								.height(8.dp)
								.background(MaterialTheme.colorScheme.surfaceVariant),
						) {
							val fraction = tag.total / max.toFloat()
							Row(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight()) {
								if (tag.scene > 0) Box(
									Modifier.weight(tag.scene.toFloat()).fillMaxHeight()
										.background(MaterialTheme.colorScheme.primary)
								)
								if (tag.note > 0) Box(
									Modifier.weight(tag.note.toFloat()).fillMaxHeight().background(hc.thing)
								)
								if (tag.ency > 0) Box(
									Modifier.weight(tag.ency.toFloat()).fillMaxHeight().background(hc.place)
								)
								if (tag.event > 0) Box(
									Modifier.weight(tag.event.toFloat()).fillMaxHeight().background(hc.event)
								)
							}
						}
						androidx.compose.material3.Text(
							tag.total.toString(),
							style = MaterialTheme.typography.titleMedium,
							color = MaterialTheme.colorScheme.onSurface,
							modifier = Modifier.width(40.dp),
						)
					}
				}
			}
		}
	}
}

@Preview
@Composable
fun BarChartPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdBarChart(
				items = listOf(
					HdBarChartItem("Ch 1", 1240),
					HdBarChartItem("Ch 2", 980),
					HdBarChartItem("Ch 3", 1680),
					HdBarChartItem("Ch 4", 540),
					HdBarChartItem("Ch 5", 1420),
					HdBarChartItem("Ch 6", 860),
				),
				modifier = Modifier.fillMaxWidth(),
				tooltipText = { "Chapter ${it.label}: ${it.value} words" },
			)
		}
	}
}

private val bottomBarPreviewDestinations = listOf(
	HdBottomBarDestination(id = "home", icon = Icons.Default.Refresh, label = "Home", shortLabel = "HOME"),
	HdBottomBarDestination(id = "editor", icon = Icons.Default.MoreVert, label = "Story", shortLabel = "STRY"),
	HdBottomBarDestination(id = "notes", icon = Icons.Default.Warning, label = "Notes", shortLabel = "NOTE"),
)

@Preview
@Composable
fun BottomBarPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		HdBottomBar(
			destinations = bottomBarPreviewDestinations,
			selectedId = "home",
			onSelect = {},
		)
	}
}

@Preview
@Composable
fun FabPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdFab(
				onClick = {},
				icon = Icons.Default.Add,
				contentDescription = "Add entry",
			)
		}
	}
}

@Preview
@Composable
fun FolioDividerPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdFolioDivider(modifier = Modifier.fillMaxWidth())
		}
	}
}

@Preview
@Composable
fun HairlineButtonPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				HdHairlineButton(label = "Cancel", onClick = {})
				HdHairlineButton(label = "Save", onClick = {}, emphasised = true)
			}
		}
	}
}

@Preview
@Composable
fun HairlineFieldPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdHairlineField(
				label = "Name",
				value = "The Cheshire Cat",
				onValueChange = {},
				counter = "16/60",
				modifier = Modifier.fillMaxWidth(),
			)
			HdHairlineField(
				label = "Description",
				value = "",
				onValueChange = {},
				placeholder = "A short description…",
				hint = "Markdown",
				singleLine = false,
				minLines = 3,
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

@Preview
@Composable
fun HairlineImageDropPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdHairlineImageDrop(
				label = "Cover Art",
				onClick = {},
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

@Preview
@Composable
fun HairlineTagFieldPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdHairlineTagField(
				label = "Tags",
				tags = listOf("animal", "guide", "magic"),
				onTagsChange = {},
				hint = "↵ to add",
				placeholder = "ritual, trickster…",
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

@Preview
@Composable
fun HairlineTypePickerPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdHairlineTypePicker(
				selected = EntryType.PERSON,
				onSelect = {},
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

@Preview
@Composable
fun ReferenceChipPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdReferenceChip(
				type = EntryType.PERSON,
				name = "Cheshire Cat",
				onClick = {},
				onAction = {},
				actionContentDescription = "Remove",
				variant = HdReferenceChipVariant.Active,
			)
			HdReferenceChip(
				type = EntryType.PLACE,
				name = "Tea Party",
				onClick = {},
				onAction = {},
				actionContentDescription = "Restore",
				variant = HdReferenceChipVariant.Dismissed,
			)
		}
	}
}

@Preview
@Composable
fun ResponsiveStripPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdMonoLabel("Wide — row layout")
			HdResponsiveStrip(isWide = true) {
				HdStatBlock("Total Words", "23,214", modifier = Modifier.cell())
				HdStatBlock("This Week", "+4,128", modifier = Modifier.cell())
				HdStatBlock("Streak", "11 days", modifier = Modifier.cell())
			}
			HdMonoLabel("Narrow — column layout")
			HdResponsiveStrip(isWide = false) {
				HdStatBlock("Total Words", "23,214", modifier = Modifier.cell())
				HdStatBlock("This Week", "+4,128", modifier = Modifier.cell())
			}
		}
	}
}

@Preview
@Composable
fun SearchFieldPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdSearchField(
				value = "",
				onValueChange = {},
				placeholder = "Search by name",
				modifier = Modifier.fillMaxWidth(),
			)
			HdSearchField(
				value = "White Rabbit",
				onValueChange = {},
				onClear = {},
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

@Preview
@Composable
fun UnsavedBadgePreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				HdUnsavedBadge(text = "Unsaved")
				HdUnsavedBadge(text = "Conflict")
				HdUnsavedBadge(text = "Pending")
			}
		}
	}
}

// ── Gallery ──────────────────────────────────────────────────────────────────

@Preview
@Composable
fun DesignSystemGalleryPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		Column(
			modifier = Modifier
				.background(MaterialTheme.colorScheme.background)
				.padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(32.dp),
		) {
			// ── Typography ──────────────────────────────────────────────────
			GallerySection("Typography") {
				HdMonoLabel("Total Words")
				HdMonoLabel("scenes · 13 ch")
				HdMonoLabel("Saved · 12:04", style = MaterialTheme.typography.labelMedium)
				HdSectionHeader(
					section = 1,
					title = "Structure",
					modifier = Modifier.fillMaxWidth(),
					trailing = { HdMonoLabel("15 scenes · 13 chapters") },
				)
				HdSectionHeader(
					section = 2,
					title = "Inhabitants",
					modifier = Modifier.fillMaxWidth(),
					trailing = { HdMonoLabel("12 people · 38 connections") },
				)
				Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
					HdMetadataItem(label = "Status", value = "Draft")
					HdMetadataItem(label = "POV", value = "3rd · Alice")
					HdMetadataItem(label = "Where", value = "Rabbit-hole")
				}
				Column(modifier = Modifier.fillMaxWidth(0.5f)) {
					HdInlineStat("Today", "847", valueStyle = MaterialTheme.typography.titleSmall)
					HdInlineStat("Daily avg", "590", valueStyle = MaterialTheme.typography.titleSmall)
				}
			}

			// ── Stats ───────────────────────────────────────────────────────
			GallerySection("Stats") {
				HdStatBlock(
					label = "Total Words",
					value = "23,214",
					subtitle = "≈ 93 min reading · 77 pages",
					modifier = Modifier.fillMaxWidth(),
				)
				HdStatBlock(
					label = "This Week",
					value = "+4,128",
					valueColor = MaterialTheme.colorScheme.primary,
					modifier = Modifier.fillMaxWidth(),
					content = {
						HdDeltaBadge(percent = 22f, suffix = "vs last week")
						HdInlineStat("Today", "847")
						HdInlineStat("Daily avg", "590")
					},
				)
				HdDeltaBadge(percent = 22f, suffix = "vs last week")
				HdDeltaBadge(percent = -3f, suffix = "vs last week")
				HdDeltaBadge(percent = 0f, suffix = "vs last week")
				HdDailyGoalProgress(current = 847, goal = 1000, modifier = Modifier.fillMaxWidth())
				HdDailyGoalProgress(current = 1500, goal = 1000, modifier = Modifier.fillMaxWidth())
			}

			// ── Charts ──────────────────────────────────────────────────────
			GallerySection("Charts") {
				HdBarChart(
					items = listOf(
						HdBarChartItem("Ch 1", 1240),
						HdBarChartItem("Ch 2", 980),
						HdBarChartItem("Ch 3", 1680),
						HdBarChartItem("Ch 4", 540),
						HdBarChartItem("Ch 5", 1420),
						HdBarChartItem("Ch 6", 860),
					),
					modifier = Modifier.fillMaxWidth(),
				)
				HdMiniBarChart(items = sampleAttribution, modifier = Modifier.fillMaxWidth())
			}

			// ── Layout ──────────────────────────────────────────────────────
			GallerySection("Layout") {
				HdFolioDivider(modifier = Modifier.fillMaxWidth())
				HdPlainSection {
					Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
						HdStatBlock("Total Words", "23,214", subtitle = "≈ 93 min · 77 pages")
						HdStatBlock("Streak", "11 days", subtitle = "longest 17")
					}
				}
				HdHairlineSection(
					section = 1,
					title = "Structure",
					headerTrailing = { HdMonoLabel("15 scenes · 13 chapters") },
				) {
					Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
						HdStatBlock("Scenes", "15", subtitle = "across 13 chapters")
						HdStatBlock("Avg / Scene", "1,547", subtitle = "median 1,695")
					}
				}
				HdHairlineGrid(
					columns = 2,
					cells = listOf<@Composable () -> Unit>(
						{ HdStatBlock("Scenes", "15", subtitle = "across 13 chapters") },
						{ HdStatBlock("Avg / Scene", "1,547", subtitle = "median 1,694") },
						{ HdStatBlock("Notes", "2", valueStyle = MaterialTheme.typography.displayMedium) },
						{ HdStatBlock("Events", "12", valueStyle = MaterialTheme.typography.displayMedium) },
					),
				)
				HdMonoLabel("Wide — row layout")
				HdResponsiveStrip(isWide = true) {
					HdStatBlock("Total Words", "23,214", modifier = Modifier.cell())
					HdStatBlock("This Week", "+4,128", modifier = Modifier.cell())
					HdStatBlock("Streak", "11 days", modifier = Modifier.cell())
				}
				HdMonoLabel("Narrow — column layout")
				HdResponsiveStrip(isWide = false) {
					HdStatBlock("Total Words", "23,214", modifier = Modifier.cell())
					HdStatBlock("This Week", "+4,128", modifier = Modifier.cell())
				}
			}

			// ── Controls ────────────────────────────────────────────────────
			GallerySection("Controls") {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					HdHairlineButton(label = "Cancel", onClick = {})
					HdHairlineButton(label = "Save", onClick = {}, emphasised = true)
				}
				HdFab(onClick = {}, icon = Icons.Default.Add, contentDescription = "Add entry")
				HdSearchField(
					value = "",
					onValueChange = {},
					placeholder = "Search by name",
					modifier = Modifier.fillMaxWidth(),
				)
				HdSearchField(
					value = "White Rabbit",
					onValueChange = {},
					onClear = {},
					modifier = Modifier.fillMaxWidth(),
				)
			}

			// ── Forms ───────────────────────────────────────────────────────
			GallerySection("Forms") {
				HdHairlineField(
					label = "Name",
					value = "The Cheshire Cat",
					onValueChange = {},
					counter = "16/60",
					modifier = Modifier.fillMaxWidth(),
				)
				HdHairlineField(
					label = "Description",
					value = "",
					onValueChange = {},
					placeholder = "A short description…",
					hint = "Markdown",
					singleLine = false,
					minLines = 3,
					modifier = Modifier.fillMaxWidth(),
				)
				HdHairlineTagField(
					label = "Tags",
					tags = listOf("animal", "guide", "magic"),
					onTagsChange = {},
					hint = "↵ to add",
					modifier = Modifier.fillMaxWidth(),
				)
				HdHairlineTypePicker(
					selected = EntryType.PERSON,
					onSelect = {},
					modifier = Modifier.fillMaxWidth(),
				)
				HdHairlineImageDrop(
					label = "Cover Art",
					onClick = {},
					modifier = Modifier.fillMaxWidth(),
				)
			}

			// ── Chips & Badges ──────────────────────────────────────────────
			GallerySection("Chips & Badges") {
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					EntryType.entries.forEach { type ->
						HdCategorySwatch(type = type, size = 12.dp)
					}
				}
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					EntryType.entries.forEach { HdCategoryChip(it) }
				}
				Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					HdTagChip(label = "animal", onClick = {})
					HdTagChip(label = "magical", onClick = {}, active = true)
					HdTagChip(label = "guide", onClick = {})
				}
				HdReferenceChip(
					type = EntryType.PERSON,
					name = "Cheshire Cat",
					onClick = {},
					onAction = {},
					actionContentDescription = "Remove",
					variant = HdReferenceChipVariant.Active,
				)
				HdReferenceChip(
					type = EntryType.PLACE,
					name = "Tea Party",
					onClick = {},
					onAction = {},
					actionContentDescription = "Restore",
					variant = HdReferenceChipVariant.Dismissed,
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					HdUnsavedBadge(text = "Unsaved")
					HdUnsavedBadge(text = "Conflict")
					HdUnsavedBadge(text = "Pending")
				}
			}

			// ── Entry Cards ─────────────────────────────────────────────────
			GallerySection("Entry Cards") {
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					EntryType.entries.forEach { type ->
						HdTypeStamp(type = type, label = type.name, onClick = {})
					}
				}
				HdTypographicHero(
					name = "The White Rabbit",
					type = EntryType.PERSON,
					modifier = Modifier.fillMaxWidth(),
				)
				HdTypographicHero(
					name = "Pool of Tears",
					type = EntryType.PLACE,
					modifier = Modifier.fillMaxWidth(),
				)
				HdEngravingPlaceholder(
					label = "Tea Party",
					modifier = Modifier.fillMaxWidth().height(120.dp),
				)
				HdEntryFilterBar(
					options = listOf(
						HdEntryFilterOption(type = null, label = "ALL", count = 37),
						HdEntryFilterOption(type = EntryType.PERSON, label = "PEOPLE", count = 12),
						HdEntryFilterOption(type = EntryType.PLACE, label = "PLACES", count = 18),
						HdEntryFilterOption(type = EntryType.THING, label = "THINGS", count = 7),
					),
					selected = EntryType.PLACE,
					onSelect = {},
				)
				HdEntryCard(
					onClick = {},
					hero = {
						HdTypographicHero(
							name = "The White Rabbit",
							type = EntryType.PERSON,
							modifier = Modifier.fillMaxWidth(),
						)
					},
					stamp = {
						HdTypeStamp(type = EntryType.PERSON, label = "PERSON", onClick = {})
					},
					description = "A nervous, well-dressed rabbit consulting a pocket watch. " +
						"Functions as a guide and a panic.",
					meta = "322 W · 3 TAGS",
					tags = listOf("animal", "guide", "panic"),
					modifier = Modifier.fillMaxWidth(),
				)
			}

			// ── Navigation ──────────────────────────────────────────────────
			GallerySection("Navigation") {
				Row(modifier = Modifier.fillMaxWidth().height(400.dp)) {
					HdNavRail(
						destinations = navRailPreviewDestinations,
						selectedId = "home",
						onSelect = {},
						expanded = false,
						onToggleExpanded = {},
					)
					HdNavRail(
						destinations = navRailPreviewDestinations,
						selectedId = "home",
						onSelect = {},
						expanded = true,
						onToggleExpanded = {},
					)
				}
				HdBottomBar(
					destinations = bottomBarPreviewDestinations,
					selectedId = "home",
					onSelect = {},
				)
			}
		}
	}
}

@Composable
private fun GallerySection(title: String, content: @Composable () -> Unit) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		HdFolioDivider(modifier = Modifier.fillMaxWidth())
		HdMonoLabel(
			text = title.uppercase(),
			style = MaterialTheme.typography.labelMedium,
		)
		content()
	}
}

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
	Column(
		modifier = Modifier
			.background(MaterialTheme.colorScheme.background)
			.padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		content()
	}
}
