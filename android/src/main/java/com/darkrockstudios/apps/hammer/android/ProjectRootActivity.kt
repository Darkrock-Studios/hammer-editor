package com.darkrockstudios.apps.hammer.android

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.AppCloseManager
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRoot
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootComponent
import com.darkrockstudios.apps.hammer.common.projectroot.ProjectRootUi
import com.darkrockstudios.apps.hammer.common.projectroot.getDestinationIcon
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.LocalImageLoader
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ProjectRootActivity : AppCompatActivity() {

	private val imageLoader: ImageLoader by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val projectDef = intent.getParcelableExtra<ProjectDef>(EXTRA_PROJECT)
		if (projectDef == null) {
			finish()
		} else {
			setContent {
				CompositionLocalProvider(LocalImageLoader provides imageLoader) {
					AppTheme {
						Content(projectDef)
					}
				}
			}
		}
	}

	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	private fun Content(projectDef: ProjectDef) {
		val scope = rememberCoroutineScope()
		val drawerState = rememberDrawerState(DrawerValue.Closed)

		val menu = remember { mutableStateOf<Set<MenuDescriptor>>(emptySet()) }
		val component = remember {
			ProjectRootComponent(
				componentContext = defaultComponentContext(),
				projectDef = projectDef,
				addMenu = { menuDescriptor ->
					menu.value =
						mutableSetOf(menuDescriptor).apply { add(menuDescriptor) }
				},
				removeMenu = { menuId ->
					menu.value = menu.value.filter { it.id != menuId }.toSet()
				}
			)
		}

		val router by component.routerState.subscribeAsState()
		val showBack = !component.isAtRoot()
		val shouldConfirmClose by component.shouldConfirmClose.subscribeAsState()
		val backEnabled by component.backEnabled.subscribeAsState()
		val destinationTypes = remember { ProjectRoot.DestinationTypes.values() }

		BackHandler(enabled = backEnabled) {
			if (shouldConfirmClose) {
				confirmCloseDialog(component)
			} else {
				finish()
			}
		}

		Scaffold(
			modifier = Modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.background),
			topBar = {
				TopBar(
					title = projectDef.name,
					drawerOpen = drawerState,
					showBack = showBack,
					onButtonClicked = {
						if (showBack) {
							onBackPressed()
						} else {
							scope.launch {
								if (drawerState.isOpen) {
									drawerState.close()
								} else {
									drawerState.open()
								}
							}
						}
					},
					actions = {
						if (menu.value.isNotEmpty()) {
							TopAppBarDropdownMenu(menu.value.toList())
						}
					}
				)
			},
			content = { innerPadding ->
				ModalNavigationDrawer(
					modifier = Modifier.padding(innerPadding),
					drawerState = drawerState,
					drawerContent = {
						ModalDrawerSheet(modifier = Modifier.width(Ui.NAV_DRAWER)) {
							Spacer(Modifier.height(12.dp))
							destinationTypes.forEach { item ->
								NavigationDrawerItem(
									icon = {
										Icon(
											imageVector = getDestinationIcon(item),
											contentDescription = item.text
										)
									},
									label = { Text(item.text) },
									selected = router.active.instance.getLocationType() == item,
									onClick = {
										scope.launch { drawerState.close() }
										component.showDestination(item)
									}
								)
							}
						}
					},
					content = {
						ProjectRootUi(component, R.drawable::class)
					}
				)
			}
		)
	}

	private fun confirmCloseDialog(component: AppCloseManager) {
		AlertDialog.Builder(this)
			.setTitle("Unsaved Scenes")
			.setMessage("Save unsaved scenes?")
			.setNegativeButton("Discard and close") { _, _ -> finish() }
			.setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }
			.setPositiveButton("Save and close") { _, _ ->
				component.storeDirtyBuffers()
				finish()
			}
			.create()
			.show()
	}

	companion object {
		const val EXTRA_PROJECT = "project"
	}
}
