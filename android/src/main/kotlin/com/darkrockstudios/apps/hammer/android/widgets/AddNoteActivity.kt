package com.darkrockstudios.apps.hammer.android.widgets

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.serializableStateSaver
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val DialogMaxWidth = 480.dp

class AddNoteActivity : ComponentActivity(), KoinComponent {

	private val projectsRepository: ProjectsRepository by inject()
	private val projectsMetadataRepository: ProjectMetadataDatasource by inject()
	private val globalSettingsStore: GlobalSettingsStore by inject()
	private val globalSettings = MutableValue(globalSettingsStore.globalSettings)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		// Drop touches delivered while another app's window overlays this dialog, so a tapjacking
		// overlay can't drive the note save into a chosen project.
		window.decorView.filterTouchesWhenObscured = true

		setFinishOnTouchOutside(false)
		window.setBackgroundDrawableResource(android.R.color.transparent)

		val projectNameExtra = intent.extras?.getString(EXTRA_PROJECT_NAME)
		val projectName = if (projectNameExtra.isNullOrBlank()) null else projectNameExtra

		val projects = projectsRepository.getProjects().map { projectDef ->
			val metadata = projectsMetadataRepository.loadMetadata(projectDef)
			Pair(projectDef, metadata)
		}.sortedByDescending { it.second.info.lastAccessed }.map { it.first }

		if (projects.isEmpty()) {
			Toast.makeText(
				this,
				getString(R.string.note_widget_toast_no_projects),
				Toast.LENGTH_SHORT
			).show()
			finish()
		} else {
			val preselectedProject = projects.find { projectDef -> projectDef.name == projectName }
			if (projectName != null && preselectedProject == null) {
				val text = getString(R.string.note_widget_dialog_failure_bad_project, projectName)
				Toast.makeText(this, text, Toast.LENGTH_LONG).show()
				finish()
			}

			setContent {
				var noteText by rememberSaveable { mutableStateOf("") }
				var selectedProject by rememberSaveable(
					saver = serializableStateSaver(ProjectDef.serializer())
				) {
					mutableStateOf(projects.first())
				}
				var confirmCancel by rememberSaveable { mutableStateOf(false) }

				val settingsState by globalSettings.subscribeAsState()
				val isDark = when (settingsState.uiTheme) {
					UiTheme.Light -> false
					UiTheme.Dark -> true
					UiTheme.FollowSystem -> isSystemInDarkTheme()
				}

				val localCtx = LocalContext.current
				fun getDynamicColorScheme(useDark: Boolean): ColorScheme? {
					val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
					return when {
						dynamicColor && useDark -> dynamicDarkColorScheme(localCtx)
						dynamicColor && !useDark -> dynamicLightColorScheme(localCtx)
						else -> null
					}
				}

				val tryCancel = {
					if (noteText.isNotBlank()) confirmCancel = true else finish()
				}

				BackHandler(true) { tryCancel() }

				AppTheme(
					settings = settingsState,
					useDarkTheme = isDark,
					getOverrideColorScheme = ::getDynamicColorScheme
				) {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.systemBarsPadding()
							.padding(Ui.Padding.M),
						contentAlignment = Alignment.Center,
					) {
						if (!confirmCancel) {
							AddNoteDialog(
								projectName = projectName,
								projects = projects,
								preselectedProject = preselectedProject,
								noteText = noteText,
								onNoteTextChange = { noteText = it },
								onProjectSelected = { selectedProject = it },
								onCancel = tryCancel,
								onSave = {
									if (noteText.isNotBlank()) {
										saveNote(preselectedProject ?: selectedProject, noteText)
									}
								},
							)
						} else {
							DiscardConfirmDialog(
								onKeepEditing = { confirmCancel = false },
								onDiscard = ::finish,
							)
						}
					}
				}
			}
		}
	}

	private fun saveNote(project: ProjectDef, note: String) {
		val data = Data.Builder()
		data.putString(AddNoteWorker.DATA_PROJECT_NAME, project.name)
		data.putString(AddNoteWorker.DATA_NOTE_TEXT, note)

		val request = OneTimeWorkRequestBuilder<AddNoteWorker>()
			.setInputData(data.build())
			.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
			.build()

		WorkManager.getInstance(this).enqueue(request)

		Toast.makeText(this, getString(R.string.note_widget_toast_success), Toast.LENGTH_SHORT).show()

		finish()
	}

	companion object {
		const val EXTRA_PROJECT_NAME = "project_name"
	}
}

@Composable
private fun AddNoteDialog(
	projectName: String?,
	projects: List<ProjectDef>,
	preselectedProject: ProjectDef?,
	noteText: String,
	onNoteTextChange: (String) -> Unit,
	onProjectSelected: (ProjectDef) -> Unit,
	onCancel: () -> Unit,
	onSave: () -> Unit,
) {
	Surface(
		modifier = Modifier
			.widthIn(max = DialogMaxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
	) {
		Column {
			HdMasthead(
				section = "ADD NOTE",
				leadingMeta = if (projectName != null) listOf(projectName) else emptyList(),
				trailing = { HdMastheadAction(label = "× CLOSE", onClick = onCancel) },
			)
			HdFolioDivider()
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				if (preselectedProject == null) {
					ProjectDropDownUi(projects = projects, onProjectSelected = onProjectSelected)
				}
				HdHairlineField(
					label = "NOTE",
					value = noteText,
					onValueChange = onNoteTextChange,
					singleLine = false,
					minLines = 4,
					maxLines = 10,
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					HdHairlineButton(
						label = stringResource(R.string.note_widget_dialog_cancel_button),
						onClick = onCancel,
					)
					HdHairlineButton(
						label = stringResource(R.string.note_widget_dialog_save_button),
						emphasised = true,
						enabled = noteText.isNotBlank(),
						onClick = onSave,
					)
				}
			}
		}
	}
}

@Composable
private fun DiscardConfirmDialog(
	onKeepEditing: () -> Unit,
	onDiscard: () -> Unit,
) {
	Surface(
		modifier = Modifier
			.widthIn(max = DialogMaxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
	) {
		Column {
			HdMasthead(section = "DISCARD NOTE")
			HdFolioDivider()
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(Ui.Padding.XL),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				Text(
					text = stringResource(R.string.note_widget_confirm_cancel_title),
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface,
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					HdHairlineButton(
						label = stringResource(R.string.note_widget_confirm_cancel_negative),
						onClick = onKeepEditing,
					)
					HdHairlineButton(
						label = stringResource(R.string.note_widget_confirm_cancel_positive),
						danger = true,
						onClick = onDiscard,
					)
				}
			}
		}
	}
}
