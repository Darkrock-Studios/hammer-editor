package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class ReviewSceneDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.reviewSceneQueries

	suspend fun createScene(
		reviewRequestId: Long,
		sceneId: Int,
		draftId: Int,
		sceneName: String,
		sceneOrder: Int,
		snapshotContent: String,
		cipher: String,
	) {
		withContext(ioDispatcher) {
			queries.createScene(
				reviewRequestId = reviewRequestId,
				sceneId = sceneId,
				draftId = draftId,
				sceneName = sceneName,
				sceneOrder = sceneOrder,
				snapshotContent = snapshotContent,
				cipher = cipher,
			)
		}
	}

	suspend fun getScenesForRequest(reviewRequestId: Long): List<ReviewScene> =
		withContext(ioDispatcher) {
				queries.getScenesForRequest(reviewRequestId).executeAsList()
			}

		suspend fun getScene(id: Long): ReviewScene? = withContext(ioDispatcher) {
			queries.getScene(id).executeAsOneOrNull()
		}

		suspend fun setReviewerDone(id: Long, done: Boolean) {
			withContext(ioDispatcher) {
			queries.setReviewerDone(done, id)
		}
	}

	/** (done, total) scene counts for a request. */
	suspend fun sceneProgress(reviewRequestId: Long): Pair<Long, Long> = withContext(ioDispatcher) {
		val row = queries.sceneProgressForRequest(reviewRequestId).executeAsOne()
		row.done to row.total
	}

	/** (done, total) scene counts for several requests in one query, keyed by request id. */
	suspend fun sceneProgress(reviewRequestIds: Collection<Long>): Map<Long, Pair<Long, Long>> =
		withContext(ioDispatcher) {
			if (reviewRequestIds.isEmpty()) {
				emptyMap()
			} else {
				queries.sceneProgressForRequests(reviewRequestIds.toList())
					.executeAsList()
					.associate { it.review_request_id to (it.done to it.total) }
			}
		}
}
