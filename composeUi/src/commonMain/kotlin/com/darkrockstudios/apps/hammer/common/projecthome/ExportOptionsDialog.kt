package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdButtonBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineCheckbox
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHelpButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerList
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ExportFont
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.data.ManuscriptContact
import com.darkrockstudios.apps.hammer.common.data.export.BuiltInExportFormat
import com.darkrockstudios.apps.hammer.project_home_export_blank_lines_hint
import com.darkrockstudios.apps.hammer.project_home_export_blank_lines_label
import com.darkrockstudios.apps.hammer.project_home_export_cancel
import com.darkrockstudios.apps.hammer.project_home_export_chapters_label
import com.darkrockstudios.apps.hammer.project_home_export_close
import com.darkrockstudios.apps.hammer.project_home_export_contact_address
import com.darkrockstudios.apps.hammer.project_home_export_contact_email
import com.darkrockstudios.apps.hammer.project_home_export_contact_hint
import com.darkrockstudios.apps.hammer.project_home_export_contact_label
import com.darkrockstudios.apps.hammer.project_home_export_contact_legal_name
import com.darkrockstudios.apps.hammer.project_home_export_contact_phone
import com.darkrockstudios.apps.hammer.project_home_export_dialog_title
import com.darkrockstudios.apps.hammer.project_home_export_execute
import com.darkrockstudios.apps.hammer.project_home_export_font_label
import com.darkrockstudios.apps.hammer.project_home_export_format_label
import com.darkrockstudios.apps.hammer.project_home_export_help_icon_description
import com.darkrockstudios.apps.hammer.project_home_export_limit_scenes_hint
import com.darkrockstudios.apps.hammer.project_home_export_limit_scenes_label
import com.darkrockstudios.apps.hammer.project_home_export_manuscript_classic_hint
import com.darkrockstudios.apps.hammer.project_home_export_manuscript_classic_label
import com.darkrockstudios.apps.hammer.project_home_export_manuscript_short_story_hint
import com.darkrockstudios.apps.hammer.project_home_export_manuscript_short_story_label
import com.darkrockstudios.apps.hammer.project_home_export_number_chapters_label
import com.darkrockstudios.apps.hammer.project_home_export_scenes_clear_all
import com.darkrockstudios.apps.hammer.project_home_export_scenes_label
import com.darkrockstudios.apps.hammer.project_home_export_scenes_select_all
import com.darkrockstudios.apps.hammer.project_home_export_scenes_selected
import com.darkrockstudios.apps.hammer.project_home_export_section

private val DialogMaxWidth = 520.dp

internal const val EXPORT_CONTACT_LEGAL_NAME_TAG = "export-contact-legal-name"
internal const val EXPORT_CONTACT_ADDRESS_TAG = "export-contact-address"
internal const val EXPORT_CONTACT_PHONE_TAG = "export-contact-phone"
internal const val EXPORT_CONTACT_EMAIL_TAG = "export-contact-email"

/**
 * Fully controlled: all option state lives in the component's retained state so
 * in-dialog edits survive configuration changes (see issue #885).
 */
@Composable
fun ExportOptionsDialog(
	visible: Boolean,
	options: ExportOptions,
	contact: ManuscriptContact,
	exportableScenes: List<ExportableScene>,
	onOptionsChanged: (ExportOptions) -> Unit,
	onContactChanged: (ManuscriptContact) -> Unit,
	onCancel: () -> Unit,
	onConfirm: (ExportOptions) -> Unit,
	onDismissed: () -> Unit = {},
	working: Boolean = false,
) {
	var showHelp by remember { mutableStateOf(false) }

	AnimatedDialog(
		visible = visible,
		onCloseRequest = { if (!working) onCancel() },
		dismissOnTapOutside = !working,
		onDismissed = onDismissed,
	) {
		ExportOptionsDialogContent(
			options = options,
			contact = contact,
			formats = exportFormatChoices(),
			exportableScenes = exportableScenes,
			onOptionsChanged = onOptionsChanged,
			onContactChanged = onContactChanged,
			onCancel = onCancel,
			onConfirm = onConfirm,
			onShowHelp = { showHelp = true },
			working = working,
		)
	}

	if (showHelp) {
		ExportHelpDialog(onDismiss = { showHelp = false })
	}
}

