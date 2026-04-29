package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.http.projectdata.WordCountGoal
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.EntryAppearance
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.WritingActivityDerived
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.estimatePages
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.estimateReadingMinutes
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator
import io.github.koalaplot.core.gestures.GestureConfig
import io.github.koalaplot.core.line.LinePlot2
import io.github.koalaplot.core.pie.BezierLabelConnector
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.style.KoalaPlotTheme
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.generateHueColorPalette
import io.github.koalaplot.core.xygraph.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random
import kotlin.time.Clock

private val spanAll: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }

@Composable
fun ProjectStatsUi(
	modifier: Modifier,
	component: ProjectHome,
	scope: CoroutineScope,
) {
	val state by component.state.subscribeAsState()

	LazyVerticalGrid(
		columns = GridCells.Adaptive(300.dp),
		modifier = modifier.fillMaxHeight(),
		contentPadding = PaddingValues(horizontal = Ui.Padding.XL)
	) {
		item(key = "header", span = spanAll) {
			Column {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					HeaderUi(
						state.projectDef.name,
						"\uD83C\uDFE1",
						modifier = Modifier.weight(1f),
					)
					ProjectHomeMenu(
						component = component,
						scope = scope,
					)
				}

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Text(
					stringResource(Res.string.project_home_stat_created, state.created),
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface
				)
				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Text(
						Res.string.project_home_stat_header.get(),
						style = MaterialTheme.typography.headlineLarge,
						color = MaterialTheme.colorScheme.onSurface
					)

					Row(verticalAlignment = Alignment.CenterVertically) {
						if (state.isStatsDirty) {
							Icon(
								Icons.Default.Warning,
								contentDescription = stringResource(Res.string.project_home_stats_dirty_indicator),
								tint = MaterialTheme.colorScheme.tertiary,
								modifier = Modifier.size(20.dp)
							)
							Spacer(modifier = Modifier.width(4.dp))
						}
						IconButton(
							onClick = { component.refreshStatistics() },
							enabled = !state.isLoadingStats
						) {
							Icon(
								Icons.Default.Refresh,
								contentDescription = stringResource(Res.string.project_home_refresh_stats_button),
								tint = if (state.isLoadingStats)
									MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
								else
									MaterialTheme.colorScheme.primary
							)
						}
					}
				}
			}
		}

		if (state.isLoadingStats) {
			item(key = "loading", span = spanAll) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(vertical = Ui.Padding.XL),
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically
				) {
					CircularProgressIndicator(
						modifier = Modifier.size(24.dp),
						strokeWidth = 2.dp,
						color = MaterialTheme.colorScheme.primary
					)
					Spacer(modifier = Modifier.width(Ui.Padding.M))
					Text(
						stringResource(Res.string.project_home_loading_stats),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
		}

		item(key = "totalWords") {
			TotalWordsBlock(state.totalWords)
		}

		val goal = state.wordCountGoal
		if (goal != null) {
			item(key = "goalProgress") {
				GoalProgressBlock(goal = goal, activity = state.writingActivity)
			}
		}

		item(key = "thisWeek") {
			ThisWeekBlock(state.writingActivity)
		}

		item(key = "streak") {
			StreakBlock(state.writingActivity)
		}

		item(key = "numScenes") {
			NumericStatsBlock(Res.string.project_home_stat_num_scenes.get(), state.numberOfScenes)
		}

		item(key = "avgWordsPerScene") {
			NumericStatsBlock(
				Res.string.project_home_stat_avg_words_per_scene.get(),
				state.averageWordsPerScene
			)
		}

		item(key = "longestScene") {
			GenericStatsBlock(Res.string.project_home_stat_longest_scene.get()) {
				LongestSceneContent(state = state)
			}
		}

		item(key = "numNotes") {
			NumericStatsBlock(Res.string.project_home_stat_num_notes.get(), state.numberOfNotes)
		}

		item(key = "numTimelineEvents") {
			NumericStatsBlock(
				Res.string.project_home_stat_num_timeline_events.get(),
				state.numberOfTimelineEvents
			)
		}

		item(key = "chapterWords") {
			GenericStatsBlock(Res.string.project_home_stat_chapter_words.get()) {
				WordsInChaptersChart(state = state)
				ChapterStatsSummary(state = state)
			}
		}

		item(key = "sceneLengths") {
			GenericStatsBlock(Res.string.project_home_stat_scene_lengths.get()) {
				SceneLengthsContent(state = state)
			}
		}

		if (state.topAppearances.isNotEmpty()) {
			item(key = "charactersByAppearances") {
				GenericStatsBlock(Res.string.project_home_stat_characters_appearances.get()) {
					CharactersByAppearancesChart(state.topAppearances)
				}
			}
		}

		item(key = "encyclopediaEntries") {
			GenericStatsBlock(Res.string.project_home_stat_encyclopedia_entries.get()) {
				EncyclopediaChart(state = state)
				if (state.totalEntryConnections > 0) {
					Spacer(modifier = Modifier.size(Ui.Padding.S))
					Text(
						stringResource(
							Res.string.project_home_stat_connections,
							state.totalEntryConnections
						),
						modifier = Modifier.fillMaxWidth(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center
					)
				}
			}
		}

		if (state.wordsPerDevice.size >= 2) {
			item(key = "wordsPerDevice") {
				GenericStatsBlock(Res.string.project_home_stat_words_per_device.get()) {
					WordsPerDeviceChart(state.wordsPerDevice)
				}
			}
		}

		if (state.dailyWordTotals.isNotEmpty()) {
			item(key = "activity") {
				val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
				GenericStatsBlock(
					stringResource(Res.string.project_home_stat_activity, DEFAULT_HEATMAP_WEEKS)
				) {
					ActivityHeatmap(
						dailyTotals = state.dailyWordTotals,
						today = today,
					)
					Spacer(modifier = Modifier.size(Ui.Padding.M))
					HeatmapAverages(state.writingActivity)
				}
			}
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
private fun NumericStatsBlock(label: String, stateValue: Int) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(Ui.Padding.L),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.MEDIUM)
	) {
		Column(modifier = Modifier.padding(Ui.Padding.L).align(Alignment.CenterHorizontally)) {

			var targetScale by remember { mutableStateOf(1f) }
			var hasAnimated by rememberSaveable { mutableStateOf(false) }
			var targetValue by remember { mutableStateOf(if (hasAnimated) stateValue else 0) }

			val animatedValue by animateIntAsState(
				targetValue = targetValue,
				animationSpec = if (hasAnimated) {
					snap()
				} else {
					tween(
						durationMillis = 750,
						delayMillis = Random.nextInt(300, 1000),
						easing = LinearOutSlowInEasing
					)
				},
				finishedListener = {
					if (!hasAnimated) {
						targetScale = 1.25f
						hasAnimated = true
					}
				}
			)

			val scaleValue by animateFloatAsState(
				targetValue = targetScale,
				animationSpec = tween(
					durationMillis = 250,
					easing = LinearEasing
				),
				finishedListener = {
					if (targetScale > 1f) {
						targetScale = 1f
					}
				}
			)

			LaunchedEffect(stateValue) {
				targetValue = stateValue
			}

			Text(
				animatedValue.formatDecimalSeparator(),
				modifier = Modifier.fillMaxWidth().scale(scaleValue),
				style = MaterialTheme.typography.displayMedium,
				color = MaterialTheme.colorScheme.onSurface,
				textAlign = TextAlign.Center
			)

			Text(
				label,
				modifier = Modifier.fillMaxWidth(),
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onSurface,
				textAlign = TextAlign.Center
			)
		}
	}
}

