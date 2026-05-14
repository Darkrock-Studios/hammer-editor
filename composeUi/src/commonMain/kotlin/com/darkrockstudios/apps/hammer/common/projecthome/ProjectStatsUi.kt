package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.components.projecthome.TagBreakdown
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.WritingActivityDerived
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.estimatePages
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.estimateReadingMinutes
import com.darkrockstudios.apps.hammer.common.data.tagindex.TaggedEntityType
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator
import io.github.koalaplot.core.pie.BezierLabelConnector
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.style.KoalaPlotTheme
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.generateHueColorPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random
import kotlin.time.Clock

@Composable
fun ProjectStatsUi(
	modifier: Modifier,
	component: ProjectHome,
	scope: CoroutineScope,
) {
	val state by component.state.subscribeAsState()
	val isWide = LocalScreenCharacteristic.current.isWide

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 24.dp, vertical = 16.dp),
		verticalArrangement = Arrangement.spacedBy(20.dp),
	) {
		DashboardHeader(
			state = state,
			component = component,
			scope = scope,
		)

		if (state.isLoadingStats) {
			LoadingRow()
		}

		StatsStrip(state = state, isWide = isWide)

		StructureSection(state = state, isWide = isWide)

		if (state.dailyWordTotals.isNotEmpty() || state.encyclopediaEntriesByType.isNotEmpty() || state.topAppearances.isNotEmpty()) {
			InhabitantsSection(state = state, isWide = isWide)
		}

		if (state.tagBreakdowns.isNotEmpty()) {
			ThemesSection(state = state, isWide = isWide)
		}

		if (state.wordsPerDevice.size >= 2) {
			DevicesSection(state = state)
		}
	}

	ExportOptionsDialog(
		visible = state.showExportDialog,
		initialOptions = state.exportOptions,
		onCancel = component::cancelExportDialog,
		onConfirm = component::confirmExportDialog,
	)
	ExportDirectoryPicker(state.showExportFilePicker, component, scope)

	ImportStoryDialog(
		visible = state.showImportDialog,
		options = state.importOptions,
		preview = state.importPreview,
		onCancel = component::cancelImportDialog,
		onOptionsChange = component::updateImportOptions,
		onConfirm = { scope.launch { component.confirmImportDialog() } },
	)
	ImportFilePicker(state.showImportFilePicker, component, scope)
}

