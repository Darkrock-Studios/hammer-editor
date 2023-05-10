package com.darkrockstudios.apps.hammer.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Padded(composable: @Composable () -> Unit) {
	Box(modifier = Modifier.padding(32.dp)) {
		composable()
	}
}