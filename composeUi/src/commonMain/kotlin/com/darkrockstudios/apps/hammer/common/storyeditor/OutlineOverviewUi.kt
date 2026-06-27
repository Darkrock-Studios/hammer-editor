package com.darkrockstudios.apps.hammer.common.storyeditor

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.outlineoverview.OutlineOverview
import com.darkrockstudios.apps.hammer.common.compose.AnimatedFullScreenDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.romanNumeral
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import kotlinx.coroutines.launch

private val WideThreshold = 720.dp
private const val FRONTISPIECE_ITEM_COUNT = 1

private data class OutlineChapter(
	val chapter: OutlineOverview.OutlineItem.ChapterOutline?,
	val scenes: List<OutlineOverview.OutlineItem.SceneOutline>,
)

private fun groupByChapter(items: List<OutlineOverview.OutlineItem>): List<OutlineChapter> {
	if (items.isEmpty()) return emptyList()
	val grouped = mutableListOf<OutlineChapter>()
	var current: OutlineOverview.OutlineItem.ChapterOutline? = null
	var pending = mutableListOf<OutlineOverview.OutlineItem.SceneOutline>()
	fun flush() {
		if (current != null || pending.isNotEmpty()) {
			grouped.add(OutlineChapter(current, pending.toList()))
		}
	}
	for (item in items) {
		when (item) {
			is OutlineOverview.OutlineItem.ChapterOutline -> {
				flush()
				current = item
				pending = mutableListOf()
			}

			is OutlineOverview.OutlineItem.SceneOutline -> pending.add(item)
		}
	}
	flush()
	return grouped
}

@Composable
fun OutlineOverviewUi(component: OutlineOverview) {
	AnimatedFullScreenDialog(
		onDismissed = component::dismiss,
		backgroundColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
	) {
		OutlineOverviewContent(
			component = component,
			onDismiss = { requestDismiss() },
		)
	}
}

/**
 * The static content of [OutlineOverviewUi] without the [AnimatedFullScreenDialog] wrapper.
 * [OutlineOverviewUi] animates this in/out; render it directly (e.g. in a `@Preview`) to capture
 * a settled, opaque frame, since the Desktop preview renderer can't advance the enter animation.
 */
@Composable
fun OutlineOverviewContent(
	component: OutlineOverview,
	onDismiss: () -> Unit,
) {
	val state by component.state.subscribeAsState()

	val chapters = remember(state.overview) { groupByChapter(state.overview) }
	val totalScenes = remember(chapters) { chapters.sumOf { it.scenes.size } }
	val totalChapters = chapters.count { it.chapter != null }

	val listState = rememberLazyListState()
	val coroutineScope = rememberCoroutineScope()

	val activeChapterIndex by remember(chapters.size) {
		derivedStateOf {
			if (chapters.isEmpty()) 0
			else (listState.firstVisibleItemIndex - FRONTISPIECE_ITEM_COUNT)
				.coerceIn(0, chapters.size - 1)
		}
	}

	val scrollToChapter: (Int) -> Unit = { idx ->
		coroutineScope.launch {
			listState.animateScrollToItem(idx + FRONTISPIECE_ITEM_COUNT)
		}
	}

	val projectName = remember(state.overview) {
		state.overview.firstOrNull()?.projectName()
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.pointerInput(Unit) {
				detectTapGestures(onTap = { onDismiss() })
			},
		contentAlignment = Alignment.Center,
	) {
		BoxWithConstraints(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center,
		) {
			val isWide = maxWidth >= WideThreshold

			Column(
				modifier = Modifier
					.padding(if (isWide) Ui.Padding.XXL else 0.dp)
					.widthIn(max = 1080.dp)
					.heightIn(max = 880.dp)
					.fillMaxWidth()
					.fillMaxHeight()
					.background(MaterialTheme.colorScheme.surface)
					.border(
						width = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
						shape = RectangleShape,
					)
					.pointerInput(Unit) {
						detectTapGestures(onTap = {})
					},
			) {
				Masthead(
					chapterCount = totalChapters,
					sceneCount = totalScenes,
					isWide = isWide,
					onClose = onDismiss,
				)

				HdFolioDivider()

				if (chapters.isEmpty()) {
					EmptyOutline(modifier = Modifier.weight(1f))
				} else if (isWide) {
					Row(
						modifier = Modifier
							.weight(1f)
							.fillMaxWidth(),
					) {
						ChapterRail(
							chapters = chapters,
							activeChapterIndex = activeChapterIndex,
							onJumpTo = scrollToChapter,
							modifier = Modifier
								.width(240.dp)
								.fillMaxHeight(),
						)
						VerticalDivider(
							color = MaterialTheme.colorScheme.outlineVariant,
							thickness = Dp.Hairline,
						)
						ReadingColumn(
							chapters = chapters,
							projectName = projectName,
							listState = listState,
							wide = true,
							onSceneClick = component::selectScene,
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight(),
						)
					}
				} else {
					ChapterDropdown(
						chapters = chapters,
						activeChapterIndex = activeChapterIndex,
						onJumpTo = scrollToChapter,
					)
					ReadingColumn(
						chapters = chapters,
						projectName = projectName,
						listState = listState,
						wide = false,
						onSceneClick = component::selectScene,
						modifier = Modifier
							.weight(1f)
							.fillMaxWidth(),
					)
				}
			}
		}
	}
}

