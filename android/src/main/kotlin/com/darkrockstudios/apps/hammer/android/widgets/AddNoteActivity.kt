package com.darkrockstudios.apps.hammer.android.widgets

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.compose.SpacerL
import com.darkrockstudios.apps.hammer.common.compose.SpacerXL
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AddNoteActivity : ComponentActivity(), KoinComponent {

	private val projectsRepository: ProjectsRepository by inject()
	private val projectsMetadataRepository: ProjectMetadataRepository by inject()
	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val globalSettings = MutableValue(globalSettingsRepository.globalSettings)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setFinishOnTouchOutside(false)
		window.setBackgroundDrawableResource(android.R.color.transparent)

		val projectNameExtra = intent.extras?.getString(EXTRA_PROJECT_NAME)
		val projectName = if (projectNameExtra.isNullOrBlank()) {
			null
		} else {
			projectNameExtra
		}

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
			// Bail if we can't find the project
			if (projectName != null && preselectedProject == null) {
				val text = getString(R.string.note_widget_dialog_failure_bad_project, projectName)
				Toast.makeText(this, text, Toast.LENGTH_LONG).show()
				finish()
			}

			setContent {
				var noteText by rememberSaveable { mutableStateOf("") }
				var selectedProject by rememberSaveable { mutableStateOf(projects.first()) }
				var confirmCancel by rememberSaveable { mutableStateOf(false) }

				val settingsState by globalSettings.subscribeAsState()
				val isDark = when (settingsState.uiTheme) {
					UiTheme.Light -> false
					UiTheme.Dark -> true
					UiTheme.FollowSystem -> isSystemInDarkTheme()
				}

				// Dynamic color is available on Android 12+
				val localCtx = LocalContext.current
				fun getDynamicColorScheme(useDark: Boolean): ColorScheme? {
					val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
					return when {
						dynamicColor && useDark -> dynamicDarkColorScheme(localCtx)
						dynamicColor && !useDark -> dynamicLightColorScheme(localCtx)
						else -> null
					}
				}


				BackHandler(true) {
					if (noteText.isNotBlank()) {
						confirmCancel = true
					} else {
						finish()
					}
				}

				AppTheme(
					useDarkTheme = isDark,
					getOverrideColorScheme = ::getDynamicColorScheme
				) {
					Box {
						if (confirmCancel.not()) {
							OutlinedCard(
								modifier = Modifier.height(IntrinsicSize.Min),
								elevation = CardDefaults.outlinedCardElevation(Ui.Elevation.MEDIUM),
							) {
								Column(
									modifier = Modifier
										.padding(Ui.Padding.XL)
										.width(IntrinsicSize.Min)
								) {
									Text(
										stringResource(R.string.note_widget_dialog_title),
										style = MaterialTheme.typography.headlineMedium,
										color = MaterialTheme.colorScheme.onBackground
									)

									if (projectName == null) {
										ProjectDropDownUi(projects) {
											selectedProject = it
										}
									} else {
										Text(
											projectName,
											style = MaterialTheme.typography.bodyLarge,
											color = MaterialTheme.colorScheme.onBackground,
											fontStyle = FontStyle.Italic
										)
									}

									SpacerL()

									OutlinedTextField(
										value = noteText,
										onValueChange = { noteText = it },
										modifier = Modifier.heightIn(128.dp),
										maxLines = 10
									)
									SpacerL()
									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Button(
											onClick = {
												if (noteText.isNotBlank()) {
													confirmCancel = true
												} else {
													finish()
												}
											}
										) {
											Text(stringResource(R.string.note_widget_dialog_cancel_button))
										}

										Button(
											onClick = {
												if (noteText.isNotBlank()) {
													if (preselectedProject != null) {
														saveNote(preselectedProject, noteText)
													} else {
														saveNote(selectedProject, noteText)
													}
												}
											}
										) {
											Text(stringResource(R.string.note_widget_dialog_save_button))
										}
									}
								}
							}
						} else {
							OutlinedCard(
								modifier = Modifier
									.wrapContentSize()
									.align(Alignment.Center),
								elevation = CardDefaults.outlinedCardElevation(Ui.Elevation.MEDIUM),
							) {
								Column(
									modifier = Modifier
										.padding(Ui.Padding.XL)
										.width(IntrinsicSize.Min)
								) {
									Text(
										stringResource(R.string.note_widget_confirm_cancel_title),
										style = MaterialTheme.typography.headlineMedium,
										color = MaterialTheme.colorScheme.onBackground
									)

									SpacerL()

									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Button(
											onClick = { confirmCancel = false }
										) {
											Text(stringResource(R.string.note_widget_confirm_cancel_negative))
										}

										SpacerXL()

										Button(
											modifier = Modifier.width(IntrinsicSize.Max),
											onClick = { finish() }
										) {
											Text(stringResource(R.string.note_widget_confirm_cancel_positive))
										}
									}
								}
							}
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
