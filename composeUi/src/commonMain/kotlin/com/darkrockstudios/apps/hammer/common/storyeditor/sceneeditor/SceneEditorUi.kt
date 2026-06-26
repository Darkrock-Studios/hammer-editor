package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.common.TextEditorDefaults
import com.darkrockstudios.apps.hammer.common.components.storyeditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.ComposeRichText
import com.darkrockstudios.apps.hammer.common.compose.LocalMarkdownConfig
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Toaster
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.findShortcutModifier
import com.darkrockstudios.apps.hammer.common.compose.markdown.updateMarkdownConfiguration
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownFormatBar
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.markdownFormatShortcuts
import com.darkrockstudios.apps.hammer.common.compose.saveShortcutModifier
import com.darkrockstudios.apps.hammer.common.data.UpdateSource
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneDeleteDialog
import com.darkrockstudios.apps.hammer.common.utils.toEditorSpellChecker
import com.darkrockstudios.texteditor.find.FindBar
import com.darkrockstudios.texteditor.find.rememberFindState
import com.darkrockstudios.texteditor.rememberTextEditorStyle
import com.darkrockstudios.texteditor.spellcheck.SpellCheckMode
import com.darkrockstudios.texteditor.spellcheck.SpellCheckingTextEditor
import com.darkrockstudios.texteditor.spellcheck.markdown.withMarkdown
import com.darkrockstudios.texteditor.spellcheck.rememberSpellCheckState
import kotlinx.coroutines.launch

