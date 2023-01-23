package com.darkrockstudios.apps.hammer.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import com.darkrockstudios.apps.hammer.common.projectselection.getLocationIcon
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

@ExperimentalMaterialApi
@ExperimentalComposeApi
class ProjectSelectActivity : AppCompatActivity() {

	private val imageLoader: ImageLoader by inject()

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
				AppTheme {
					Content(component)
				}
			}
		}
	}

	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	private fun Content(component: ProjectSelectionComponent) {
		val scope = rememberCoroutineScope()
		val drawerState = rememberDrawerState(DrawerValue.Closed)
		val state by component.state.subscribeAsState()

		Scaffold(
			modifier = Modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.background),
			topBar = {
				TopBar(
					title = "Hammer",
					drawerOpen = drawerState,
					onButtonClicked = {
						scope.launch {
							if (drawerState.isOpen) {
								drawerState.close()
							} else {
								drawerState.open()
							}
						}
					},
				)
			},
			content = { innerPadding ->
				ModalNavigationDrawer(
					modifier = Modifier.padding(innerPadding),
					drawerState = drawerState,
					drawerContent = {
						ModalDrawerSheet(modifier = Modifier.width(Ui.NAV_DRAWER)) {
							Spacer(Modifier.height(12.dp))
							ProjectSelection.Locations.values().forEach { item ->
								NavigationDrawerItem(
									icon = { Icon(getLocationIcon(item), contentDescription = item.text) },
									label = { Text(item.name) },
									selected = item == state.location,
									onClick = {
										scope.launch { drawerState.close() }
										component.showLocation(item)
									},
									modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
								)
							}
						}
					},
					content = {
						ProjectSelectionUi(component, Modifier)
					}
				)
			}
		)
	}

	private fun onProjectSelected(projectDef: ProjectDef) {
		val intent = Intent(this, ProjectRootActivity::class.java).apply {
			putExtra(ProjectRootActivity.EXTRA_PROJECT, projectDef)
		}
		startActivity(intent)
	}
}