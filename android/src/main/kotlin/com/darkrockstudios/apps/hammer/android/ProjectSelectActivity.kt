package com.darkrockstudios.apps.hammer.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.reduce
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.UiTheme
import com.darkrockstudios.apps.hammer.common.platformMainDispatcher
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionComponent
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSelectionUi
import com.darkrockstudios.apps.hammer.common.projectselection.getLocationIcon
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

@ExperimentalMaterialApi
@ExperimentalComposeApi
class ProjectSelectActivity : AppCompatActivity() {

	private val imageLoader: ImageLoader by inject()
	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val globalSettings = MutableValue(globalSettingsRepository.globalSettings)
	private var settingsUpdateJob: Job? = null

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
				val settingsState by globalSettings.subscribeAsState()
				val isDark = when (settingsState.uiTheme) {
					UiTheme.Light -> false
					UiTheme.Dark -> true
					UiTheme.FollowSystem -> isSystemInDarkTheme()
				}

				AppTheme(isDark) {
					Content(component)
				}
			}
		}
	}

	override fun onStart() {
		super.onStart()

		settingsUpdateJob = lifecycleScope.launch {
			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				withContext(platformMainDispatcher) {
					globalSettings.reduce { settings }
				}
			}
		}
	}

	override fun onStop() {
		super.onStop()
		settingsUpdateJob?.cancel()
		settingsUpdateJob = null
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
				SetStatusBar()
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