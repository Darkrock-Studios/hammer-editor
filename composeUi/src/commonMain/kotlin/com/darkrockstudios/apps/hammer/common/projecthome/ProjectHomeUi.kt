package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.animation.core.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import io.github.koalaplot.core.bar.DefaultBarChartEntry
import io.github.koalaplot.core.bar.VerticalBarChart
import io.github.koalaplot.core.pie.BezierLabelConnector
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.generateHueColorPalette
import io.github.koalaplot.core.xychart.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ProjectHomeUi(
	component: ProjectHome,
) {
	val state by component.state.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }

	Box {
		if (screen.isWide) {
			Row(
				modifier = Modifier.fillMaxSize()
					.padding(Ui.Padding.XL)
			) {
				Stats(
					modifier = Modifier.weight(3f).rightBorder(1.dp, MaterialTheme.colorScheme.outline),
					state = state
				)
				Actions(
					modifier = Modifier.weight(1f),
					component = component,
					scope = scope,
					snackbarHostState = snackbarHostState
				)
			}
		} else {
			Stats(
				modifier = Modifier.fillMaxWidth().bottomBorder(1.dp, MaterialTheme.colorScheme.outline),
				state = state,
				otherContent = {
					Actions(
						modifier = Modifier.fillMaxWidth(),
						component = component,
						scope = scope,
						snackbarHostState = snackbarHostState
					)
				}
			)
		}

		SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
	}
}

private val spanAll: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(Int.MAX_VALUE) }

@Composable
private fun Stats(
	modifier: Modifier,
	state: ProjectHome.State,
	otherContent: (@Composable () -> Unit)? = null
) {
	LazyVerticalGrid(
		columns = GridCells.Adaptive(300.dp),
		modifier = modifier.fillMaxHeight(),
		contentPadding = PaddingValues(Ui.Padding.XL)
	) {
		item(span = spanAll) {
			Column {
				Text(
					state.projectDef.name,
					style = MaterialTheme.typography.displayMedium,
					color = MaterialTheme.colorScheme.onSurface
				)

				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Text(
					"Created: ${state.created}",
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface
				)
				Spacer(modifier = Modifier.size(Ui.Padding.XL))

				Text(
					"Stats:",
					style = MaterialTheme.typography.headlineLarge,
					color = MaterialTheme.colorScheme.onSurface
				)
			}
		}

		/*
		item {
			NumericStatsBlock("Scenes", state.numberOfScenes)
		}

		item {
			NumericStatsBlock("Total Words", state.totalWords)
		}

		item {
			GenericStatsBlock("Words in Chapters") {
				WordsInChaptersChart(state = state)
			}
		}

		item {
			GenericStatsBlock("Encyclopedia Entries") {
				EncyclopediaChart(state = state)
			}
		}
		*/

		if (otherContent != null) {
			item {
				otherContent()
			}
		}
	}
}

@Composable
private fun NumericStatsBlock(label: String, stateValue: Int) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(Ui.Padding.L),
		elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.MEDIUM)
	) {
		Column(modifier = Modifier.padding(Ui.Padding.L).align(CenterHorizontally)) {

			var targetScale by remember { mutableStateOf(1f) }
			var targetValue by remember { mutableStateOf(0) }

			val animatedValue by animateIntAsState(
				targetValue = targetValue,
				animationSpec = tween(
					durationMillis = 750,
					easing = LinearOutSlowInEasing
				),
				finishedListener = {
					targetScale = 1.25f
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
		Column(modifier = Modifier.padding(Ui.Padding.L).align(CenterHorizontally)) {
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

private val entryTypes = EntryType.values()
private val colors = generateHueColorPalette(entryTypes.size)

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun EncyclopediaChart(
	modifier: Modifier = Modifier,
	state: ProjectHome.State
) {
	val values =
		remember(state.encyclopediaEntriesByType) { state.encyclopediaEntriesByType.map { it.value.toFloat() } }

	PieChart(
		modifier = modifier.focusable(false),
		values = values,
		label = { index ->
			Text(
				entryTypes[index].text,
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

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun WordsInChaptersChart(
	modifier: Modifier = Modifier,
	state: ProjectHome.State
) {
	val entries = remember(state.wordsByChapter) {
		state.wordsByChapter.entries.mapIndexed { index, entry ->
			DefaultBarChartEntry(
				xValue = index,
				yMin = 0f,
				yMax = entry.value.toFloat()
			)
		}
	}

	val xAxis = remember(state.wordsByChapter) {
		if (state.wordsByChapter.isNotEmpty()) {
			List(state.wordsByChapter.keys.size) { i -> i }
		} else {
			listOf(0, 100)
		}
	}

	val range = remember(state.wordsByChapter) {
		val entryValues = state.wordsByChapter.entries.map { it.value.toFloat() }
		if (entryValues.isNotEmpty()) {
			entryValues.autoScaleRange()
		} else {
			listOf(0f, 1f).autoScaleRange()
		}
	}

	XYChart(
		modifier = modifier.heightIn(64.dp, 196.dp).focusable(false),
		xAxisModel = CategoryAxisModel(xAxis),
		yAxisModel = LinearAxisModel(range = range),
		xAxisTitle = "Chapter",
		yAxisTitle = "Words",
		xAxisLabels = { index -> (index + 1).toString() },
		xAxisStyle = rememberAxisStyle(color = MaterialTheme.colorScheme.onBackground),
		yAxisLabels = { it.toInt().toString() },
		yAxisStyle = rememberAxisStyle(color = MaterialTheme.colorScheme.onSurface)
	) {
		VerticalBarChart(series = listOf(entries))
	}
}

@Composable
private fun Actions(
	modifier: Modifier,
	component: ProjectHome,
	scope: CoroutineScope,
	snackbarHostState: SnackbarHostState
) {
	val defaultDispatcher = rememberDefaultDispatcher()
	val state by component.state.subscribeAsState()

	Column(modifier = modifier.padding(Ui.Padding.XL)) {
		Text(
			"Actions:",
			style = MaterialTheme.typography.headlineLarge,
			color = MaterialTheme.colorScheme.onSurface
		)
		Spacer(modifier = Modifier.size(Ui.Padding.XL))
		Button(onClick = component::beginProjectExport) {
			Text("Export Story")
		}
	}

	DirectoryPicker(state.showExportDialog) { path ->
		if (path != null) {
			scope.launch(defaultDispatcher) {
				component.exportProject(path)
				snackbarHostState.showSnackbar("Story Exported")
			}
		} else {
			component.endProjectExport()
		}
	}
}