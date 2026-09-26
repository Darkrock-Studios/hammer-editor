package com.darkrockstudios.apps.hammer.common.compose.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdButtonBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.rememberClipboardCopier
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.projecthome.SceneSelector
import com.darkrockstudios.apps.hammer.composeui.resources.Res
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_cancel
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_run
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_scenes_choose
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_scenes_done
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_scenes_more
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_scenes_none
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_stop
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_working
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_close
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_copied
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_copy
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_save
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.koin.compose.koinInject

/** The open project's action runner, for menus to start actions with; null outside a project. */
val LocalPluginActions = staticCompositionLocalOf<PluginActionRunner?> { null }

/** Provides [content] with the project's action runner, and shows its dialogs over it. */
@Composable
fun PluginActionHost(project: String, rootSnackbar: RootSnackbarHostState, content: @Composable () -> Unit) {
	val scope = rememberCoroutineScope()
	val operations = koinInject<OperationRegistry>()
	val strings = rememberStrRes()
	val runner = remember(project) { PluginActionRunner(project, scope, operations, strings, showMessage = { rootSnackbar.showSnackbar(it) }) }
	CompositionLocalProvider(LocalPluginActions provides runner) {
		content()
	}
	PluginActionDialogs(runner)
}

/** A menu item for each plugin action at [place], run on the item with [itemId] there. */
@Composable
fun PluginActionMenuItems(place: ActionPlace, itemId: Int?, onChosen: () -> Unit) {
	val runner = LocalPluginActions.current ?: return
	koinInject<PluginUiRegistry>().actions(place).forEach { action ->
		DropdownMenuItem(
			text = { Text(action.label) },
			onClick = {
				onChosen()
				runner.start(action, place, itemId)
			},
		)
	}
}

@Composable
private fun PluginActionDialogs(runner: PluginActionRunner) {
	val state by runner.state.collectAsState()
	val scenes by runner.scenes.collectAsState()
	InputDialog(state as? ActionRunState.Asking, scenes, runner)
	WorkingDialog(state as? ActionRunState.Working, runner)
	ResultDialog(state as? ActionRunState.Showing, runner)
}

/** [current], or the last one there was, so a dialog keeps its content while it animates closed. */
@Composable
private fun <T : Any> lastOf(current: T?): T? {
	val last = remember { object { var value: T? = null } }
	if (current != null) last.value = current
	return current ?: last.value
}

@Composable
private fun DialogSurface(title: String, onClose: (() -> Unit)?, content: @Composable () -> Unit) {
	Surface(
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		shadowElevation = Ui.Elevation.LARGE,
		modifier = Modifier
			.padding(Ui.Padding.XL)
			.widthIn(max = 640.dp)
			.fillMaxWidth(),
	) {
		Column {
			HdMasthead(
				section = title.uppercase(),
				trailing = {
					if (onClose != null) HdMastheadAction(label = Res.string.plugin_document_close.get(), onClick = onClose)
				},
			)
			HdFolioDivider()
			content()
		}
	}
}

@Composable
private fun InputDialog(asking: ActionRunState.Asking?, scenes: List<ExportableScene>?, runner: PluginActionRunner) {
	val shown = lastOf(asking)
	// Keyed on the run, so a chooser left open never outlives its dialog.
	var choosing by remember(asking?.run) { mutableStateOf<ActionField.Scenes?>(null) }
	AnimatedDialog(visible = asking != null, onCloseRequest = runner::dismiss) {
		val current = shown ?: return@AnimatedDialog
		val fields = current.run.action.fields
		val ready = fields.none { it is ActionField.Scenes && it.required && sceneIds(current.values[it.key]).isEmpty() }
		DialogSurface(current.run.action.label, onClose = runner::dismiss) {
			Column(
				modifier = Modifier
					.heightIn(max = 520.dp)
					.verticalScroll(rememberScrollState())
					.padding(Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				fields.forEach { field ->
					when (field) {
						is ActionField.Setting -> DeclaredSettingsForm(
							declarations = listOf(field.declaration),
							values = current.values,
							onChange = { key, value -> runner.change(key, value) },
						)
						is ActionField.Scenes -> ScenesField(field, sceneIds(current.values[field.key]), scenes) { choosing = field }
					}
				}
			}
			HdButtonBar(
				cancelLabel = Res.string.plugin_action_cancel.get(),
				primaryLabel = Res.string.plugin_action_run.get(),
				onCancel = runner::dismiss,
				onPrimary = runner::submit,
				primaryEnabled = ready,
				modifier = Modifier.padding(Ui.Padding.XL),
			)
		}
	}

	val field = choosing
	val values = asking?.values
	SceneChooser(
		field = field?.takeIf { asking != null },
		entries = scenes.orEmpty(),
		selected = field?.let { sceneIds(values?.get(it.key)) }.orEmpty(),
		onChange = { ids ->
			val chosen = field ?: return@SceneChooser
			runner.change(
				chosen.key,
				if (chosen.multiple) JsonArray(ids.map(::JsonPrimitive)) else ids.firstOrNull()?.let(::JsonPrimitive) ?: JsonNull,
			)
		},
		onDone = { choosing = null },
	)
}

/** A scenes field: its label, what is chosen in brief, and a button opening the tree. */
@Composable
private fun ScenesField(field: ActionField.Scenes, selected: Set<Int>, scenes: List<ExportableScene>?, onChoose: () -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
			HdMonoLabel(text = field.label)
			Text(
				text = scenesSummary(selected, scenes.orEmpty()),
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.weight(1f),
			)
			HdHairlineButton(label = Res.string.plugin_action_scenes_choose.get(), onClick = onChoose, enabled = scenes != null)
		}
		field.hint?.let {
			Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
}

@Composable
private fun scenesSummary(selected: Set<Int>, scenes: List<ExportableScene>): String {
	val names = scenes.filter { !it.isGroup && it.id in selected }.map { it.name }
	return when (names.size) {
		0 -> Res.string.plugin_action_scenes_none.get()
		1, 2 -> names.joinToString(", ")
		else -> Res.string.plugin_action_scenes_more.get(names.first(), names.size - 1)
	}
}

@Composable
private fun SceneChooser(
	field: ActionField.Scenes?,
	entries: List<ExportableScene>,
	selected: Set<Int>,
	onChange: (Set<Int>) -> Unit,
	onDone: () -> Unit,
) {
	val shown = lastOf(field)
	AnimatedDialog(visible = field != null, onCloseRequest = onDone) {
		val current = shown ?: return@AnimatedDialog
		DialogSurface(current.label, onClose = onDone) {
			Column(modifier = Modifier.padding(Ui.Padding.XL).heightIn(max = 480.dp)) {
				SceneSelector(entries = entries, selected = selected, multiple = current.multiple, onSelectionChanged = onChange)
			}
			Row(modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL)) {
				Spacer(modifier = Modifier.weight(1f))
				HdHairlineButton(label = Res.string.plugin_action_scenes_done.get(), onClick = onDone, emphasised = true)
			}
		}
	}
}

