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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectDeepLink
import com.darkrockstudios.apps.hammer.common.components.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.ConfirmationDialog
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarColumn
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.entitypicker.EntityChip
import com.darkrockstudios.apps.hammer.common.compose.entitypicker.EntityPickerDialog
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownView
import com.darkrockstudios.apps.hammer.common.compose.rememberClipboardCopier
import com.darkrockstudios.apps.hammer.common.compose.rememberIoDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.EntityCatalog
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.find
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.projecthome.SceneSelector
import com.darkrockstudios.apps.hammer.composeui.resources.Res
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_changed
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_changed_nothing
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_renumbered
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_entity_missing
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_entity_not_taken
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_no
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_not_an_option
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_required
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_run
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_scenes_choose
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_scenes_done
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_scenes_more
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_scenes_none
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_start_afresh
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_start_afresh_message
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_start_afresh_title
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_start_over
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_start_over_cancel
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_start_over_message
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_start_over_title
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_stop
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_working
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_yes
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_close
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_copied
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_copy
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_document_save
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_setting_max
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_setting_min
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_setting_range
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_setting_whole_number
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.ActionButton
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionStore
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import com.darkrockstudios.apps.hammer.operations.plugin.entityRefs
import com.darkrockstudios.texteditor.EditorLineLimits
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.koin.compose.koinInject

/** The open project's action runner, for menus to start actions with; null outside a project. */
val LocalPluginActions = staticCompositionLocalOf<PluginActionRunner?> { null }

/** Ids of the plugins the open project turned off; empty outside a project. */
val LocalDisabledPlugins = compositionLocalOf { emptySet<String>() }

/**
 * Provides [content] with the project's action runner and the plugins it turned off, and shows the
 * runner's dialogs over it. [navigate] shows what an action opens: the project's home, or the item at
 * a place with an id, returning false when there is no such item.
 */
@Composable
fun PluginActionHost(
	project: ProjectDef,
	disabledPlugins: Set<String>,
	rootSnackbar: RootSnackbarHostState,
	navigate: suspend (ActionPlace, Int?) -> Boolean,
	content: @Composable () -> Unit,
) {
	val scope = rememberCoroutineScope()
	val operations = koinInject<OperationRegistry>()
	val sessions = koinInject<ActionSessionStore>()
	val ioDispatcher = rememberIoDispatcher()
	val strings = rememberStrRes()
	val currentNavigate by rememberUpdatedState(navigate)
	val runner = remember(project) {
		PluginActionRunner(
			project = project,
			scope = scope,
			operations = operations,
			entities = projectEntityCatalog(project),
			strings = strings,
			showMessage = { rootSnackbar.showSnackbar(it) },
			navigate = { place, id -> currentNavigate(place, id) },
			sessions = sessions,
			sessionDispatcher = ioDispatcher,
		)
	}
	// The runner's scope ends with this composition, taking any save still waiting with it, so those
	// are written here. Sessions are small, so blocking on them is brief.
	DisposableEffect(runner) {
		onDispose {
			runner.flush()
			runner.close()
		}
	}
	CompositionLocalProvider(LocalPluginActions provides runner, LocalDisabledPlugins provides disabledPlugins) {
		content()
	}
	PluginActionDialogs(runner)
}

/** The project's catalog, looked up only when a page has an entity field. */
private fun projectEntityCatalog(project: ProjectDef) = object : EntityCatalog {
	override suspend fun items(kinds: Set<CatalogKind>): List<CatalogItem> =
		ProjectDefScope(project).scope.get<EntityCatalog>().items(kinds)
}

/** Shows what an action opened: the project's home, or the item at [place] with [id]; false when there is none. */
suspend fun ProjectRoot.openActionTarget(place: ActionPlace, id: Int?): Boolean {
	val link = when (place) {
		ActionPlace.Project -> {
			showHome()
			return true
		}
		ActionPlace.Scene -> id?.let(ProjectDeepLink::Scene)
		ActionPlace.Note -> id?.let(ProjectDeepLink::Note)
		ActionPlace.Entry -> id?.let(ProjectDeepLink::EncyclopediaEntry)
		ActionPlace.Event -> id?.let(ProjectDeepLink::TimelineEvent)
	}
	return link != null && navigateToDeepLink(link)
}