@Composable
private fun GenericStatsBlock(label: String, content: @Composable () -> Unit) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(Ui.Padding.L),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.MEDIUM)
	) {
		Column(modifier = Modifier.padding(Ui.Padding.L).align(Alignment.CenterHorizontally)) {
			content()
			Spacer(modifier = Modifier.size(Ui.Padding.L))
			Text(
				label,
				modifier = Modifier.fillMaxWidth(),
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onSurface,
				textAlign = TextAlign.Center
			)
		}
	}
}

private val entryTypes = EntryType.entries
private val colors = generateHueColorPalette(entryTypes.size)

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun EncyclopediaChart(
	modifier: Modifier = Modifier,
	state: ProjectHome.State
) {
	// TODO this chart library is so full of bugs... having zero here crashes?! Add .01f
	val values =
		remember(state.encyclopediaEntriesByType) { state.encyclopediaEntriesByType.map { it.value.toFloat() + .01f } }

	if (values.isNotEmpty() && values.sum() > 0f) {
		var hasAnimated by rememberSaveable { mutableStateOf(false) }

		if (values.isNotEmpty() && values.sum() > 0f) {
			KoalaPlotTheme(
				animationSpec = if (!hasAnimated) {
					tween(
						durationMillis = 800,
						delayMillis = Random.nextInt(300, 1000),
						easing = LinearOutSlowInEasing
					)
				} else {
					snap()
				}
			) {
				val totalEntries = remember(state.encyclopediaEntriesByType) {
					state.encyclopediaEntriesByType.values.sum()
				}
				PieChart(
					modifier = modifier.focusable(false),
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
							Text(
								Res.string.project_home_stat_donut_entries.get(),
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					},
					label = { index ->
						Text(
							entryTypes[index].toStringResource().get(),
							style = MaterialTheme.typography.headlineSmall,
							color = MaterialTheme.colorScheme.onSurface
						)
					},
					labelConnector = { i ->
						BezierLabelConnector(
							connectorColor = colors[i],
							connectorStroke = Stroke(width = 3f)
						)
					},
				)
			}

			LaunchedEffect(Unit) {
				hasAnimated = true
			}
		}
	} else {
		Spacer(modifier = Modifier.size(128.dp))
	}
}