@Composable
private fun Masthead(
	chapterCount: Int,
	sceneCount: Int,
	isWide: Boolean,
	onClose: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(
				horizontal = if (isWide) Ui.Padding.XXL else Ui.Padding.XL,
				vertical = Ui.Padding.XL,
			),
		verticalAlignment = Alignment.Top,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		) {
			HdMonoLabel(text = Res.string.scene_list_outline_overview_eyebrow.get())
			Text(
				text = Res.string.scene_list_outline_overview_title.get(),
				style = MaterialTheme.typography.displaySmall,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Row(
				modifier = Modifier
					.padding(top = Ui.Padding.M)
					.height(IntrinsicSize.Min),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.XL),
				verticalAlignment = Alignment.CenterVertically,
			) {
				StatPair(
					label = Res.string.scene_list_outline_overview_stat_chapters.get(),
					value = chapterCount.toString(),
				)
				VerticalDivider(
					color = MaterialTheme.colorScheme.outlineVariant,
					thickness = Dp.Hairline,
					modifier = Modifier.height(36.dp),
				)
				StatPair(
					label = Res.string.scene_list_outline_overview_stat_scenes.get(),
					value = sceneCount.toString(),
				)
			}
		}

		CloseButton(onClose = onClose)
	}
}

@Composable
private fun StatPair(label: String, value: String) {
	Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
		HdMonoLabel(text = label)
		Text(
			text = value,
			style = MaterialTheme.typography.headlineMedium,
			fontWeight = FontWeight.Light,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
	Box(
		modifier = Modifier
			.size(32.dp)
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
				shape = RectangleShape,
			)
			.clickable(onClick = onClose),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			imageVector = Icons.Default.Close,
			contentDescription = Res.string.scene_list_outline_dismiss.get(),
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(16.dp),
		)
	}
}

@Composable
private fun ChapterRail(
	chapters: List<OutlineChapter>,
	activeChapterIndex: Int,
	onJumpTo: (Int) -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
	) {
		HdMonoLabel(
			text = Res.string.scene_list_outline_overview_jump_to.get(),
			modifier = Modifier.padding(
				start = Ui.Padding.XL,
				end = Ui.Padding.XL,
				top = Ui.Padding.XL,
				bottom = Ui.Padding.M,
			),
		)
		LazyColumn(modifier = Modifier.fillMaxSize()) {
			itemsIndexed(chapters, key = { idx, _ -> "rail-$idx" }) { index, ch ->
				ChapterRailItem(
					index = index,
					chapter = ch,
					selected = index == activeChapterIndex,
					onClick = { onJumpTo(index) },
				)
			}
		}
	}
}

