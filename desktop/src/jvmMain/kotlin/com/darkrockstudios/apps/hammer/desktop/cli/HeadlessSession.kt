package com.darkrockstudios.apps.hammer.desktop.cli

import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.dependencyinjection.appModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.getConfigDirectory
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugins.wasmhost.RuntimePlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Hammer without a window, for one CLI call: take the writer lock, start Koin, migrate data, run the
 * work, and stop again, so nothing loaded survives into the next call.
 */
object HeadlessSession {

	/** Thrown when another Hammer process holds the writer lock. */
	class Busy(holder: WriterLock.Holder?) : Exception(
		when (holder) {
			WriterLock.Holder.App -> "Hammer is running. Close it and try again."
			else -> "Another Hammer command is using your data. Try again when it finishes."
		}
	)

	// Koin's context and the writer lock are both one per process, so calls take turns.
	private val calls = Mutex()

	/** The plugins active for CLI calls, fresh per process: the enabled runtime plugins. */
	fun pluginRegistry(): PluginRegistry {
		return PluginRegistry().also(RuntimePlugins.inConfigDirectory(FileSystem.SYSTEM)::activate)
	}

	/** Runs [block] with Hammer started; [plugins] is this process's [pluginRegistry], built once. */
	suspend fun <T> run(plugins: PluginRegistry, block: suspend (OperationRegistry) -> T): T = calls.withLock {
		val lock = when (val result = WriterLock.acquire(File(getConfigDirectory()), WriterLock.Holder.Cli, LOCK_WAIT)) {
			is WriterLock.Result.Acquired -> result.lock
			is WriterLock.Result.Busy -> throw Busy(result.holder)
		}
		lock.use {
			val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
			GlobalContext.startKoin {
				modules(mainModule, appModule(appScope), plugins.koinModule())
			}
			try {
				GlobalContext.get().get<DataMigrator>().handleDataMigration()
				block(GlobalContext.get().get())
			} finally {
				appScope.cancel()
				GlobalContext.stopKoin()
			}
		}
	}

	/** How long to wait for another CLI call to finish. */
	private val LOCK_WAIT = 3.seconds
}