private val disabledInput = GestureConfig(
	panXEnabled = false, panYEnabled = false,
	panXConsumptionEnabled = false,
	panYConsumptionEnabled = false,
	zoomXEnabled = false,
	zoomYEnabled = false,
	independentZoomEnabled = false,
	panFlingAnimationEnabled = false,
)

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun WordsInChaptersChart(
	modifier: Modifier = Modifier,
	state: ProjectHome.State
) {
	if (state.wordsByChapter.size <= 1) return

	val points = remember(state.wordsByChapter) {
		state.wordsByChapter.entries.mapIndexed { index, entry ->
			Point(index, entry.value.toFloat())
		}
	}

	val xAxis = remember(state.wordsByChapter) {
		List(state.wordsByChapter.keys.size) { i -> i }
	}

	val range = remember(state.wordsByChapter) {
		state.wordsByChapter.values.map { it.toFloat() }.autoScaleRange()
	}

	var hasAnimated by rememberSaveable { mutableStateOf(false) }

	val lineColor = MaterialTheme.colorScheme.primary
	val lineStyle = remember(lineColor) {
		LineStyle(brush = SolidColor(lineColor), strokeWidth = 2.dp)
	}

	KoalaPlotTheme(
		animationSpec = if (!hasAnimated) {
			tween(
				durationMillis = 700,
				delayMillis = Random.nextInt(300, 1000),
				easing = LinearOutSlowInEasing
			)
		} else {
			snap()
		}
	) {
		XYGraph(
			modifier = modifier.heightIn(96.dp, 196.dp).focusable(false),
			xAxisModel = CategoryAxisModel(xAxis),
			yAxisModel = FloatLinearAxisModel(range = range),
			xAxisTitle = Res.string.project_home_stat_chapter_words_x_axis.get(),
			xAxisLabels = { index -> (index + 1).toString() },
			xAxisStyle = rememberAxisStyle(color = MaterialTheme.colorScheme.onBackground),
			yAxisLabels = { "" },
			yAxisStyle = rememberAxisStyle(color = MaterialTheme.colorScheme.onSurface),
			gestureConfig = disabledInput,
			content = {
				LinePlot2(
					data = points,
					lineStyle = lineStyle,
					symbol = { point ->
						ChapterPointSymbol(
							chapterIndex = point.x,
							wordCount = point.y.toInt(),
							color = lineColor,
							chapterEntries = state.wordsByChapter.entries.toList(),
						)
					},
				)
			}
		)
	}

	LaunchedEffect(Unit) {
		hasAnimated = true
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterPointSymbol(
	chapterIndex: Int,
	wordCount: Int,
	color: Color,
	chapterEntries: List<Map.Entry<String, Int>>,
) {
	val tooltipState = rememberTooltipState(isPersistent = false)
	val coroutineScope = rememberCoroutineScope()
	val chapterName = chapterEntries.getOrNull(chapterIndex)?.key ?: ""
	val tooltipText = stringResource(
		Res.string.project_home_stat_chapter_words_tooltip,
		chapterName,
		wordCount.formatDecimalSeparator(),
	)

	TooltipBox(
		positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
		tooltip = { PlainTooltip { Text(tooltipText) } },
		state = tooltipState,
	) {
		Box(
			modifier = Modifier
				.size(8.dp)
				.clip(RoundedCornerShape(4.dp))
				.background(color)
				.clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
				) { coroutineScope.launch { tooltipState.show() } },
		)
	}
}

@Composable
private fun LongestSceneContent(state: ProjectHome.State) {
	val name = state.longestSceneName
	if (name.isNullOrBlank() || state.longestSceneWords <= 0) {
		Text(
			Res.string.project_home_stat_longest_scene_empty.get(),
			modifier = Modifier.fillMaxWidth().padding(vertical = Ui.Padding.L),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center
		)
	} else {
		Column(
			modifier = Modifier.fillMaxWidth().padding(vertical = Ui.Padding.L),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				name,
				modifier = Modifier.fillMaxWidth(),
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onSurface,
				textAlign = TextAlign.Center
			)
			Spacer(modifier = Modifier.size(Ui.Padding.S))
			Text(
				stringResource(
					Res.string.project_home_stat_longest_scene_words,
					state.longestSceneWords.formatDecimalSeparator()
				),
				modifier = Modifier.fillMaxWidth(),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center
			)
		}
	}
}

