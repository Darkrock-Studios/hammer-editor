package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope

@Composable
expect fun ImportFilePicker(
	show: Boolean,
	scope: CoroutineScope,
	onFileSelected: (name: String, content: String) -> Unit,
	onCancel: () -> Unit,
)
