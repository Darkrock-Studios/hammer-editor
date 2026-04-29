package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdAttributionItem
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdBarChart
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdBarChartItem
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDailyGoalProgress
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDeltaBadge
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdInlineStat
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMetadataRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMiniBarChart
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPlainSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdResponsiveStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdStatBlock
import com.darkrockstudios.apps.hammer.common.compose.designsystem.formatThousands
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.WritingActivityDerived
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.estimatePages
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.estimateReadingMinutes
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
				ProjectHomeMenu(component = component, scope = scope)
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
		HdResponsiveStrip(isWide = isWide) {
			TotalWordsBlock(state, modifier = Modifier.cell())
			ThisWeekBlock(state.writingActivity, modifier = Modifier.cell())
			StreakBlock(state.writingActivity, modifier = Modifier.cell())
		}
	}
}

@Composable
private fun TotalWordsBlock(state: ProjectHome.State, modifier: Modifier = Modifier) {
	HdStatBlock(
		label = stringResource(Res.string.project_home_stat_total_words),
		value = formatThousands(state.totalWords),
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
		value = "+${formatThousands(activity.wordsThisWeek)}",
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
			value = formatThousands(activity.wordsToday),
		)
		HdInlineStat(
			label = stringResource(Res.string.project_home_stat_daily_avg),
			value = formatThousands(activity.dailyAverageThisWeek),
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
			value = formatThousands(activity.daysWritten),
		)
		val best = activity.bestDayInStreak
		if (best != null) {
			HdInlineStat(
				label = stringResource(Res.string.project_home_stat_best_day),
				value = stringResource(
					Res.string.project_home_stat_best_day_value,
					best.date.toString(),
					formatThousands(best.words),
				),
			)
		}
	}
}

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
		HdResponsiveStrip(isWide = isWide) {
			HdStatBlock(
				label = stringResource(Res.string.project_home_stat_num_scenes),
				value = formatThousands(state.numberOfScenes),
				subtitle = "across $chapterCount chapters",
				modifier = Modifier.cell(),
			)
			HdStatBlock(
				label = stringResource(Res.string.project_home_stat_avg_words_per_scene),
				value = formatThousands(state.averageWordsPerScene),
				subtitle = if (state.medianSceneWords > 0)
					"${stringResource(Res.string.project_home_stat_scene_median).lowercase()} ${formatThousands(state.medianSceneWords)}"
				else null,
				modifier = Modifier.cell(),
			)
			val longestName = state.longestSceneName
			HdStatBlock(
				label = stringResource(Res.string.project_home_stat_longest_scene),
				value = longestName ?: stringResource(Res.string.project_home_stat_longest_scene_empty),
				valueStyle = MaterialTheme.typography.headlineMedium,
				subtitle = if (state.longestSceneWords > 0)
					stringResource(Res.string.project_home_stat_longest_scene_words, formatThousands(state.longestSceneWords))
				else null,
				modifier = Modifier.cell(),
			)
			Column(modifier = Modifier.cell(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				HdMonoLabel(
					text = "${stringResource(Res.string.project_home_stat_num_notes)} · ${stringResource(Res.string.project_home_stat_num_timeline_events)}",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Row(
					verticalAlignment = Alignment.Bottom,
					horizontalArrangement = Arrangement.spacedBy(20.dp),
				) {
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Text(
							text = formatThousands(state.numberOfNotes),
							style = MaterialTheme.typography.displayMedium,
							color = MaterialTheme.colorScheme.onSurface,
						)
						HdMonoLabel(
							text = stringResource(Res.string.project_home_stat_num_notes),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
						Text(
							text = formatThousands(state.numberOfTimelineEvents),
							style = MaterialTheme.typography.displayMedium,
							color = MaterialTheme.colorScheme.primary,
						)
						HdMonoLabel(
							text = stringResource(Res.string.project_home_stat_num_timeline_events),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}
		}

		if (state.wordsByChapter.isNotEmpty()) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				val maxV = state.wordsByChapter.values.maxOrNull() ?: 0
				val minV = state.wordsByChapter.values.minOrNull() ?: 0
				HdSectionHeader(
					marker = "—",
					title = stringResource(Res.string.project_home_stat_chapter_words),
					modifier = Modifier.fillMaxWidth(),
					trailing = {
						HdMonoLabel(
							text = stringResource(
								Res.string.project_home_stat_chapter_words_summary,
								state.sceneWordsStdDev,
								minV,
								maxV,
							),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					},
				)
				HdBarChart(
					items = state.wordsByChapter.entries.mapIndexed { index, entry ->
						HdBarChartItem(label = (index + 1).toString(), value = entry.value)
					},
					modifier = Modifier.fillMaxWidth(),
					height = 140.dp,
				)
			}
		}
	}
}

@Composable
private fun InhabitantsSection(state: ProjectHome.State, isWide: Boolean) {
	val totalEntries = state.encyclopediaEntriesByType.values.sum()
	val typeCounts = state.encyclopediaEntriesByType
	HdHairlineSection(
		section = 2,
		title = "Inhabitants",
		headerTrailing = {
			val parts = buildList {
				if (totalEntries > 0) add("$totalEntries entries")
				typeCounts[EntryType.PLACE]?.takeIf { it > 0 }?.let { add("$it places") }
				typeCounts[EntryType.PERSON]?.takeIf { it > 0 }?.let { add("$it people") }
				typeCounts[EntryType.THING]?.takeIf { it > 0 }?.let { add("$it things") }
			}
			if (parts.isNotEmpty()) {
				HdMonoLabel(
					text = parts.joinToString(" · "),
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
				if (state.topAppearances.isNotEmpty()) {
					val hammerColors = LocalHammerColors.current
					HdMiniBarChart(
						items = state.topAppearances.map { entry ->
							HdAttributionItem(
								label = entry.name,
								value = entry.sceneCount,
								color = hammerColors.colorForCharacter(entry.entryId),
							)
						},
						modifier = Modifier.fillMaxWidth(),
					)
				} else {
					Spacer(Modifier.height(48.dp))
				}
			}
			Column(modifier = Modifier.cell(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				HdMonoLabel(
					text = stringResource(Res.string.project_home_stat_encyclopedia_entries),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				EncyclopediaDonut(state = state, modifier = Modifier.fillMaxWidth().height(220.dp))
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
					HdMetadataRow(
						label = stringResource(Res.string.project_home_stat_avg_weekday),
						value = formatThousands(state.writingActivity.avgWeekday),
					)
					HdMetadataRow(
						label = stringResource(Res.string.project_home_stat_avg_weekend),
						value = formatThousands(state.writingActivity.avgWeekend),
					)
				}
			}
		}
	}
}

@Composable
private fun DevicesSection(state: ProjectHome.State) {
	HdHairlineSection(
		section = 3,
		title = stringResource(Res.string.project_home_stat_words_per_device),
	) {
		val sorted = remember(state.wordsPerDevice) {
			state.wordsPerDevice.entries.sortedByDescending { it.value }
		}
		val maxValue = sorted.firstOrNull()?.value?.coerceAtLeast(1) ?: 1
		val palette = remember(sorted.size) { generateHueColorPalette(sorted.size.coerceAtLeast(1)) }
		HdMiniBarChart(
			items = sorted.mapIndexed { index, (label, words) ->
				HdAttributionItem(label = label, value = words, color = palette[index])
			},
			modifier = Modifier.fillMaxWidth(),
			maxValue = maxValue,
		)
	}
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun EncyclopediaDonut(state: ProjectHome.State, modifier: Modifier = Modifier) {
	// KoalaPlot crashes on zero values, so add 0.01f.
	val values = remember(state.encyclopediaEntriesByType) {
		state.encyclopediaEntriesByType.map { it.value.toFloat() + .01f }
	}
	val keys = remember(state.encyclopediaEntriesByType) {
		state.encyclopediaEntriesByType.keys.toList()
	}
	if (values.isEmpty() || values.sum() <= 0f) {
		Spacer(modifier = modifier.height(180.dp))
		return
	}

	var hasAnimated by rememberSaveable { mutableStateOf(false) }
	val totalEntries = state.encyclopediaEntriesByType.values.sum()
	val hammerColors = LocalHammerColors.current

	KoalaPlotTheme(
		animationSpec = if (!hasAnimated) {
			tween(
				durationMillis = 800,
				delayMillis = Random.nextInt(300, 1000),
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
							formatThousands(totalEntries),
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

	if (!hasAnimated) {
		hasAnimated = true
	}
}

@Composable
private fun ProjectHomeMenu(
	component: ProjectHome,
	scope: CoroutineScope,
) {
	val state by component.state.subscribeAsState()
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
				text = { Text(Res.string.project_home_action_settings_button.get()) },
				onClick = {
					component.showProjectSettings()
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

			if (state.hasServer) {
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
		}
	}
}
