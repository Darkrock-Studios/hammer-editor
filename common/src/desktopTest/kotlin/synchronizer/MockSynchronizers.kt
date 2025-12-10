package synchronizer

import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.*
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import org.koin.dsl.ScopeDSL

class MockSynchronizers(relaxed: Boolean) {
	@MockK
	lateinit var sceneSynchronizer: ClientSceneSynchronizer

	@MockK
	lateinit var noteSynchronizer: ClientNoteSynchronizer

	@MockK
	lateinit var timelineSynchronizer: ClientTimelineSynchronizer

	@MockK
	lateinit var encyclopediaSynchronizer: ClientEncyclopediaSynchronizer

	@MockK
	lateinit var sceneDraftSynchronizer: ClientSceneDraftSynchronizer

	init {
		MockKAnnotations.init(this, relaxed = relaxed)
	}

	val synchronizers = listOf(
		sceneSynchronizer,
		noteSynchronizer,
		timelineSynchronizer,
		encyclopediaSynchronizer,
		sceneDraftSynchronizer,
	)
}

fun ScopeDSL.addSynchronizers(mockSynchronizers: MockSynchronizers) {
	scoped { mockSynchronizers.sceneSynchronizer }
	scoped { mockSynchronizers.noteSynchronizer }
	scoped { mockSynchronizers.timelineSynchronizer }
	scoped { mockSynchronizers.encyclopediaSynchronizer }
	scoped { mockSynchronizers.sceneDraftSynchronizer }
}