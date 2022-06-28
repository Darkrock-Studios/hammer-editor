package com.darkrockstudios.apps.hammer.android

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.defaultComponentContext
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorUi

class ProjectEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val projectDef = intent.getParcelableExtra<ProjectDef>(EXTRA_PROJECT)
        if (projectDef == null) {
            finish()
        } else {
            setContent {
                MaterialTheme {
                    val menu = remember { mutableStateOf<Set<MenuDescriptor>>(emptySet()) }
                    val component = remember {
                        ProjectEditorComponent(
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

                    val scaffoldState = rememberScaffoldState()
                    Scaffold(
                        scaffoldState = scaffoldState,
                        topBar = {
                            TopAppBar(
                                title = { Text("Hammer") },
                                elevation = Ui.ELEVATION,
                                navigationIcon = {
                                    IconButton(onClick = ::onBackPressed) {
                                        Icon(Icons.Filled.ArrowBack, "backIcon")
                                    }
                                },
                                actions = {
                                    if (menu.value.isNotEmpty()) {
                                        TopAppBarDropdownMenu(menu.value.toList())
                                    }
                                }
                            )
                        },
                        content = { padding ->
                            ProjectEditorUi(
                                component,
                                Modifier.padding(padding),
                                R.drawable::class
                            )

                            val shouldConfirmClose = component.shouldConfirmClose.subscribeAsState()
                            BackHandler(enabled = shouldConfirmClose.value) {
                                confirmCloseDialog(component)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun confirmCloseDialog(component: ProjectEditorComponent) {
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
