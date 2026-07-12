package com.darkrockstudios.apps.hammer.common.globalsearch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.globalsearch.AnnotatedSnippet
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearch
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdClearGlyph
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdEntityId
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.hammerMonoFontFamily
import com.darkrockstudios.apps.hammer.global_search_clear
import com.darkrockstudios.apps.hammer.global_search_filter_all
import com.darkrockstudios.apps.hammer.global_search_filter_encyclopedia
import com.darkrockstudios.apps.hammer.global_search_filter_notes
import com.darkrockstudios.apps.hammer.global_search_filter_scenes
import com.darkrockstudios.apps.hammer.global_search_filter_timeline
import com.darkrockstudios.apps.hammer.global_search_no_results
import com.darkrockstudios.apps.hammer.global_search_placeholder
import com.darkrockstudios.apps.hammer.global_search_results_count
import com.darkrockstudios.apps.hammer.global_search_source_encyclopedia
import com.darkrockstudios.apps.hammer.global_search_source_note
import com.darkrockstudios.apps.hammer.global_search_source_scene
import com.darkrockstudios.apps.hammer.global_search_source_timeline
import com.darkrockstudios.apps.hammer.global_search_title
import com.darkrockstudios.apps.hammer.global_search_too_short
import com.darkrockstudios.apps.hammer.ic_editor
import com.darkrockstudios.apps.hammer.ic_encyclopedia
import com.darkrockstudios.apps.hammer.ic_notes
import com.darkrockstudios.apps.hammer.ic_timeline
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.vectorResource

private val DialogMaxWidth = 720.dp

const val GLOBAL_SEARCH_INPUT_TAG = "global-search-input"

@Composable
fun GlobalSearchUi(component: GlobalSearch) {
	val state by component.state.subscribeAsState()
	var isOpen by remember { mutableStateOf(true) }

	AnimatedDialog(
		visible = isOpen,
		onCloseRequest = { isOpen = false },
		onDismissed = component::dismiss,
		contentAlignment = Alignment.TopCenter,
		dismissOnTapOutside = true,
	) {
		val focusRequester = remember { FocusRequester() }
		LaunchedEffect(Unit) { focusRequester.requestFocus() }

		Surface(
			shape = RectangleShape,
			color = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface,
			shadowElevation = Ui.Elevation.LARGE,
			modifier = Modifier
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.XXL)
				.widthIn(max = DialogMaxWidth)
				.fillMaxWidth(),
		) {
			Column {
				Masthead(
					marker = "§ ${Res.string.global_search_title.get().uppercase()}",
					resultCount = state.results.size,
					onClose = { isOpen = false },
				)
				HdFolioDivider()

				Column(
					modifier = Modifier.padding(
						start = 26.dp,
						end = 26.dp,
						top = 22.dp,
						bottom = 22.dp,
					),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				) {
					HdSearchField(
						value = state.query,
						onValueChange = component::onQueryChanged,
						placeholder = Res.string.global_search_placeholder.get(),
						onClear = { component.onQueryChanged("") },
						clearContentDescription = Res.string.global_search_clear.get(),
						focusRequester = focusRequester,
						testTag = GLOBAL_SEARCH_INPUT_TAG,
						modifier = Modifier.fillMaxWidth(),
					)

					HdHairlineSegmentedPicker(
						options = GlobalSearchFilter.entries.toList(),
						selected = state.filter,
						onSelect = component::onFilterChanged,
						label = { filterLabel(it).get() },
						modifier = Modifier.fillMaxWidth(),
					)

					if (state.parsedTags.isNotEmpty()) {
						LazyRow(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
						) {
							items(state.parsedTags, key = { it }) { tag ->
								HdTagChip(label = tag)
							}
						}
					}

					ResultsBody(state = state, onResultClick = component::onResultClicked)
				}
			}
		}
	}
}

@Composable
private fun Masthead(
	marker: String,
	resultCount: Int,
	onClose: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 26.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text(
			text = marker,
			fontFamily = hammerMonoFontFamily(),
			fontSize = 10.sp,
			fontWeight = FontWeight.Medium,
			letterSpacing = 1.8.sp,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.weight(1f))
		if (resultCount > 0) {
			Text(
				text = Res.string.global_search_results_count.get(resultCount).uppercase(),
				fontFamily = hammerMonoFontFamily(),
				fontSize = 10.sp,
				letterSpacing = 1.8.sp,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		HdClearGlyph(
			onClick = onClose,
			glyphSize = 9.dp,
		)
	}
}

@Composable
private fun ResultsBody(
	state: GlobalSearch.State,
	onResultClick: (SearchResult) -> Unit,
) {
	val hasUsableQuery = state.parsedTags.any { it.isNotEmpty() } ||
		state.parsedText.length >= MIN_QUERY_DISPLAY_LENGTH

	when {
		state.isSearching && state.results.isEmpty() -> {
			Box(
				modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL),
				contentAlignment = Alignment.Center,
			) {
				CircularProgressIndicator()
			}
		}

		!hasUsableQuery -> EmptyState(Res.string.global_search_too_short.get())

		state.results.isEmpty() -> EmptyState(Res.string.global_search_no_results.get())

		else -> {
			val listState = rememberLazyListState()
			LazyColumn(
				state = listState,
				modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.S),
			) {
				items(state.results, key = { it.uniqueKey() }) { result ->
					SearchResultRow(
						result = result,
						onClick = { onResultClick(result) },
					)
				}
			}
		}
	}
}

