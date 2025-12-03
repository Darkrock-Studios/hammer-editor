package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.animation.core.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectHome
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator
import dev.icerock.moko.resources.compose.stringResource
import io.github.koalaplot.core.bar.DefaultBar
import io.github.koalaplot.core.bar.VerticalBarPlot
import io.github.koalaplot.core.gestures.GestureConfig
import io.github.koalaplot.core.pie.BezierLabelConnector
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.generateHueColorPalette
import io.github.koalaplot.core.xygraph.*

private val spanAll: (LazyGridItemSpanScope) -> GridItemSpan = { GridItemSpan(it.maxLineSpan) }

@Composable
fun ProjectStatsUi(
	modifier: Modifier,
	state: ProjectHome.State,
	otherContent: (@Composable () -> Unit)? = null
) {
	LazyVerticalGrid(
		columns = GridCells.Adaptive(300.dp),
		modifier = modifier.fillMaxHeight(),
		contentPadding = PaddingValues(horizontal = Ui.Padding.XL)
	) {
		item(span = spanAll) {
			Column {
				val screen = LocalScreenCharacteristic.current
				when (screen.windowWidthClass) {
					WindowWidthSizeClass.Companion.Compact -> {
						HeaderUi(
							state.projectDef.name,
							"\uD83C\uDFE1",
							Modifier.Companion.padding(top = Ui.Padding.L)
						)
					}

					else -> {
						Text(
							state.projectDef.name,
							style = MaterialTheme.typography.displayMedium,
							color = MaterialTheme.colorScheme.onSurface
						)
					}
				}

				Spacer(modifier = Modifier.Companion.size(Ui.Padding.XL))

				Text(
					stringResource(MR.strings.project_home_stat_created, state.created),
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface
				)
				Spacer(modifier = Modifier.Companion.size(Ui.Padding.XL))

				Text(
					MR.strings.project_home_stat_header.get(),
					style = MaterialTheme.typography.headlineLarge,
					color = MaterialTheme.colorScheme.onSurface
				)
			}
		}

		item {
			NumericStatsBlock(MR.strings.project_home_stat_num_scenes.get(), state.numberOfScenes)
		}

		item {
			NumericStatsBlock(MR.strings.project_home_stat_total_words.get(), state.totalWords)
		}

		item {
			GenericStatsBlock(MR.strings.project_home_stat_chapter_words.get()) {
				WordsInChaptersChart(state = state)
			}
		}

		item {
			GenericStatsBlock(MR.strings.project_home_stat_encyclopedia_entries.get()) {
				EncyclopediaChart(state = state)
			}
		}

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

	if (state.wordsByChapter.size > 1) {
		XYGraph(
			modifier = modifier.heightIn(64.dp, 196.dp).focusable(false),
			xAxisModel = CategoryAxisModel(xAxis),
			yAxisModel = FloatLinearAxisModel(range = range),
			xAxisTitle = MR.strings.project_home_stat_chapter_words_x_axis.get(),
			yAxisTitle = MR.strings.project_home_stat_chapter_words_y_axis.get(),
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
}