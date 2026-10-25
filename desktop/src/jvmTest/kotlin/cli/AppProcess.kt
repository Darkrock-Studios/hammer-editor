package cli

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.ProjectSyncGateRepository
import com.darkrockstudios.apps.hammer.desktop.cli.AppInstance
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/** An app window for [AppInstanceTest] to run as its own process: serves "greet" until stdin closes. */
object AppProcess {
	const val READY = "ready"

	@JvmStatic
	fun main(args: Array<String>) {
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		val registry = OperationRegistry(
			listOf(
				operation<AppInstanceTest.Greeting, AppInstanceTest.Greeting>("greet", "", Access.Read, OperationScope.Content) {
					AppInstanceTest.Greeting("Hello, ${it.name}")
				},
			),
			object : ProjectResolver {
				override fun resolve(project: String): ProjectDef = error("unused")
				override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
			},
			::ProjectSyncGateRepository,
		)
		AppInstance(File(args[0]), { registry }, {}).use { app ->
			check(app.open(scope, emptyList()))
			app.started()
			println(READY)
			while (System.`in`.read() != -1) continue
		}
		scope.cancel()
	}
}