@Composable
private fun ChapterRailItem(
	index: Int,
	chapter: OutlineChapter,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val accent = MaterialTheme.colorScheme.primary
	val rowBg = if (selected) {
		MaterialTheme.colorScheme.surfaceContainer
	} else {
		MaterialTheme.colorScheme.surfaceContainerLow
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(IntrinsicSize.Min)
			.background(rowBg)
			.clickable(onClick = onClick),
		verticalAlignment = Alignment.Top,
	) {
		Box(
			modifier = Modifier
				.width(2.dp)
				.fillMaxHeight()
				.background(if (selected) accent else androidx.compose.ui.graphics.Color.Transparent),
		)
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					start = Ui.Padding.L,
					end = Ui.Padding.XL,
					top = Ui.Padding.L,
					bottom = Ui.Padding.L,
				),
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			verticalAlignment = Alignment.Top,
		) {
			Text(
				text = romanNumeral(index + 1),
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Light,
				color = if (selected) {
					MaterialTheme.colorScheme.onSurface
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
				modifier = Modifier.widthIn(min = 24.dp),
			)
			Column(
				modifier = Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = chapter.chapter?.sceneItem?.name ?: untitledChapter(),
					style = MaterialTheme.typography.titleSmall,
					fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				HdMonoLabel(
					text = Res.string.scene_list_outline_overview_scene_count.get(chapter.scenes.size),
				)
			}
		}
	}
}

@Composable
private fun untitledChapter(): String =
	Res.string.scene_list_outline_overview_untitled_chapter.get()