/** Shown only once a run has taken a moment, so quick actions don't flash a dialog. */
@Composable
private fun WorkingDialog(working: ActionRunState.Working?, runner: PluginActionRunner) {
	var visible by remember { mutableStateOf(false) }
	LaunchedEffect(working?.run) {
		visible = false
		if (working != null) {
			delay(SHOW_WORKING_AFTER_MS)
			visible = true
		}
	}
	val shown = lastOf(working)
	AnimatedDialog(visible = visible && working != null, onCloseRequest = {}) {
		val current = shown ?: return@AnimatedDialog
		DialogSurface(current.run.action.label, onClose = null) {
			Column(modifier = Modifier.padding(Ui.Padding.XL), verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
				val fraction = current.progress?.fraction
				if (fraction != null) {
					LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
				} else {
					LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				}
				Text(
					text = current.progress?.message ?: Res.string.plugin_action_working.get(),
					style = MaterialTheme.typography.bodyMedium,
				)
				Row(modifier = Modifier.fillMaxWidth()) {
					Spacer(modifier = Modifier.weight(1f))
					HdHairlineButton(label = Res.string.plugin_action_stop.get(), onClick = runner::stop)
				}
			}
		}
	}
}

/** An action's markdown, to read, copy, or save as a note, with any buttons of the plugin's own. */
@Composable
private fun ResultDialog(showing: ActionRunState.Showing?, runner: PluginActionRunner) {
	val shown = lastOf(showing)
	var copied by remember(shown?.reply) { mutableStateOf(false) }
	val copy = rememberClipboardCopier()

	AnimatedDialog(visible = showing != null, onCloseRequest = runner::dismiss) {
		val current = shown ?: return@AnimatedDialog
		val markdown = current.reply.markdown.orEmpty()
		DialogSurface(current.run.action.label, onClose = runner::dismiss) {
			val scrollState = rememberScrollState()
			Box(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
				MarkdownView(
					markdown = markdown,
					modifier = Modifier
						.fillMaxWidth()
						.verticalScroll(scrollState)
						.padding(Ui.Padding.XL),
					selectable = true,
				)
				MpScrollBarColumn(modifier = scrollBarOverlay(), state = scrollState)
			}
			val (footerButtons, buttons) = current.reply.buttons.partition { it.footer }
			if (buttons.isNotEmpty()) {
				FlowRow(
					modifier = Modifier.fillMaxWidth().padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				) {
					buttons.forEach { button ->
						HdHairlineButton(label = button.label, onClick = { runner.press(button) })
					}
				}
			}
			// Wraps, so a plugin's footer buttons never squeeze Copy and Save on a narrow screen.
			FlowRow(
				modifier = Modifier
					.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surfaceContainerLow)
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
				itemVerticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				footerButtons.forEach { button ->
					HdHairlineButton(label = button.label, onClick = { runner.press(button) })
				}
				Spacer(modifier = Modifier.weight(1f))
				HdHairlineButton(
					label = (if (copied) Res.string.plugin_document_copied else Res.string.plugin_document_copy).get(),
					onClick = {
						copy(markdown)
						copied = true
					},
				)
				HdHairlineButton(label = Res.string.plugin_document_save.get(), onClick = runner::saveAsNote, emphasised = true)
			}
		}
	}
}

private const val SHOW_WORKING_AFTER_MS = 400L