@Composable
private fun DashboardHeader(
	state: ProjectHome.State,
	component: ProjectHome,
	scope: CoroutineScope,
) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			HeaderUi(
				state.projectDef.name,
				"🏡",
				modifier = Modifier.weight(1f),
			)
			Row(verticalAlignment = Alignment.CenterVertically) {
				if (state.isStatsDirty) {
					Icon(
						Icons.Default.Warning,
						contentDescription = stringResource(Res.string.project_home_stats_dirty_indicator),
						tint = MaterialTheme.colorScheme.tertiary,
						modifier = Modifier.size(18.dp),
					)
					Spacer(Modifier.width(4.dp))
				}
				IconButton(
					onClick = { component.refreshStatistics() },
					enabled = !state.isLoadingStats,
				) {
					Icon(
						Icons.Default.Refresh,
						contentDescription = stringResource(Res.string.project_home_refresh_stats_button),
						tint = if (state.isLoadingStats) {
							MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
						} else {
							MaterialTheme.colorScheme.primary
						},
					)
				}
				IconButton(onClick = { component.showProjectSettings() }) {
					Icon(
						Icons.Default.Settings,
						contentDescription = stringResource(Res.string.project_home_action_settings_button),
						tint = MaterialTheme.colorScheme.primary,
					)
				}
				ProjectHomeMenu(component = component, hasServer = state.hasServer)
			}
		}
		HdMonoLabel(
			text = stringResource(Res.string.project_home_stat_created, state.created),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun LoadingRow() {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
		horizontalArrangement = Arrangement.Center,
		verticalAlignment = Alignment.CenterVertically,
	) {
		CircularProgressIndicator(
			modifier = Modifier.size(20.dp),
			strokeWidth = 2.dp,
			color = MaterialTheme.colorScheme.primary,
		)
		Spacer(Modifier.width(12.dp))
		Text(
			stringResource(Res.string.project_home_loading_stats),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun StatsStrip(state: ProjectHome.State, isWide: Boolean) {
	HdPlainSection {
		if (isWide) {
			HdResponsiveStrip(isWide = true) {
				TotalWordsBlock(state, modifier = Modifier.cell())
				ThisWeekBlock(state.writingActivity, modifier = Modifier.cell())
				StreakBlock(state.writingActivity, modifier = Modifier.cell())
			}
		} else {
			TotalWordsBlock(state, modifier = Modifier.fillMaxWidth())
			HdHairlineGrid(
				columns = 2,
				cells = listOf<@Composable () -> Unit>(
					{ ThisWeekBlock(state.writingActivity) },
					{ StreakBlock(state.writingActivity) },
				),
			)
		}
	}
}

@Composable
private fun TotalWordsBlock(state: ProjectHome.State, modifier: Modifier = Modifier) {
	HdStatBlock(
		label = stringResource(Res.string.project_home_stat_total_words),
		value = state.totalWords.formatDecimalSeparator(),
		subtitle = if (state.totalWords > 0) {
			stringResource(Res.string.project_home_stat_reading_time, estimateReadingMinutes(state.totalWords)) +
				" · " + stringResource(Res.string.project_home_stat_pages, estimatePages(state.totalWords))
		} else null,
		modifier = modifier,
	) {
		val goal = state.wordCountGoal
		if (goal != null) {
			Spacer(Modifier.height(4.dp))
			HdDailyGoalProgress(
				current = state.writingActivity.wordsToday,
				goal = goal.count,
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

@Composable
private fun ThisWeekBlock(activity: WritingActivityDerived, modifier: Modifier = Modifier) {
	HdStatBlock(
		label = stringResource(Res.string.project_home_stat_this_week),
		value = "+${activity.wordsThisWeek.formatDecimalSeparator()}",
		valueColor = MaterialTheme.colorScheme.primary,
		modifier = modifier,
	) {
		val pct = activity.weekChangePercent
		when {
			pct == null -> HdMonoLabel(
				text = stringResource(Res.string.project_home_stat_week_change_new),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			pct == 0 -> HdMonoLabel(
				text = stringResource(Res.string.project_home_stat_week_change_flat),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			else -> HdDeltaBadge(percent = pct.toFloat(), suffix = "vs last week")
		}
		Spacer(Modifier.height(4.dp))
		HdInlineStat(
			label = stringResource(Res.string.project_home_stat_today),
			value = activity.wordsToday.formatDecimalSeparator(),
		)
		HdInlineStat(
			label = stringResource(Res.string.project_home_stat_daily_avg),
			value = activity.dailyAverageThisWeek.formatDecimalSeparator(),
		)
	}
}

@Composable
private fun StreakBlock(activity: WritingActivityDerived, modifier: Modifier = Modifier) {
	HdStatBlock(
		label = stringResource(Res.string.project_home_stat_streak),
		value = stringResource(Res.string.project_home_stat_streak_days, activity.currentStreak),
		subtitle = stringResource(Res.string.project_home_stat_longest_streak, activity.longestStreak),
		modifier = modifier,
	) {
		Spacer(Modifier.height(4.dp))
		HdInlineStat(
			label = stringResource(Res.string.project_home_stat_days_written),
			value = activity.daysWritten.formatDecimalSeparator(),
		)
		val best = activity.bestDayInStreak
		if (best != null) {
			HdInlineStat(
				label = stringResource(Res.string.project_home_stat_best_day),
				value = stringResource(
					Res.string.project_home_stat_best_day_value,
					best.date.toString(),
					best.words.formatDecimalSeparator(),
				),
			)
		}
	}
}

private data class ChapterStats(
	val items: List<HdBarChartItem>,
	val min: Int,
	val max: Int,
)

@Composable
private fun StructureSection(state: ProjectHome.State, isWide: Boolean) {
	val sceneCount = state.numberOfScenes
	val chapterCount = state.wordsByChapter.size
	HdHairlineSection(
		section = 1,
		title = "Structure",
		headerTrailing = {
			HdMonoLabel(
				text = "$sceneCount scenes · $chapterCount chapters",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		},
	) {
		val scenes: @Composable () -> Unit = {
			HdStatBlock(
				label = stringResource(Res.string.project_home_stat_num_scenes),
				value = state.numberOfScenes.formatDecimalSeparator(),
				subtitle = "across $chapterCount chapters",
			)
		}
		val avgPerScene: @Composable () -> Unit = {
			HdStatBlock(
				label = stringResource(Res.string.project_home_stat_avg_words_per_scene),
				value = state.averageWordsPerScene.formatDecimalSeparator(),
				subtitle = if (state.medianSceneWords > 0)
					"${stringResource(Res.string.project_home_stat_scene_median).lowercase()} ${state.medianSceneWords.formatDecimalSeparator()}"
				else null,
			)
		}
		val longestScene: @Composable (Modifier) -> Unit = { mod ->
			val longestName = state.longestSceneName
			HdStatBlock(
				label = stringResource(Res.string.project_home_stat_longest_scene),
				value = longestName ?: stringResource(Res.string.project_home_stat_longest_scene_empty),
				valueStyle = MaterialTheme.typography.headlineMedium,
				valueMaxLines = 2,
				subtitle = if (state.longestSceneWords > 0)
					stringResource(Res.string.project_home_stat_longest_scene_words, state.longestSceneWords.formatDecimalSeparator())
				else null,
				modifier = mod,
			)
		}
		val notes: @Composable () -> Unit = {
			HdStatBlock(
				label = stringResource(Res.string.project_home_stat_num_notes),
				value = state.numberOfNotes.formatDecimalSeparator(),
				valueStyle = MaterialTheme.typography.displayMedium,
			)
		}
		val events: @Composable () -> Unit = {
			HdStatBlock(
				label = stringResource(Res.string.project_home_stat_num_timeline_events),
				value = state.numberOfTimelineEvents.formatDecimalSeparator(),
				valueStyle = MaterialTheme.typography.displayMedium,
				valueColor = MaterialTheme.colorScheme.primary,
			)
		}

		// On wide layouts, Scenes and Avg/Scene need horizontal room for
		// big display values like "1,548", while Notes and Timeline have
		// 1-2 digit values that look lonely in their own column. So Notes
		// and Timeline share the third column, stacked vertically — they
		// add up to roughly the same visual weight as a single tall tile.
		// Longest scene gets its own full-width row below since its value
		// is a name, not a number.
		if (isWide) {
			HdHairlineGrid(
				columns = 3,
				cells = listOf<@Composable () -> Unit>(
					scenes,
					avgPerScene,
					{
						Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
							notes()
							events()
						}
					},
				),
			)
		} else {
			HdHairlineGrid(
				columns = 2,
				cells = listOf<@Composable () -> Unit>(scenes, avgPerScene, notes, events),
			)
		}
		longestScene(Modifier.fillMaxWidth())

		if (state.wordsByChapter.isNotEmpty()) {
			val chapterStats = remember(state.wordsByChapter) {
				val items = state.wordsByChapter.entries.mapIndexed { index, entry ->
					HdBarChartItem(label = (index + 1).toString(), value = entry.value)
				}
				val values = state.wordsByChapter.values
				ChapterStats(
					items = items,
					min = values.min(),
					max = values.max(),
				)
			}
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				HdSectionHeader(
					marker = "—",
					title = stringResource(Res.string.project_home_stat_chapter_words),
					modifier = Modifier.fillMaxWidth(),
					trailing = {
						HdMonoLabel(
							text = stringResource(
								Res.string.project_home_stat_chapter_words_summary,
								state.sceneWordsStdDev,
								chapterStats.min,
								chapterStats.max,
							),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					},
				)
				HdBarChart(
					items = chapterStats.items,
					modifier = Modifier.fillMaxWidth(),
					height = 140.dp,
				)
			}
		}
	}
}

@Composable
private fun InhabitantsSection(state: ProjectHome.State, isWide: Boolean) {
	val typeCounts = state.encyclopediaEntriesByType
	val totalEntries = remember(typeCounts) { typeCounts.values.sum() }
	val headerSummary = remember(typeCounts, totalEntries) {
		buildList {
			if (totalEntries > 0) add("$totalEntries entries")
			typeCounts[EntryType.PLACE]?.takeIf { it > 0 }?.let { add("$it places") }
			typeCounts[EntryType.PERSON]?.takeIf { it > 0 }?.let { add("$it people") }
			typeCounts[EntryType.THING]?.takeIf { it > 0 }?.let { add("$it things") }
		}.joinToString(" · ").takeIf { it.isNotEmpty() }
	}
	val hammerColors = LocalHammerColors.current
	val attributions = remember(state.topAppearances, hammerColors) {
		state.topAppearances.map { entry ->
			HdAttributionItem(
				label = entry.name,
				value = entry.sceneCount,
				color = hammerColors.colorForCharacter(entry.entryId),
			)
		}
	}

	HdHairlineSection(
		section = 2,
		title = "Inhabitants",
		headerTrailing = {
			if (headerSummary != null) {
				HdMonoLabel(
					text = headerSummary,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		},
	) {
		HdResponsiveStrip(isWide = isWide) {
			Column(modifier = Modifier.cell(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				HdMonoLabel(
					text = stringResource(Res.string.project_home_stat_characters_appearances),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				if (attributions.isNotEmpty()) {
					HdMiniBarChart(items = attributions, modifier = Modifier.fillMaxWidth())
				} else {
					Spacer(Modifier.height(48.dp))
				}
			}
			Column(modifier = Modifier.cell(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				HdMonoLabel(
					text = stringResource(Res.string.project_home_stat_encyclopedia_entries),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				EncyclopediaDonut(
					typeCounts = typeCounts,
					totalEntries = totalEntries,
					modifier = Modifier.fillMaxWidth().height(220.dp),
				)
				if (state.totalEntryConnections > 0) {
					HdMonoLabel(
						text = stringResource(Res.string.project_home_stat_connections, state.totalEntryConnections),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
			Column(modifier = Modifier.cell(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				HdMonoLabel(
					text = stringResource(Res.string.project_home_stat_activity, DEFAULT_HEATMAP_WEEKS),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				if (state.dailyWordTotals.isNotEmpty()) {
					val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
					ActivityHeatmap(
						dailyTotals = state.dailyWordTotals,
						today = today,
					)
					Spacer(Modifier.height(4.dp))
					HdInlineStat(
						label = stringResource(Res.string.project_home_stat_avg_weekday),
						value = state.writingActivity.avgWeekday.formatDecimalSeparator(),
						valueStyle = MaterialTheme.typography.titleSmall,
					)
					HdInlineStat(
						label = stringResource(Res.string.project_home_stat_avg_weekend),
						value = state.writingActivity.avgWeekend.formatDecimalSeparator(),
						valueStyle = MaterialTheme.typography.titleSmall,
					)
				}
			}
		}
	}
}

private const val THEMES_TOP_WIDE = 10
private const val THEMES_TOP_NARROW = 7

private val THEMES_SOURCE_ORDER = listOf(
	TaggedEntityType.Scene,
	TaggedEntityType.Note,
	TaggedEntityType.Encyclopedia,
	TaggedEntityType.TimelineEvent,
)

@Composable
private fun colorForTagSource(source: TaggedEntityType): Color {
	val hc = LocalHammerColors.current
	return when (source) {
		TaggedEntityType.Scene -> MaterialTheme.colorScheme.primary
		TaggedEntityType.Note -> hc.thing
		TaggedEntityType.Encyclopedia -> hc.place
		TaggedEntityType.TimelineEvent -> hc.event
	}
}

@Composable
private fun connectiveBreadthCaption(connective: TagBreakdown, short: Boolean): String {
	val isAllFour = connective.breadth >= THEMES_SOURCE_ORDER.size
	return when {
		isAllFour && short -> stringResource(Res.string.project_home_stat_themes_all_four_short)
		isAllFour -> stringResource(Res.string.project_home_stat_themes_all_four, connective.total)
		short -> stringResource(
			Res.string.project_home_stat_themes_breadth_of_four_short,
			connective.breadth,
		)

		else -> stringResource(
			Res.string.project_home_stat_themes_breadth_of_four,
			connective.breadth,
			connective.total,
		)
	}
}

@Composable
private fun labelForTagSource(source: TaggedEntityType): String = stringResource(
	when (source) {
		TaggedEntityType.Scene -> Res.string.project_home_stat_themes_source_scenes
		TaggedEntityType.Note -> Res.string.project_home_stat_themes_source_notes
		TaggedEntityType.Encyclopedia -> Res.string.project_home_stat_themes_source_encyclopedia
		TaggedEntityType.TimelineEvent -> Res.string.project_home_stat_themes_source_events
	}
)

@Composable
private fun ThemesStackedBar(
	tag: TagBreakdown,
	max: Int,
	modifier: Modifier = Modifier,
	height: Dp = 8.dp,
) {
	val safeMax = max.coerceAtLeast(1)
	val fraction = (tag.total.toFloat() / safeMax).coerceIn(0f, 1f)
	Box(
		modifier = modifier
			.height(height)
			.background(MaterialTheme.colorScheme.surfaceVariant),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth(fraction)
				.fillMaxHeight(),
		) {
			THEMES_SOURCE_ORDER.forEach { source ->
				val count = tag.getCount(source)
				if (count > 0) {
					Box(
						modifier = Modifier
							.weight(count.toFloat())
							.fillMaxHeight()
							.background(colorForTagSource(source)),
					)
				}
			}
		}
	}
}

@Composable
private fun ThemesSection(state: ProjectHome.State, isWide: Boolean) {
	val tags = state.tagBreakdowns
	val totalUses = remember(state.tagUsesByType) { state.tagUsesByType.values.sum() }
	val connective = remember(tags) {
		tags.maxWithOrNull(
			compareBy<TagBreakdown> { it.breadth }.thenBy { it.total }
		)
	}

	HdHairlineSection(
		section = 3,
		title = stringResource(Res.string.project_home_stat_themes_title),
		headerTrailing = {
			HdMonoLabel(
				text = stringResource(
					Res.string.project_home_stat_themes_summary,
					tags.size,
					totalUses,
				),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		},
	) {
		if (isWide) {
			ThemesSectionWide(
				tags = tags,
				connective = connective,
				tagUsesByType = state.tagUsesByType,
			)
		} else {
			ThemesSectionNarrow(
				tags = tags,
				connective = connective,
				tagUsesByType = state.tagUsesByType,
			)
		}
	}
}

@Composable
private fun ThemesSectionWide(
	tags: List<TagBreakdown>,
	connective: TagBreakdown?,
	tagUsesByType: Map<TaggedEntityType, Int>,
) {
	val top = remember(tags) { tags.take(THEMES_TOP_WIDE) }
	val max = top.firstOrNull()?.total ?: 1

	Row(modifier = Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.weight(1.55f),
			verticalArrangement = Arrangement.spacedBy(14.dp),
		) {
			top.forEachIndexed { index, tag ->
				ThemesRankedRow(rank = index + 1, tag = tag, max = max)
			}
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				HdMonoLabel(
					text = stringResource(
						Res.string.project_home_stat_themes_showing,
						top.size,
						tags.size,
					),
				)
				HdMonoLabel(
					text = stringResource(Res.string.project_home_stat_themes_columns_legend),
				)
			}
		}
		Spacer(Modifier.width(32.dp))
		VerticalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(start = 32.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			ThemesConnectiveCallout(connective = connective)
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				modifier = Modifier.padding(vertical = 8.dp),
			)
			HdMonoLabel(text = stringResource(Res.string.project_home_stat_themes_distribution))
			Column(
				modifier = Modifier.fillMaxWidth(),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				val distMax = tagUsesByType.values.maxOrNull()?.coerceAtLeast(1) ?: 1
				THEMES_SOURCE_ORDER.forEach { source ->
					val count = tagUsesByType[source] ?: 0
					ThemesDistributionRow(
						source = source,
						count = count,
						fraction = count.toFloat() / distMax,
					)
				}
			}
		}
	}
}

@Composable
private fun ThemesRankedRow(rank: Int, tag: TagBreakdown, max: Int) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		HdMonoLabel(
			text = rank.toString().padStart(2, '0'),
			modifier = Modifier.width(24.dp),
		)
		Row(
			modifier = Modifier.weight(1f),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Text(
				text = "#",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Text(
				text = tag.name,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
			)
		}
		HdMonoLabel(
			text = stringResource(Res.string.project_home_stat_themes_breadth, tag.breadth),
			modifier = Modifier.width(36.dp),
			textAlign = TextAlign.End,
		)
		ThemesStackedBar(
			tag = tag,
			max = max,
			modifier = Modifier.weight(2f),
		)
		Text(
			text = tag.total.formatDecimalSeparator(),
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
			textAlign = TextAlign.End,
			modifier = Modifier.width(40.dp),
		)
	}
}

@Composable
private fun ThemesConnectiveCallout(connective: TagBreakdown?) {
	HdMonoLabel(text = stringResource(Res.string.project_home_stat_themes_most_connective))
	if (connective == null) return
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.Bottom,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			text = "#",
			style = MaterialTheme.typography.headlineSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = connective.name,
			style = MaterialTheme.typography.displaySmall,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 1,
		)
	}
	HdMonoLabel(text = connectiveBreadthCaption(connective, short = false))
}

@Composable
private fun ThemesDistributionRow(
	source: TaggedEntityType,
	count: Int,
	fraction: Float,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Row(
			modifier = Modifier.width(120.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Box(
				modifier = Modifier
					.size(10.dp)
					.background(colorForTagSource(source)),
			)
			Text(
				text = labelForTagSource(source),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
			)
		}
		Box(
			modifier = Modifier
				.weight(1f)
				.height(5.dp)
				.background(MaterialTheme.colorScheme.surfaceVariant),
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth(fraction.coerceIn(0f, 1f))
					.fillMaxHeight()
					.background(colorForTagSource(source)),
			)
		}
		HdMonoLabel(
			text = count.formatDecimalSeparator(),
			modifier = Modifier.width(36.dp),
			textAlign = TextAlign.End,
		)
	}
}

@Composable
private fun ThemesSectionNarrow(
	tags: List<TagBreakdown>,
	connective: TagBreakdown?,
	tagUsesByType: Map<TaggedEntityType, Int>,
) {
	val top = remember(tags) { tags.take(THEMES_TOP_NARROW) }
	val max = top.firstOrNull()?.total ?: 1
	val rule = MaterialTheme.colorScheme.outlineVariant

	if (connective != null) {
		Column(modifier = Modifier.fillMaxWidth()) {
			HorizontalDivider(thickness = Dp.Hairline, color = rule)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Column(modifier = Modifier.weight(1f)) {
					HdMonoLabel(text = stringResource(Res.string.project_home_stat_themes_most_connective))
					Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
						Text(
							text = "#",
							style = MaterialTheme.typography.titleMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
						Text(
							text = connective.name,
							style = MaterialTheme.typography.headlineSmall,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
						)
					}
				}
				Column(horizontalAlignment = Alignment.End) {
					Text(
						text = connective.total.formatDecimalSeparator(),
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onSurface,
					)
					HdMonoLabel(text = connectiveBreadthCaption(connective, short = true))
				}
			}
			HorizontalDivider(thickness = Dp.Hairline, color = rule)
		}
	}

	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		top.forEachIndexed { index, tag ->
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(10.dp),
			) {
				HdMonoLabel(
					text = (index + 1).toString().padStart(2, '0'),
					modifier = Modifier.width(18.dp),
				)
				Row(
					modifier = Modifier.width(96.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp),
				) {
					Text(
						text = "#",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text(
						text = tag.name,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
				ThemesStackedBar(
					tag = tag,
					max = max,
					modifier = Modifier.weight(1f),
					height = 6.dp,
				)
				HdMonoLabel(
					text = tag.total.formatDecimalSeparator(),
					modifier = Modifier.width(28.dp),
					textAlign = TextAlign.End,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}
		}
	}

	HorizontalDivider(
		thickness = Dp.Hairline,
		color = rule,
		modifier = Modifier.padding(vertical = 12.dp),
	)
	HdMonoLabel(text = stringResource(Res.string.project_home_stat_themes_distribution))
	val distMax = tagUsesByType.values.maxOrNull()?.coerceAtLeast(1) ?: 1
	val distributionCells: List<@Composable () -> Unit> = THEMES_SOURCE_ORDER.map { source ->
		val count = tagUsesByType[source] ?: 0
		val cell: @Composable () -> Unit = {
			ThemesDistributionCellNarrow(
				source = source,
				count = count,
				fraction = count.toFloat() / distMax,
			)
		}
		cell
	}
	HdHairlineGrid(columns = 2, cells = distributionCells)
}

@Composable
private fun ThemesDistributionCellNarrow(source: TaggedEntityType, count: Int, fraction: Float) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Box(
				modifier = Modifier
					.size(8.dp)
					.background(colorForTagSource(source)),
			)
			Text(
				text = labelForTagSource(source),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f),
				maxLines = 1,
			)
			HdMonoLabel(
				text = count.formatDecimalSeparator(),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(3.dp)
				.background(MaterialTheme.colorScheme.surfaceVariant),
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth(fraction.coerceIn(0f, 1f))
					.fillMaxHeight()
					.background(colorForTagSource(source)),
			)
		}
	}
}

@Composable
private fun DevicesSection(state: ProjectHome.State) {
	HdHairlineSection(
		section = 4,
		title = stringResource(Res.string.project_home_stat_words_per_device),
	) {
		val sorted = remember(state.wordsPerDevice) {
			state.wordsPerDevice.entries.sortedByDescending { it.value }
		}
		val palette = remember(sorted.size) { generateHueColorPalette(sorted.size.coerceAtLeast(1)) }
		val items = remember(sorted, palette) {
			sorted.mapIndexed { index, (label, words) ->
				HdAttributionItem(label = label, value = words, color = palette[index])
			}
		}
		val maxValue = sorted.firstOrNull()?.value?.coerceAtLeast(1) ?: 1
		HdMiniBarChart(
			items = items,
			modifier = Modifier.fillMaxWidth(),
			maxValue = maxValue,
		)
	}
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun EncyclopediaDonut(
	typeCounts: Map<EntryType, Int>,
	totalEntries: Int,
	modifier: Modifier = Modifier,
) {
	// KoalaPlot crashes on zero values, so add 0.01f.
	val values = remember(typeCounts) { typeCounts.map { it.value.toFloat() + .01f } }
	val keys = remember(typeCounts) { typeCounts.keys.toList() }
	if (values.isEmpty() || values.sum() <= 0f) {
		Spacer(modifier = modifier.height(180.dp))
		return
	}

	var hasAnimated by rememberSaveable { mutableStateOf(false) }
	val animationDelay = remember { Random.nextInt(300, 1000) }
	val hammerColors = LocalHammerColors.current

	KoalaPlotTheme(
		animationSpec = if (!hasAnimated) {
			tween(
				durationMillis = 800,
				delayMillis = animationDelay,
				easing = LinearOutSlowInEasing,
			)
		} else {
			snap()
		},
	) {
		Box(modifier = modifier) {
			PieChart(
				modifier = Modifier.fillMaxSize().focusable(false),
				values = values,
				holeSize = 0.55f,
				holeContent = {
					Column(
						modifier = Modifier.fillMaxSize(),
						verticalArrangement = Arrangement.Center,
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						Text(
							totalEntries.formatDecimalSeparator(),
							style = MaterialTheme.typography.headlineMedium,
							color = MaterialTheme.colorScheme.onSurface,
						)
						HdMonoLabel(
							text = stringResource(Res.string.project_home_stat_donut_entries),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				},
				label = { index ->
					HdMonoLabel(
						text = keys[index].text,
						color = MaterialTheme.colorScheme.onSurface,
					)
				},
				labelConnector = { i ->
					BezierLabelConnector(
						connectorColor = hammerColors.colorFor(keys[i]),
						connectorStroke = Stroke(width = 3f),
					)
				},
			)
		}
	}

	LaunchedEffect(Unit) { hasAnimated = true }
}

@Composable
private fun ProjectHomeMenu(
	component: ProjectHome,
	hasServer: Boolean,
) {
	var expanded by remember { mutableStateOf(false) }

	Box {
		IconButton(onClick = { expanded = true }) {
			Icon(
				Icons.Default.MoreVert,
				tint = MaterialTheme.colorScheme.onBackground,
				contentDescription = Res.string.project_home_menu_button.get(),
			)
		}

		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			DropdownMenuItem(
				text = { Text(Res.string.global_search_button.get()) },
				onClick = {
					component.showGlobalSearch()
					expanded = false
				},
			)

			DropdownMenuItem(
				text = { Text(Res.string.project_home_action_export.get()) },
				onClick = {
					component.beginProjectExport()
					expanded = false
				},
			)

			DropdownMenuItem(
				text = { Text(Res.string.project_home_action_import.get()) },
				onClick = {
					component.beginProjectImport()
					expanded = false
				},
			)

			if (hasServer) {
				DropdownMenuItem(
					text = { Text(Res.string.project_home_action_sync.get()) },
					onClick = {
						component.startProjectSync()
						expanded = false
					},
				)
			}

			if (component.supportsBackup()) {
				DropdownMenuItem(
					text = { Text(Res.string.project_home_action_backup.get()) },
					onClick = {
						component.createBackup { _ ->
							expanded = false
						}
					},
				)
			}

			if (component.supportsCloseProject()) {
				HorizontalDivider()
				DropdownMenuItem(
					text = { Text(Res.string.project_window_menu_item_close.get()) },
					onClick = {
						component.closeProject()
						expanded = false
					},
				)
			}
		}
	}
}
