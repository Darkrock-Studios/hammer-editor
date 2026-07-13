package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.filterProjects
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSearchRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortMenu
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortOption
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdTagChip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdToolButton
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.search.parseQuery
import com.darkrockstudios.apps.hammer.common.protocolmismatch.ProtocolMismatchDialog
import com.darkrockstudios.apps.hammer.common.reauthentication.ReauthenticationUi
import com.darkrockstudios.apps.hammer.project_select_project_list_empty
import com.darkrockstudios.apps.hammer.projects_list_column_last_open
import com.darkrockstudios.apps.hammer.projects_list_column_title
import com.darkrockstudios.apps.hammer.projects_list_create_button
import com.darkrockstudios.apps.hammer.projects_list_empty_index
import com.darkrockstudios.apps.hammer.projects_list_entry_count_none
import com.darkrockstudios.apps.hammer.projects_list_entry_count_one
import com.darkrockstudios.apps.hammer.projects_list_entry_count_other
import com.darkrockstudios.apps.hammer.projects_list_footer_library
import com.darkrockstudios.apps.hammer.projects_list_footer_local
import com.darkrockstudios.apps.hammer.projects_list_footer_sync_on
import com.darkrockstudios.apps.hammer.projects_list_masthead_title
import com.darkrockstudios.apps.hammer.projects_list_no_matches
import com.darkrockstudios.apps.hammer.projects_list_search_button
import com.darkrockstudios.apps.hammer.projects_list_search_clear
import com.darkrockstudios.apps.hammer.projects_list_search_close
import com.darkrockstudios.apps.hammer.projects_list_search_placeholder
import com.darkrockstudios.apps.hammer.projects_list_sort_glyph_created_asc
import com.darkrockstudios.apps.hammer.projects_list_sort_glyph_created_desc
import com.darkrockstudios.apps.hammer.projects_list_sort_glyph_opened_asc
import com.darkrockstudios.apps.hammer.projects_list_sort_glyph_opened_desc
import com.darkrockstudios.apps.hammer.projects_list_sort_glyph_words_asc
import com.darkrockstudios.apps.hammer.projects_list_sort_glyph_words_desc
import com.darkrockstudios.apps.hammer.projects_list_sort_label
import com.darkrockstudios.apps.hammer.projects_list_sort_least_recent
import com.darkrockstudios.apps.hammer.projects_list_sort_longest
import com.darkrockstudios.apps.hammer.projects_list_sort_newest
import com.darkrockstudios.apps.hammer.projects_list_sort_oldest
import com.darkrockstudios.apps.hammer.projects_list_sort_recently_opened
import com.darkrockstudios.apps.hammer.projects_list_sort_shortest
import com.darkrockstudios.apps.hammer.projects_list_sync_button
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

private val WideContentPadding: Dp = Ui.Padding.XXL
private val NarrowContentPadding: Dp = Ui.Padding.XL

/** Tags the "Create Project" affordance (masthead button when wide, bottom bar when narrow). */
const val CreateProjectButtonTestTag = "create-project-button"

internal enum class ProjectsSortMode(
	override val labelRes: StringResource,
	override val glyphRes: StringResource,
) : HdSortOption {
	LastAccessedDesc(
		Res.string.projects_list_sort_recently_opened,
		Res.string.projects_list_sort_glyph_opened_desc,
	),
	LastAccessedAsc(
		Res.string.projects_list_sort_least_recent,
		Res.string.projects_list_sort_glyph_opened_asc,
	),
	CreatedDesc(
		Res.string.projects_list_sort_newest,
		Res.string.projects_list_sort_glyph_created_desc,
	),
	CreatedAsc(
		Res.string.projects_list_sort_oldest,
		Res.string.projects_list_sort_glyph_created_asc,
	),
	WordCountDesc(
		Res.string.projects_list_sort_longest,
		Res.string.projects_list_sort_glyph_words_desc,
	),
	WordCountAsc(
		Res.string.projects_list_sort_shortest,
		Res.string.projects_list_sort_glyph_words_asc,
	),
}

