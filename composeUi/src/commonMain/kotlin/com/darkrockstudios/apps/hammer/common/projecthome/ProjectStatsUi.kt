package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.animation.core.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator
import io.github.koalaplot.core.bar.DefaultBar
import io.github.koalaplot.core.bar.VerticalBarPlot
import io.github.koalaplot.core.gestures.GestureConfig
import io.github.koalaplot.core.pie.BezierLabelConnector
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.style.KoalaPlotTheme
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.generateHueColorPalette
import io.github.koalaplot.core.xygraph.*
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

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
				val screen = LocalScreenCharacteristic.current
				when (screen.windowWidthClass) {
					WindowWidthSizeClass.Compact -> {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							HeaderUi(
								state.projectDef.name,
								"\uD83C\uDFE1",
							)
							ProjectHomeMenu(
								component = component,
								scope = scope,
							)
						}
					}

					else -> {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								state.projectDef.name,
								modifier = Modifier.weight(1f),
								style = MaterialTheme.typography.displayMedium,
								color = MaterialTheme.colorScheme.onSurface
							)
							ProjectHomeMenu(
								component = component,
								scope = scope,
							)
						}
					}
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

		item(key = "numScenes") {
			NumericStatsBlock(Res.string.project_home_stat_num_scenes.get(), state.numberOfScenes)
		}

		item(key = "totalWords") {
			NumericStatsBlock(Res.string.project_home_stat_total_words.get(), state.totalWords)
		}

		item(key = "chapterWords") {
			GenericStatsBlock(Res.string.project_home_stat_chapter_words.get()) {
				WordsInChaptersChart(state = state)
			}
		}

		item(key = "encyclopediaEntries") {
			GenericStatsBlock(Res.string.project_home_stat_encyclopedia_entries.get()) {
				EncyclopediaChart(state = state)
			}
		}
	}

	ExportDirectoryPicker(state.showExportDialog, component, scope)
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
				PieChart(
					modifier = modifier.focusable(false),
					values = values,
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
	val xAxis = remember(state.wordsByChapter) {
		if (state.wordsByChapter.isNotEmpty()) {
			List(state.wordsByChapter.keys.size) { i -> i }
		} else {
			listOf(0, 10)
		}
	}

	val yData = remember(state.wordsByChapter) {
		state.wordsByChapter.entries.map { it.value.toFloat() }
	}

	val range = remember(state.wordsByChapter) {
		val entryValues = state.wordsByChapter.entries.map { it.value.toFloat() }
		if (entryValues.isNotEmpty()) {
			entryValues.autoScaleRange()
		} else {
			listOf(0f, 1f).autoScaleRange()
		}
	}

	var hasAnimated by rememberSaveable { mutableStateOf(false) }

	if (state.wordsByChapter.size > 1) {
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
				modifier = modifier.heightIn(64.dp, 196.dp).focusable(false),
				xAxisModel = CategoryAxisModel(xAxis),
				yAxisModel = FloatLinearAxisModel(range = range),
				xAxisTitle = Res.string.project_home_stat_chapter_words_x_axis.get(),
				yAxisTitle = Res.string.project_home_stat_chapter_words_y_axis.get(),
				xAxisLabels = { index -> (index + 1).toString() },
				xAxisStyle = rememberAxisStyle(color = MaterialTheme.colorScheme.onBackground),
				yAxisLabels = { it.toInt().toString() },
				yAxisStyle = rememberAxisStyle(color = MaterialTheme.colorScheme.onSurface),
				gestureConfig = disabledInput,
				content = {
					VerticalBarPlot(
						xData = xAxis,
						yData = yData,
						bar = { _, _, _ ->
							DefaultBar(colors.first())
						}
					)
				}
			)
		}

		LaunchedEffect(Unit) {
			hasAnimated = true
		}
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