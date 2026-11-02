package com.darkrockstudios.apps.hammer.common.preview.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.timeline.SelectionSummary
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineRow
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.timeline.SelectionStrip
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TimelineDrawing
import com.darkrockstudios.apps.hammer.common.timeline.drawing.TimelineDrawingPane
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutInput
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import com.darkrockstudios.apps.hammer.common.timeline.layout.samples.TimelineLayoutSamples

private const val DESKTOP_WIDTH = 840
private const val PHONE_WIDTH = 360
private const val TALL = 1400

/** Laid out with the estimate measurer, as the geometry goldens are, so the shapes match them. */
@Composable
private fun SampleDrawing(input: TimelineLayoutInput, dark: Boolean = false) {
	AppTheme(globalSettingsPreview, dark) {
		val layout = remember(input) { TimelineLayoutEngine.layout(input, MonospaceEstimateMeasurer()) }
		val colors = LocalHammerColors.current
		Box(modifier = Modifier.width(input.widthDp.dp).background(MaterialTheme.colorScheme.surface)) {
			TimelineDrawing(
				layout = layout,
				laneColor = colors::laneColor,
				selectedId = input.selectedId,
				onTap = {},
				titles = input.events.associate { it.id to it.title },
			)
		}
	}
}

@Preview(widthDp = DESKTOP_WIDTH, heightDp = 900)
@Composable
fun TimelineDrawingMinimalPreview() = SampleDrawing(TimelineLayoutSamples.minimal())

@Preview(widthDp = PHONE_WIDTH, heightDp = 900)
@Composable
fun TimelineDrawingMinimalPhonePreview() = SampleDrawing(TimelineLayoutSamples.minimal(phone = true))

@Preview(widthDp = DESKTOP_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingHollowmerePreview() = SampleDrawing(TimelineLayoutSamples.hollowmere())

@Preview(widthDp = DESKTOP_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingHollowmereSequencePreview() =
	SampleDrawing(TimelineLayoutSamples.hollowmere().copy(view = TimelineView.SEQUENCE))

@Preview(widthDp = DESKTOP_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingHollowmereDarkPreview() = SampleDrawing(TimelineLayoutSamples.hollowmere(), dark = true)

@Preview(widthDp = PHONE_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingHollowmerePhonePreview() = SampleDrawing(TimelineLayoutSamples.hollowmere(phone = true))

@Preview(widthDp = DESKTOP_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingLeviathanPreview() = SampleDrawing(TimelineLayoutSamples.leviathan())

@Preview(widthDp = PHONE_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingLeviathanPhonePreview() = SampleDrawing(TimelineLayoutSamples.leviathan(phone = true))

@Preview(widthDp = DESKTOP_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingInsurgencyEverythingPreview() = SampleDrawing(TimelineLayoutSamples.insurgencyEverything())

@Preview(widthDp = PHONE_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingInsurgencyEverythingPhonePreview() =
	SampleDrawing(TimelineLayoutSamples.insurgencyEverything(phone = true))

@Preview(widthDp = DESKTOP_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingInsurgencyStoryPreview() = SampleDrawing(TimelineLayoutSamples.insurgencyStory())

@Preview(widthDp = PHONE_WIDTH, heightDp = TALL)
@Composable
fun TimelineDrawingInsurgencyStoryPhonePreview() = SampleDrawing(TimelineLayoutSamples.insurgencyStory(phone = true))

@Preview(widthDp = DESKTOP_WIDTH, heightDp = 900)
@Composable
fun TimelineDrawingOverlappingStackPreview() = SampleDrawing(TimelineLayoutSamples.overlappingStack())

@Preview(widthDp = PHONE_WIDTH, heightDp = 900)
@Composable
fun TimelineDrawingOverlappingStackPhonePreview() = SampleDrawing(TimelineLayoutSamples.overlappingStack(phone = true))

private const val HOLLOWMERE_FINDS_BODY = 25

private val hollowmereSelection = TimelineRow(
	event = TimeLineEvent(
		id = HOLLOWMERE_FINDS_BODY,
		order = HOLLOWMERE_FINDS_BODY,
		title = "Finds the body",
		lane = TimelineLayoutSamples.HOLLOWMERE_PIKE,
		milestone = true,
	),
	title = "Finds the body",
	subtitle = null,
	whenText = "23:27",
	laneIndex = 3,
)

private val hollowmereReferences = listOf(
	CatalogItem(CatalogKind.Entry, 1, "Pike", entryType = EntryType.PERSON),
	CatalogItem(CatalogKind.Entry, 2, "Lord Hollowmere", entryType = EntryType.PERSON),
	CatalogItem(CatalogKind.Entry, 3, "Col. Hayes", entryType = EntryType.PERSON),
	CatalogItem(CatalogKind.Entry, 4, "The study", entryType = EntryType.PLACE),
	CatalogItem(CatalogKind.Entry, 5, "The wine cellar", entryType = EntryType.PLACE),
)

/** The phone drawing as the overview shows a selection: the strip over its foot, the drawing padded clear of it. */
@Composable
private fun PhoneSelectionDrawing(dark: Boolean) {
	val input = remember { TimelineLayoutSamples.hollowmere(phone = true, selectedId = HOLLOWMERE_FINDS_BODY) }
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, dark) {
			val layout = remember(input) { TimelineLayoutEngine.layout(input, MonospaceEstimateMeasurer()) }
			val colors = LocalHammerColors.current
			val density = LocalDensity.current
			var stripHeight by remember { mutableStateOf(0.dp) }
			Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
				TimelineDrawingPane(
					layout = layout,
					laneColor = colors::laneColor,
					selectedId = input.selectedId,
					onTap = {},
					titles = input.events.associate { it.id to it.title },
					bottomPadding = stripHeight,
					modifier = Modifier.fillMaxSize(),
				)
				Box(
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.fillMaxWidth()
						.onSizeChanged { stripHeight = with(density) { it.height.toDp() } },
				) {
					SelectionStrip(
						row = hollowmereSelection,
						summary = SelectionSummary(hollowmereSelection, hollowmereReferences, excerpt = null),
						onOpen = {},
						onClear = {},
						onReference = {},
						maxChips = 4,
					)
				}
			}
		}
	}
}

@Preview(widthDp = PHONE_WIDTH, heightDp = 800)
@Composable
fun TimelineDrawingHollowmerePhoneSelectedPreview() = PhoneSelectionDrawing(dark = false)

@Preview(widthDp = PHONE_WIDTH, heightDp = 800)
@Composable
fun TimelineDrawingHollowmerePhoneSelectedDarkPreview() = PhoneSelectionDrawing(dark = true)
