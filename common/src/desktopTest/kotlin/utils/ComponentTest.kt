package utils

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.instancekeeper.InstanceKeeperDispatcher
import com.arkivanov.essenty.lifecycle.*
import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagIndexService
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.spellcheck.ProjectSpellCheckRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * A real [ComponentContext] for testing Decompose components, backed by the genuine Essenty
 * primitives rather than mocks. Because it delegates [ComponentContext], it can be passed
 * straight to a component constructor, while still exposing the handles needed to drive the
 * component: lifecycle transitions, back presses, and process-death (state restoration).
 *
 * Prefer this over mocking the ComponentContext quartet — it exercises real lifecycle and
 * back-handler behavior, and can simulate process death via [saveAndRecreate].
 */
class TestComponentContext private constructor(
	override val lifecycle: LifecycleRegistry,
	override val stateKeeper: StateKeeperDispatcher,
	val backDispatcher: BackDispatcher,
	override val instanceKeeper: InstanceKeeperDispatcher,
) : ComponentContext by DefaultComponentContext(
	lifecycle = lifecycle,
	stateKeeper = stateKeeper,
	instanceKeeper = instanceKeeper,
	backHandler = backDispatcher,
) {

	private val recreated = mutableListOf<TestComponentContext>()

	fun start() = lifecycle.start()
	fun resume() = lifecycle.resume()
	fun pause() = lifecycle.pause()
	fun stop() = lifecycle.stop()
	fun destroy() = lifecycle.destroy()

	/** Fire a back press through the real back handler. Returns whether a callback handled it. */
	fun back(): Boolean = backDispatcher.back()

	/**
	 * Simulate process death: serialize the current saved state and return a fresh context
	 * restored from it. Build a new component on the result to assert restoration behavior.
	 */
	fun saveAndRecreate(): TestComponentContext =
		create(stateKeeper.save()).also { recreated.add(it) }

	/** Destroy this lifecycle and any contexts recreated from it, cancelling their component scopes. */
	fun destroyAll() {
		recreated.forEach { it.destroyAll() }
		if (lifecycle.state != Lifecycle.State.DESTROYED) lifecycle.destroy()
	}

	companion object {
		fun create(savedState: SerializableContainer? = null): TestComponentContext =
			TestComponentContext(
				lifecycle = LifecycleRegistry(),
				stateKeeper = StateKeeperDispatcher(savedState),
				backDispatcher = BackDispatcher(),
				instanceKeeper = InstanceKeeperDispatcher(),
			)
	}
}

/**
 * Base class for Decompose component tests. Provides a fresh [TestComponentContext] per test
 * in addition to everything [BaseTest] sets up. Subclasses override [setup], call
 * `super.setup()`, then register their dependencies via [setupKoin].
 */
open class ComponentTest : BaseTest() {

	protected lateinit var context: TestComponentContext

	/** Default project for project-scoped components; override if a test needs a different one. */
	protected open val projectDef = ProjectDef("Test", HPath("/projects/Test", "Test", false))

	@BeforeEach
	override fun setup() {
		super.setup()
		context = TestComponentContext.create()
	}

	/**
	 * Like [setupKoin] but also registers the dependencies every project-scoped component injects
	 * via [com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase] — currently a
	 * relaxed [TagIndexService] and a [ProjectSpellCheckRepository] whose flows never emit (both
	 * lazy, so harmless when a component never uses them). A test that needs to control either
	 * should register its own via plain [setupKoin] instead.
	 */
	protected fun setupComponentKoin(module: Module) {
		setupKoin(
			module {
				single<TagIndexService> { mockk(relaxed = true) }
				single<ProjectSpellCheckRepository> {
					mockk(relaxed = true) {
						every { spellCheckAllowed } returns emptyFlow()
						every { dictionaryFlow } returns emptyFlow()
					}
				}
			},
			module,
		)
	}

	@AfterEach
	override fun tearDown() {
		// Destroy the lifecycle(s) so each component's lifecycleCoroutineScope is cancelled (it
		// only cancels on destroy). try/finally so a failing destroy can't skip Koin/dispatcher
		// cleanup in super.tearDown() and cascade into the next test.
		try {
			if (::context.isInitialized) context.destroyAll()
		} finally {
			super.tearDown()
		}
	}
}
