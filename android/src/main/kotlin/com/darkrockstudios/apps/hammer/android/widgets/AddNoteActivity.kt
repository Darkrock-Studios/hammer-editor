package com.darkrockstudios.apps.hammer.android.widgets

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.darkrockstudios.apps.hammer.android.R
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AddNoteActivity : ComponentActivity(), KoinComponent {

	private val projectsRepository: ProjectsRepository by inject()

	@OptIn(ExperimentalMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setFinishOnTouchOutside(false)
		window.setBackgroundDrawableResource(android.R.color.transparent)

		val projects = projectsRepository.getProjects()
		if (projects.isEmpty()) {
			Toast.makeText(this, getString(R.string.note_widget_toast_no_projects), Toast.LENGTH_SHORT).show()
			finish()
		} else {
			setContent {
				var noteText by rememberSaveable { mutableStateOf("") }
				var selectedProject by rememberSaveable { mutableStateOf(projects.first()) }
				var confirmCancel by rememberSaveable { mutableStateOf(false) }

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
									ProjectDropDown(projects) {
										selectedProject = it
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
													saveNote(selectedProject, noteText)
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

	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	private fun ProjectDropDown(projects: List<ProjectDef>, onProjectSelected: (ProjectDef) -> Unit) {
		var expanded by remember { mutableStateOf(false) }
		var selectedOptionText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
			mutableStateOf(
				TextFieldValue(
					text = projects.firstOrNull()?.name ?: ""
				)
			)
		}

		ExposedDropdownMenuBox(
			expanded = expanded,
			onExpandedChange = {
				expanded = !expanded
			}
		) {
			OutlinedTextField(
				modifier = Modifier.menuAnchor(),
				readOnly = true,
				value = selectedOptionText,
				onValueChange = { selectedOptionText = it },
				label = { Text(stringResource(R.string.note_widget_dialog_categories_dropdown)) },
				trailingIcon = {
					ExposedDropdownMenuDefaults.TrailingIcon(
						expanded = expanded
					)
				},
				colors = ExposedDropdownMenuDefaults.textFieldColors()
			)
			ExposedDropdownMenu(
				expanded = expanded,
				onDismissRequest = {
					expanded = false
				}
			) {
				projects.forEach { selectionOption ->
					DropdownMenuItem(
						text = { Text(selectionOption.name) },
						onClick = {
							selectedOptionText = TextFieldValue(selectionOption.name)
							onProjectSelected(selectionOption)
							expanded = false
						}
					)
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
