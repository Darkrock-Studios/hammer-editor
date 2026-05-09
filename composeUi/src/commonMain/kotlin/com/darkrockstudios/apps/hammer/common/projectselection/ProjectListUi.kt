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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.reauthentication.ReauthenticationUi

private val WideContentPadding: Dp = Ui.Padding.XXL
private val NarrowContentPadding: Dp = Ui.Padding.XL

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
				if (state.projects.isEmpty()) {
					item(key = "empty") {
						EmptyState(horizontalPadding = horizontalPadding)
					}
				}

				items(
					count = state.projects.size,
					key = { index -> state.projects[index].definition.name.hashCode() }
				) { index ->
					ProjectIndexRow(
						isWide = isWide,
						index = index,
						projectData = state.projects[index],
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
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				horizontal = horizontalPadding,
				vertical = Ui.Padding.L,
			),
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
		if (isServerSynced) {
			RefreshAffordance(onClick = onSync)
		}
		if (showCreate) {
			HdHairlineButton(
				label = "＋  ${Res.string.projects_list_create_button.get()}",
				onClick = onCreate,
				emphasised = true,
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