/** A menu item for each plugin action at [place], run on the item with [itemId] there. */
@Composable
fun PluginActionMenuItems(place: ActionPlace, itemId: Int?, onChosen: () -> Unit) {
	val runner = LocalPluginActions.current ?: return
	koinInject<PluginUiRegistry>().actions(place, LocalDisabledPlugins.current).forEach { action ->
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
	val catalog by runner.catalog.collectAsState()
	val working = state as? ActionRunState.Working
	// A call made from a page keeps the page showing, rather than closing and opening it again.
	PageDialog((state as? ActionRunState.Page) ?: working?.from, working?.takeIf { it.from != null }, scenes, catalog, runner)
	WorkingDialog(working?.takeIf { it.from == null }, runner)
	ChangedDialog(state as? ActionRunState.Changed, runner)
}

/** [current], or the last one there was, so a dialog keeps its content while it animates closed. */
@Composable
private fun <T : Any> lastOf(current: T?): T? {
	val last = remember { object { var value: T? = null } }
	if (current != null) last.value = current
	return current ?: last.value
}

/** Whether a call has run long enough to show its progress, so quick ones don't flash it. */
@Composable
private fun showsProgress(working: ActionRunState.Working?): Boolean {
	var shown by remember { mutableStateOf(false) }
	LaunchedEffect(working?.run, working != null) {
		shown = false
		if (working != null) {
			delay(SHOW_WORKING_AFTER_MS)
			shown = true
		}
	}
	return shown && working != null
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

/**
 * A page of an action: its markdown, to read, copy, or save as a note when the page asks nothing;
 * its fields with any errors under them; and its buttons. While [working] on a call from it, the
 * page stays, its buttons give way to the call's progress and Stop.
 */
@Composable
private fun PageDialog(
	page: ActionRunState.Page?,
	working: ActionRunState.Working?,
	scenes: List<ExportableScene>?,
	catalog: List<CatalogItem>?,
	runner: PluginActionRunner,
) {
	val shown = lastOf(page)
	// Keyed on the page, so a chooser left open never outlives it.
	var choosing by remember(page?.page) { mutableStateOf<ActionField.Scenes?>(null) }
	var choosingEntity by remember(page?.page) { mutableStateOf<ActionField.Entity?>(null) }
	var copied by remember(shown?.page) { mutableStateOf(false) }
	var confirmingStartOver by remember(page?.run) { mutableStateOf(false) }
	val copy = rememberClipboardCopier()
	val progress = showsProgress(working)

	AnimatedDialog(visible = page != null, onCloseRequest = runner::dismiss) {
		val current = shown ?: return@AnimatedDialog
		val content = current.page
		val markdown = content.markdown
		// Only Stop ends a call, as when there is no page.
		DialogSurface(content.title ?: current.run.action.label, onClose = runner::dismiss.takeIf { working == null }) {
			val scrollState = rememberScrollState()
			Box(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.verticalScroll(scrollState)
						.padding(Ui.Padding.XL),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				) {
					if (markdown != null) MarkdownView(markdown = markdown, modifier = Modifier.fillMaxWidth(), isSelectable = true, paragraphIndent = false)
					val inline = content.buttons.filter { it.inline }
					if (inline.isNotEmpty()) {
						FlowRow(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
							verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
						) {
							inline.forEach { button -> PageButton(button, enabled = working == null, runner = runner) }
						}
					}
					current.errors[""]?.let { FieldErrorText(it) }
					content.fields.forEach { field ->
						// Keyed on the page, so a rich editor, which reads its text only once, starts afresh on a new one.
						key(content, field.key) {
							PageField(
								field = field,
								value = current.values[field.key],
								error = current.errors[field.key],
								scenes = scenes,
								catalog = catalog,
								onChange = { runner.change(field.key, it) },
								onChooseScenes = { choosing = field as? ActionField.Scenes },
								onChooseEntity = { choosingEntity = field as? ActionField.Entity },
							)
						}
					}
				}
				MpScrollBarColumn(modifier = scrollBarOverlay(), state = scrollState)
			}
			if (progress && working != null) {
				ProgressFooter(working, runner)
			} else {
				PageFooter(
					current = current,
					enabled = working == null,
					copied = copied,
					onCopy = {
						copy(markdown.orEmpty())
						copied = true
					},
					onStartOver = { confirmingStartOver = true },
					runner = runner,
				)
			}
		}
	}

	ConfirmationDialog(
		visible = confirmingStartOver && page?.resumed == true && working == null,
		title = Res.string.plugin_action_start_over_title.get(),
		message = Res.string.plugin_action_start_over_message.get(),
		confirmLabel = Res.string.plugin_action_start_over.get(),
		cancelLabel = Res.string.plugin_action_start_over_cancel.get(),
		onConfirm = {
			confirmingStartOver = false
			runner.startOver()
		},
		onDismiss = { confirmingStartOver = false },
		destructive = true,
	)

	val field = choosing
	val values = page?.values
	SceneChooser(
		field = field?.takeIf { page != null && working == null },
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

	val entityField = choosingEntity?.takeIf { page != null && working == null && catalog != null }
	val shownEntityField = lastOf(entityField)
	if (shownEntityField != null) {
		EntityPickerDialog(
			visible = entityField != null,
			title = shownEntityField.label,
			items = catalog.orEmpty(),
			filter = shownEntityField.filter,
			multiple = shownEntityField.multiple,
			selected = entityRefs(values?.get(shownEntityField.key)),
			onSelectedChange = { refs ->
				val chosen = choosingEntity ?: return@EntityPickerDialog
				runner.change(chosen.key, chosen.valueOf(refs))
			},
			onDismiss = { choosingEntity = null },
		)
	}
}

@Composable
private fun PageButton(button: ActionButton, enabled: Boolean, runner: PluginActionRunner) {
	HdHairlineButton(label = button.label, onClick = { runner.press(button) }, emphasised = button.primary, enabled = enabled)
}

/**
 * The page's footer buttons, the primary ones last; Start over on a page restored from a kept run;
 * Copy for a page that shows markdown and asks nothing, and Save as note too when it has no buttons
 * to go on with; and Run on an action's first page.
 */
@Composable
private fun PageFooter(
	current: ActionRunState.Page,
	enabled: Boolean,
	copied: Boolean,
	onCopy: () -> Unit,
	onStartOver: () -> Unit,
	runner: PluginActionRunner,
) {
	val content = current.page
	val (primary, others) = content.buttons.filter { !it.inline }.partition { it.primary }
	val copyable = content.markdown != null && content.fields.isEmpty()
	val document = content.isDocument
	if (primary.isEmpty() && others.isEmpty() && !copyable && !current.first && !current.resumed) return
	// Wraps, so a plugin's buttons never squeeze Copy and Save on a narrow screen.
	FlowRow(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		itemVerticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		if (current.resumed) {
			HdHairlineButton(label = Res.string.plugin_action_start_over.get(), onClick = onStartOver, enabled = enabled)
		}
		others.forEach { button -> PageButton(button, enabled, runner) }
		Spacer(modifier = Modifier.weight(1f))
		if (copyable) {
			HdHairlineButton(
				label = (if (copied) Res.string.plugin_document_copied else Res.string.plugin_document_copy).get(),
				onClick = onCopy,
			)
		}
		if (document) {
			HdHairlineButton(
				label = Res.string.plugin_document_save.get(),
				onClick = runner::saveAsNote,
				emphasised = !current.first,
				enabled = enabled && !current.saving,
			)
		}
		primary.forEach { button -> PageButton(button, enabled, runner) }
		if (current.first) {
			HdHairlineButton(label = Res.string.plugin_action_run.get(), onClick = { runner.press(null) }, emphasised = true, enabled = enabled)
		}
	}
}

@Composable
private fun ProgressFooter(working: ActionRunState.Working, runner: PluginActionRunner) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		Progress(working)
		StopRow(runner)
	}
}

