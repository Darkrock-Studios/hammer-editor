package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.base.http.writingactivity.DeviceLog
import com.darkrockstudios.apps.hammer.base.http.writingactivity.WritingSession
import com.darkrockstudios.apps.hammer.common.data.ExampleProjectRepository.Companion.EXAMPLE_DAYS
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.SceneDatasource
import com.darkrockstudios.apps.hammer.common.data.writingactivity.WritingActivityDatasource
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent
import org.koin.core.module.Module
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant


expect val exampleProjectModule: Module

abstract class ExampleProjectRepository(
	protected val globalSettingsStore: GlobalSettingsStore,
	protected val fileSystem: FileSystem,
	private val toml: Toml,
	private val clock: Clock,
) : KoinComponent {
	protected val dispatcherMain by injectMainDispatcher()
	protected val dispatcherDefault by injectDefaultDispatcher()
	private val scope = CoroutineScope(dispatcherDefault)

	fun shouldInstallFirstTime(): Boolean =
		!globalSettingsStore.globalSettings.nux.exampleProjectCreated

	fun install() {
		removeExampleProject()
		platformInstall()

		scope.launch {
			fabricateExampleActivity()
			globalSettingsStore.updateSettings {
				it.copy(
					nux = globalSettingsStore.globalSettings.nux.copy(
						exampleProjectCreated = true
					)
				)
			}
		}
	}

	abstract fun removeExampleProject()

	protected abstract fun platformInstall()

	protected fun projectsDir(): Path =
		globalSettingsStore.globalSettings.projectsDirectory.toPath()

	@Suppress("TooGenericExceptionCaught") // Best-effort fabrication; any failure is logged
	private fun fabricateExampleActivity() {
		val activityDir = projectsDir() / PROJECT_NAME /
			SceneDatasource.SCENE_DIRECTORY / WritingActivityDatasource.ACTIVITY_DIRECTORY
		try {
			fileSystem.createDirectories(activityDir)
			val sessions = generateExampleSessions(clock.now(), TimeZone.currentSystemDefault())
			val log = DeviceLog(deviceLabel = FABRICATED_DEVICE_LABEL, sessions = sessions)
			fileSystem.writeToml(activityDir / "$FABRICATED_DEVICE_ID.toml", toml, log)
		} catch (e: Exception) {
			Napier.e("Failed to fabricate example writing activity", e)
		}
	}

	companion object {
		const val PROJECT_NAME = "Alice In Wonderland"
		const val EXAMPLE_PROJECT_FILE_NAME = "alice_in_wonderland_zip"

		const val FABRICATED_DEVICE_ID = "example-author"
		const val FABRICATED_DEVICE_LABEL = "Example Author"
		const val EXAMPLE_SEED: Long = 0xA11CEL

		const val EXAMPLE_DAYS = 77 // 11 weeks

		/**
		 * Sprints-and-rest cadence over [EXAMPLE_DAYS] ending at [now], expressed
		 * in [tz]. Every session is sealed so [StatisticsService] counts it.
		 */
		internal fun generateExampleSessions(
			now: Instant,
			tz: TimeZone,
			seed: Long = EXAMPLE_SEED,
		): List<WritingSession> {
			val random = Random(seed)
			val today = now.toLocalDateTime(tz).date
			// Offsets in days back from today; 0 is today, EXAMPLE_DAYS-1 is the oldest day.
			// Walk backwards from today so the most recent day is always a writing day.
			val writingDays = mutableListOf<Int>()
			var cursor = 0
			while (cursor < EXAMPLE_DAYS) {
				val sprintLength = random.nextInt(2, 5) // 2..4 consecutive days
				repeat(sprintLength) {
					if (cursor < EXAMPLE_DAYS) {
						writingDays += cursor
						cursor++
					}
				}
				val restGap = random.nextInt(2, 6) // 2..5 day gap
				cursor += restGap
			}

			return writingDays.sorted().flatMap { offset ->
				val date = today.minus(offset, DateTimeUnit.DAY)
				val sessionCount = random.nextInt(1, 4) // 1..3 sessions
				List(sessionCount) {
					val startHour = random.nextInt(9, 21)
					val startMinute = random.nextInt(0, 60)
					val durationMinutes = random.nextInt(30, 91)
					val words = random.nextInt(1500, 2501)
					val startLocal: LocalDateTime = date.atTime(LocalTime(startHour, startMinute))
					val startInstant = startLocal.toInstant(tz)
					val endInstant = startInstant + durationMinutes.minutes
					WritingSession(
						startedAt = startInstant,
						endedAt = endInstant,
						wordsWritten = words,
						sealed = true,
					)
				}
			}.sortedBy { it.startedAt }
		}
	}
}
