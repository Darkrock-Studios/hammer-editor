package com.darkrockstudios.apps.hammer.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.defaultComponentContext
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.Project
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorComponent
import com.darkrockstudios.apps.hammer.common.projecteditor.ProjectEditorUi

class ProjectEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val project = intent.getParcelableExtra<Project>(EXTRA_PROJECT)
        if (project == null) {
            finish()
        } else {
            setContent {
                MaterialTheme {
                    val menu = remember { mutableStateOf<Set<MenuDescriptor>>(emptySet()) }
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
                                ProjectEditorComponent(
                                    componentContext = defaultComponentContext(),
                                    project = project,
                                    addMenu = { menuDescriptor ->
                                        menu.value =
                                            mutableSetOf(menuDescriptor).apply { add(menuDescriptor) }
                                    },
                                    removeMenu = { menuId ->
                                        menu.value = menu.value.filter { it.id != menuId }.toSet()
                                    }
                                ),
                                Modifier.padding(padding),
                                R.drawable::class
                            )
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PROJECT = "project"
    }
}
