package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.platformDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.platformIoDispatcher
import com.darkrockstudios.apps.hammer.common.platformMainDispatcher
import com.darkrockstudios.apps.hammer.common.util.StrRes
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@Composable
fun KoinApplicationPreview(
	application: KoinAppDeclaration? = null,
	content: @Composable () -> Unit
) {
	val koinApplication = koinApplication(false, appDeclaration = {
		modules(listOf(module {
			single(named(DISPATCHER_MAIN)) { platformMainDispatcher }
			single(named(DISPATCHER_DEFAULT)) { platformDefaultDispatcher }
			single(named(DISPATCHER_IO)) { platformIoDispatcher }
			single { PreviewStrRes() } bind StrRes::class

			if (application != null) application()
		}))
	})

	GlobalContext.startKoin(koinApplication = koinApplication)
	DisposableEffect(Unit) {
		onDispose {
			GlobalContext.stopKoin()
		}
	}

	content()

	GlobalContext.stopKoin()
}