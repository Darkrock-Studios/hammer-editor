package com.darkrockstudios.apps.hammer.common.preview.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdAttributionItem
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCategoryChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCategorySwatch
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDailyGoalProgress
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDeltaBadge
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEngravingPlaceholder
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineGrid
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdInlineStat
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetadataItem
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMiniBarChart
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNavRail
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNavRailItem
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPlainSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntryCard
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntryFilterBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntryFilterOption
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdStatBlock
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTypeStamp
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTypographicHero
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
private fun MonoLabelPreview() {
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
private fun SectionHeaderPreview() {
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
private fun CategorySwatchAndChipPreview() {
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
private fun DeltaBadgePreview() {
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
private fun EngravingPlaceholderPreview() {
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
private fun MetadataItemPreview() {
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
private fun StatBlockPreview() {
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
private fun DailyGoalProgressPreview() {
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
private fun AttributionBarPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		PreviewSurface {
			HdMiniBarChart(items = sampleAttribution, modifier = Modifier.fillMaxWidth())
		}
	}
}

@Preview
@Composable
private fun HairlineSectionPreview() {
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
private fun HairlineGridPreview() {
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

@Preview
@Composable
private fun NavRailPreview() {
	AppTheme(globalSettingsPreview, useDarkTheme = true) {
		Row(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
			HdNavRail {
				HdNavRailItem(
					selected = true,
					onClick = {},
					icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
					label = "Home",
				)
				HdNavRailItem(
					selected = false,
					onClick = {},
					icon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
					label = "Editor",
				)
				HdNavRailItem(
					selected = false,
					onClick = {},
					icon = { Icon(Icons.Default.Warning, contentDescription = null) },
					label = "Notes",
				)
			}
			Column(modifier = Modifier.padding(24.dp)) {
				HdMonoLabel(
					text = "Selected item adopts project secondaryContainer",
					style = MaterialTheme.typography.labelMedium,
				)
			}
		}
	}
}

@Preview
@Composable
private fun LightThemePreview() {
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
private fun TypeStampPreview() {
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
private fun TypographicHeroPreview() {
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
private fun TagChipPreview() {
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
private fun EntryFilterBarPreview() {
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
private fun EntryCardPreview() {
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
