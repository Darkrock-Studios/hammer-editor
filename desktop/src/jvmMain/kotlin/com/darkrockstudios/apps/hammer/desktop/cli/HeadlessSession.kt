package com.darkrockstudios.apps.hammer.desktop.cli

import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.secrets.EncryptedFileSecretDatasource
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

	/** Thrown when another CLI call holds the writer lock. */
	class Busy : Exception("Another Hammer command is using your data. Try again when it finishes.")

	/** Thrown when an app window is open, so the call has to go to it instead. */
	class AppOpen : Exception()

	// Koin's context and the writer lock are both one per process, so calls take turns.
	private val calls = Mutex()

	private val runtimePlugins by lazy {
		RuntimePlugins.inConfigDirectory(FileSystem.SYSTEM, EncryptedFileSecretDatasource(FileSystem.SYSTEM))
	}

	/** The plugins active for CLI calls, fresh per process: the enabled runtime plugins. */
	fun pluginRegistry(): PluginRegistry {
		return PluginRegistry().also(runtimePlugins::activate)
	}

	/** Runs [block] with Hammer started; [plugins] is this process's [pluginRegistry], built once. */
	suspend fun <T> run(plugins: PluginRegistry, block: suspend (OperationRegistry) -> T): T = calls.withLock {
		withWriterLock(File(getConfigDirectory())) {
			val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
			GlobalContext.startKoin {
				modules(mainModule, appModule(appScope), plugins.koinModule(), runtimePlugins.koinModule())
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

	/**
	 * Runs [block] holding the writer lock in [directory], unless an app window is open. Checked once the
	 * lock is held, since an app that opens after that waits for the lock before it serves any call.
	 */
	suspend fun <T> withWriterLock(directory: File, block: suspend () -> T): T {
		val lock = when (val result = WriterLock.acquire(directory, WriterLock.Holder.Cli, LOCK_WAIT)) {
			is WriterLock.Result.Acquired -> result.lock
			is WriterLock.Result.Busy -> throw when (result.holder) {
				WriterLock.Holder.App -> AppOpen()
				WriterLock.Holder.Cli -> Busy()
				null -> if (AppInstance.isOpen(directory)) AppOpen() else Busy()
			}
		}
		return lock.use {
			if (AppInstance.isOpen(directory)) throw AppOpen()
			block()
		}
	}

	/** How long to wait for another CLI call to finish. */
	private val LOCK_WAIT = 3.seconds
}
