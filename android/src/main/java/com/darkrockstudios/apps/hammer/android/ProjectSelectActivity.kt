package com.darkrockstudios.apps.hammer.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.defaultComponentContext
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import org.koin.android.ext.android.inject

@ExperimentalMaterialApi
@ExperimentalComposeApi
class ProjectSelectActivity : AppCompatActivity() {

	private val imageLoader: ImageLoader by inject()

	@OptIn(ExperimentalMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val component = ProjectSelectionComponent(
			componentContext = defaultComponentContext(),
			onProjectSelected = ::onProjectSelected
		)

		setContent {
			CompositionLocalProvider(
				LocalImageLoader provides imageLoader,
			) {
				AppTheme(true) {
					Scaffold(
						topBar = {
							CenterAlignedTopAppBar(
								title = { Text("Hammer") },
							)
						},
						content = { innerPadding ->
							ProjectSelectionUi(component, Modifier.padding(innerPadding))
						}
					)
				}
			}
		}
	}

	private fun onProjectSelected(projectDef: ProjectDef) {
		val intent = Intent(this, ProjectRootActivity::class.java).apply {
			putExtra(ProjectRootActivity.EXTRA_PROJECT, projectDef)
		}
		startActivity(intent)
	}
}