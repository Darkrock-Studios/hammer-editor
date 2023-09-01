package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.Info
import com.darkrockstudios.apps.hammer.common.components.projecteditor.metadata.ProjectMetadata
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.platformDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.platformIoDispatcher
import com.darkrockstudios.apps.hammer.common.platformMainDispatcher
import com.darkrockstudios.apps.hammer.common.util.StrRes
import dev.icerock.moko.resources.StringResource
import kotlinx.datetime.Instant
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun fakeProjectDef(): ProjectDef = ProjectDef(
	name = "Test",
	path = HPath(
		name = "Test",
		path = "/",
		isAbsolute = true
	)
)

fun fakeProjectMetadata(): ProjectMetadata = ProjectMetadata(
	info = Info(
		created = Instant.DISTANT_FUTURE,
		lastAccessed = Instant.DISTANT_FUTURE,
	)
)

fun fakeSceneItem() = SceneItem(
	projectDef = fakeProjectDef(),
	type = SceneItem.Type.Scene,
	id = 0,
	name = "Test Scene",
	order = 0
)

class PreviewStrRes : StrRes {
	override fun get(str: StringResource): String = ""
	override fun get(str: StringResource, vararg args: Any): String = ""
}

// TODO but it isn't working...
@Composable
fun koinForPreview(block: @Composable () -> Unit) {
	startKoin {
		module {
			single(named(DISPATCHER_MAIN)) { platformMainDispatcher }
			single(named(DISPATCHER_DEFAULT)) { platformDefaultDispatcher }
			single(named(DISPATCHER_IO)) { platformIoDispatcher }
			single { PreviewStrRes() } bind StrRes::class
		}
	}

	block()

	stopKoin()
}