private fun applySort(projects: List<ProjectData>, mode: ProjectsSortMode): List<ProjectData> = when (mode) {
	// Sentinels keep never-opened projects and projects without cached stats
	// at the bottom regardless of direction, so ascending sorts don't lead with "unknown".
	ProjectsSortMode.LastAccessedDesc -> projects.sortedByDescending { it.metadata.info.lastAccessed }
	ProjectsSortMode.LastAccessedAsc -> projects.sortedBy { it.metadata.info.lastAccessed ?: Instant.DISTANT_FUTURE }
	ProjectsSortMode.CreatedDesc -> projects.sortedByDescending { it.metadata.info.created }
	ProjectsSortMode.CreatedAsc -> projects.sortedBy { it.metadata.info.created }
	ProjectsSortMode.WordCountDesc -> projects.sortedByDescending { it.totalWords }
	ProjectsSortMode.WordCountAsc -> projects.sortedBy { it.totalWords ?: Int.MAX_VALUE }
}

@Composable
fun ProjectListUi(
	component: ProjectsList,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier
) {
	// Cut at WindowWidthSizeClass.Compact (600dp) rather than the project-wide
	// 720dp `isWide` so portrait tablets keep the desktop masthead instead of
	// dropping to the phone layout.
	val widthClass = LocalScreenCharacteristic.current.windowWidthClass
	val isWide = widthClass != WindowWidthSizeClass.Compact
	val state by component.state.subscribeAsState()
	val scope = rememberCoroutineScope()

	Toaster(component, rootSnackbar)

	val horizontalPadding = if (isWide) WideContentPadding else NarrowContentPadding

	var sortMode by remember { mutableStateOf(ProjectsSortMode.LastAccessedDesc) }
	var showSearchBar by rememberSaveable { mutableStateOf(false) }
	var query by rememberSaveable { mutableStateOf("") }
	val parsed = remember(query) { parseQuery(query) }
	// Every input is a remember key, so derivedStateOf would add nothing here.
	val visibleProjects = remember(state.projects, sortMode, parsed) {
		applySort(filterProjects(state.projects, parsed), sortMode)
	}
	val onTagClick: (String) -> Unit = { tag ->
		showSearchBar = true
		if (parsed.tags.none { it.equals(tag, ignoreCase = true) }) {
			query = "$query #$tag".trim()
		}
	}

	// Same scroll-away pattern as the scene list outline button: hide the
	// bar when the user scrolls down, reveal it on any upward scroll.
	var createBarVisible by remember { mutableStateOf(true) }
	val scrollConnection = remember {
		object : NestedScrollConnection {
			override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
				if (available.y < -1f) createBarVisible = false
				else if (available.y > 1f) createBarVisible = true
				return Offset.Zero
			}
		}
	}

	Column(
		modifier = modifier.fillMaxSize(),
	) {
		Masthead(
			entryCount = state.projects.size,
			showEntryCount = isWide,
			isServerSynced = state.isServerSynced,
			onSync = component::showProjectsSync,
			onCreate = component::showCreate,
			showCreate = isWide,
			horizontalPadding = horizontalPadding,
			sortMode = sortMode,
			onSortChange = { sortMode = it },
			showSort = isWide,
			searchActive = showSearchBar,
			onToggleSearch = {
				if (showSearchBar) {
					showSearchBar = false
					query = ""
				} else {
					showSearchBar = true
				}
			},
		)

		HdFolioDivider()

		AnimatedVisibility(visible = showSearchBar) {
			SearchStrip(
				query = query,
				onQueryChange = { query = it },
				parsedTags = parsed.tags,
				onClose = {
					showSearchBar = false
					query = ""
				},
				horizontalPadding = horizontalPadding,
				showSort = !isWide,
				sortMode = sortMode,
				onSortChange = { sortMode = it },
			)
		}

		ColumnHeader(showLastOpen = isWide)

		Box(modifier = Modifier.weight(1f)) {
			val listState: LazyListState = rememberLazyListState()
			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.nestedScroll(scrollConnection),
				state = listState,
			) {
				if (visibleProjects.isEmpty()) {
					item(key = "empty") {
						if (state.projects.isEmpty()) {
							EmptyState(horizontalPadding = horizontalPadding)
						} else {
							NoMatchesState(horizontalPadding = horizontalPadding)
						}
					}
				}

				items(
					count = visibleProjects.size,
					key = { index -> visibleProjects[index].definition.name.hashCode() }
				) { index ->
					ProjectIndexRow(
						isWide = isWide,
						index = index,
						projectData = visibleProjects[index],
						onProjectClick = component::selectProject,
						onProjectAltClick = component::showProjectDelete,
						onProjectRenameClick = component::showProjectRename,
						onTagClick = onTagClick,
					)
				}
			}
			MpScrollBarList(
				modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
				state = listState,
			)
		}

		if (!isWide) {
			AnimatedVisibility(
				visible = createBarVisible,
				enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
				exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
			) {
				BottomCreateBar(
					onCreate = component::showCreate,
					horizontalPadding = horizontalPadding,
				)
			}
		}

		FooterFolio(
			horizontalPadding = horizontalPadding,
			isServerSynced = state.isServerSynced,
		)
	}

	ModalContent(component, rootSnackbar)

	ImportStoryDialog(
		visible = state.showImportDialog,
		projectName = state.importProjectName,
		options = state.importOptions,
		preview = state.importPreview,
		onProjectNameChange = component::updateImportProjectName,
		onCancel = component::cancelImportDialog,
		onOptionsChange = component::updateImportOptions,
		onConfirm = { scope.launch { component.confirmImportDialog() } },
	)
	ImportFilePicker(
		show = state.showImportFilePicker,
		scope = scope,
		onFileSelected = component::selectImportFile,
		onCancel = component::cancelImportFilePicker,
	)
}

