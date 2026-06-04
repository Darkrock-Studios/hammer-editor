package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
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
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.reauthentication.ReauthenticationUi
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

private val WideContentPadding: Dp = Ui.Padding.XXL
private val NarrowContentPadding: Dp = Ui.Padding.XL

/** Tags the "Create Project" affordance (masthead button when wide, bottom bar when narrow). */
const val CreateProjectButtonTestTag = "create-project-button"

private enum class ProjectsSortMode(
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

	Toaster(component, rootSnackbar)

	val horizontalPadding = if (isWide) WideContentPadding else NarrowContentPadding

	var sortMode by remember { mutableStateOf(ProjectsSortMode.LastAccessedDesc) }
	val sortedProjects by remember(state.projects, sortMode) {
		derivedStateOf { applySort(state.projects, sortMode) }
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
			isServerSynced = state.isServerSynced,
			onSync = component::showProjectsSync,
			onCreate = component::showCreate,
			showCreate = isWide,
			horizontalPadding = horizontalPadding,
			sortMode = sortMode,
			onSortChange = { sortMode = it },
		)

		HdFolioDivider()

		if (isWide) {
			PageHeading(horizontalPadding = horizontalPadding)
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
				if (sortedProjects.isEmpty()) {
					item(key = "empty") {
						EmptyState(horizontalPadding = horizontalPadding)
					}
				}

				items(
					count = sortedProjects.size,
					key = { index -> sortedProjects[index].definition.name.hashCode() }
				) { index ->
					ProjectIndexRow(
						isWide = isWide,
						index = index,
						projectData = sortedProjects[index],
						onProjectClick = component::selectProject,
						onProjectAltClick = component::showProjectDelete,
						onProjectRenameClick = component::showProjectRename,
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
}

@Composable
private fun Masthead(
	entryCount: Int,
	isServerSynced: Boolean,
	onSync: () -> Unit,
	onCreate: () -> Unit,
	showCreate: Boolean,
	horizontalPadding: Dp,
	sortMode: ProjectsSortMode,
	onSortChange: (ProjectsSortMode) -> Unit,
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
			text = "Hammer · Library",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(
			text = entrySummary(entryCount),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		HdSortMenu(
			label = Res.string.projects_list_sort_label,
			options = ProjectsSortMode.entries,
			selected = sortMode,
			onSelect = onSortChange,
		)
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
private fun PageHeading(horizontalPadding: Dp) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				horizontal = horizontalPadding,
				vertical = Ui.Padding.L,
			),
		verticalAlignment = Alignment.Bottom,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(
			text = "§ 00 · Index",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(bottom = 6.dp),
		)
		Text(
			text = Res.string.project_select_list_header.get(),
			style = MaterialTheme.typography.headlineLarge,
			fontWeight = FontWeight.Light,
			color = MaterialTheme.colorScheme.onSurface,
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
				text = "Title",
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.weight(1f),
			)
			if (showLastOpen) {
				HdMonoLabel(
					text = "Last Open",
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
			text = "— Empty Index —",
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
			text = "Library",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(
			text = if (isServerSynced) "Sync · On" else "Local",
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

private fun entrySummary(count: Int): String = when (count) {
	0 -> "0 entries"
	1 -> "1 entry"
	else -> "$count entries"
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
	}
}