const val SCENE_EDITOR_TEXT_TAG = "scene-editor-text"
const val SCENE_EDITOR_SAVE_TAG = "scene-editor-save"
const val RENAME_SCENE_FIELD_TAG = "rename-scene-field"

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
fun SceneEditorUi(
	component: SceneEditor,
	rootSnackbar: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()
	val lastForceUpdate by component.lastForceUpdate.subscribeAsState()
	val markdownConfig = LocalMarkdownConfig.current
	val scope = rememberCoroutineScope()

	val textEditorState = rememberSpellCheckState(
		spellChecker = state.spellChecker.toEditorSpellChecker(),
		initialText = getInitialEditorContent(state.sceneBuffer?.content, markdownConfig),
		enableSpellChecking = state.spellCheckingEnabled,
		spellCheckMode = SpellCheckMode.Word,
	)
	val markdownExtension = remember { textEditorState.withMarkdown(markdownConfig) }

	val findState = rememberFindState(textEditorState.textState)
	var showFindBar by remember { mutableStateOf(false) }

	LaunchedEffect(markdownConfig) {
		markdownExtension.updateMarkdownConfiguration(markdownConfig)
	}

	var hasReceivedInitialBuffer by remember { mutableStateOf(false) }

	LaunchedEffect(lastForceUpdate, state.sceneBuffer) {
		// Update text when buffer becomes available or on force update
		state.sceneBuffer?.let { buffer ->
			// Always update on first buffer (initial load), or when external update occurs
			if (!hasReceivedInitialBuffer || buffer.source != UpdateSource.Editor) {
				val newContent = getInitialEditorContent(buffer.content, markdownConfig)
				textEditorState.textState.setText(newContent)
				hasReceivedInitialBuffer = true
			}
		}
	}

	LaunchedEffect(hasReceivedInitialBuffer) {
		// Only start collecting edit operations after the initial buffer has been loaded
		// to prevent empty content from being sent to the buffer before it's populated
		if (hasReceivedInitialBuffer) {
			textEditorState.textState.editOperations
				.collect { _ ->
					component.onContentChanged(ComposeRichText(markdownExtension))
				}
		}
	}

	Toaster(component, rootSnackbar)

	BoxWithConstraints(modifier = modifier) {
		val boxWithConstraintsScope = this

		if (state.isLoading) {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center
			) {
				CircularProgressIndicator()
			}
		} else {
			val remainingWidth = remember(boxWithConstraintsScope.maxWidth) {
				boxWithConstraintsScope.maxWidth - TextEditorDefaults.MAX_WIDTH
			}
			val isWide = remember(remainingWidth) { remainingWidth >= SCENE_METADATA_MIN_WIDTH }
			SideEffect { component.setLayoutMode(isWide) }
			val onToggleMetadata: () -> Unit = remember(isWide, component) {
				if (isWide) component::toggleMetadataPanelVisible else component::toggleMetadataModal
			}

			Column(
				modifier = Modifier
					.fillMaxHeight()
					.findShortcutModifier { showFindBar = true }
					.saveShortcutModifier { scope.launch { component.storeSceneContent() } }
					.markdownFormatShortcuts(markdownExtension)
			) {
				EditorTopBar(
					component = component,
					rootSnackbar = rootSnackbar,
					onToggleMetadata = onToggleMetadata,
				)

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				MarkdownFormatBar(
					markdownState = markdownExtension,
					decreaseTextSize = component::decreaseTextSize,
					increaseTextSize = component::increaseTextSize,
					resetTextSize = component::resetTextSize,
				)

				AnimatedVisibility(
					visible = showFindBar,
					enter = expandVertically(expandFrom = Alignment.Top),
					exit = shrinkVertically(shrinkTowards = Alignment.Top)
				) {
					FindBar(
						state = findState,
						onClose = { showFindBar = false }
					)
				}

				Row(
					modifier = Modifier.fillMaxSize(),
					horizontalArrangement = Arrangement.Center
				) {
					SpellCheckingTextEditor(
						state = textEditorState,
						contentPadding = PaddingValues(Ui.Padding.XL),
						enabled = hasReceivedInitialBuffer,
						style = rememberTextEditorStyle(
							textStyle = TextStyle.Default.copy(
								textIndent = TextIndent(firstLine = 24.sp)
							),
							focusedBorderColor = Color.Transparent,
							unfocusedBorderColor = Color.Transparent,
						),
						modifier = Modifier
							.testTag(SCENE_EDITOR_TEXT_TAG)
							.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
							.fillMaxHeight()
							.widthIn(128.dp, TextEditorDefaults.MAX_WIDTH),
					)

					HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

					SceneMetadataSidebar(component, isWide)
				}
			}
		}
	}

	SaveDraftDialog(state, component) { message ->
		rootSnackbar.showSnackbar(message)
	}

	if (state.confirmDelete) {
		SceneDeleteDialog(state.sceneItem) { doDelete ->
			if (doDelete) {
				component.doDelete()
			} else {
				component.endDelete()
			}
		}
	}

	if (state.confirmArchive) {
		ArchiveSceneDialog(state.sceneItem) { doArchive ->
			if (doArchive) {
				component.doArchive()
			} else {
				component.endArchive()
			}
		}
	}

	if (state.confirmDiscard) {
		DiscardBufferDialog(state.sceneItem) { doDiscard ->
			if (doDiscard) {
				component.doDiscard()
			} else {
				component.endDiscard()
			}
		}
	}
}

@Composable
private fun SceneMetadataSidebar(component: SceneEditor, isWide: Boolean) {
	val state by component.state.subscribeAsState()

	if (isWide) {
		AnimatedVisibility(
			visible = state.metadataPanelVisible,
			enter = slideInHorizontally { it } + fadeIn(),
			exit = slideOutHorizontally { it } + fadeOut(),
		) {
			SceneMetadataPanelUi(
				component = component.sceneMetadataComponent,
				modifier = Modifier.wrapContentWidth()
					.widthIn(max = SCENE_METADATA_MAX_WIDTH)
					.fillMaxHeight(),
				closeMetadata = component::toggleMetadataPanelVisible,
			)
		}
	} else {
		AnimatedDialog(
			visible = state.showMetadataModal,
			onCloseRequest = component::toggleMetadataModal,
			modifier = Modifier.padding(Ui.Padding.L),
			dismissOnTapOutside = true,
		) {
			SceneMetadataPanelUi(
				component = component.sceneMetadataComponent,
				modifier = Modifier
					.widthIn(max = SCENE_METADATA_MAX_WIDTH)
					.fillMaxWidth()
					.wrapContentHeight(),
				closeMetadata = { requestDismiss() },
			)
		}
	}
}