@Composable
internal fun Masthead(
	entryCount: Int,
	showEntryCount: Boolean,
	isServerSynced: Boolean,
	onSync: () -> Unit,
	onCreate: () -> Unit,
	showCreate: Boolean,
	horizontalPadding: Dp,
	sortMode: ProjectsSortMode,
	onSortChange: (ProjectsSortMode) -> Unit,
	showSort: Boolean,
	searchActive: Boolean,
	onToggleSearch: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(Ui.TOP_BAR_HEIGHT)
			.padding(horizontal = horizontalPadding),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(
			text = Res.string.projects_list_masthead_title.get(),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.weight(1f))
		if (showEntryCount) {
			HdMonoLabel(
				text = entrySummary(entryCount),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		SearchAffordance(active = searchActive, onClick = onToggleSearch)
		if (showSort) {
			HdSortMenu(
				label = Res.string.projects_list_sort_label,
				options = ProjectsSortMode.entries,
				selected = sortMode,
				onSelect = onSortChange,
			)
		}
		if (isServerSynced) {
			RefreshAffordance(onClick = onSync)
		}
		if (showCreate) {
			HdHairlineButton(
				label = "＋  ${Res.string.projects_list_create_button.get()}",
				onClick = onCreate,
				emphasised = true,
				modifier = Modifier.testTag(CreateProjectButtonTestTag),
			)
		}
	}
}

@Composable
private fun RefreshAffordance(onClick: () -> Unit) {
	Box(
		modifier = Modifier
			.size(32.dp)
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				shape = RectangleShape,
			)
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			imageVector = Icons.Default.Refresh,
			contentDescription = Res.string.projects_list_sync_button.get(),
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(16.dp),
		)
	}
}

@Composable
private fun SearchAffordance(active: Boolean, onClick: () -> Unit) {
	HdToolButton(
		active = active,
		onClick = onClick,
		modifier = Modifier.testTag("projects-list-search-toggle"),
	) {
		Icon(
			imageVector = Icons.Default.Search,
			contentDescription = Res.string.projects_list_search_button.get(),
			tint = if (active) {
				MaterialTheme.colorScheme.onSurface
			} else {
				MaterialTheme.colorScheme.onSurfaceVariant
			},
			modifier = Modifier.size(16.dp),
		)
	}
}

@Composable
internal fun SearchStrip(
	query: String,
	onQueryChange: (String) -> Unit,
	parsedTags: List<String>,
	onClose: () -> Unit,
	horizontalPadding: Dp,
	showSort: Boolean,
	sortMode: ProjectsSortMode,
	onSortChange: (ProjectsSortMode) -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontalPadding, vertical = Ui.Padding.L),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdSearchRow(
			query = query,
			onQueryChange = onQueryChange,
			placeholder = Res.string.projects_list_search_placeholder.get(),
			clearContentDescription = Res.string.projects_list_search_clear.get(),
			onCollapse = onClose,
			collapseContentDescription = Res.string.projects_list_search_close.get(),
			modifier = Modifier.fillMaxWidth(),
			testTag = "projects-list-search",
		)
		if (showSort || parsedTags.isNotEmpty()) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
			) {
				parsedTags.forEach { tag ->
					HdTagChip(label = tag, active = true)
				}
				if (showSort) {
					Spacer(modifier = Modifier.weight(1f))
					HdSortMenu(
						label = Res.string.projects_list_sort_label,
						options = ProjectsSortMode.entries,
						selected = sortMode,
						onSelect = onSortChange,
					)
				}
			}
		}
	}
}

