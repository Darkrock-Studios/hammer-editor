package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FixedThreshold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import kotlinx.coroutines.time.delay
import java.time.Duration
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class)
@Composable
actual fun SceneGroupActionContainer(
	scene: SceneItem,
	shouldNux: Boolean,
	onSceneAltClick: (scene: SceneItem) -> Unit,
	onCreateSceneClick: (scene: SceneItem) -> Unit,
	onCreateGroupClick: (scene: SceneItem) -> Unit,
	itemContent: @Composable (modifier: Modifier) -> Unit
) {
	val hapticFeedback = LocalHapticFeedback.current

	var showMenu by remember { mutableStateOf(false) }
	val swipeableState = rememberSwipeableState(SwipeState.Initial, confirmStateChange = { value ->
		if (value == SwipeState.Swiped) {
			hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
			showMenu = true
		}
		false
	})
	val sizePx = with(LocalDensity.current) { 50.dp.toPx() }
	val anchors = mapOf(0f to SwipeState.Initial, sizePx to SwipeState.Swiped)

	if (shouldNux) {
		LaunchedEffect(swipeableState) {
			delay(Duration.ofMillis(500))
			swipeableState.animateTo(SwipeState.Swiped)
			delay(Duration.ofMillis(500))
			swipeableState.animateTo(SwipeState.Initial)
		}
	}

	Box(
		modifier = Modifier
			.swipeable(
				state = swipeableState,
				anchors = anchors,
				thresholds = { _, _ -> FixedThreshold(50.dp) },
				orientation = Orientation.Horizontal
			)
	) {
		val alpha =
			if (!(swipeableState.progress.to == SwipeState.Initial && swipeableState.progress.from == SwipeState.Initial)) {
				swipeableState.progress.fraction
			} else {
				0f
			}

		Icon(
			Icons.Filled.MenuOpen,
			contentDescription = "Options",
			modifier = Modifier
				.align(Alignment.CenterStart)
				.alpha(alpha),
			tint = MaterialTheme.colorScheme.onSurface
		)

		Box(
			Modifier
				.offset { IntOffset(swipeableState.offset.value.roundToInt(), 0) }
				.wrapContentSize()
		) {
			itemContent(Modifier)
		}
	}

	DropdownMenu(
		expanded = showMenu,
		onDismissRequest = { showMenu = false }
	) {
		DropdownMenuItem(
			text = { Text("Delete") },
			onClick = {
				onSceneAltClick(scene)
				showMenu = false
			},
			leadingIcon = {
				Icon(
					Icons.Outlined.Delete,
					contentDescription = "Delete Group"
				)
			}
		)
		DropdownMenuItem(
			text = { Text("Create Scene") },
			onClick = {
				onCreateSceneClick(scene)
				showMenu = false
			},
			leadingIcon = {
				Icon(
					Icons.Outlined.Create,
					contentDescription = "Create Scene"
				)
			}
		)
		DropdownMenuItem(
			text = { Text("Create Group") },
			onClick = {
				onCreateGroupClick(scene)
				showMenu = false
			},
			leadingIcon = {
				Icon(
					Icons.Outlined.Create,
					contentDescription = "Create Group"
				)
			}
		)
	}
}