@Composable
private fun EmptyState(message: String) {
	Box(
		modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL),
		contentAlignment = Alignment.Center,
	) {
		HdMonoLabel(text = message)
	}
}

@Composable
private fun SearchResultRow(
	result: SearchResult,
	onClick: () -> Unit,
) {
	Surface(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick),
		color = MaterialTheme.colorScheme.surfaceContainerLow,
		shape = RectangleShape,
		border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
	) {
		Column(modifier = Modifier.padding(Ui.Padding.L)) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(
					imageVector = sourceIcon(result),
					contentDescription = null,
					modifier = Modifier.size(14.dp),
					tint = MaterialTheme.colorScheme.primary,
				)
				Spacer(Modifier.width(Ui.Padding.S))
				HdMonoLabel(text = sourceLabel(result).get())
				Spacer(Modifier.weight(1f))
				HdEntityId(prefix = entityPrefix(result), id = entityId(result))
			}
			Spacer(Modifier.height(Ui.Padding.S))
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
			Spacer(Modifier.height(Ui.Padding.M))
			Text(
				text = result.title,
				style = MaterialTheme.typography.titleSmall,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(Modifier.height(Ui.Padding.S))
			Text(
				text = annotatedSnippet(result.snippet),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun annotatedSnippet(snippet: AnnotatedSnippet): AnnotatedString {
	val highlight = SpanStyle(
		background = MaterialTheme.colorScheme.primaryContainer,
		color = MaterialTheme.colorScheme.onPrimaryContainer,
		fontWeight = FontWeight.Bold,
	)
	return buildAnnotatedString {
		val text = snippet.text
		val start = snippet.matchStart.coerceIn(0, text.length)
		val end = snippet.matchEnd.coerceIn(start, text.length)
		append(text.substring(0, start))
		withStyle(highlight) { append(text.substring(start, end)) }
		append(text.substring(end))
	}
}

@Composable
private fun sourceIcon(result: SearchResult): ImageVector = when (result) {
	is SearchResult.Scene -> vectorResource(Res.drawable.ic_editor)
	is SearchResult.Note -> vectorResource(Res.drawable.ic_notes)
	is SearchResult.EncyclopediaEntry -> vectorResource(Res.drawable.ic_encyclopedia)
	is SearchResult.TimelineEvent -> vectorResource(Res.drawable.ic_timeline)
}

private fun sourceLabel(result: SearchResult): StringResource = when (result) {
	is SearchResult.Scene -> Res.string.global_search_source_scene
	is SearchResult.Note -> Res.string.global_search_source_note
	is SearchResult.EncyclopediaEntry -> Res.string.global_search_source_encyclopedia
	is SearchResult.TimelineEvent -> Res.string.global_search_source_timeline
}

private fun filterLabel(filter: GlobalSearchFilter): StringResource = when (filter) {
	GlobalSearchFilter.All -> Res.string.global_search_filter_all
	GlobalSearchFilter.Scenes -> Res.string.global_search_filter_scenes
	GlobalSearchFilter.Notes -> Res.string.global_search_filter_notes
	GlobalSearchFilter.Encyclopedia -> Res.string.global_search_filter_encyclopedia
	GlobalSearchFilter.Timeline -> Res.string.global_search_filter_timeline
}

private fun entityPrefix(result: SearchResult): String = when (result) {
	is SearchResult.Scene -> "SCN"
	is SearchResult.Note -> "NOTE"
	is SearchResult.EncyclopediaEntry -> "ENT"
	is SearchResult.TimelineEvent -> "EVT"
}

private fun entityId(result: SearchResult): Int = when (result) {
	is SearchResult.Scene -> result.sceneItem.id
	is SearchResult.Note -> result.noteId
	is SearchResult.EncyclopediaEntry -> result.entryDef.id
	is SearchResult.TimelineEvent -> result.eventId
}

private fun SearchResult.uniqueKey(): String = when (this) {
	is SearchResult.Scene -> "scene:${sceneItem.id}"
	is SearchResult.Note -> "note:$noteId"
	is SearchResult.EncyclopediaEntry -> "entry:${entryDef.id}"
	is SearchResult.TimelineEvent -> "event:$eventId"
}

private const val MIN_QUERY_DISPLAY_LENGTH = 2