@Composable
private fun NoMatchesState(horizontalPadding: Dp) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontalPadding, vertical = Ui.Padding.XXL),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Text(
			text = Res.string.projects_list_no_matches.get(),
			style = MaterialTheme.typography.headlineSmall,
			fontStyle = FontStyle.Italic,
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun ColumnHeader(showLastOpen: Boolean) {
	Column(modifier = Modifier.fillMaxWidth()) {
		HorizontalDivider(
			modifier = Modifier.fillMaxWidth(),
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					start = 4.dp + Ui.Padding.XL,
					end = Ui.Padding.L,
					top = Ui.Padding.M,
					bottom = Ui.Padding.M,
				),
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdMonoLabel(
				text = "№",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.width(40.dp),
			)
			HdMonoLabel(
				text = Res.string.projects_list_column_title.get(),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.weight(1f),
			)
			if (showLastOpen) {
				HdMonoLabel(
					text = Res.string.projects_list_column_last_open.get(),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Spacer(modifier = Modifier.width(48.dp))
		}
		HorizontalDivider(
			modifier = Modifier.fillMaxWidth(),
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
	}
}

@Composable
private fun EmptyState(horizontalPadding: Dp) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontalPadding, vertical = Ui.Padding.XXL),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(
			text = Res.string.projects_list_empty_index.get(),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = Res.string.project_select_project_list_empty.get(),
			style = MaterialTheme.typography.headlineSmall,
			fontStyle = FontStyle.Italic,
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun FooterFolio(
	horizontalPadding: Dp,
	isServerSynced: Boolean,
) {
	HorizontalDivider(
		modifier = Modifier.fillMaxWidth(),
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = horizontalPadding, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(
			text = Res.string.projects_list_footer_library.get(),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(
			text = if (isServerSynced) {
				Res.string.projects_list_footer_sync_on.get()
			} else {
				Res.string.projects_list_footer_local.get()
			},
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		HdMonoLabel(
			text = "v${BuildMetadata.APP_VERSION}",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun BottomCreateBar(
	onCreate: () -> Unit,
	horizontalPadding: Dp,
) {
	HorizontalDivider(
		modifier = Modifier.fillMaxWidth(),
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outline,
	)
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = horizontalPadding, vertical = Ui.Padding.L),
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(44.dp)
				.testTag(CreateProjectButtonTestTag)
				.background(MaterialTheme.colorScheme.primary)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outline,
					shape = RectangleShape,
				)
				.clickable(onClick = onCreate),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = "＋  ${Res.string.projects_list_create_button.get()}",
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.Medium,
				color = MaterialTheme.colorScheme.onPrimary,
			)
		}
	}
}

@Composable
private fun entrySummary(count: Int): String = when (count) {
	0 -> Res.string.projects_list_entry_count_none.get()
	1 -> Res.string.projects_list_entry_count_one.get()
	else -> Res.string.projects_list_entry_count_other.get(count)
}

@Composable
fun ModalContent(component: ProjectsList, rootSnackbar: RootSnackbarHostState) {
	val state by component.modalRouterState.subscribeAsState()
	val overlay = state.child?.instance
	when (overlay) {
		null, ProjectsList.ModalDestination.None -> {}
		is ProjectsList.ModalDestination.ProjectSync -> {
			ProjectsSyncDialog(component, rootSnackbar)
		}

		is ProjectsList.ModalDestination.ProjectRename -> {
			ProjectRenameDialog(
				component = component,
				projectDef = overlay.projectDef,
				close = { component.dismissProjectRename() }
			)
		}

		is ProjectsList.ModalDestination.ProjectCreate -> {
			ProjectCreateDialog(true, component) {
				component.hideCreate()
			}
		}

		is ProjectsList.ModalDestination.ProjectDelete -> {
			ProjectDeleteDialog(
				component = component,
				projectDef = overlay.projectDef,
				close = { component.dismissProjectDelete() }
			)
		}

		is ProjectsList.ModalDestination.ServerReauth -> {
			ReauthenticationUi(overlay.component)
		}

		is ProjectsList.ModalDestination.ProtocolMismatch -> {
			ProtocolMismatchDialog(overlay.component)
		}
	}
}
