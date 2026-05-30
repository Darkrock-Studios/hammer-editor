package com.darkrockstudios.apps.hammer.common.projectsync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPrimaryAction
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.sync_conflict_tab_local
import com.darkrockstudios.apps.hammer.sync_conflict_tab_remote

typealias EntityPaneBody<T> = @Composable (
	modifier: Modifier,
	entityConflict: ProjectSynchronization.EntityConflict<T>,
	component: ProjectSynchronization
) -> Unit

private enum class Side { REMOTE, LOCAL }

/**
 * Side-by-side Remote / Local panes on wide windows, segmented tabs on
 * compact. Each pane carries its own `USE` punch button. [bottomBar] is
 * an optional peer below the split — Scene uses it for the Content /
 * Metadata / References picker.
 */
@Composable
fun <T : ApiProjectEntity> EntityConflict(
	entityConflict: ProjectSynchronization.EntityConflict<T>,
	component: ProjectSynchronization,
	screenCharacteristics: WindowSizeClass,
	onUseRemote: () -> Unit,
	onUseLocal: () -> Unit,
	LocalBody: EntityPaneBody<T>,
	RemoteBody: EntityPaneBody<T>,
	bottomBar: (@Composable () -> Unit)? = null,
) {
	val remoteLabel = Res.string.sync_conflict_tab_remote.get()
	val localLabel = Res.string.sync_conflict_tab_local.get()
	Column(modifier = Modifier.fillMaxSize()) {
		Box(modifier = Modifier.weight(1f)) {
			when (screenCharacteristics.widthSizeClass) {
				WindowWidthSizeClass.Compact -> CompactConflictUi(
					entityConflict = entityConflict,
					component = component,
					onUseRemote = onUseRemote,
					onUseLocal = onUseLocal,
					LocalBody = LocalBody,
					RemoteBody = RemoteBody,
					remoteLabel = remoteLabel,
					localLabel = localLabel,
				)

				else -> ExpandedConflictUi(
					entityConflict = entityConflict,
					component = component,
					onUseRemote = onUseRemote,
					onUseLocal = onUseLocal,
					LocalBody = LocalBody,
					RemoteBody = RemoteBody,
					remoteLabel = remoteLabel,
					localLabel = localLabel,
				)
			}
		}
		if (bottomBar != null) {
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
			bottomBar()
		}
	}
}

@Composable
private fun <T : ApiProjectEntity> ExpandedConflictUi(
	entityConflict: ProjectSynchronization.EntityConflict<T>,
	component: ProjectSynchronization,
	onUseRemote: () -> Unit,
	onUseLocal: () -> Unit,
	LocalBody: EntityPaneBody<T>,
	RemoteBody: EntityPaneBody<T>,
	remoteLabel: String,
	localLabel: String,
) {
	Row(modifier = Modifier.fillMaxSize()) {
		ConflictPane(
			sideLabel = remoteLabel,
			onUse = onUseRemote,
			modifier = Modifier.weight(1f).fillMaxHeight(),
		) {
			RemoteBody(Modifier.fillMaxSize(), entityConflict, component)
		}
		Box(
			modifier = Modifier
				.fillMaxHeight()
				.width(Dp.Hairline)
				.background(MaterialTheme.colorScheme.outlineVariant),
		)
		ConflictPane(
			sideLabel = localLabel,
			onUse = onUseLocal,
			modifier = Modifier.weight(1f).fillMaxHeight(),
		) {
			LocalBody(Modifier.fillMaxSize(), entityConflict, component)
		}
	}
}

@Composable
private fun <T : ApiProjectEntity> CompactConflictUi(
	entityConflict: ProjectSynchronization.EntityConflict<T>,
	component: ProjectSynchronization,
	onUseRemote: () -> Unit,
	onUseLocal: () -> Unit,
	LocalBody: EntityPaneBody<T>,
	RemoteBody: EntityPaneBody<T>,
	remoteLabel: String,
	localLabel: String,
) {
	var side by rememberSaveable { mutableStateOf(Side.REMOTE) }
	Column(modifier = Modifier.fillMaxSize()) {
		HdHairlineSegmentedPicker(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
			options = listOf(Side.REMOTE, Side.LOCAL),
			selected = side,
			onSelect = { side = it },
			label = { if (it == Side.REMOTE) remoteLabel else localLabel },
		)
		val isRemote = side == Side.REMOTE
		ConflictPane(
			sideLabel = if (isRemote) remoteLabel else localLabel,
			onUse = if (isRemote) onUseRemote else onUseLocal,
			modifier = Modifier.fillMaxWidth().weight(1f),
		) {
			if (isRemote) {
				RemoteBody(Modifier.fillMaxSize(), entityConflict, component)
			} else {
				LocalBody(Modifier.fillMaxSize(), entityConflict, component)
			}
		}
	}
}

@Composable
private fun ConflictPane(
	sideLabel: String,
	onUse: () -> Unit,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	Column(modifier = modifier) {
		PaneMast(sideLabel = sideLabel, onUse = onUse)
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
			content()
		}
	}
}

@Composable
private fun PaneMast(sideLabel: String, onUse: () -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		HdMonoLabel(
			text = sideLabel,
			color = MaterialTheme.colorScheme.onSurface,
		)
		HdPrimaryAction(
			prefix = "USE",
			label = sideLabel,
			onClick = onUse,
		)
	}
}

@Composable
internal fun ReadOnlyLine(text: String) {
	SelectionContainer {
		Text(
			text = text,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
internal fun ReadOnlyBlock(text: String) {
	SelectionContainer {
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.fillMaxSize(),
		)
	}
}

@Composable
internal fun ReadOnlyBlock(text: AnnotatedString) {
	SelectionContainer {
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.fillMaxSize(),
		)
	}
}
