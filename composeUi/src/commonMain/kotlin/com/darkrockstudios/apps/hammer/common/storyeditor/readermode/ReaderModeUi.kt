package com.darkrockstudios.apps.hammer.common.storyeditor.readermode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.storyeditor.readermode.ReaderMode
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.clampEditorWidth
import com.darkrockstudios.apps.hammer.common.compose.LocalMarkdownConfig
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdown.changeFontSize
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

const val READER_MODE_LIST_TAG = "reader-mode-list"
const val READER_MODE_EDIT_TAG = "reader-mode-edit"
const val READER_MODE_HEADINGS_TOGGLE_TAG = "reader-mode-headings-toggle"
const val READER_MODE_INCREASE_FONT_TAG = "reader-mode-increase-font"
const val READER_MODE_DECREASE_FONT_TAG = "reader-mode-decrease-font"
const val READER_MODE_SCENE_BREAK_TAG = "reader-mode-scene-break"
const val READER_MODE_NEXT_CHAPTER_TAG = "reader-mode-next-chapter"
const val READER_MODE_PREVIOUS_CHAPTER_TAG = "reader-mode-previous-chapter"

fun readerSceneHeadingTag(sceneId: Int) = "reader-mode-scene-heading-$sceneId"
fun readerSceneBodyTag(sceneId: Int) = "reader-mode-scene-body-$sceneId"

/** One lazy item of the reading column. [scene] is the scene the row counts as when it tops the viewport. */
private sealed interface ReaderRow {
	val key: String
	val scene: SceneItem?

	data class ChapterHeader(override val scene: SceneItem?) : ReaderRow {
		override val key = "chapter-header"
	}

	data class SceneHeading(override val scene: SceneItem, val number: Int) : ReaderRow {
		override val key = "heading-${scene.id}"
	}

	data class SceneBreak(override val scene: SceneItem) : ReaderRow {
		override val key = "break-${scene.id}"
	}

	data class Body(override val scene: SceneItem, val markdown: String) : ReaderRow {
		override val key = "body-${scene.id}"
	}

	data object EmptyChapter : ReaderRow {
		override val key = "empty-chapter"
		override val scene: SceneItem? = null
	}

	data class ChapterNav(override val scene: SceneItem?) : ReaderRow {
		override val key = "chapter-nav"
	}
}

private fun buildReaderRows(
	scenes: List<ReaderMode.ReaderScene>,
	showSceneHeadings: Boolean,
	isLoading: Boolean,
): List<ReaderRow> = buildList {
	add(ReaderRow.ChapterHeader(scenes.firstOrNull()?.sceneItem))
	scenes.forEachIndexed { index, scene ->
		if (showSceneHeadings) {
			add(ReaderRow.SceneHeading(scene.sceneItem, number = index + 1))
		} else if (index > 0) {
			add(ReaderRow.SceneBreak(scene.sceneItem))
		}
		add(ReaderRow.Body(scene.sceneItem, scene.markdown))
	}
	if (scenes.isEmpty() && !isLoading) add(ReaderRow.EmptyChapter)
	add(ReaderRow.ChapterNav(scenes.lastOrNull()?.sceneItem))
}

// The chapter's first scene scrolls to the very top so the chapter header stays in view.
private fun List<ReaderRow>.scrollIndexFor(sceneId: Int): Int {
	val index = indexOfFirst { it !is ReaderRow.ChapterHeader && it.scene?.id == sceneId }
	val firstSceneIndex = indexOfFirst { it !is ReaderRow.ChapterHeader && it.scene != null }
	return if (index == firstSceneIndex) 0 else index
}

@Composable
fun ReaderModeUi(component: ReaderMode, modifier: Modifier = Modifier) {
	val state by component.state.subscribeAsState()
	val screen = LocalScreenCharacteristic.current
	val compact = !screen.isWide
	val chapterIndex = state.activeChapterIndex

	Column(modifier = modifier.fillMaxSize()) {
		HdMasthead(
			section = Res.string.reader_mode_section.get(),
			modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
			leadingMeta = if (compact || chapterIndex < 0) {
				emptyList()
			} else {
				listOf(Res.string.reader_mode_position.get(chapterIndex + 1, state.chapters.size))
			},
			trailing = {
				if (!compact) {
					ReaderTools(state = state, component = component)
				}
				if (chapterIndex >= 0) {
					HdMastheadAction(
						label = Res.string.reader_mode_edit.get(),
						onClick = { component.editScene() },
						modifier = Modifier.testTag(READER_MODE_EDIT_TAG),
					)
				}
				if (screen.needsExplicitClose) {
					HdMastheadAction(label = Res.string.reader_mode_close.get(), onClick = component::close)
				}
			},
		)
		HdFolioDivider()

		if (compact) {
			if (chapterIndex >= 0) {
				val chapterRows = remember(state.chapters) {
					state.chapters.map { HdChapterRow(title = it.chapter.name, sceneCount = it.sceneCount) }
				}
				HdChapterDropdown(
					rows = chapterRows,
					selectedIndex = chapterIndex,
					onSelect = { component.showChapter(state.chapters[it].chapter.id) },
				)
			}
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
				horizontalArrangement = Arrangement.End,
			) {
				ReaderTools(state = state, component = component)
			}
			HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
		}

		if (chapterIndex < 0) {
			EmptyStory(modifier = Modifier.weight(1f))
		} else {
			ReadingPane(
				state = state,
				component = component,
				chapterIndex = chapterIndex,
				wide = !compact,
				modifier = Modifier.weight(1f).fillMaxWidth(),
			)
		}
	}
}