@Composable
private fun StopRow(runner: PluginActionRunner) {
	Row(modifier = Modifier.fillMaxWidth()) {
		Spacer(modifier = Modifier.weight(1f))
		HdHairlineButton(label = Res.string.plugin_action_stop.get(), onClick = runner::stop)
	}
}

/** A bar and a line the plugin sets as it reports; without reports, the bar is indeterminate. */
@Composable
private fun Progress(working: ActionRunState.Working) {
	val fraction = working.progress?.fraction
	if (fraction != null) {
		LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
	} else {
		LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
	}
	Text(
		text = working.progress?.message ?: Res.string.plugin_action_working.get(),
		style = MaterialTheme.typography.bodyMedium,
	)
}

/** One field of a page, in the widget its type calls for, with its error under it. */
@Composable
private fun PageField(
	field: ActionField,
	value: JsonElement?,
	error: FieldError?,
	scenes: List<ExportableScene>?,
	catalog: List<CatalogItem>?,
	onChange: (JsonElement) -> Unit,
	onChooseScenes: () -> Unit,
	onChooseEntity: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
		when (field) {
			is ActionField.Setting -> when (val declaration = field.declaration) {
				is SettingDeclaration.Number -> NumberField(declaration, value, error, onChange)
				else -> {
					DeclaredSettingsForm(
						declarations = listOf(declaration),
						values = JsonObject(mapOf(field.key to (value ?: field.default))),
						onChange = { _, changed -> onChange(changed) },
					)
					error?.let { FieldErrorText(it) }
				}
			}

			is ActionField.Scenes -> {
				ScenesField(field, sceneIds(value), scenes, onChooseScenes)
				error?.let { FieldErrorText(it) }
			}

			is ActionField.Entity -> {
				val picked = entityRefs(value)
				EntityField(field, picked, catalog, onRemove = { ref -> onChange(field.valueOf(picked - ref)) }, onChoose = onChooseEntity)
				error?.let { FieldErrorText(it) }
			}

			is ActionField.Text -> TextField(field, (value as? JsonPrimitive)?.contentOrNull.orEmpty(), error, onChange)
		}
	}
}

