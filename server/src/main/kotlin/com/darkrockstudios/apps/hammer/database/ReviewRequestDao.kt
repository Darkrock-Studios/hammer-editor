package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.review.ReviewStatus
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import kotlin.time.Instant

class ReviewRequestDao(
	database: Database,
) : KoinComponent {

	private val ioDispatcher by injectIoDispatcher()
	private val queries = database.serverDatabase.reviewRequestQueries

	suspend fun createRequest(
		userId: Long,
		projectId: Long,
		token: String,
		reviewerEmail: String,
		label: String,
		note: String?,
		status: ReviewStatus,
		expires: Instant?,
	): ReviewRequest = withContext(ioDispatcher) {
		queries.createRequest(
			userId = userId,
			projectId = projectId,
			token = token,
			reviewerEmail = reviewerEmail,
			label = label,
			note = note,
			status = status.toStringId(),
			expires = expires,
		)
		queries.getRequestByToken(token).executeAsOne()
	}

	suspend fun getRequestByToken(token: String): ReviewRequest? = withContext(ioDispatcher) {
		queries.getRequestByToken(token).executeAsOneOrNull()
	}

	suspend fun getRequest(id: Long, userId: Long): ReviewRequest? = withContext(ioDispatcher) {
		queries.getRequest(id, userId).executeAsOneOrNull()
	}

	suspend fun getRequestsForProject(userId: Long, projectId: Long): List<ReviewRequest> =
		withContext(ioDispatcher) {
			queries.getRequestsForProject(userId, projectId).executeAsList()
		}

	suspend fun deleteRequest(id: Long) {
		withContext(ioDispatcher) {
			queries.deleteRequest(id)
		}
	}

	suspend fun updateStatus(id: Long, status: ReviewStatus) {
		withContext(ioDispatcher) {
			queries.updateStatus(status.toStringId(), id, OPEN_STATUSES)
		}
	}

	suspend fun markOpened(id: Long, status: ReviewStatus, at: Instant) {
		withContext(ioDispatcher) {
			queries.markOpened(status.toStringId(), at, id, ReviewStatus.SENT.toStringId())
		}
	}

	suspend fun touchActivity(id: Long, status: ReviewStatus, at: Instant) {
		withContext(ioDispatcher) {
			queries.touchActivity(status.toStringId(), at, id, OPEN_STATUSES)
		}
	}

	suspend fun markSubmitted(id: Long, at: Instant) {
		withContext(ioDispatcher) {
			queries.markSubmitted(ReviewStatus.SUBMITTED.toStringId(), at, id, OPEN_STATUSES)
		}
	}

	suspend fun markResolved(id: Long, at: Instant) {
		withContext(ioDispatcher) {
			queries.markResolved(ReviewStatus.RESOLVED.toStringId(), at, id, ReviewStatus.SUBMITTED.toStringId())
		}
	}

	suspend fun statusCounts(now: Instant): ReviewStatusCounts = withContext(ioDispatcher) {
		queries.statusCounts(now).executeAsOne().let {
			ReviewStatusCounts(
				total = it.total,
				waiting = it.waiting,
				editing = it.editing,
				expired = it.expired,
				awaitingAuthor = it.awaiting_author,
				resolved = it.resolved,
				canceled = it.canceled,
				opened = it.opened,
				returned = it.returned,
			)
		}
	}

	suspend fun lifecycleSince(since: Instant): List<ReviewLifecycle> = withContext(ioDispatcher) {
		queries.lifecycleSince(since).executeAsList().map {
			ReviewLifecycle(created = it.created, submittedAt = it.submitted_at, resolvedAt = it.resolved_at)
		}
	}

	companion object {
		/** Statuses the editor is still working in; the only ones activity writes may move. */
		private val OPEN_STATUSES = listOf(
			ReviewStatus.SENT,
			ReviewStatus.OPENED,
			ReviewStatus.IN_PROGRESS,
		).map { it.toStringId() }
	}
}

/** Every review request bucketed by where it stands now. [opened] and [returned] count milestones ever reached. */
data class ReviewStatusCounts(
	val total: Long,
	val waiting: Long,
	val editing: Long,
	val expired: Long,
	val awaitingAuthor: Long,
	val resolved: Long,
	val canceled: Long,
	val opened: Long,
	val returned: Long,
)

data class ReviewLifecycle(
	val created: Instant,
	val submittedAt: Instant?,
	val resolvedAt: Instant?,
)