@Composable
private fun ReaderTools(state: ReaderMode.State, component: ReaderMode) {
	val decreaseLabel = Res.string.reader_mode_decrease_font_size.get()
	val increaseLabel = Res.string.reader_mode_increase_font_size.get()
	val resetLabel = Res.string.reader_mode_reset_font_size.get()
	val headingsLabel = Res.string.reader_mode_toggle_scene_headings.get()

	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdToolButton(
			active = false,
			onClick = component::decreaseFontSize,
			modifier = Modifier
				.testTag(READER_MODE_DECREASE_FONT_TAG)
				.semantics { contentDescription = decreaseLabel },
		) {
			ToolGlyph("A−")
		}
		HdMonoLabel(
			text = state.fontSize.roundToInt().toString(),
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.clickable(onClickLabel = resetLabel, onClick = component::resetFontSize)
				.padding(Ui.Padding.S),
		)
		HdToolButton(
			active = false,
			onClick = component::increaseFontSize,
			modifier = Modifier
				.testTag(READER_MODE_INCREASE_FONT_TAG)
				.semantics { contentDescription = increaseLabel },
		) {
			ToolGlyph("A+")
		}
		HdToolButton(
			active = state.showSceneHeadings,
			onClick = component::toggleSceneHeadings,
			modifier = Modifier
				.testTag(READER_MODE_HEADINGS_TOGGLE_TAG)
				.semantics { contentDescription = headingsLabel },
		) {
			ToolGlyph("§")
		}
	}
}