@Composable
private fun SceneLengthsContent(state: ProjectHome.State) {
	if (state.numberOfScenes <= 0) {
		Text(
			Res.string.project_home_stat_longest_scene_empty.get(),
			modifier = Modifier.fillMaxWidth().padding(vertical = Ui.Padding.L),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center
		)
		return
	}

	val chapterCount = state.wordsByChapter.size
	val avgScenesPerChapter = if (chapterCount > 0) {
		(state.numberOfScenes.toDouble() / chapterCount)
	} else 0.0

	Column(
		modifier = Modifier.fillMaxWidth().padding(vertical = Ui.Padding.M),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)
	) {
		SceneLengthRow(
			label = Res.string.project_home_stat_scene_shortest.get(),
			value = state.shortestSceneWords.formatDecimalSeparator()
		)
		SceneLengthRow(
			label = Res.string.project_home_stat_scene_median.get(),
			value = state.medianSceneWords.formatDecimalSeparator()
		)
		if (chapterCount > 0) {
			SceneLengthRow(
				label = Res.string.project_home_stat_avg_scenes_per_chapter.get(),
				value = formatOneDecimal(avgScenesPerChapter)
			)
		}
	}
}

private fun formatOneDecimal(value: Double): String {
	val rounded = (value * 10).toLong()
	val whole = rounded / 10
	val frac = (rounded % 10).let { if (it < 0) -it else it }
	return "$whole.$frac"
}

@Composable
private fun SceneLengthRow(label: String, value: String) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			label,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Text(
			value,
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface
		)
	}
}

@Composable
private fun TotalWordsBlock(totalWords: Int) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(Ui.Padding.L),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.MEDIUM)
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.L).fillMaxWidth(),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				totalWords.formatDecimalSeparator(),
				modifier = Modifier.fillMaxWidth(),
				style = MaterialTheme.typography.displayMedium,
				color = MaterialTheme.colorScheme.onSurface,
				textAlign = TextAlign.Center
			)
			Text(
				Res.string.project_home_stat_total_words.get(),
				modifier = Modifier.fillMaxWidth(),
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.onSurface,
				textAlign = TextAlign.Center
			)
			if (totalWords > 0) {
				Spacer(modifier = Modifier.size(Ui.Padding.S))
				val readingMinutes = remember(totalWords) { estimateReadingMinutes(totalWords) }
				val pages = remember(totalWords) { estimatePages(totalWords) }
				Text(
					stringResource(Res.string.project_home_stat_reading_time, readingMinutes) +
						"  ·  " +
						stringResource(Res.string.project_home_stat_pages, pages),
					modifier = Modifier.fillMaxWidth(),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center
				)
			}
		}
	}
}

