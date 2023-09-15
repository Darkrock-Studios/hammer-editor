package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.koin.core.context.GlobalContext
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.koinApplication

@Composable
fun KoinApplicationPreview(
	application: KoinAppDeclaration,
	content: @Composable () -> Unit
) {
	val koinApplication = koinApplication(false, application)

	GlobalContext.startKoin(koinApplication = koinApplication)
	DisposableEffect(Unit) {
		onDispose {
			GlobalContext.stopKoin()
		}
	}

	content()

	GlobalContext.stopKoin()
}