@Composable
private fun ToolGlyph(text: String) {
	Text(
		text = text,
		fontFamily = FontFamily.Monospace,
		fontWeight = FontWeight.Medium,
		fontSize = 12.sp,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

@Composable
private fun ReadingPane(
	state: ReaderMode.State,
	component: ReaderMode,
	chapterIndex: Int,
	wide: Boolean,
	modifier: Modifier = Modifier,
) {
	val rows = remember(state.scenes, state.showSceneHeadings, state.isLoading) {
		buildReaderRows(state.scenes, state.showSceneHeadings, state.isLoading)
	}
	val listState = key(state.activeChapterId) { rememberLazyListState() }

	if (!state.isLoading) {
		LaunchedEffect(listState, rows) {
			snapshotFlow { rows.getOrNull(listState.firstVisibleItemIndex)?.scene }
				.distinctUntilChanged()
				.collect { component.sceneInView(it) }
		}
	}

	// A jump within the chapter on screen animates; landing in a freshly opened chapter does not.
	var settledChapterId by remember { mutableStateOf<Int?>(null) }
	LaunchedEffect(state.scrollToSceneId, rows, state.isLoading) {
		if (state.isLoading) return@LaunchedEffect
		val sceneId = state.scrollToSceneId
		if (sceneId != null) {
			val index = rows.scrollIndexFor(sceneId)
			if (index >= 0) {
				// Offset past the top content padding, or the previous scene shows through it.
				val offset = if (index > 0) listState.layoutInfo.beforeContentPadding else 0
				if (settledChapterId == state.activeChapterId) {
					listState.animateScrollToItem(index, offset)
				} else {
					listState.scrollToItem(index, offset)
				}
			}
			component.scrollHandled()
		}
		settledChapterId = state.activeChapterId
	}

	val baseMarkdownConfig = LocalMarkdownConfig.current
	val markdownConfig = remember(baseMarkdownConfig, state.fontSize) {
		baseMarkdownConfig.changeFontSize(state.fontSize)
	}

	BoxWithConstraints(modifier = modifier) {
		val widthState = rememberHdResizeHandleState(
			persistedWidth = state.editorMaxWidth,
			availableWidth = maxWidth,
			clampWidth = ::clampEditorWidth,
			onCommit = component::setEditorMaxWidth,
			onReset = component::resetEditorMaxWidth,
		)

		Row(
			modifier = Modifier.fillMaxSize(),
			horizontalArrangement = Arrangement.Center,
		) {
			if (widthState.showHandle) {
				HdResizeHandle(
					onOutwardDrag = widthState::onOutwardDrag,
					onDragEnd = widthState::onDragEnd,
					onReset = widthState::reset,
				)
			}

			CompositionLocalProvider(LocalMarkdownConfig provides markdownConfig) {
				ReadingColumn(
					rows = rows,
					state = state,
					component = component,
					chapterIndex = chapterIndex,
					wide = wide,
					listState = listState,
					modifier = Modifier
						.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
						.fillMaxHeight()
						.widthIn(TextEditorDefaults.MIN_WIDTH, widthState.width)
						.fillMaxWidth(),
				)
			}
		}

		MpScrollBarList(
			modifier = scrollBarOverlay(),
			state = listState,
		)
	}
}

@Composable
private fun ReadingColumn(
	rows: List<ReaderRow>,
	state: ReaderMode.State,
	component: ReaderMode,
	chapterIndex: Int,
	wide: Boolean,
	listState: LazyListState,
	modifier: Modifier = Modifier,
) {
	val chapter = state.chapters[chapterIndex]
	val horizontalPad = if (wide) Ui.Padding.XXL else Ui.Padding.XL

	LazyColumn(
		modifier = modifier.testTag(READER_MODE_LIST_TAG),
		state = listState,
		contentPadding = PaddingValues(horizontal = horizontalPad, vertical = Ui.Padding.XXL),
	) {
		items(rows, key = { it.key }, contentType = { it::class }) { row ->
			when (row) {
				is ReaderRow.ChapterHeader -> HdChapterHeader(
					index = chapterIndex,
					title = chapter.chapter.name,
					sceneCount = chapter.sceneCount,
					wide = wide,
					modifier = Modifier.padding(bottom = Ui.Padding.XXL),
				)

				is ReaderRow.SceneHeading -> SceneHeading(
					chapterIndex = chapterIndex,
					row = row,
					wide = wide,
					onClick = { component.editScene(row.scene) },
				)

				is ReaderRow.SceneBreak -> HdSceneBreak(
					modifier = Modifier
						.testTag(READER_MODE_SCENE_BREAK_TAG)
						.padding(vertical = Ui.Padding.XL),
				)

				is ReaderRow.Body -> MarkdownView(
					markdown = row.markdown,
					modifier = Modifier.fillMaxWidth().testTag(readerSceneBodyTag(row.scene.id)),
					importDuringComposition = true,
					isSelectable = true,
				)

				ReaderRow.EmptyChapter -> Text(
					text = Res.string.reader_mode_empty_chapter.get(),
					style = MaterialTheme.typography.bodyLarge,
					fontStyle = FontStyle.Italic,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				is ReaderRow.ChapterNav -> ChapterNav(
					hasPrevious = chapterIndex > 0,
					hasNext = chapterIndex < state.chapters.lastIndex,
					onPrevious = component::previousChapter,
					onNext = component::nextChapter,
				)
			}
		}
	}
}

@Composable
private fun SceneHeading(
	chapterIndex: Int,
	row: ReaderRow.SceneHeading,
	wide: Boolean,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = if (row.number > 1) Ui.Padding.XXL else 0.dp, bottom = Ui.Padding.L)
			.testTag(readerSceneHeadingTag(row.scene.id))
			.clickable(onClick = onClick),
		verticalAlignment = Alignment.Bottom,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdMonoLabel(text = "§ ${romanNumeral(chapterIndex + 1)}.${row.number}")
		Text(
			text = row.scene.name,
			style = if (wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f, fill = false),
		)
		HdMonoLabel(text = "↗")
	}
}

@Composable
private fun ChapterNav(
	hasPrevious: Boolean,
	hasNext: Boolean,
	onPrevious: () -> Unit,
	onNext: () -> Unit,
) {
	Column(modifier = Modifier.fillMaxWidth().padding(top = Ui.Padding.XXL)) {
		if (hasPrevious || hasNext) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				if (hasPrevious) {
					HdHairlineButton(
						label = Res.string.reader_mode_previous_chapter.get(),
						onClick = onPrevious,
						modifier = Modifier.testTag(READER_MODE_PREVIOUS_CHAPTER_TAG),
					)
				} else {
					Spacer(Modifier)
				}
				if (hasNext) {
					HdHairlineButton(
						label = Res.string.reader_mode_next_chapter.get(),
						onClick = onNext,
						modifier = Modifier.testTag(READER_MODE_NEXT_CHAPTER_TAG),
					)
				}
			}
		}
		if (!hasNext) {
			HdEndMark(
				label = Res.string.reader_mode_end.get(),
				modifier = Modifier.padding(top = Ui.Padding.L),
			)
		}
	}
}

@Composable
private fun EmptyStory(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier.fillMaxWidth(),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = Res.string.reader_mode_empty_story.get(),
			style = MaterialTheme.typography.bodyLarge,
			fontStyle = FontStyle.Italic,
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
