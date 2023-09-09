package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import org.koin.compose.LocalKoinApplication
import org.koin.compose.LocalKoinScope
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.koinApplication

@OptIn(KoinInternalApi::class)
@Composable
fun KoinApplicationPreview(
	application: KoinAppDeclaration,
	content: @Composable () -> Unit
) {
	val koinApplication = koinApplication(application)

	CompositionLocalProvider(
		LocalKoinApplication provides koinApplication.koin,
		LocalKoinScope provides koinApplication.koin.scopeRegistry.rootScope
	) {
		GlobalContext.startKoin(koinApplication = koinApplication)
		DisposableEffect(Unit) {
			onDispose {
				GlobalContext.stopKoin()
			}
		}

		content()
	}

	GlobalContext.stopKoin()
}