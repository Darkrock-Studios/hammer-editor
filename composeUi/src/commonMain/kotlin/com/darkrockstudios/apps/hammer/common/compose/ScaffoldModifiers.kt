package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalLayoutApi::class)
fun Modifier.rootElement(scaffoldPadding: PaddingValues): Modifier = this
	.fillMaxSize()
	.padding(scaffoldPadding)
	.consumeWindowInsets(scaffoldPadding)
	.systemBarsPadding()

fun Modifier.fab(): Modifier = this
	.systemBarsPadding()
	.navigationBarsPadding()

fun Modifier.defaultScaffold(): Modifier = this
	.imePadding()
	.fillMaxSize()
