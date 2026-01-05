package com.darkrockstudios.apps.hammer.common.notes

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.notes.BrowseNotes
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.util.format
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BrowseNotesUi(
	component: BrowseNotes,
	modifier: Modifier = Modifier,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
) {
	val state by component.state.subscribeAsState()

	var showSearchBar by rememberSaveable { mutableStateOf(false) }
	var searchQuery by rememberSaveable { mutableStateOf("") }
	var sortAscending by rememberSaveable { mutableStateOf(false) }

	val filteredSortedNotes by rememberSaveable(state.notes, searchQuery, sortAscending) {
		derivedStateOf {
			val filtered = if (searchQuery.isBlank()) {
				state.notes
			} else {
				state.notes.filter { it.content.contains(searchQuery, ignoreCase = true) }
			}

			if (sortAscending) {
				filtered.sortedBy { it.created }
			} else {
				filtered.sortedByDescending { it.created }
			}
		}
	}

	Column(modifier = modifier.padding(top = Ui.Padding.L)) {
		AnimatedContent(
			targetState = showSearchBar,
			modifier = Modifier.fillMaxWidth().height(Ui.TOP_BAR_HEIGHT).padding(horizontal = Ui.Padding.XL),
			transitionSpec = {
				fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
			},
			contentAlignment = Alignment.Center,
			label = "SearchBarAnimation"
		) { isSearchVisible ->
			if (isSearchVisible) {
				NotesSearchBar(
					modifier = Modifier.fillMaxSize(),
					searchQuery = searchQuery,
					onSearchQueryChange = { searchQuery = it },
					sortAscending = sortAscending,
					onSortToggle = { sortAscending = !sortAscending },
					onClose = {
						showSearchBar = false
						searchQuery = ""
					}
				)
			} else {
				Row(
					modifier = Modifier.fillMaxWidth().fillMaxHeight(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					HeaderUi(Res.string.notes_header, "\uD83D\uDCD1")

					IconButton(onClick = { showSearchBar = true }) {
						Icon(
							imageVector = Icons.Default.Search,
							contentDescription = Res.string.notes_search_button.get(),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
				}
			}
		}

		LazyVerticalStaggeredGrid(
			columns = StaggeredGridCells.Adaptive(400.dp),
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(horizontal = Ui.Padding.XL)
		) {
			if (filteredSortedNotes.isEmpty()) {
				item {
					Text(
						Res.string.notes_list_empty.get(),
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onBackground
					)
				}
			}

			items(
				count = filteredSortedNotes.size,
			) { index ->
				val note = filteredSortedNotes[index]
				NoteItem(
					note = note,
					sharedTransitionScope = sharedTransitionScope,
					animatedVisibilityScope = animatedVisibilityScope,
				) {
					component.viewNote(note.id)
				}
			}
		}
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NoteItem(
	note: NoteContent,
	modifier: Modifier = Modifier,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	viewNote: () -> Unit,
) {
	with(sharedTransitionScope) {
		Card(
			modifier = modifier
				.fillMaxWidth()
				.padding(Ui.Padding.XL)
				.sharedElement(
					sharedContentState = rememberSharedContentState(key = "note-card-${note.id}"),
					animatedVisibilityScope = animatedVisibilityScope
				)
				.clickable { viewNote() },
			elevation = CardDefaults.elevatedCardElevation(Ui.Elevation.SMALL)
		) {
			Column(
				modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth()
			) {
				Row {
					Text(
						note.content,
						modifier = Modifier
							.weight(1f)
							.sharedElement(
								sharedContentState = rememberSharedContentState(key = "note-content-${note.id}"),
								animatedVisibilityScope = animatedVisibilityScope
							),
						style = MaterialTheme.typography.bodyMedium
							.copy(color = MaterialTheme.colorScheme.onBackground),
						maxLines = 12,
						overflow = TextOverflow.Ellipsis
					)
				}
				Spacer(modifier = Modifier.size(Ui.Padding.L))

				val date = remember(note.created) {
					val created = note.created.toLocalDateTime(TimeZone.currentSystemDefault())
					created.format("dd MMM `yy")
				}

				Text(
					date,
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier.sharedElement(
						sharedContentState = rememberSharedContentState(key = "note-date-${note.id}"),
						animatedVisibilityScope = animatedVisibilityScope
					)
				)
			}
		}
	}
}


@Composable
fun BrowseNotesFab(
	component: BrowseNotes,
	modifier: Modifier,
) {
	FloatingActionButton(
		modifier = modifier,
		onClick = { component.showCreate() },
	) {
		Icon(Icons.Filled.Create, Res.string.notes_create_note_button.get())
	}
}

@Composable
private fun NotesSearchBar(
	searchQuery: String,
	onSearchQueryChange: (String) -> Unit,
	sortAscending: Boolean,
	onSortToggle: () -> Unit,
	onClose: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier
			.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		OutlinedTextField(
			value = searchQuery,
			onValueChange = onSearchQueryChange,
			modifier = Modifier.weight(1f),
			placeholder = { Text(Res.string.notes_search_placeholder.get()) },
			singleLine = true,
			textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = TextUnit.Unspecified),
			trailingIcon = {
				if (searchQuery.isNotEmpty()) {
					IconButton(
						onClick = {
							onSearchQueryChange("")
						}
					) {
						Icon(
							imageVector = Icons.Filled.Clear,
							contentDescription = Res.string.notes_search_clear.get(),
						)
					}
				}
			}
		)

		Spacer(modifier = Modifier.width(Ui.Padding.M))

		IconButton(onClick = onSortToggle) {
			Icon(
				imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
				contentDescription = if (sortAscending)
					Res.string.notes_sort_ascending.get()
				else
					Res.string.notes_sort_descending.get(),
				tint = MaterialTheme.colorScheme.onSurface
			)
		}

		IconButton(onClick = onClose) {
			Icon(
				imageVector = Icons.Default.Close,
				contentDescription = Res.string.notes_search_close.get(),
				tint = MaterialTheme.colorScheme.onSurface
			)
		}
	}
}