@Composable
private fun TextField(field: ActionField.Text, value: String, error: FieldError?, onChange: (JsonElement) -> Unit) {
	val errorText = error?.let { fieldErrorText(it) }
	// A field for more than a line is often a whole question, so its label is in sentence case as a
	// toggle row's is, not a mono caption.
	val longLabel = field.multiline || field.format != TextFormat.Plain
	if (longLabel) Text(text = field.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
	if (field.format == TextFormat.Plain) {
		HdHairlineField(
			label = if (longLabel) "" else field.label,
			value = value,
			onValueChange = { onChange(JsonPrimitive(it)) },
			singleLine = !field.multiline,
			minLines = if (field.multiline) MULTILINE_MIN_LINES else 1,
			error = errorText,
		)
	} else {
		MarkdownEditField(
			initialMarkdown = value,
			onMarkdownChanged = { onChange(JsonPrimitive(it)) },
			modifier = Modifier.fillMaxWidth(),
			extendedFormatBar = field.format == TextFormat.RichExtended,
			// Grows with the text: the dialog scrolls, so the editor cannot take the height left.
			lineLimits = EditorLineLimits.MultiLine(minLines = RICH_MIN_LINES, maxLines = RICH_MAX_LINES),
		)
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
		)
		errorText?.let { HdMonoLabel(text = it, color = MaterialTheme.colorScheme.error) }
	}
	FieldHint(field.hint)
}

/**
 * An `int` field. Unlike a setting's, it passes on any whole number typed, in range or not, so the
 * check before a call sees what the user sees; until it is one, the value stays as it was.
 */
@Composable
private fun NumberField(declaration: SettingDeclaration.Number, value: JsonElement?, error: FieldError?, onChange: (JsonElement) -> Unit) {
	var typed by remember { mutableStateOf((value as? JsonPrimitive)?.content ?: declaration.defaultValue.toString()) }
	val number = typed.trim().toLongOrNull()
	val outOfRange = FieldError.OutOfRange(declaration.min, declaration.max).takeIf { number?.let { declaration.accept(JsonPrimitive(it)) } == null }
	HdHairlineField(
		label = declaration.label,
		value = typed,
		onValueChange = { entered ->
			typed = entered
			entered.trim().toLongOrNull()?.let { onChange(JsonPrimitive(it)) }
		},
		error = (error ?: outOfRange)?.let { fieldErrorText(it) },
		keyboardType = KeyboardType.Number,
	)
	FieldHint(declaration.hint)
}

@Composable
private fun FieldErrorText(error: FieldError) {
	Text(text = fieldErrorText(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun fieldErrorText(error: FieldError): String = when (error) {
	is FieldError.Plugin -> error.message
	FieldError.Required -> Res.string.plugin_action_required.get()
	FieldError.NotAnOption -> Res.string.plugin_action_not_an_option.get()
	FieldError.Missing -> Res.string.plugin_action_entity_missing.get()
	FieldError.NotTaken -> Res.string.plugin_action_entity_not_taken.get()
	is FieldError.OutOfRange -> {
		val min = error.min
		val max = error.max
		when {
			min != null && max != null -> Res.string.plugin_setting_range.get(min, max)
			min != null -> Res.string.plugin_setting_min.get(min)
			max != null -> Res.string.plugin_setting_max.get(max)
			else -> Res.string.plugin_setting_whole_number.get()
		}
	}
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
		FieldHint(field.hint)
	}
}

/**
 * An entity field: its label, a chip for each pick with × to remove it, and a button opening the
 * picker. The picks show once the project's items have loaded, which they need for their names.
 */
@Composable
private fun EntityField(
	field: ActionField.Entity,
	picked: List<CatalogRef>,
	catalog: List<CatalogItem>?,
	onRemove: (CatalogRef) -> Unit,
	onChoose: () -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.S)) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
			HdMonoLabel(text = field.label)
			FlowRow(
				modifier = Modifier.weight(1f),
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.S),
				itemVerticalAlignment = Alignment.CenterVertically,
			) {
				when {
					picked.isEmpty() -> Text(text = Res.string.plugin_action_scenes_none.get(), style = MaterialTheme.typography.bodyMedium)
					catalog != null -> picked.forEach { ref ->
						// Only a pick made since the items loaded can be missing from them.
						val item = catalog.find(ref) ?: CatalogItem(ref.kind, ref.id, "#${ref.id}")
						EntityChip(item = item, onClick = onChoose, onRemove = { onRemove(ref) })
					}
				}
			}
			HdHairlineButton(label = Res.string.plugin_action_scenes_choose.get(), onClick = onChoose, enabled = catalog != null)
		}
		FieldHint(field.hint)
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