@Composable
private fun GoalProgressBlock(goal: WordCountGoal, activity: WritingActivityDerived) {
	val isDaily = goal.cadence == WordCountGoal.Cadence.DAY
	val current = if (isDaily) activity.wordsToday else activity.wordsThisWeek
	val target = goal.count.coerceAtLeast(1)
	val progress = (current.toFloat() / target).coerceIn(0f, 1f)
	val label = if (isDaily) {
		Res.string.project_home_stat_daily_goal.get()
	} else {
		Res.string.project_home_stat_weekly_goal.get()
	}

	Card(
		modifier = Modifier.fillMaxWidth().padding(Ui.Padding.L),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.MEDIUM)
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.L).fillMaxWidth()
		) {
			Text(
				label,
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(modifier = Modifier.size(Ui.Padding.S))
			Text(
				stringResource(
					Res.string.project_home_stat_goal_progress,
					current.formatDecimalSeparator(),
					goal.count.formatDecimalSeparator(),
				),
				style = MaterialTheme.typography.headlineMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(modifier = Modifier.size(Ui.Padding.S))
			LinearProgressIndicator(
				progress = { progress },
				modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
			)
		}
	}
}

@Composable
private fun ThisWeekBlock(activity: WritingActivityDerived) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(Ui.Padding.L),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.MEDIUM)
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.L).fillMaxWidth()
		) {
			Text(
				Res.string.project_home_stat_this_week.get(),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(modifier = Modifier.size(Ui.Padding.S))
			Text(
				stringResource(
					Res.string.project_home_stat_this_week_value,
					activity.wordsThisWeek.formatDecimalSeparator(),
				),
				style = MaterialTheme.typography.displaySmall,
				color = MaterialTheme.colorScheme.primary,
			)
			Spacer(modifier = Modifier.size(2.dp))
			val change = activity.weekChangePercent
			val changeText = when {
				change == null && activity.wordsLastWeek == 0 && activity.wordsThisWeek > 0 ->
					Res.string.project_home_stat_week_change_new.get()
				change == null -> ""
				change > 0 -> stringResource(Res.string.project_home_stat_week_change_up, change)
				change < 0 -> stringResource(Res.string.project_home_stat_week_change_down, -change)
				else -> Res.string.project_home_stat_week_change_flat.get()
			}
			if (changeText.isNotEmpty()) {
				Text(
					changeText,
					style = MaterialTheme.typography.bodyMedium,
					color = when {
						change != null && change > 0 -> MaterialTheme.colorScheme.primary
						change != null && change < 0 -> MaterialTheme.colorScheme.error
						else -> MaterialTheme.colorScheme.onSurfaceVariant
					},
				)
			}
			Spacer(modifier = Modifier.size(Ui.Padding.M))
			KeyValueRow(
				label = Res.string.project_home_stat_today.get(),
				value = activity.wordsToday.formatDecimalSeparator(),
			)
			KeyValueRow(
				label = Res.string.project_home_stat_daily_avg.get(),
				value = activity.dailyAverageThisWeek.formatDecimalSeparator(),
			)
		}
	}
}

