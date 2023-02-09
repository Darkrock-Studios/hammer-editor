import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
open class BaseTest {

	protected val scope = TestScope()

	lateinit var mainTestDispatcher: TestDispatcher
	lateinit var ioTestDispatcher: TestDispatcher
	lateinit var defaultTestDispatcher: TestDispatcher

	@Before
	open fun setup() {
		Dispatchers.setMain(StandardTestDispatcher(scope.testScheduler))
	}

	@After
	open fun tearDown() {
		scope.cancel()
	}
}