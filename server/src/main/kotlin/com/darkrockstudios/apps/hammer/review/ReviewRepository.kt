package com.darkrockstudios.apps.hammer.review

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.validate.EmailValidator
import com.darkrockstudios.apps.hammer.database.AccountDao
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.database.ReviewRequestDao
import com.darkrockstudios.apps.hammer.database.ReviewSceneDao
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.encryption.ContentEncryptor
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.project.synchronizers.ServerSceneSynchronizer
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.Msg
import com.darkrockstudios.apps.hammer.utilities.SResult
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import com.darkrockstudios.apps.hammer.utilities.SecureTokenGenerator
import com.darkrockstudios.apps.hammer.utilities.TokenHasher
import com.darkrockstudios.apps.hammer.utilities.isFailure
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.database.ReviewSuggestionDao
import com.darkrockstudios.apps.hammer.database.ReviewRequest as ReviewRequestRow
import com.darkrockstudios.apps.hammer.database.ReviewScene as ReviewSceneRow
import com.darkrockstudios.apps.hammer.database.ReviewSuggestion as ReviewSuggestionRow
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration

class ReviewRepository(
	private val accountDao: AccountDao,
	private val projectDao: ProjectDao,
	private val reviewRequestDao: ReviewRequestDao,
	private val reviewSceneDao: ReviewSceneDao,
	private val reviewSuggestionDao: ReviewSuggestionDao,
	private val projectEntityDatasource: ProjectEntityDatasource,
	private val sceneDraftSynchronizer: ServerSceneDraftSynchronizer,
	private val sceneSynchronizer: ServerSceneSynchronizer,
	private val contentEncryptor: ContentEncryptor,
	private val tokenHasher: TokenHasher,
	private val clock: Clock,
	base64: Base64,
) : KoinComponent {

	private val tokenGenerator = SecureTokenGenerator(REVIEW_TOKEN_LENGTH, base64)

	private val projectsSessions: SyncSessionManager<Long, ProjectsSynchronizationSession> by KoinJavaComponent.inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECTS_SYNC_MANAGER)
	)

	private val projectSessions: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession> by KoinJavaComponent.inject(
		clazz = SyncSessionManager::class.java,
		qualifier = named(PROJECT_SYNC_MANAGER)
	)

	data class CreatedReviewRequest(
		val reviewRequestId: Long,
		/** Plaintext capability token; only ever returned here, stored hashed. */
		val token: String,
	)

	suspend fun createReviewRequest(
		userId: Long,
		projectId: ProjectId,
		reviewerEmail: String,
		label: String,
		note: String?,
		expiresIn: Duration?,
		sceneIds: List<Int>,
	): SResult<CreatedReviewRequest> {
		if (sceneIds.isEmpty()) {
			return SResult.failure("No scenes selected", Msg.r("api_review_create_error_no_scenes"))
		}
		if (sceneIds.size != sceneIds.distinct().size) {
			return SResult.failure("Duplicate scene ids", Msg.r("api_review_create_error_invalid_scene"))
		}
		if (EmailValidator.validate(reviewerEmail).not()) {
			return SResult.failure("Invalid email", Msg.r("api_review_create_error_invalid_email"))
		}
		if (label.length > MAX_LABEL_LENGTH || (note?.length ?: 0) > MAX_NOTE_LENGTH) {
			return SResult.failure("Label or note too long", Msg.r("api_review_create_error_too_long"))
		}

		val projectDef = projectEntityDatasource.getProject(userId, projectId)
			?: return SResult.failure("Project not found", Msg.r("api_review_create_error_project_not_found"))
		val numericProjectId = projectDao.getProjectIdOrNull(userId, projectId)
			?: return SResult.failure("Project not found", Msg.r("api_review_create_error_project_not_found"))

		// Load and validate every requested scene before minting anything.
		val scenes = mutableListOf<ApiProjectEntity.SceneEntity>()
		for (sceneId in sceneIds) {
			val type = projectEntityDatasource.findEntityType(sceneId, userId, projectDef)
			if (type != ApiProjectEntity.Type.SCENE) {
				return SResult.failure(
					"Entity $sceneId is not a scene",
					Msg.r("api_review_create_error_invalid_scene")
				)
			}
			val result = projectEntityDatasource.loadEntity(
				userId, projectDef, sceneId,
				ApiProjectEntity.Type.SCENE,
				ApiProjectEntity.SceneEntity.serializer(),
			)
			if (isFailure(result)) {
				return SResult.failure(
					"Failed to load scene $sceneId",
					Msg.r("api_review_create_error_invalid_scene"),
					result.exception,
				)
			}
			scenes += result.data
		}

		val draftName = forEditDraftName(label)
		val now = clock.now()

		// Resolve the cipher secret before minting anything, so a missing account
		// can't leave orphaned for-edit drafts behind.
		val cipherSecret = accountDao.getAccount(userId)?.cipher_secret
			?: return SResult.failure("Account not found", Msg.r("api_review_error_not_found"))

		val draftIds = withInternalSyncSession(userId, projectDef) {
			// Reserve the ID range first: a failure mid-store burns IDs (harmless gap)
			// instead of leaving stored entities below last_id (future collision).
			val syncData = projectEntityDatasource.loadProjectSyncData(userId, projectDef)
			val maxKnownId = maxOf(
				syncData.lastId,
				projectEntityDatasource.findLastId(userId, projectDef) ?: -1,
				syncData.deletedIds.maxOrNull() ?: -1,
			)
			val firstId = maxKnownId + 1
			projectEntityDatasource.updateSyncData(userId, projectDef) {
				it.copy(lastId = maxKnownId + scenes.size)
			}

			val mintedIds = mutableListOf<Int>()
			for ((index, scene) in scenes.withIndex()) {
				touchInternalSession(userId, projectDef)
				val draft = ApiProjectEntity.SceneDraftEntity(
					id = firstId + index,
					sceneId = scene.id,
					created = now,
					name = draftName,
					content = scene.content,
				)
				val saveResult = sceneDraftSynchronizer.saveEntity(
					userId, projectDef, draft,
					originalHash = null,
					force = true,
				)
				if (isFailure(saveResult)) {
					mintedIds.forEach { mintedId ->
						sceneDraftSynchronizer.deleteEntity(userId, projectDef, mintedId)
					}
					return@withInternalSyncSession SResult.failure(
						"Failed to store for-edit draft",
						Msg.r("api_review_create_error_draft_failed"),
						saveResult.exception,
					)
				}
				mintedIds += draft.id
			}
			SResult.success(mintedIds.toList())
		}

		if (draftIds is ServerResult.Failure) {
			return SResult.failure(draftIds.error, draftIds.displayMessage, draftIds.exception)
		}
		draftIds as ServerResult.Success

		val plainToken = tokenGenerator.generateToken()
		val hashedToken = tokenHasher.hashToken(plainToken)

		val request = reviewRequestDao.createRequest(
			userId = userId,
			projectId = numericProjectId,
			token = hashedToken,
			reviewerEmail = reviewerEmail.trim(),
			label = label.trim().ifEmpty { reviewerEmail.trim() },
			note = note?.trim()?.ifEmpty { null },
			status = ReviewStatus.SENT,
			expires = expiresIn?.let { now + it },
		)

		try {
			scenes.forEachIndexed { index, scene ->
				reviewSceneDao.createScene(
					reviewRequestId = request.id,
					sceneId = scene.id,
					draftId = draftIds.data[index],
					sceneName = scene.name,
					sceneOrder = index,
					snapshotContent = contentEncryptor.encrypt(scene.content, cipherSecret),
				)
			}
		} catch (e: Exception) {
			// Don't leave a half-snapshotted request visible; cascade removes its scenes.
			reviewRequestDao.deleteRequest(request.id)
			return SResult.failure(
				"Failed to snapshot scenes for review",
				Msg.r("api_review_create_error_draft_failed"),
				e,
			)
		}

		return SResult.success(CreatedReviewRequest(request.id, plainToken))
	}

	/**
	 * Look up the request a capability token belongs to, with no side effects and
	 * no status/expiry checks. Used to recognize the author following their own
	 * reviewer link, before [openReviewByToken] would mark it opened.
	 */
	suspend fun findReviewByToken(plainToken: String): ReviewRequest? =
		reviewRequestDao.getRequestByToken(tokenHasher.hashToken(plainToken))?.toDomain()

	data class OpenedReview(
		val request: ReviewRequest,
		/** True the very first time this link is opened — the editor's welcome moment. */
		val firstOpen: Boolean,
	)

	/**
	 * Resolve a reviewer's capability token. Marks the request opened on first use
	 * and refuses expired or canceled requests.
	 */
	suspend fun openReviewByToken(plainToken: String): SResult<OpenedReview> {
		val hashedToken = tokenHasher.hashToken(plainToken)
		val row = reviewRequestDao.getRequestByToken(hashedToken)
			?: return SResult.failure("Unknown token", Msg.r("api_review_token_error_invalid"))

		val request = row.toDomain()
		val now = clock.now()
		return when {
			request.status == ReviewStatus.CANCELED ->
				SResult.failure("Request revoked", Msg.r("api_review_token_error_revoked"))

			request.expires != null && now > request.expires ->
				SResult.failure("Request expired", Msg.r("api_review_token_error_expired"))

			request.status == ReviewStatus.SENT -> {
				reviewRequestDao.markOpened(request.id, ReviewStatus.OPENED, now)
				SResult.success(
					OpenedReview(
						request.copy(status = ReviewStatus.OPENED, openedAt = now, lastActiveAt = now),
						firstOpen = true,
					)
				)
			}

			else -> SResult.success(OpenedReview(request, firstOpen = false))
		}
	}

	suspend fun getReviewsForProject(userId: Long, projectId: ProjectId): SResult<List<ReviewRequest>> {
		val numericProjectId = projectDao.getProjectIdOrNull(userId, projectId)
			?: return SResult.failure("Project not found", Msg.r("api_review_create_error_project_not_found"))
		val rows = reviewRequestDao.getRequestsForProject(userId, numericProjectId)
		return SResult.success(rows.map { it.toDomain() })
	}

	suspend fun getReview(userId: Long, reviewRequestId: Long): SResult<ReviewRequest> {
		val row = reviewRequestDao.getRequest(reviewRequestId, userId)
			?: return SResult.failure("Review not found", Msg.r("api_review_error_not_found"))
		return SResult.success(row.toDomain())
	}

	/** Scenes for a request with their snapshots decrypted. */
	suspend fun getReviewScenes(request: ReviewRequest): List<ReviewScene> {
		val cipherSecret = accountDao.getAccount(request.userId)?.cipher_secret
			?: error("Account ${request.userId} missing for review ${request.id}")
		return reviewSceneDao.getScenesForRequest(request.id).map { it.toDomain(cipherSecret) }
	}

	suspend fun revokeReview(userId: Long, reviewRequestId: Long): SResult<Unit> {
		val row = reviewRequestDao.getRequest(reviewRequestId, userId)
			?: return SResult.failure("Review not found", Msg.r("api_review_error_not_found"))
		val status = ReviewStatus.fromString(row.status)
		return when (status) {
			ReviewStatus.SUBMITTED, ReviewStatus.RESOLVED ->
				SResult.failure("Already submitted", Msg.r("api_review_revoke_error_submitted"))

			else -> {
				reviewRequestDao.updateStatus(reviewRequestId, ReviewStatus.CANCELED)
				SResult.success()
			}
		}
	}

	/* ===== Reviewer-side suggestion editing (capability-token authenticated) ===== */

	/** All suggestions for a request, grouped by review_scene id. */
	suspend fun getSuggestionsByScene(request: ReviewRequest): Map<Long, List<ReviewSuggestion>> =
		reviewSuggestionDao.getSuggestionsForRequest(request.id)
			.map { it.toDomain() }
			.groupBy { it.reviewSceneId }

	suspend fun countSuggestions(reviewRequestId: Long): Long =
		reviewSuggestionDao.countSuggestionsForRequest(reviewRequestId)

	suspend fun addSuggestion(
		token: String,
		reviewSceneId: Long,
		type: ReviewSuggestionType,
		paragraph: Int,
		start: Int,
		end: Int,
		replacement: String?,
		reason: String?,
	): SResult<ReviewSuggestion> {
		val request = when (val r = resolveOpenReview(token)) {
			is ServerResult.Failure -> return SResult.failure(r.error, r.displayMessage, r.exception)
			is ServerResult.Success -> r.data
		}

		val scene = reviewSceneDao.getScene(reviewSceneId)
		if (scene == null || scene.review_request_id != request.id) {
			return SResult.failure("Scene not in review", Msg.r("api_review_suggestion_error_invalid"))
		}

		val cipherSecret = accountDao.getAccount(request.userId)?.cipher_secret
			?: return SResult.failure("Account missing", Msg.r("api_review_error_not_found"))
		val snapshot = contentEncryptor.decrypt(scene.snapshot_content, cipherSecret)
		val paraText = ReviewParagraphs.paragraph(snapshot, paragraph)
			?: return SResult.failure("Bad paragraph", Msg.r("api_review_suggestion_error_invalid"))

		val isInsert = type == ReviewSuggestionType.INSERT
		val validOffsets = start in 0..paraText.length && end in start..paraText.length &&
			(if (isInsert) start == end else end > start)
		if (!validOffsets) {
			return SResult.failure("Bad offsets", Msg.r("api_review_suggestion_error_invalid"))
		}
		if ((type == ReviewSuggestionType.REWORD || type == ReviewSuggestionType.INSERT) &&
			replacement.isNullOrEmpty()
		) {
			return SResult.failure("Missing replacement", Msg.r("api_review_suggestion_error_invalid"))
		}
		if ((replacement?.length ?: 0) > MAX_REPLACEMENT_LENGTH || (reason?.length ?: 0) > MAX_REASON_LENGTH) {
			return SResult.failure("Suggestion too long", Msg.r("api_review_suggestion_error_too_long"))
		}
		if (type == ReviewSuggestionType.COMMENT && reason?.trim().isNullOrEmpty()) {
			return SResult.failure("Missing comment", Msg.r("api_review_suggestion_error_invalid"))
		}

		// Reject ranges that overlap an existing edit in the same paragraph. A caret
		// may not land strictly inside another suggestion's span, and a range edit may
		// not span an existing caret — applying both would swallow the insertion.
		val existing = reviewSuggestionDao.getSuggestionsForScene(reviewSceneId)
			.filter { it.paragraph == paragraph }
		for (s in existing) {
			val sIsCaret = s.start_offset == s.end_offset
			val clash = if (isInsert) {
				!sIsCaret && start > s.start_offset && start < s.end_offset
			} else if (sIsCaret) {
				s.start_offset > start && s.start_offset < end
			} else {
				!(end <= s.start_offset || start >= s.end_offset)
			}
			if (clash) return SResult.failure("Overlapping suggestion", Msg.r("api_review_suggestion_error_overlap"))
		}

		val originalText = if (isInsert) "" else paraText.substring(start, end)
		val id = reviewSuggestionDao.createSuggestion(
			reviewSceneId = reviewSceneId,
			type = type,
			paragraph = paragraph,
			startOffset = start,
			endOffset = end,
			originalText = originalText,
			replacementText = replacement?.ifEmpty { null },
			reason = reason?.trim()?.ifEmpty { null },
			status = ReviewSuggestionStatus.PENDING,
		)

		touchInProgress(request)

		val created = reviewSuggestionDao.getSuggestion(id)
			?: return SResult.failure("Lost suggestion", Msg.r("api_review_suggestion_error_invalid"))
		return SResult.success(created.toDomain())
	}

	suspend fun updateSuggestion(
		token: String,
		suggestionId: Long,
		replacement: String?,
		reason: String?,
	): SResult<ReviewSuggestion> {
		val request = when (val r = resolveOpenReview(token)) {
			is ServerResult.Failure -> return SResult.failure(r.error, r.displayMessage, r.exception)
			is ServerResult.Success -> r.data
		}
		val suggestion = reviewSuggestionDao.getSuggestion(suggestionId)
			?: return SResult.failure("Not found", Msg.r("api_review_suggestion_error_invalid"))
		val scene = reviewSceneDao.getScene(suggestion.review_scene_id)
		if (scene == null || scene.review_request_id != request.id) {
			return SResult.failure("Not in review", Msg.r("api_review_suggestion_error_invalid"))
		}

		val type = ReviewSuggestionType.fromString(suggestion.type)
		val newReplacement = when (type) {
			ReviewSuggestionType.REWORD, ReviewSuggestionType.INSERT -> {
				if (replacement.isNullOrEmpty()) {
					return SResult.failure("Missing replacement", Msg.r("api_review_suggestion_error_invalid"))
				}
				replacement
			}

			else -> null
		}
		val newReason = reason?.trim()?.ifEmpty { null }
		if (type == ReviewSuggestionType.COMMENT && newReason == null) {
			return SResult.failure("Missing comment", Msg.r("api_review_suggestion_error_invalid"))
		}
		if ((newReplacement?.length ?: 0) > MAX_REPLACEMENT_LENGTH || (newReason?.length ?: 0) > MAX_REASON_LENGTH) {
			return SResult.failure("Suggestion too long", Msg.r("api_review_suggestion_error_too_long"))
		}

		reviewSuggestionDao.updateSuggestionContent(suggestionId, newReplacement, newReason, clock.now())
		touchInProgress(request)

		val updated = reviewSuggestionDao.getSuggestion(suggestionId)
			?: return SResult.failure("Lost suggestion", Msg.r("api_review_suggestion_error_invalid"))
		return SResult.success(updated.toDomain())
	}

	suspend fun deleteSuggestion(token: String, suggestionId: Long): SResult<Unit> {
		val request = when (val r = resolveOpenReview(token)) {
			is ServerResult.Failure -> return SResult.failure(r.error, r.displayMessage, r.exception)
			is ServerResult.Success -> r.data
		}
		val suggestion = reviewSuggestionDao.getSuggestion(suggestionId)
			?: return SResult.failure("Not found", Msg.r("api_review_suggestion_error_invalid"))
		val scene = reviewSceneDao.getScene(suggestion.review_scene_id)
		if (scene == null || scene.review_request_id != request.id) {
			return SResult.failure("Not in review", Msg.r("api_review_suggestion_error_invalid"))
		}
		reviewSuggestionDao.deleteSuggestion(suggestionId)
		touchInProgress(request)
		return SResult.success()
	}

	/** Editor marks a scene as read/done (or unmarks it); pure progress signal for the author. */
	suspend fun setSceneDone(token: String, reviewSceneId: Long, done: Boolean): SResult<Unit> {
		val request = when (val r = resolveOpenReview(token)) {
			is ServerResult.Failure -> return SResult.failure(r.error, r.displayMessage, r.exception)
			is ServerResult.Success -> r.data
		}
		val scene = reviewSceneDao.getScene(reviewSceneId)
		if (scene == null || scene.review_request_id != request.id) {
			return SResult.failure("Scene not in review", Msg.r("api_review_suggestion_error_invalid"))
		}
		reviewSceneDao.setReviewerDone(reviewSceneId, done)
		touchInProgress(request)
		return SResult.success()
	}

	/** (done, total) scene progress for a request, for the author's status display. */
	suspend fun getSceneProgress(reviewRequestId: Long): Pair<Long, Long> =
		reviewSceneDao.sceneProgress(reviewRequestId)

	suspend fun submitReview(token: String): SResult<ReviewRequest> {
		val request = when (val r = resolveOpenReview(token)) {
			is ServerResult.Failure -> return SResult.failure(r.error, r.displayMessage, r.exception)
			is ServerResult.Success -> r.data
		}
		val now = clock.now()
		reviewRequestDao.markSubmitted(request.id, now)
		return SResult.success(request.copy(status = ReviewStatus.SUBMITTED, submittedAt = now))
	}

	/* ===== Author-side resolution (session authenticated) ===== */

	/**
	 * Author accepts/rejects an edit (or resolves a comment). Anchors and content
	 * are immutable here; only the status moves, and only while the review is
	 * submitted but not yet resolved. Any status can return to PENDING (undo).
	 */
	suspend fun setSuggestionStatus(
		userId: Long,
		reviewRequestId: Long,
		suggestionId: Long,
		status: ReviewSuggestionStatus,
	): SResult<ReviewSuggestion> {
		val request = when (val r = resolveSubmittedReview(userId, reviewRequestId)) {
			is ServerResult.Failure -> return SResult.failure(r.error, r.displayMessage, r.exception)
			is ServerResult.Success -> r.data
		}
		val suggestion = reviewSuggestionDao.getSuggestion(suggestionId)
			?: return SResult.failure("Not found", Msg.r("api_review_suggestion_error_invalid"))
		val scene = reviewSceneDao.getScene(suggestion.review_scene_id)
		if (scene == null || scene.review_request_id != request.id) {
			return SResult.failure("Not in review", Msg.r("api_review_suggestion_error_invalid"))
		}

		val type = ReviewSuggestionType.fromString(suggestion.type)
		val allowed = when (type) {
			ReviewSuggestionType.COMMENT ->
				setOf(ReviewSuggestionStatus.RESOLVED, ReviewSuggestionStatus.PENDING)

			else -> setOf(
				ReviewSuggestionStatus.ACCEPTED,
				ReviewSuggestionStatus.REJECTED,
				ReviewSuggestionStatus.PENDING,
			)
		}
		if (status !in allowed) {
			return SResult.failure("Invalid status", Msg.r("api_review_suggestion_error_invalid"))
		}

		reviewSuggestionDao.updateSuggestionStatus(suggestionId, status, clock.now())
		val updated = reviewSuggestionDao.getSuggestion(suggestionId)
			?: return SResult.failure("Lost suggestion", Msg.r("api_review_suggestion_error_invalid"))
		return SResult.success(updated.toDomain())
	}

	/**
	 * Commit a submitted review: apply the accepted suggestions to each scene's
	 * snapshot, store the revised text as a new draft, and — when the working
	 * scene hasn't changed since the snapshot was taken — overwrite the scene
	 * itself so clean clients pick it up on their next sync. Diverged scenes get
	 * the draft only; the author merges in-app via Draft Compare. Marks the
	 * request resolved.
	 */
	suspend fun commitReview(userId: Long, reviewRequestId: Long): SResult<ReviewCommitResult> {
		val request = when (val r = resolveSubmittedReview(userId, reviewRequestId)) {
			is ServerResult.Failure -> return SResult.failure(r.error, r.displayMessage, r.exception)
			is ServerResult.Success -> r.data
		}
		val projectRow = projectDao.getProjectByRowId(request.projectId)
			?: return SResult.failure("Project not found", Msg.r("api_review_create_error_project_not_found"))
		val projectDef = projectEntityDatasource.getProject(userId, ProjectId(projectRow.uuid))
			?: return SResult.failure("Project not found", Msg.r("api_review_create_error_project_not_found"))

		val scenes = getReviewScenes(request).sortedBy { it.sceneOrder }
		val suggestionsByScene = getSuggestionsByScene(request)
		val now = clock.now()
		val draftName = reviewedDraftName(request.label, now)

		data class Plan(val scene: ReviewScene, val revised: String)

		val plans = scenes.map { scene ->
			Plan(scene, ReviewApplier.applyAccepted(scene.snapshotContent, suggestionsByScene[scene.id].orEmpty()))
		}
		val changed = plans.filter { it.revised != it.scene.snapshotContent }

		data class Minted(val plan: Plan, val current: ApiProjectEntity.SceneEntity, val draftId: Int)

		// The whole commit — including the no-change path and marking resolved — runs
		// inside the exclusive session, so two overlapping commits serialize and the
		// second one fails the re-check instead of double-minting drafts.
		val outcomes = withInternalSyncSession(userId, projectDef) {
			when (val r = resolveSubmittedReview(userId, reviewRequestId)) {
				is ServerResult.Failure -> return@withInternalSyncSession SResult.failure(
					r.error, r.displayMessage, r.exception
				)

				is ServerResult.Success -> Unit
			}

			val results = mutableMapOf<Long, ReviewCommitOutcome>()
			if (changed.isNotEmpty()) {
				// Reserve the full ID range up front; a mid-commit failure burns IDs
				// (harmless gap) instead of risking stored entities above last_id.
				val syncData = projectEntityDatasource.loadProjectSyncData(userId, projectDef)
				val maxKnownId = maxOf(
					syncData.lastId,
					projectEntityDatasource.findLastId(userId, projectDef) ?: -1,
					syncData.deletedIds.maxOrNull() ?: -1,
				)
				val firstId = maxKnownId + 1
				projectEntityDatasource.updateSyncData(userId, projectDef) {
					it.copy(lastId = maxKnownId + changed.size)
				}

				// Phase 1: mint every draft before touching any working scene, so a
				// failure here rolls back cleanly while the scenes are still untouched.
				val minted = mutableListOf<Minted>()
				for ((index, plan) in changed.withIndex()) {
					touchInternalSession(userId, projectDef)
					val currentResult = projectEntityDatasource.loadEntity(
						userId, projectDef, plan.scene.sceneId,
						ApiProjectEntity.Type.SCENE,
						ApiProjectEntity.SceneEntity.serializer(),
					)
					if (isFailure(currentResult)) {
						results[plan.scene.id] = ReviewCommitOutcome.SCENE_MISSING
						continue
					}

					val draft = ApiProjectEntity.SceneDraftEntity(
						id = firstId + index,
						sceneId = plan.scene.sceneId,
						created = now,
						name = draftName,
						content = plan.revised,
					)
					val draftResult = sceneDraftSynchronizer.saveEntity(
						userId, projectDef, draft,
						originalHash = null,
						force = true,
					)
					if (isFailure(draftResult)) {
						minted.forEach { m ->
							sceneDraftSynchronizer.deleteEntity(userId, projectDef, m.draftId)
						}
						return@withInternalSyncSession SResult.failure(
							"Failed to store reviewed draft",
							Msg.r("api_review_commit_error_draft_failed"),
							draftResult.exception,
						)
					}
					minted += Minted(plan, currentResult.data, draft.id)
				}

				// Phase 2: overwrite clean working scenes. Every draft already exists,
				// so a failure here needs no rollback — Draft Compare is the recovery.
				for (m in minted) {
					touchInternalSession(userId, projectDef)
					results[m.plan.scene.id] = if (m.current.content == m.plan.scene.snapshotContent) {
						val sceneResult = sceneSynchronizer.saveEntity(
							userId, projectDef, m.current.copy(content = m.plan.revised),
							originalHash = null,
							force = true,
						)
						if (isSuccess(sceneResult)) ReviewCommitOutcome.APPLIED else ReviewCommitOutcome.DIVERGED
					} else {
						ReviewCommitOutcome.DIVERGED
					}
				}
			}

			reviewRequestDao.markResolved(request.id, clock.now())
			SResult.success(plans.map { plan ->
				ReviewCommitScene(
					sceneId = plan.scene.sceneId,
					sceneName = plan.scene.sceneName,
					outcome = results[plan.scene.id] ?: ReviewCommitOutcome.UNCHANGED,
				)
			})
		}

		if (outcomes is ServerResult.Failure) {
			return SResult.failure(outcomes.error, outcomes.displayMessage, outcomes.exception)
		}
		outcomes as ServerResult.Success

		return SResult.success(ReviewCommitResult(draftName, outcomes.data))
	}

	/** Resolve an author's review that has been submitted but not yet resolved. */
	private suspend fun resolveSubmittedReview(userId: Long, reviewRequestId: Long): SResult<ReviewRequest> {
		val row = reviewRequestDao.getRequest(reviewRequestId, userId)
			?: return SResult.failure("Review not found", Msg.r("api_review_error_not_found"))
		val request = row.toDomain()
		return when (request.status) {
			ReviewStatus.RESOLVED ->
				SResult.failure("Already resolved", Msg.r("api_review_commit_error_resolved"))

			ReviewStatus.SUBMITTED -> SResult.success(request)

			else ->
				SResult.failure("Not submitted", Msg.r("api_review_commit_error_not_submitted"))
		}
	}

	/** Resolve a token to a review that is still open for editing (not submitted, expired, or revoked). */
	private suspend fun resolveOpenReview(token: String): SResult<ReviewRequest> {
		val row = reviewRequestDao.getRequestByToken(tokenHasher.hashToken(token))
			?: return SResult.failure("Unknown token", Msg.r("api_review_token_error_invalid"))
		val request = row.toDomain()
		val now = clock.now()
		return when {
			request.status == ReviewStatus.CANCELED ->
				SResult.failure("Revoked", Msg.r("api_review_token_error_revoked"))

			request.status == ReviewStatus.SUBMITTED || request.status == ReviewStatus.RESOLVED ->
				SResult.failure("Already submitted", Msg.r("api_review_token_error_submitted"))

			request.expires != null && now > request.expires ->
				SResult.failure("Expired", Msg.r("api_review_token_error_expired"))

			else -> SResult.success(request)
		}
	}

	private suspend fun touchInProgress(request: ReviewRequest) {
		reviewRequestDao.touchActivity(request.id, ReviewStatus.IN_PROGRESS, clock.now())
	}

	/**
	 * Run [block] holding an exclusive project sync session, so entity minting can't
	 * race a client sync (mid-session ID collisions, end_sync last_id regression).
	 */
	private suspend fun <T> withInternalSyncSession(
		userId: Long,
		projectDef: ProjectDefinition,
		block: suspend () -> SResult<T>,
	): SResult<T> {
		val syncKey = ProjectSyncKey(userId, projectDef)
		if (projectsSessions.hasActiveSyncSession(userId)) {
			return SResult.failure("Project sync in progress", Msg.r("api_review_create_error_sync_busy"))
		}

		// Atomic claim; unique installId so a client's begin-sync can never reclaim this session.
		projectSessions.claimSession(syncKey) { key, syncId ->
			ProjectSynchronizationSession(
				userId = key.userId,
				projectDef = key.projectDef,
				started = clock.now(),
				syncId = syncId,
				installId = "$INTERNAL_INSTALL_PREFIX${UUID.randomUUID()}",
			)
		} ?: return SResult.failure("Project sync in progress", Msg.r("api_review_create_error_sync_busy"))

		return try {
			block()
		} finally {
			projectSessions.terminateSession(syncKey)
		}
	}

	/** Keep the internal session from expiring during long per-scene mint/encrypt loops. */
	private fun touchInternalSession(userId: Long, projectDef: ProjectDefinition) {
		projectSessions.findSession(ProjectSyncKey(userId, projectDef))?.updateLastAccessed(clock)
	}

	private fun ReviewRequestRow.toDomain() = ReviewRequest(
		id = id,
		userId = user_id,
		projectId = project_id,
		reviewerEmail = reviewer_email,
		label = label,
		note = note,
		status = ReviewStatus.fromString(status)
			?: error("Unknown review status '$status' for review $id"),
		created = created,
		expires = expires,
		openedAt = opened_at,
		lastActiveAt = last_active_at,
		submittedAt = submitted_at,
		resolvedAt = resolved_at,
	)

	private fun ReviewSuggestionRow.toDomain() = ReviewSuggestion(
		id = id,
		reviewSceneId = review_scene_id,
		type = ReviewSuggestionType.fromString(type)
			?: error("Unknown suggestion type '$type' for suggestion $id"),
		paragraph = paragraph,
		startOffset = start_offset,
		endOffset = end_offset,
		originalText = original_text,
		replacementText = replacement_text,
		reason = reason,
		status = ReviewSuggestionStatus.fromString(status)
			?: error("Unknown suggestion status '$status' for suggestion $id"),
		created = created,
		updated = updated,
	)

	private suspend fun ReviewSceneRow.toDomain(cipherSecret: String) = ReviewScene(
		id = id,
		reviewRequestId = review_request_id,
		sceneId = scene_id,
		draftId = draft_id,
		sceneName = scene_name,
		sceneOrder = scene_order,
		snapshotContent = contentEncryptor.decrypt(snapshot_content, cipherSecret),
		reviewerDone = reviewer_done,
	)

	companion object {
		const val REVIEW_TOKEN_LENGTH = 32
		const val INTERNAL_INSTALL_PREFIX = "internal:review:"

		private val invalidDraftNameChars = Regex("""[^\da-zA-Z _']""")
		private val collapseSpaces = Regex(""" {2,}""")

		/**
		 * Build a for-edit draft name that satisfies the client's
		 * SceneDraftsDatasource.validDraftName rules (alphanumeric, space, apostrophe, max 128).
		 */
		fun forEditDraftName(label: String): String {
			val sanitizedLabel = sanitizeForDraftName(label)
			val base = if (sanitizedLabel.isEmpty()) "Sent for review" else "Sent for review $sanitizedLabel"
			return base.take(MAX_DRAFT_NAME_LENGTH).trim()
		}

		/** Name for the committed draft, same validDraftName constraints (so no comma in the date). */
		fun reviewedDraftName(label: String, at: kotlin.time.Instant): String {
			val date = java.time.format.DateTimeFormatter
				.ofPattern("MMM d yyyy", java.util.Locale.ENGLISH)
				.withZone(java.time.ZoneOffset.UTC)
				.format(java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()))
			val sanitizedLabel = sanitizeForDraftName(label)
			val base = if (sanitizedLabel.isEmpty()) {
				"Editorial Review $date"
			} else {
				"Editorial Review $sanitizedLabel $date"
			}
			return base.take(MAX_DRAFT_NAME_LENGTH).trim()
		}

		private fun sanitizeForDraftName(label: String): String = label
			.replace(invalidDraftNameChars, " ")
			.replace(collapseSpaces, " ")
			.trim()

		private const val MAX_DRAFT_NAME_LENGTH = 128

		// Generous caps on free-text fields; the anonymous token endpoints
		// must not accept unbounded input.
		const val MAX_REPLACEMENT_LENGTH = 10_000
		const val MAX_REASON_LENGTH = 5_000
		const val MAX_NOTE_LENGTH = 2_000
		const val MAX_LABEL_LENGTH = 100
	}
}
