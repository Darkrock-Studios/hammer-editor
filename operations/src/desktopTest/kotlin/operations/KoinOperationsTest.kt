package operations

import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_DEFAULT
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_MAIN
import com.darkrockstudios.apps.hammer.common.dependencyinjection.RAW_FILESYSTEM
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.plugin.PluginRegistry
import io.mockk.mockk
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/** Runs operations against the app's real Koin graph over a fake filesystem. */
abstract class KoinOperationsTest : KoinComponent {

	protected val ffs = FakeFileSystem()

	// FakeFileSystem is not thread-safe, so the test and every dispatcher share one thread.
	private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

	@BeforeEach
	fun startHammer() {
		ffs.createDirectories(getDefaultRootDocumentDirectory().toPath())
		val strRes = mockk<StrRes>(relaxed = true)
		val overrides = module {
			single<FileSystem> { ffs }
			single(named(RAW_FILESYSTEM)) { ffs } bind FileSystem::class
			single { strRes }
			single<CoroutineContext>(named(DISPATCHER_MAIN)) { thread }
			single<CoroutineContext>(named(DISPATCHER_DEFAULT)) { thread }
			single<CoroutineContext>(named(DISPATCHER_IO)) { thread }
		}
		GlobalContext.startKoin {
			allowOverride(true)
			modules(listOf(mainModule, overrides) + listOf(PluginRegistry().koinModule()))
		}
	}

	@AfterEach
	fun stopHammer() {
		GlobalContext.stopKoin()
		thread.close()
	}

	protected fun <T> onTestThread(block: suspend () -> T): T = runBlocking(thread) { block() }

	protected suspend fun <I, O> run(op: String, input: I): O {
		val registry = get<OperationRegistry>()
		@Suppress("UNCHECKED_CAST")
		return registry.run(registry.find(op) as Operation<I, O>, input)
	}
}