@Composable
internal fun ExportOptionsDialogContent(
	options: ExportOptions,
	contact: ManuscriptContact,
	formats: List<ExportFormatChoice>,
	exportableScenes: List<ExportableScene>,
	onOptionsChanged: (ExportOptions) -> Unit,
	onContactChanged: (ManuscriptContact) -> Unit,
	onCancel: () -> Unit,
	onConfirm: (ExportOptions) -> Unit,
	onShowHelp: () -> Unit,
	working: Boolean = false,
) {
	val allSceneIds = remember(exportableScenes) { allSceneIds(exportableScenes) }
	// A format no longer offered shows, and exports, as the default instead.
	val shownFormat = formats.firstOrNull { it.formatId == options.format }
		?: formats.firstOrNull { it.formatId == ExportOptions().format }
		?: formats.first()
	val shown = options.copy(format = shownFormat.formatId)
	// The export runs with the options and contact as confirmed, so edits while working would never apply.
	val editable = !working

	Surface(
		modifier = Modifier
			.padding(Ui.Padding.M)
			.widthIn(max = DialogMaxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(
			width = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		),
	) {
		Column {
			HdMasthead(
				section = Res.string.project_home_export_section.get(),
				trailing = {
					HdHelpButton(
						onClick = onShowHelp,
						contentDescription = Res.string.project_home_export_help_icon_description.get(),
					)
					HdMastheadAction(
						label = Res.string.project_home_export_close.get(),
						onClick = { if (!working) onCancel() },
					)
				},
			)
			HdFolioDivider()

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						start = Ui.Padding.XL,
						end = Ui.Padding.XL,
						top = Ui.Padding.L,
						bottom = Ui.Padding.M,
					),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = Res.string.project_home_export_dialog_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Column(
				modifier = Modifier
					// Short screens and large font scales must scroll the options rather
					// than push the button bar off-screen.
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
					.padding(
						horizontal = Ui.Padding.XL,
						vertical = Ui.Padding.XL,
					),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.XL),
			) {
				HdHairlineToggleRow(
					checked = shown.treatTopLevelAsChapters,
					onCheckedChange = { onOptionsChanged(shown.copy(treatTopLevelAsChapters = it)) },
					label = Res.string.project_home_export_chapters_label.get(),
					enabled = editable,
				)

				HdHairlineDropdown(
					title = Res.string.project_home_export_format_label.get(),
					options = formats,
					selected = shownFormat,
					onSelect = { onOptionsChanged(shown.copy(format = it.formatId)) },
					label = { it.label },
					enabled = editable,
				)

				if (shown.numbersChapters) {
					HdHairlineToggleRow(
						checked = shown.numberChapters,
						onCheckedChange = { onOptionsChanged(shown.copy(numberChapters = it)) },
						label = Res.string.project_home_export_number_chapters_label.get(),
						enabled = editable,
					)
				}

				if (shown.format in FONT_EXPORT_FORMATS) {
					HdHairlineDropdown(
						title = Res.string.project_home_export_font_label.get(),
						options = ExportFont.entries,
						selected = shown.font,
						onSelect = { onOptionsChanged(shown.copy(font = it)) },
						label = { it.faceName },
						enabled = editable,
					)
				}

				if (shown.format == BuiltInExportFormat.DOCX) {
					HdHairlineToggleRow(
						checked = shown.manuscriptShortStory,
						onCheckedChange = { onOptionsChanged(shown.copy(manuscriptShortStory = it)) },
						label = Res.string.project_home_export_manuscript_short_story_label.get(),
						hint = Res.string.project_home_export_manuscript_short_story_hint.get(),
						enabled = editable,
					)
					HdHairlineToggleRow(
						checked = shown.manuscriptClassic,
						onCheckedChange = { onOptionsChanged(shown.copy(manuscriptClassic = it)) },
						label = Res.string.project_home_export_manuscript_classic_label.get(),
						hint = Res.string.project_home_export_manuscript_classic_hint.get(),
						enabled = editable,
					)
					ManuscriptContactFields(
						contact = contact,
						onContactChanged = onContactChanged,
						enabled = editable,
					)
				}

				if (shown.format !in BLANK_LINE_FREE_FORMATS) {
					HdHairlineToggleRow(
						checked = shown.keepBlankLines,
						onCheckedChange = { onOptionsChanged(shown.copy(keepBlankLines = it)) },
						label = Res.string.project_home_export_blank_lines_label.get(),
						hint = Res.string.project_home_export_blank_lines_hint.get(),
						enabled = editable,
					)
				}

				if (allSceneIds.isNotEmpty()) {
					HdHairlineToggleRow(
						checked = shown.sceneIds != null,
						onCheckedChange = { limit ->
							onOptionsChanged(shown.copy(sceneIds = if (limit) emptySet() else null))
						},
						label = Res.string.project_home_export_limit_scenes_label.get(),
						hint = Res.string.project_home_export_limit_scenes_hint.get(),
						enabled = editable,
					)

					val selectedIds = shown.sceneIds
					if (selectedIds != null) {
						SceneSelector(
							entries = exportableScenes,
							selected = selectedIds,
							multiple = true,
							onSelectionChanged = { onOptionsChanged(shown.copy(sceneIds = it)) },
							enabled = editable,
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(Ui.Padding.M))

			HdButtonBar(
				cancelLabel = Res.string.project_home_export_cancel.get(),
				primaryLabel = Res.string.project_home_export_execute.get(),
				onCancel = onCancel,
				onPrimary = {
					// Selecting every scene means no limit at all: exporting the full story
					// keeps empty chapter groups instead of silently dropping them.
					val selectedIds = shown.sceneIds
					val confirmed = if (selectedIds != null && isFullSelection(exportableScenes, selectedIds)) {
						shown.copy(sceneIds = null)
					} else {
						shown
					}
					onConfirm(confirmed)
				},
				primaryLoading = working,
				primaryEnabled = shown.sceneIds?.isNotEmpty() ?: true,
				cancelEnabled = !working,
			)
		}
	}
}

@Composable
private fun ManuscriptContactFields(
	contact: ManuscriptContact,
	onContactChanged: (ManuscriptContact) -> Unit,
	enabled: Boolean,
) {
	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
		Column {
			HdMonoLabel(text = Res.string.project_home_export_contact_label.get())
			Text(
				text = Res.string.project_home_export_contact_hint.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		HdHairlineField(
			label = Res.string.project_home_export_contact_legal_name.get(),
			value = contact.legalName,
			onValueChange = { onContactChanged(contact.copy(legalName = it)) },
			capitalization = KeyboardCapitalization.Words,
			autoCorrectEnabled = false,
			enabled = enabled,
			testTag = EXPORT_CONTACT_LEGAL_NAME_TAG,
		)
		HdHairlineField(
			label = Res.string.project_home_export_contact_address.get(),
			value = contact.address,
			onValueChange = { onContactChanged(contact.copy(address = it)) },
			singleLine = false,
			minLines = 2,
			maxLines = 4,
			capitalization = KeyboardCapitalization.Words,
			autoCorrectEnabled = false,
			enabled = enabled,
			testTag = EXPORT_CONTACT_ADDRESS_TAG,
		)
		HdHairlineField(
			label = Res.string.project_home_export_contact_phone.get(),
			value = contact.phone,
			onValueChange = { onContactChanged(contact.copy(phone = it)) },
			keyboardType = KeyboardType.Phone,
			enabled = enabled,
			testTag = EXPORT_CONTACT_PHONE_TAG,
		)
		HdHairlineField(
			label = Res.string.project_home_export_contact_email.get(),
			value = contact.email,
			onValueChange = { onContactChanged(contact.copy(email = it)) },
			keyboardType = KeyboardType.Email,
			capitalization = KeyboardCapitalization.None,
			autoCorrectEnabled = false,
			imeAction = ImeAction.Done,
			enabled = enabled,
			testTag = EXPORT_CONTACT_EMAIL_TAG,
		)
	}
}

/**
 * PDF renders in its library's built-in face, Markdown carries no font, and the
 * Word manuscript takes its font from the Classic toggle.
 */
private val FONT_EXPORT_FORMATS = setOf(BuiltInExportFormat.EPUB, BuiltInExportFormat.RTF)

/** Markdown keeps its source as written, and a manuscript never uses blank lines. */
private val BLANK_LINE_FREE_FORMATS = setOf(BuiltInExportFormat.MARKDOWN, BuiltInExportFormat.DOCX)

/** Markdown only has chapter headings in chapter mode. */
private val ExportOptions.numbersChapters: Boolean
	get() = when (format) {
		BuiltInExportFormat.MARKDOWN -> treatTopLevelAsChapters
		else -> true
	}
