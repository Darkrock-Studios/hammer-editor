package repositories.projectstatistics

import com.darkrockstudios.apps.hammer.common.data.projectstatistics.ProjectStatisticsCacheReader
import com.darkrockstudios.apps.hammer.common.data.projectstatistics.StatisticsCachePaths
import com.darkrockstudios.apps.hammer.common.dependencyinjection.createTomlSerializer
import getProject1Def
import okio.fakefilesystem.FakeFileSystem
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

/**
 * A stale or hand-edited stats cache can hold values that tomlkt fails to coerce
 * (e.g. a non-integer key in a `Map<Int, Int>`), which surfaces as a
 * NumberFormatException - an IllegalArgumentException, not a SerializationException.
 * The reader must treat that as a cache miss rather than letting it escape and
 * crash the project-list load.
 */
class ProjectStatisticsCacheReaderTest {

	@Test
	fun `malformed stats cache returns null instead of throwing`() {
		val fileSystem = FakeFileSystem()
		val toml = createTomlSerializer()
		val projectDef = getProject1Def()

		val statsFile = StatisticsCachePaths.statsFile(projectDef)
		fileSystem.createDirectories(statsFile.parent!!)
		fileSystem.write(statsFile) {
			// "Title" is not a valid Int key for wordsByChapter: Map<Int, Int>.
			writeUtf8(
				"""
				numberOfScenes = 0
				totalWords = 0
				lastCalculated = "1970-01-01T00:00:00Z"

				[wordsByChapter]
				Title = 5

				[encyclopediaEntriesByType]
				""".trimIndent()
			)
		}

		val reader = ProjectStatisticsCacheReader(fileSystem, toml)

		assertNull(reader.loadStatistics(projectDef))
	}
}
