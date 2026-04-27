package com.darkrockstudios.apps.hammer.common.globalsearch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.globalsearch.AnnotatedSnippet
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearch
import com.darkrockstudios.apps.hammer.common.components.globalsearch.GlobalSearchFilter
import com.darkrockstudios.apps.hammer.common.components.globalsearch.SearchResult
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun GlobalSearchUi(component: GlobalSearch) {
	val state by component.state.subscribeAsState()
	var isOpen by remember { mutableStateOf(true) }

	SimpleDialog(
		title = Res.string.global_search_title.get(),
		onCloseRequest = { isOpen = false },
		onDismissed = component::dismiss,
		visible = isOpen,
		modifier = Modifier.wrapContentSize(),
		dialogContainerModifier = Modifier
			.fillMaxSize(0.9f)
			.wrapContentSize(Alignment.TopCenter),
		overridePlatformWidth = true,
		contentAlignment = Alignment.TopCenter,
		dismissOnTapOutside = true,
	) {
		val focusRequester = remember { FocusRequester() }
		LaunchedEffect(Unit) { focusRequester.requestFocus() }

		val filterActive = state.filter != GlobalSearchFilter.All
		var filtersExpanded by remember { mutableStateOf(filterActive) }

		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			OutlinedTextField(
				value = state.query,
				onValueChange = component::onQueryChanged,
				modifier = Modifier
					.weight(1f)
					.focusRequester(focusRequester),
				placeholder = { Text(Res.string.global_search_placeholder.get()) },
				singleLine = true,
				leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
				trailingIcon = {
					if (state.query.isNotEmpty()) {
						IconButton(onClick = { component.onQueryChanged("") }) {
							Icon(
								imageVector = Icons.Filled.Cancel,
								contentDescription = Res.string.global_search_clear.get(),
							)
						}
					}
				},
			)

			Spacer(Modifier.width(Ui.Padding.S))

			IconButton(onClick = { filtersExpanded = !filtersExpanded }) {
				Icon(
					imageVector = Icons.Filled.FilterList,
					contentDescription = Res.string.global_search_filter_toggle.get(),
					tint = if (filterActive) MaterialTheme.colorScheme.primary
					else LocalContentColor.current,
				)
			}
		}

		AnimatedVisibility(visible = filtersExpanded) {
			Column {
				Spacer(modifier = Modifier.size(Ui.Padding.M))
				FilterChipsRow(
					selected = state.filter,
					onSelected = component::onFilterChanged,
				)
			}
		}

		Spacer(modifier = Modifier.size(Ui.Padding.M))

		when {
			state.isSearching && state.results.isEmpty() -> {
				Box(
					modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL),
					contentAlignment = Alignment.Center,
				) {
					CircularProgressIndicator()
				}
			}

			state.query.length < MIN_QUERY_DISPLAY_LENGTH -> {
				EmptyState(Res.string.global_search_too_short.get())
			}

			state.results.isEmpty() -> {
				EmptyState(Res.string.global_search_no_results.get())
			}

			else -> {
				val listState = rememberLazyListState()
				LazyColumn(
					state = listState,
					modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				) {
					items(state.results, key = { it.uniqueKey() }) { result ->
						SearchResultRow(
							result = result,
							onClick = { component.onResultClicked(result) },
						)
					}
				}
			}
		}
	}
}

@Composable
private fun FilterChipsRow(
	selected: GlobalSearchFilter,
	onSelected: (GlobalSearchFilter) -> Unit,
) {
	LazyRow(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
	) {
		items(GlobalSearchFilter.entries.toList(), key = { it.name }) { filter ->
			FilterChip(
				selected = filter == selected,
				onClick = { onSelected(filter) },
				label = { Text(filterLabel(filter).get()) },
			)
		}
	}
}

private fun filterLabel(filter: GlobalSearchFilter): StringResource = when (filter) {
	GlobalSearchFilter.All -> Res.string.global_search_filter_all
	GlobalSearchFilter.Scenes -> Res.string.global_search_filter_scenes
	GlobalSearchFilter.Notes -> Res.string.global_search_filter_notes
	GlobalSearchFilter.Encyclopedia -> Res.string.global_search_filter_encyclopedia
	GlobalSearchFilter.Timeline -> Res.string.global_search_filter_timeline
}

@Composable
private fun EmptyState(message: String) {
	Box(
		modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = message,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
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
		color = MaterialTheme.colorScheme.surfaceContainer,
		shape = MaterialTheme.shapes.medium,
		tonalElevation = Ui.ToneElevation.SMALL,
	) {
		Row(
			modifier = Modifier.padding(Ui.Padding.L),
			verticalAlignment = Alignment.Top,
		) {
			Icon(
				imageVector = sourceIcon(result),
				contentDescription = null,
				modifier = Modifier.padding(top = Ui.Padding.S),
				tint = MaterialTheme.colorScheme.primary,
			)
			Spacer(Modifier.width(Ui.Padding.M))
			Column(modifier = Modifier.weight(1f)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						text = sourceLabel(result).get(),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					Spacer(Modifier.width(Ui.Padding.M))
					Text(
						text = result.title,
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.weight(1f),
					)
				}
				Spacer(Modifier.size(Ui.Padding.S))
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

private fun SearchResult.uniqueKey(): String = when (this) {
	is SearchResult.Scene -> "scene:${sceneItem.id}"
	is SearchResult.Note -> "note:$noteId"
	is SearchResult.EncyclopediaEntry -> "entry:${entryDef.id}"
	is SearchResult.TimelineEvent -> "event:$eventId"
}

private const val MIN_QUERY_DISPLAY_LENGTH = 2