/**
 * A run that cannot carry on, saved by another version of the plugin or holding an item sync renumbered:
 * what was entered in it, to copy or save as a note, and Start afresh to clear it. Closing leaves it for
 * next time.
 */
@Composable
private fun ChangedDialog(changed: ActionRunState.Changed?, runner: PluginActionRunner) {
	val shown = lastOf(changed)
	var copied by remember(shown?.run) { mutableStateOf(false) }
	var confirmingStartAfresh by remember(changed?.run) { mutableStateOf(false) }
	val copy = rememberClipboardCopier()
	AnimatedDialog(visible = changed != null, onCloseRequest = runner::dismiss) {
		val current = shown ?: return@AnimatedDialog
		val markdown = answersMarkdown(current.answers.values, Res.string.plugin_action_yes.get(), Res.string.plugin_action_no.get())
		DialogSurface(current.run.action.label, onClose = runner::dismiss) {
			val scrollState = rememberScrollState()
			Box(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.verticalScroll(scrollState)
						.padding(Ui.Padding.XL),
					verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
				) {
					Text(
						text = (if (current.renumbered) Res.string.plugin_action_renumbered else Res.string.plugin_action_changed).get(),
						style = MaterialTheme.typography.bodyMedium,
					)
					if (markdown.isEmpty()) {
						Text(text = Res.string.plugin_action_changed_nothing.get(), style = MaterialTheme.typography.bodyMedium)
					} else {
						MarkdownView(markdown = markdown, modifier = Modifier.fillMaxWidth(), isSelectable = true, paragraphIndent = false)
					}
				}
				MpScrollBarColumn(modifier = scrollBarOverlay(), state = scrollState)
			}
			FlowRow(
				modifier = Modifier
					.fillMaxWidth()
					.background(MaterialTheme.colorScheme.surfaceContainerLow)
					.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
				itemVerticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
			) {
				Spacer(modifier = Modifier.weight(1f))
				if (markdown.isNotEmpty()) {
					HdHairlineButton(
						label = (if (copied) Res.string.plugin_document_copied else Res.string.plugin_document_copy).get(),
						onClick = {
							copy(markdown)
							copied = true
						},
					)
					HdHairlineButton(label = Res.string.plugin_document_save.get(), onClick = runner::saveAsNote, enabled = !current.saving)
				}
				HdHairlineButton(label = Res.string.plugin_action_start_afresh.get(), onClick = { confirmingStartAfresh = true }, emphasised = true)
			}
		}
	}

	ConfirmationDialog(
		visible = confirmingStartAfresh && changed != null,
		title = Res.string.plugin_action_start_afresh_title.get(),
		message = Res.string.plugin_action_start_afresh_message.get(),
		confirmLabel = Res.string.plugin_action_start_afresh.get(),
		cancelLabel = Res.string.plugin_action_start_over_cancel.get(),
		onConfirm = {
			confirmingStartAfresh = false
			runner.startAfresh()
		},
		onDismiss = { confirmingStartAfresh = false },
		destructive = true,
	)
}

/** A call made with no page showing, once it has taken a moment, so quick actions don't flash a dialog. */
@Composable
private fun WorkingDialog(working: ActionRunState.Working?, runner: PluginActionRunner) {
	val visible = showsProgress(working)
	val shown = lastOf(working)
	AnimatedDialog(visible = visible && working != null, onCloseRequest = {}) {
		val current = shown ?: return@AnimatedDialog
		DialogSurface(current.run.action.label, onClose = null) {
			Column(modifier = Modifier.padding(Ui.Padding.XL), verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
				Progress(current)
				StopRow(runner)
			}
		}
	}
}

private const val SHOW_WORKING_AFTER_MS = 400L
private const val MULTILINE_MIN_LINES = 3
private const val RICH_MIN_LINES = 3
private const val RICH_MAX_LINES = 12
