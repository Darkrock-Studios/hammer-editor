package com.darkrockstudios.apps.hammer.android.widgets

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataRepository
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AddNoteActivity : ComponentActivity(), KoinComponent {

	private val projectsRepository: ProjectsRepository by inject()
	private val projectsMetadataRepository: ProjectMetadataRepository by inject()

	@OptIn(ExperimentalMaterial3Api::class)
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

				BackHandler(true) {
					if (noteText.isNotBlank()) {
						confirmCancel = true
					} else {
						finish()
					}
				}

				AppTheme {
					Box {
						if (confirmCancel.not()) {
							Card(
								elevation = 2.dp,
								shape = RoundedCornerShape(20.dp)
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

									Spacer(modifier = Modifier.size(Ui.Padding.L))

									OutlinedTextField(
										value = noteText,
										onValueChange = { noteText = it },
										modifier = Modifier.heightIn(128.dp)
									)
									Spacer(modifier = Modifier.size(Ui.Padding.L))
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
							Card(
								modifier = Modifier
									.wrapContentSize()
									.align(Alignment.Center),
								elevation = 2.dp,
								shape = RoundedCornerShape(20.dp)
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

									Spacer(modifier = Modifier.size(Ui.Padding.L))

									Row(
										modifier = Modifier.fillMaxWidth(),
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Button(
											onClick = { confirmCancel = false }
										) {
											Text(stringResource(R.string.note_widget_confirm_cancel_negative))
										}

										Spacer(modifier = Modifier.size(Ui.Padding.XL))

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