@Composable
private fun StreakBlock(activity: WritingActivityDerived) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(Ui.Padding.L),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.MEDIUM)
	) {
		Column(
			modifier = Modifier.padding(Ui.Padding.L).fillMaxWidth()
		) {
			Text(
				Res.string.project_home_stat_streak.get(),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(modifier = Modifier.size(Ui.Padding.S))
			Text(
				stringResource(
					Res.string.project_home_stat_streak_days,
					activity.currentStreak,
				),
				style = MaterialTheme.typography.displaySmall,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(modifier = Modifier.size(2.dp))
			Text(
				stringResource(
					Res.string.project_home_stat_longest_streak,
					activity.longestStreak,
				),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(modifier = Modifier.size(Ui.Padding.M))
			KeyValueRow(
				label = Res.string.project_home_stat_days_written.get(),
				value = activity.daysWritten.formatDecimalSeparator(),
			)
			val best = activity.bestDayInStreak
			if (best != null) {
				KeyValueRow(
					label = Res.string.project_home_stat_best_day.get(),
					value = stringResource(
						Res.string.project_home_stat_best_day_value,
						best.date.toString(),
						best.words.formatDecimalSeparator(),
					),
				)
			}
		}
	}
}

@Composable
private fun KeyValueRow(label: String, value: String) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			value,
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
private fun ChapterStatsSummary(state: ProjectHome.State) {
	if (state.wordsByChapter.isEmpty()) return
	val chapterValues = state.wordsByChapter.values
	val min = chapterValues.minOrNull() ?: 0
	val max = chapterValues.maxOrNull() ?: 0
	Spacer(modifier = Modifier.size(Ui.Padding.S))
	Text(
		stringResource(
			Res.string.project_home_stat_chapter_words_summary,
			state.sceneWordsStdDev,
			min,
			max,
		),
		modifier = Modifier.fillMaxWidth(),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		textAlign = TextAlign.Center,
	)
}

@Composable
private fun CharactersByAppearancesChart(appearances: List<EntryAppearance>) {
	if (appearances.isEmpty()) return
	val maxCount = appearances.maxOf { it.sceneCount }.coerceAtLeast(1)
	Column(
		modifier = Modifier.fillMaxWidth().padding(vertical = Ui.Padding.M),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)
	) {
		appearances.forEach { entry ->
			val typeIndex = EntryType.entries.indexOf(entry.type).coerceAtLeast(0)
			val barColor = colors[typeIndex.coerceIn(0, colors.size - 1)]
			AppearanceRow(
				name = entry.name,
				count = entry.sceneCount,
				ratio = entry.sceneCount.toFloat() / maxCount,
				color = barColor,
			)
		}
	}
}

@Composable
private fun AppearanceRow(name: String, count: Int, ratio: Float, color: Color) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			name,
			modifier = Modifier.weight(0.4f),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 1,
		)
		Box(
			modifier = Modifier
				.weight(0.5f)
				.height(8.dp)
				.clip(RoundedCornerShape(4.dp))
				.background(MaterialTheme.colorScheme.surfaceVariant),
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth(ratio.coerceIn(0f, 1f))
					.fillMaxHeight()
					.background(color)
			)
		}
		Spacer(modifier = Modifier.width(Ui.Padding.S))
		Text(
			count.toString(),
			modifier = Modifier.wrapContentWidth(),
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.onSurface,
			textAlign = TextAlign.End,
			maxLines = 1,
			softWrap = false,
		)
	}
}

@Composable
private fun WordsPerDeviceChart(perDevice: Map<String, Int>) {
	if (perDevice.isEmpty()) return
	val sorted = remember(perDevice) {
		perDevice.entries.sortedByDescending { it.value }
	}
	val maxCount = sorted.first().value.coerceAtLeast(1)
	val palette = remember(sorted.size) {
		generateHueColorPalette(sorted.size.coerceAtLeast(1))
	}
	Column(
		modifier = Modifier.fillMaxWidth().padding(vertical = Ui.Padding.M),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)
	) {
		sorted.forEachIndexed { index, (label, words) ->
			AppearanceRow(
				name = label,
				count = words,
				ratio = words.toFloat() / maxCount,
				color = palette[index],
			)
		}
	}
}

@Composable
private fun HeatmapAverages(activity: WritingActivityDerived) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(2.dp)
	) {
		KeyValueRow(
			label = Res.string.project_home_stat_avg_weekday.get(),
			value = activity.avgWeekday.formatDecimalSeparator(),
		)
		KeyValueRow(
			label = Res.string.project_home_stat_avg_weekend.get(),
			value = activity.avgWeekend.formatDecimalSeparator(),
		)
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
				contentDescription = Res.string.project_home_menu_button.get()
			)
		}

		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false }
		) {
			DropdownMenuItem(
				text = { Text(Res.string.global_search_button.get()) },
				onClick = {
					component.showGlobalSearch()
					expanded = false
				}
			)

			DropdownMenuItem(
				text = { Text(Res.string.project_home_action_settings_button.get()) },
				onClick = {
					component.showProjectSettings()
					expanded = false
				}
			)

			DropdownMenuItem(
				text = { Text(Res.string.project_home_action_export.get()) },
				onClick = {
					component.beginProjectExport()
					expanded = false
				}
			)

			DropdownMenuItem(
				text = { Text(Res.string.project_home_action_import.get()) },
				onClick = {
					component.beginProjectImport()
					expanded = false
				}
			)

			if (state.hasServer) {
				DropdownMenuItem(
					text = { Text(Res.string.project_home_action_sync.get()) },
					onClick = {
						component.startProjectSync()
						expanded = false
					}
				)
			}

			if (component.supportsBackup()) {
				DropdownMenuItem(
					text = { Text(Res.string.project_home_action_backup.get()) },
					onClick = {
						component.createBackup { backup ->
							expanded = false
						}
					}
				)
			}
		}
	}
}