package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.project.EntityValidationException
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.utils.BaseTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalSerializationApi::class)
class ServerTimelineSynchronizerValidateTest : BaseTest() {

	private lateinit var datasource: ProjectEntityDatasource

	@BeforeEach
	override fun setup() {
		super.setup()
		datasource = mockk()
	}

	private val event = ApiProjectEntity.TimelineEventEntity(
		id = 1,
		order = 0,
		label = "Late autumn",
		content = "Wind takes the roof.",
		tags = setOf("weather"),
		references = setOf(12, 31),
		lane = "Keep",
		milestone = true,
		start = StoryDate(mapOf("year" to 97L, "day" to 210L)),
		end = StoryDate(mapOf("year" to 97L, "day" to 212L)),
		title = "The storm",
	)

	private val placement = ApiProjectEntity.TimelineEventEntity(
		id = 9,
		order = 1,
		label = null,
		content = "",
		kind = TimelineKind.SCENE,
		subject = 31,
		lane = "Alice",
		cachedTitle = "Landfall",
		start = StoryDate(mapOf("year" to 97L, "day" to 214L, "hour" to 6L)),
	)

	private suspend fun save(entity: ApiProjectEntity.TimelineEventEntity): SResult<Unit> {
		coEvery {
			datasource.loadEntityHash(any(), any(), any())
		} returns SResult.failure(IllegalStateException("not found"))
		coEvery {
			datasource.storeEntity(
				any(), any(), any(), any(),
				ApiProjectEntity.TimelineEventEntity::class.serializer(),
			)
		} returns SResult.success(Unit)
		return ServerTimelineSynchronizer(datasource).saveEntity(1L, mockk(), entity, null, false)
	}

	private suspend fun assertRejected(entity: ApiProjectEntity.TimelineEventEntity, reason: String) {
		val result = save(entity)
		assertFalse(isSuccess(result))
		val exception = assertIs<EntityValidationException>(result.exception)
		assertContains(exception.reason, reason)
		coVerify(exactly = 0) {
			datasource.storeEntity(
				any(), any(), any(), any(),
				ApiProjectEntity.TimelineEventEntity::class.serializer(),
			)
		}
	}

	private fun longString(length: Int) = "x".repeat(length)

	@Test
	fun `Accepts a fully populated event`() = runTest {
		assertTrue(isSuccess(save(event)))
	}

	@Test
	fun `Accepts a placement of each kind`() = runTest {
		assertTrue(isSuccess(save(placement)))
		assertTrue(isSuccess(save(placement.copy(kind = TimelineKind.ENTRY))))
	}

	@Test
	fun `Accepts values at every length limit`() = runTest {
		val atLimits = event.copy(
			label = longString(TimelineLimits.MAX_LABEL),
			title = longString(TimelineLimits.MAX_TITLE),
			lane = longString(TimelineLimits.MAX_LANE),
			tags = setOf(longString(TimelineLimits.MAX_TAG)),
			start = StoryDate((1..TimelineLimits.MAX_RUNGS).associate { "u$it" to it.toLong() }),
			end = StoryDate(mapOf(longString(TimelineLimits.MAX_UNIT_NAME) to 1L)),
		)
		assertTrue(isSuccess(save(atLimits)))
		assertTrue(isSuccess(save(placement.copy(cachedTitle = longString(TimelineLimits.MAX_TITLE)))))
	}

	@Test
	fun `Rejects an event with a subject`() = runTest {
		assertRejected(event.copy(subject = 5), "subject")
	}

	@Test
	fun `Rejects a placement without a subject`() = runTest {
		assertRejected(placement.copy(subject = null), "needs a subject")
	}

	@Test
	fun `Rejects a placement that is its own subject`() = runTest {
		assertRejected(placement.copy(subject = placement.id), "own subject")
	}

	@Test
	fun `Rejects a placement with references`() = runTest {
		assertRejected(placement.copy(references = setOf(12)), "references")
	}

	@Test
	fun `Rejects a placement with content`() = runTest {
		assertRejected(placement.copy(content = "words"), "content")
	}

	@Test
	fun `Rejects a placement with tags`() = runTest {
		assertRejected(placement.copy(tags = setOf("weather")), "tags")
	}

	@Test
	fun `Rejects a subject that is also a reference`() = runTest {
		assertRejected(placement.copy(references = setOf(placement.subject!!)), "subject cannot also be a reference")
	}

	@Test
	fun `Rejects an event that references itself`() = runTest {
		assertRejected(event.copy(references = setOf(event.id, 12)), "reference itself")
	}

	@Test
	fun `Rejects a blank lane`() = runTest {
		assertRejected(event.copy(lane = "  "), "lane")
	}

	@Test
	fun `Rejects a lane over the limit`() = runTest {
		assertRejected(event.copy(lane = longString(TimelineLimits.MAX_LANE + 1)), "lane")
	}

	@Test
	fun `Rejects a title over the limit`() = runTest {
		assertRejected(event.copy(title = longString(TimelineLimits.MAX_TITLE + 1)), "title")
	}

	@Test
	fun `Rejects a cachedTitle over the limit`() = runTest {
		assertRejected(placement.copy(cachedTitle = longString(TimelineLimits.MAX_TITLE + 1)), "cachedTitle")
	}

	@Test
	fun `Rejects a label over the limit`() = runTest {
		assertRejected(event.copy(label = longString(TimelineLimits.MAX_LABEL + 1)), "label")
	}

	@Test
	fun `Rejects a tag over the limit`() = runTest {
		assertRejected(event.copy(tags = setOf("ok", longString(TimelineLimits.MAX_TAG + 1))), "tag")
	}

	@Test
	fun `Rejects a date with too many rungs`() = runTest {
		val tooMany = StoryDate((0..TimelineLimits.MAX_RUNGS).associate { "u$it" to it.toLong() })
		assertRejected(event.copy(start = tooMany), "start")
		assertRejected(event.copy(end = tooMany), "end")
	}

	@Test
	fun `Rejects a blank unit name`() = runTest {
		assertRejected(event.copy(start = StoryDate(mapOf(" " to 1L))), "blank unit")
	}

	@Test
	fun `Rejects a unit name over the limit`() = runTest {
		val longUnit = StoryDate(mapOf(longString(TimelineLimits.MAX_UNIT_NAME + 1) to 1L))
		assertRejected(event.copy(end = longUnit), "unit name longer")
	}
}