@Composable
private fun ChapterDropdown(
	chapters: List<OutlineChapter>,
	activeChapterIndex: Int,
	onJumpTo: (Int) -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	val rotation by animateFloatAsState(
		targetValue = if (expanded) 180f else 0f,
		label = "outlineChapterDropdownRotation",
	)
	val current = chapters.getOrNull(activeChapterIndex)

	Column(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.background(MaterialTheme.colorScheme.surfaceContainerLow)
				.clickable { expanded = !expanded }
				.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			HdMonoLabel(text = Res.string.scene_list_outline_overview_jump_to.get())
			Row(
				modifier = Modifier.weight(1f),
				verticalAlignment = Alignment.Bottom,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				Text(
					text = romanNumeral(activeChapterIndex + 1),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Light,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Text(
					text = current?.chapter?.sceneItem?.name ?: untitledChapter(),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			HdMonoLabel(
				text = Res.string.scene_list_outline_overview_position.get(
					activeChapterIndex + 1,
					chapters.size,
				),
			)
			Icon(
				imageVector = Icons.Default.KeyboardArrowDown,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier
					.size(20.dp)
					.rotate(rotation),
			)
		}
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)

		AnimatedVisibility(
			visible = expanded,
			enter = expandVertically() + fadeIn(),
			exit = shrinkVertically() + fadeOut(),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 320.dp)
					.background(MaterialTheme.colorScheme.surface),
			) {
				LazyColumn(modifier = Modifier.fillMaxWidth()) {
					itemsIndexed(chapters, key = { idx, _ -> "drop-$idx" }) { idx, ch ->
						ChapterRailItem(
							index = idx,
							chapter = ch,
							selected = idx == activeChapterIndex,
							onClick = {
								onJumpTo(idx)
								expanded = false
							},
						)
						HorizontalDivider(
							thickness = Dp.Hairline,
							color = MaterialTheme.colorScheme.outlineVariant,
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ReadingColumn(
	chapters: List<OutlineChapter>,
	projectName: String?,
	listState: LazyListState,
	wide: Boolean,
	onSceneClick: (SceneItem) -> Unit,
	modifier: Modifier = Modifier,
) {
	val horizontalPad = if (wide) Ui.Padding.XXL else Ui.Padding.XL
	LazyColumn(
		modifier = modifier,
		state = listState,
		contentPadding = PaddingValues(
			start = horizontalPad,
			end = horizontalPad,
			top = Ui.Padding.XXL,
			bottom = Ui.Padding.XXL,
		),
	) {
		item(key = "frontispiece") {
			Frontispiece(projectName = projectName)
		}
		itemsIndexed(chapters, key = { idx, _ -> "ch-$idx" }) { index, ch ->
			ChapterBlock(
				chapterIndex = index,
				chapter = ch,
				showSeparator = index < chapters.size - 1,
				wide = wide,
				onSceneClick = onSceneClick,
			)
		}
		item(key = "end") {
			EndOfOutline()
		}
	}
}

@Composable
private fun Frontispiece(projectName: String?) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 48.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(
			text = Res.string.scene_list_outline_overview_frontispiece_eyebrow.get(),
		)
		if (!projectName.isNullOrBlank()) {
			Text(
				text = projectName,
				style = MaterialTheme.typography.displayMedium,
				fontWeight = FontWeight.Light,
				color = MaterialTheme.colorScheme.onSurface,
				textAlign = TextAlign.Center,
			)
		}
		Box(
			modifier = Modifier
				.padding(top = Ui.Padding.L)
				.width(80.dp)
				.height(Dp.Hairline)
				.background(MaterialTheme.colorScheme.outlineVariant),
		)
	}
}

@Composable
private fun ChapterBlock(
	chapterIndex: Int,
	chapter: OutlineChapter,
	showSeparator: Boolean,
	wide: Boolean,
	onSceneClick: (SceneItem) -> Unit,
) {
	val sceneTitleStyle = if (wide) {
		MaterialTheme.typography.titleLarge
	} else {
		MaterialTheme.typography.titleMedium
	}
	val proseStyle = if (wide) {
		MaterialTheme.typography.bodyLarge
	} else {
		MaterialTheme.typography.bodyMedium
	}

	Column(modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp)) {
		ChapterHeader(
			index = chapterIndex,
			chapter = chapter,
			wide = wide,
		)
		Spacer(Modifier.height(Ui.Padding.XXL))
		val chapterRoman = romanNumeral(chapterIndex + 1)
		chapter.scenes.forEachIndexed { sceneIdx, scene ->
			SceneBlock(
				chapterRoman = chapterRoman,
				sceneNumber = sceneIdx + 1,
				scene = scene,
				titleStyle = sceneTitleStyle,
				proseStyle = proseStyle,
				onClick = { onSceneClick(scene.sceneItem) },
			)
			if (sceneIdx < chapter.scenes.size - 1) {
				Spacer(Modifier.height(Ui.Padding.XXL))
			}
		}
		if (showSeparator) {
			Spacer(Modifier.height(Ui.Padding.XXL))
			Box(
				modifier = Modifier.fillMaxWidth(),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = "· · ·",
					style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 8.sp),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}

@Composable
private fun ChapterHeader(
	index: Int,
	chapter: OutlineChapter,
	wide: Boolean,
) {
	val titleStyle = if (wide) {
		MaterialTheme.typography.headlineLarge
	} else {
		MaterialTheme.typography.headlineMedium
	}
	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		HdMonoLabel(
			text = Res.string.scene_list_outline_overview_chapter_eyebrow.get(
				romanNumeral(index + 1),
				chapter.scenes.size,
			),
		)
		Text(
			text = chapter.chapter?.sceneItem?.name ?: untitledChapter(),
			style = titleStyle,
			fontWeight = FontWeight.Light,
			color = MaterialTheme.colorScheme.onSurface,
		)
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
			modifier = Modifier.padding(top = Ui.Padding.M),
		)
	}
}

@Composable
private fun SceneBlock(
	chapterRoman: String,
	sceneNumber: Int,
	scene: OutlineOverview.OutlineItem.SceneOutline,
	titleStyle: androidx.compose.ui.text.TextStyle,
	proseStyle: androidx.compose.ui.text.TextStyle,
	onClick: () -> Unit,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clickable(onClick = onClick),
			verticalAlignment = Alignment.Bottom,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
		) {
			HdMonoLabel(text = "§ $chapterRoman.$sceneNumber")
			Text(
				text = scene.sceneItem.name,
				style = titleStyle,
				color = MaterialTheme.colorScheme.onSurface,
			)
			HdMonoLabel(text = "↗")
		}
		val outline = scene.outline
		if (!outline.isNullOrBlank()) {
			Text(
				text = outline,
				style = proseStyle,
				color = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.padding(start = Ui.Padding.XL),
			)
		} else {
			Text(
				text = Res.string.scene_list_outline_overview_none.get(),
				style = proseStyle,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				fontStyle = FontStyle.Italic,
				modifier = Modifier.padding(start = Ui.Padding.XL),
			)
		}
	}
}

@Composable
private fun EndOfOutline() {
	Column(
		modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.L),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		HdMonoLabel(text = Res.string.scene_list_outline_overview_end.get())
	}
}

@Composable
private fun EmptyOutline(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier.fillMaxWidth(),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = Res.string.scene_list_outline_overview_none.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontStyle = FontStyle.Italic,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

private fun OutlineOverview.OutlineItem.projectName(): String =
	when (this) {
		is OutlineOverview.OutlineItem.ChapterOutline -> sceneItem.projectDef.name
		is OutlineOverview.OutlineItem.SceneOutline -> sceneItem.projectDef.name
	}
