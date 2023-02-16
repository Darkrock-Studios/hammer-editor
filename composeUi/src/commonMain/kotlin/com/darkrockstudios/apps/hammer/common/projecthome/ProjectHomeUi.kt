package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import io.github.koalaplot.core.pie.BezierLabelConnector
import io.github.koalaplot.core.pie.PieChart
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.generateHueColorPalette

@Composable
fun ProjectHomeUi(
    component: ProjectHome,
) {
    val state by component.state.subscribeAsState()
    val screen = LocalScreenCharacteristic.current

    if (screen.isWide) {
        Row(
            modifier = Modifier.fillMaxSize()
                .padding(Ui.Padding.XL)
                .verticalScroll(rememberScrollState(0))
        ) {
            Stats(
                modifier = Modifier.weight(1f),
                state = state
            )
            Actions(
                modifier = Modifier.weight(1f),
                state = state
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(Ui.Padding.XL)
                .verticalScroll(rememberScrollState(0))
        ) {
            Stats(
                modifier = Modifier.fillMaxWidth(),
                state = state
            )
            Actions(
                modifier = Modifier.fillMaxWidth(),
                state = state
            )
        }
    }
}

@Composable
private fun Stats(modifier: Modifier, state: ProjectHome.State) {
    Column(modifier = modifier.fillMaxSize().padding(Ui.Padding.XL)) {
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
        Text(
            "Scenes: ${state.numberOfScenes}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.size(Ui.Padding.XL))
        Text(
            "Encyclopedia Entries:",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.size(Ui.Padding.L))
        EncyclopediaChart(Modifier, state)
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
        modifier = modifier,
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

@Composable
private fun Actions(modifier: Modifier, state: ProjectHome.State) {
    Column(modifier = modifier.padding(Ui.Padding.XL)) {
        Text(
            "Actions:",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.size(Ui.Padding.XL))
        Button(onClick = {}) {
            Text("Export Story")
        }
    }
}