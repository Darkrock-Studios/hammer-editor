package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.*
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.review.ReviewInviteMailer
import com.darkrockstudios.apps.hammer.review.ReviewParagraphs
import com.darkrockstudios.apps.hammer.review.ReviewRepository
import com.darkrockstudios.apps.hammer.review.ReviewRequest
import com.darkrockstudios.apps.hammer.review.ReviewScene
import com.darkrockstudios.apps.hammer.review.ReviewStatus
import com.darkrockstudios.apps.hammer.review.ReviewSubmittedMailer
import com.darkrockstudios.apps.hammer.review.ReviewSuggestion
import com.darkrockstudios.apps.hammer.review.ReviewSuggestionStatus
import com.darkrockstudios.apps.hammer.review.ReviewSuggestionType
import kotlinx.serialization.Serializable
import com.darkrockstudios.apps.hammer.story.SceneHierarchyResult
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utilities.ServerResult
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant

fun Route.reviewFrontend(
	reviewRepository: ReviewRepository,
	projectsRepository: ProjectsRepository,
	storyExportService: StoryExportService,
	accountsRepository: AccountsRepository,
	projectDao: ProjectDao,
	markdownService: MarkdownService,
	reviewInviteMailer: ReviewInviteMailer?,
	reviewSubmittedMailer: ReviewSubmittedMailer?,
	clock: Clock,
) {
	authenticatedOnly {
		route("/story/{projectName}/reviews") {

			hx.get("/dialog") {
				val session = call.sessions.requireUser()
				val project = call.resolveProject(projectsRepository, session.userId) ?: return@get

				val hierarchyResult =
					storyExportService.getSceneHierarchy(session.userId, ProjectId(project.uuid))
				val scenes = when (hierarchyResult) {
					is SceneHierarchyResult.Success -> hierarchyResult.scenes
					else -> emptyList()
				}

				val sceneTree = scenes.map { item ->
					mapOf(
						"id" to item.id,
						"name" to item.name,
						"isGroup" to item.isGroup,
						"isScene" to item.isScene,
						"depth" to item.depth,
						"indentPx" to (item.depth * 18),
					)
				}
				val sceneCount = scenes.count { it.isScene }

				val model = call.withDefaults(
					mapOf(
						"projectNameForUrl" to ProjectName.formatForUrl(project.name),
						"sceneTree" to sceneTree,
						"sceneCount" to sceneCount,
					)
				)
				call.respond(MustacheContent("partials/review-request-dialog.mustache", model))
			}

			hx.post {
				val session = call.sessions.requireUser()
				val project = call.resolveProject(projectsRepository, session.userId) ?: return@post
				val projectId = ProjectId(project.uuid)

				val form = call.receiveParameters()
				val email = form["reviewerEmail"]?.trim().orEmpty()
				val label = form["label"]?.trim().orEmpty()
				val note = form["note"]?.trim()?.ifEmpty { null }
				val expiresIn = when (form["expiry"]) {
					"30" -> 30.days
					"60" -> 60.days
					"90" -> 90.days
					else -> null
				}
				val sceneIds = form.getAll("sceneIds")?.mapNotNull { it.toIntOrNull() } ?: emptyList()

				val result = reviewRepository.createReviewRequest(
					userId = session.userId,
					projectId = projectId,
					reviewerEmail = email,
					label = label,
					note = note,
					expiresIn = expiresIn,
					sceneIds = sceneIds,
				)

				when (result) {
					is ServerResult.Failure -> {
						val message = result.displayMessageText(call)
							?: call.msg("review_toast_email_failed")
						val model = call.reviewPanelModel(reviewRepository, project.name, session.userId, projectId)
						respondTemplateWithToast(
							templatePath = "partials/review-panel.mustache",
							model = model,
							message = message,
							toast = Toast.Error,
						)
					}

					is ServerResult.Success -> {
						val reviewUrl = call.constructReviewUrl(result.data.token)
						val review = (reviewRepository.getReview(session.userId, result.data.reviewRequestId)
							as? ServerResult.Success)?.data

						var emailSent = false
						if (reviewInviteMailer != null) {
							val account = accountsRepository.getAccount(session.userId)
							val authorName = account.pen_name?.ifBlank { null } ?: "A writer"
							val emailResult = reviewInviteMailer.sendInvite(
								toEmail = email,
								authorName = authorName,
								projectName = project.name,
								note = note,
								reviewUrl = reviewUrl,
								expiresFormatted = review?.expires?.let { formatReviewDate(it) },
								locale = call.getLocale(),
							)
							emailSent = emailResult is com.darkrockstudios.apps.hammer.email.EmailResult.Success
						}

						val model = call.reviewPanelModel(reviewRepository, project.name, session.userId, projectId)
						if (emailSent) {
							val panelHtml = renderTemplate("partials/review-panel.mustache", model)
							val closeDialog = """<div id="review-dialog-container" hx-swap-oob="innerHTML"></div>"""
							respondHtmlWithToast(
								content = panelHtml + closeDialog,
								message = call.msg("review_toast_created", email),
								toast = Toast.Success,
							)
						} else {
							// No email configured (or send failed): hand the author the link directly.
							val panelHtml = renderTemplate("partials/review-panel.mustache", model)
							val linkModel = call.withDefaults(mapOf("reviewUrl" to reviewUrl))
							val linkDialogHtml = buildString {
								append("""<div id="review-dialog-container" hx-swap-oob="innerHTML">""")
								append(renderTemplate("partials/review-link-dialog.mustache", linkModel))
								append("</div>")
							}
							val toastKey = if (reviewInviteMailer == null) {
								"review_toast_created_no_email"
							} else {
								"review_toast_email_failed"
							}
							respondHtmlWithToast(
								content = panelHtml + linkDialogHtml,
								message = call.msg(toastKey),
								toast = if (reviewInviteMailer == null) Toast.Info else Toast.Warning,
							)
						}
					}
				}
			}

			hx.post("/{reviewId}/revoke") {
				val session = call.sessions.requireUser()
				val project = call.resolveProject(projectsRepository, session.userId) ?: return@post
				val projectId = ProjectId(project.uuid)
				val reviewId = call.parameters["reviewId"]?.toLongOrNull()
				if (reviewId == null) {
					call.respond(HttpStatusCode.BadRequest)
					return@post
				}

				val review = (reviewRepository.getReview(session.userId, reviewId)
					as? ServerResult.Success)?.data
				val result = reviewRepository.revokeReview(session.userId, reviewId)

				val model = call.reviewPanelModel(reviewRepository, project.name, session.userId, projectId)
				when (result) {
					is ServerResult.Success -> respondTemplateWithToast(
						templatePath = "partials/review-panel.mustache",
						model = model,
						message = call.msg("review_toast_revoked", review?.label ?: ""),
						toast = Toast.Info,
					)

					is ServerResult.Failure -> respondTemplateWithToast(
						templatePath = "partials/review-panel.mustache",
						model = model,
						message = result.displayMessageText(call) ?: result.error,
						toast = Toast.Error,
					)
				}
			}

			// Author's review page: read the submitted suggestions, accept/reject, commit.
			get("/{reviewId}") {
				val session = call.sessions.requireUser()
				val project = call.resolveProject(projectsRepository, session.userId) ?: return@get
				val reviewId = call.parameters["reviewId"]?.toLongOrNull()
				if (reviewId == null) {
					call.respond(HttpStatusCode.BadRequest)
					return@get
				}

				val reviewResult = reviewRepository.getReview(session.userId, reviewId)
				if (reviewResult is ServerResult.Failure) {
					call.respondReviewError(
						reviewResult.displayMessageText(call) ?: call.msg("api_review_error_not_found"),
					)
					return@get
				}
				val review = (reviewResult as ServerResult.Success).data
				if (!call.reviewMatchesUrlProject(projectDao, review)) {
					call.respondReviewError(call.msg("api_review_error_not_found"))
					return@get
				}
				if (review.status != ReviewStatus.SUBMITTED && review.status != ReviewStatus.RESOLVED) {
					call.respondReviewError(call.msg("api_review_commit_error_not_submitted"))
					return@get
				}
				val resolved = review.status == ReviewStatus.RESOLVED

				val scenes = reviewRepository.getReviewScenes(review)
				val suggestionsByScene = reviewRepository.getSuggestionsByScene(review)
				val appData = ReviewAppData(
					token = "",
					locked = true,
					mode = "author",
					canDecide = !resolved,
					basePath = "/story/${ProjectName.formatForUrl(project.name)}/reviews/${review.id}",
					scenes = buildSceneDtos(scenes, suggestionsByScene),
				)

				val model = call.withDefaults(
					mapOf(
						"page_stylesheet" to "/assets/css/review.css",
						"page_script" to "/assets/js/review.js",
						"page_pre_script" to "/assets/js/review-logic.js",
						"projectName" to project.name,
						"projectNameForUrl" to ProjectName.formatForUrl(project.name),
						"reviewerLine" to call.msg("review_author_suggestions_from", review.label),
						"reviewerEmail" to review.reviewerEmail,
						"submittedLine" to call.msg(
							"review_author_submitted_on",
							formatReviewDate(review.submittedAt ?: review.created),
						),
						"resolved" to resolved,
						"resolvedLine" to if (resolved) {
							call.msg(
								"review_author_resolved_banner",
								formatReviewDate(review.resolvedAt ?: review.created),
							)
						} else {
							""
						},
						"sceneCount" to scenes.size,
						"reviewData" to jsonIsland(
							reviewJson.encodeToString(ReviewAppData.serializer(), appData)
						),
						"reviewStrings" to call.buildReviewStringsJson(),
					)
				)
				call.respond(MustacheContent("review-author.mustache", model))
			}

			// Author accepts/rejects a suggestion (or resolves a comment). Responds the updated DTO.
			post("/{reviewId}/suggestions/{suggestionId}/status") {
				val session = call.sessions.requireUser()
				val reviewId = call.parameters["reviewId"]?.toLongOrNull()
				val suggestionId = call.parameters["suggestionId"]?.toLongOrNull()
				val status = ReviewSuggestionStatus.fromString(call.receiveParameters()["status"])
				if (reviewId == null || suggestionId == null || status == null) {
					call.respondJsonError(HttpStatusCode.BadRequest, call.msg("api_review_suggestion_error_invalid"))
					return@post
				}
				if (!call.reviewBelongsToUrlProject(reviewRepository, projectDao, session.userId, reviewId)) {
					call.respondJsonError(HttpStatusCode.NotFound, call.msg("api_review_error_not_found"))
					return@post
				}

				val result = reviewRepository.setSuggestionStatus(session.userId, reviewId, suggestionId, status)
				when (result) {
					is ServerResult.Success -> call.respondText(
						reviewJson.encodeToString(ReviewSuggestionDto.serializer(), result.data.toDto()),
						ContentType.Application.Json,
					)

					is ServerResult.Failure -> call.respondJsonError(
						HttpStatusCode.Conflict,
						result.displayMessageText(call) ?: call.msg("api_review_suggestion_error_invalid"),
					)
				}
			}

			// Apply the accepted suggestions: mint the reviewed drafts, update clean scenes, resolve.
			post("/{reviewId}/commit") {
				val session = call.sessions.requireUser()
				val reviewId = call.parameters["reviewId"]?.toLongOrNull()
				if (reviewId == null) {
					call.respondJsonError(HttpStatusCode.BadRequest, call.msg("api_review_error_not_found"))
					return@post
				}
				if (!call.reviewBelongsToUrlProject(reviewRepository, projectDao, session.userId, reviewId)) {
					call.respondJsonError(HttpStatusCode.NotFound, call.msg("api_review_error_not_found"))
					return@post
				}

				when (val result = reviewRepository.commitReview(session.userId, reviewId)) {
					is ServerResult.Success -> call.respondText(
						reviewJson.encodeToString(
							ReviewCommitResultDto.serializer(),
							ReviewCommitResultDto(
								draftName = result.data.draftName,
								scenes = result.data.scenes.map {
									ReviewCommitSceneDto(it.sceneName, it.outcome.toStringId())
								},
							),
						),
						ContentType.Application.Json,
					)

					is ServerResult.Failure -> call.respondJsonError(
						HttpStatusCode.Conflict,
						result.displayMessageText(call) ?: call.msg("api_review_commit_error_resolved"),
					)
				}
			}
		}
	}

	// Public, tokenized reviewer page — no account, no session.
	route("/review/{token}") {
		get {
			val token = call.parameters["token"]
			if (token.isNullOrBlank()) {
				call.respondReviewError(call.msg("api_review_token_error_invalid"))
				return@get
			}

			// One side-effect-free lookup, reused for the owner redirect, the
			// friendly error kinds, and the open transition below.
			val found = reviewRepository.findReviewByToken(token)

			// The author following their own reviewer link gets their side of the
			// review, not the reviewer's — and the request isn't marked opened.
			val session = call.sessions.get<UserSession>()
			if (session != null && found != null && found.userId == session.userId) {
				val project = projectDao.getProjectByRowId(found.projectId)
				if (project != null) {
					val projectUrl = ProjectName.formatForUrl(project.name)
					val submitted = found.status == ReviewStatus.SUBMITTED ||
						found.status == ReviewStatus.RESOLVED
					call.respondRedirect(
						if (submitted) "/story/$projectUrl/reviews/${found.id}"
						else "/story/$projectUrl"
					)
					return@get
				}
			}

			// Resolve the failure kind up front (no side effects) so the editor gets
			// a specific, friendly page instead of a generic error.
			val now = clock.now()
			when {
				found == null -> {
					call.respondReviewErrorPage("invalid")
					return@get
				}

				found.status == ReviewStatus.CANCELED -> {
					call.respondReviewErrorKind(found, "revoked", accountsRepository, projectDao)
					return@get
				}

				found.expires != null && now > found.expires -> {
					call.respondReviewErrorKind(found, "expired", accountsRepository, projectDao)
					return@get
				}
			}

			val opened = reviewRepository.markReviewOpened(found)
			val review = opened.request
			val firstOpen = opened.firstOpen
			val scenes = reviewRepository.getReviewScenes(review)
			val suggestionsByScene = reviewRepository.getSuggestionsByScene(review)
			val project = projectDao.getProjectByRowId(review.projectId)
			val account = accountsRepository.getAccount(review.userId)
			val authorName = account.pen_name?.ifBlank { null } ?: "the author"
			val locked = review.status == ReviewStatus.SUBMITTED ||
				review.status == ReviewStatus.RESOLVED

			val appData = ReviewAppData(
				token = token,
				locked = locked,
				firstOpen = firstOpen,
				authorName = authorName,
				scenes = buildSceneDtos(scenes, suggestionsByScene),
			)

			val hasExpiry = review.expires != null
			val model = call.withDefaults(
				mapOf(
					"page_stylesheet" to "/assets/css/review.css",
					"page_script" to "/assets/js/review.js",
					"page_pre_script" to "/assets/js/review-logic.js",
					"projectName" to (project?.name ?: ""),
					"authorName" to authorName,
					"reviewerEmail" to review.reviewerEmail,
					"note" to (review.note ?: ""),
					"hasNote" to (review.note.isNullOrBlank().not()),
					"hasExpiry" to hasExpiry,
					"expiryLine" to if (hasExpiry) {
						call.msg("review_page_expires", formatReviewDate(review.expires))
					} else {
						""
					},
					"locked" to locked,
					"sceneCount" to scenes.size,
					"reviewData" to jsonIsland(
						reviewJson.encodeToString(ReviewAppData.serializer(), appData)
					),
					"reviewStrings" to call.buildReviewStringsJson(),
				)
			)
			call.respond(MustacheContent("review.mustache", model))
		}

		// Create a suggestion. Form-encoded; responds JSON { id } so the client can track it.
		post("/suggestions") {
			val token = call.parameters["token"].orEmpty()
			val form = call.receiveParameters()
			val reviewSceneId = form["reviewSceneId"]?.toLongOrNull()
			val type = ReviewSuggestionType.fromString(form["type"])
			val paragraph = form["paragraph"]?.toIntOrNull()
			val start = form["start"]?.toIntOrNull()
			val end = form["end"]?.toIntOrNull()
			if (reviewSceneId == null || type == null || paragraph == null || start == null || end == null) {
				call.respondJsonError(HttpStatusCode.BadRequest, call.msg("api_review_suggestion_error_invalid"))
				return@post
			}

			val result = reviewRepository.addSuggestion(
				token = token,
				reviewSceneId = reviewSceneId,
				type = type,
				paragraph = paragraph,
				start = start,
				end = end,
				replacement = form["replacement"],
				reason = form["reason"],
			)
			when (result) {
				is ServerResult.Success -> call.respondText(
					reviewJson.encodeToString(ReviewSuggestionDto.serializer(), result.data.toDto()),
					ContentType.Application.Json,
				)

				is ServerResult.Failure -> call.respondJsonError(
					HttpStatusCode.Conflict,
					result.displayMessageText(call) ?: call.msg("api_review_suggestion_error_invalid"),
				)
			}
		}

		// Edit an existing suggestion's replacement/reason. Responds the updated DTO.
		post("/suggestions/{id}") {
			val token = call.parameters["token"].orEmpty()
			val id = call.parameters["id"]?.toLongOrNull()
			if (id == null) {
				call.respond(HttpStatusCode.BadRequest)
				return@post
			}
			val form = call.receiveParameters()
			val result = reviewRepository.updateSuggestion(
				token = token,
				suggestionId = id,
				replacement = form["replacement"],
				reason = form["reason"],
			)
			when (result) {
				is ServerResult.Success -> call.respondText(
					reviewJson.encodeToString(ReviewSuggestionDto.serializer(), result.data.toDto()),
					ContentType.Application.Json,
				)

				is ServerResult.Failure -> call.respondJsonError(
					HttpStatusCode.Conflict,
					result.displayMessageText(call) ?: call.msg("api_review_suggestion_error_invalid"),
				)
			}
		}

		delete("/suggestions/{id}") {
			val token = call.parameters["token"].orEmpty()
			val id = call.parameters["id"]?.toLongOrNull()
			if (id == null) {
				call.respond(HttpStatusCode.BadRequest)
				return@delete
			}
			val result = reviewRepository.deleteSuggestion(token, id)
			when (result) {
				is ServerResult.Success -> call.respond(HttpStatusCode.NoContent)
				is ServerResult.Failure -> call.respondJsonError(
					HttpStatusCode.Conflict,
					result.displayMessageText(call) ?: call.msg("api_review_suggestion_error_invalid"),
				)
			}
		}

		// Editor toggles a scene's read/done flag — progress the author can see.
		post("/scenes/{id}/done") {
			val token = call.parameters["token"].orEmpty()
			val id = call.parameters["id"]?.toLongOrNull()
			val done = call.receiveParameters()["done"]?.toBooleanStrictOrNull()
			if (id == null || done == null) {
				call.respondJsonError(HttpStatusCode.BadRequest, call.msg("api_review_suggestion_error_invalid"))
				return@post
			}
			when (val result = reviewRepository.setSceneDone(token, id, done)) {
				is ServerResult.Success -> call.respondText("""{"ok":true}""", ContentType.Application.Json)
				is ServerResult.Failure -> call.respondJsonError(
					HttpStatusCode.Conflict,
					result.displayMessageText(call) ?: call.msg("api_review_suggestion_error_invalid"),
				)
			}
		}

		post("/submit") {
			val token = call.parameters["token"].orEmpty()
			when (val result = reviewRepository.submitReview(token)) {
				is ServerResult.Success -> {
					notifyAuthorOfSubmission(
						call, result.data,
						reviewRepository, accountsRepository, projectDao, reviewSubmittedMailer,
					)
					call.respondText("""{"ok":true}""", ContentType.Application.Json)
				}

				is ServerResult.Failure -> call.respondJsonError(
					HttpStatusCode.Conflict,
					result.displayMessageText(call) ?: call.msg("api_review_token_error_invalid"),
				)
			}
		}
	}
}

@Serializable
private data class ReviewAppData(
	val token: String,
	val locked: Boolean,
	val mode: String = "reviewer",
	/** Author page only: whether suggestions can still be accepted/rejected (not yet resolved). */
	val canDecide: Boolean = false,
	/** Author page only: URL prefix for the author's status/commit endpoints. */
	val basePath: String = "",
	/** Editor page only: true on the link's very first open — shows the welcome. */
	val firstOpen: Boolean = false,
	/** Editor page only: for personalizing the dialogs. */
	val authorName: String = "",
	val scenes: List<ReviewSceneDto>,
)

@Serializable
private data class ReviewSceneDto(
	val reviewSceneId: Long,
	val sceneId: Int,
	val name: String,
	val done: Boolean = false,
	val paragraphs: List<ReviewParaDto>,
	val suggestions: List<ReviewSuggestionDto>,
)

@Serializable
private data class ReviewParaDto(val index: Int, val text: String)

@Serializable
private data class ReviewSuggestionDto(
	val id: Long,
	val reviewSceneId: Long,
	val type: String,
	val paragraph: Int,
	val start: Int,
	val end: Int,
	val replacement: String?,
	val reason: String?,
	val status: String,
)

@Serializable
private data class ReviewCommitSceneDto(val sceneName: String, val outcome: String)

@Serializable
private data class ReviewCommitResultDto(
	val draftName: String,
	val scenes: List<ReviewCommitSceneDto>,
)

private fun ReviewSuggestion.toDto() = ReviewSuggestionDto(
	id = id,
	reviewSceneId = reviewSceneId,
	type = type.toStringId(),
	paragraph = paragraph,
	start = startOffset,
	end = endOffset,
	replacement = replacementText,
	reason = reason,
	status = status.toStringId(),
)

private fun buildSceneDtos(
	scenes: List<ReviewScene>,
	suggestionsByScene: Map<Long, List<ReviewSuggestion>>,
): List<ReviewSceneDto> = scenes.map { scene ->
	val paragraphs = ReviewParagraphs.split(scene.snapshotContent)
		.mapIndexedNotNull { i, text ->
			if (text.isBlank()) null else ReviewParaDto(i, text)
		}
	ReviewSceneDto(
		reviewSceneId = scene.id,
		sceneId = scene.sceneId,
		name = scene.sceneName,
		done = scene.reviewerDone,
		paragraphs = paragraphs,
		suggestions = (suggestionsByScene[scene.id] ?: emptyList()).map { it.toDto() },
	)
}

/** Email the author when their reviewer submits; a mail failure never fails the submit. */
private suspend fun notifyAuthorOfSubmission(
	call: ApplicationCall,
	review: ReviewRequest,
	reviewRepository: ReviewRepository,
	accountsRepository: AccountsRepository,
	projectDao: ProjectDao,
	mailer: ReviewSubmittedMailer?,
) {
	if (mailer == null) return
	runCatching {
		val account = accountsRepository.getAccount(review.userId)
		val project = projectDao.getProjectByRowId(review.projectId) ?: return
		val reviewUrl = call.constructBaseUrl() +
			"/story/${ProjectName.formatForUrl(project.name)}/reviews/${review.id}"
		mailer.sendSubmittedNotice(
			toEmail = account.email,
			reviewerLabel = review.label,
			projectName = project.name,
			suggestionCount = reviewRepository.countSuggestions(review.id),
			reviewUrl = reviewUrl,
			locale = call.getLocale(),
		)
	}
}

private val reviewJson = kotlinx.serialization.json.Json { encodeDefaults = true }

/**
 * JSON destined for an inline <script type="application/json"> island. Escapes `<`
 * so user content containing `</script>` can't break out of the element.
 */
private fun jsonIsland(json: String): String = json.replace("<", "\\u003c")

/** Editor UI strings as a JSON island, properly JSON-escaped (Mustache would HTML-escape). */
private suspend fun ApplicationCall.buildReviewStringsJson(): String {
	val keys = mapOf(
		"reword" to "review_action_reword",
		"delete" to "review_action_delete",
		"insert" to "review_action_insert",
		"insertHere" to "review_action_insert_here",
		"comment" to "review_action_comment",
		"save" to "review_action_save",
		"cancel" to "review_dialog_cancel",
		"remove" to "review_action_remove",
		"removeConfirm" to "review_action_remove_confirm",
		"edit" to "review_action_edit",
		"replacementLabel" to "review_action_replacement_label",
		"insertLabel" to "review_action_insert_label",
		"commentLabel" to "review_action_comment_label",
		"reasonPlaceholder" to "review_action_reason_placeholder",
		"reasonLabel" to "review_action_reason_label",
		"commentPlaceholder" to "review_action_comment_placeholder",
		"typePlaceholder" to "review_action_type_placeholder",
		"suggestionsTitle" to "review_suggestions_title",
		"noSuggestions" to "review_no_suggestions",
		"scenesTitle" to "review_scenes_title",
		"submitTitle" to "review_submit_confirm_title",
		"submitBody" to "review_submit_confirm_body",
		"submitTallyOne" to "review_submit_tally_one",
		"submitTallyMany" to "review_submit_tally_many",
		"submitAction" to "review_submit_confirm_action",
		"saveFailed" to "review_save_failed",
		"accept" to "review_accept",
		"reject" to "review_reject",
		"resolve" to "review_resolve",
		"undo" to "review_undo",
		"acceptAll" to "review_accept_all",
		"rejectAll" to "review_reject_all",
		"chipAccepted" to "review_chip_accepted",
		"chipRejected" to "review_chip_rejected",
		"chipResolved" to "review_chip_resolved",
		"commitTitle" to "review_commit_confirm_title",
		"commitBody" to "review_commit_confirm_body",
		"commitTallyAccepted" to "review_commit_tally_accepted",
		"commitTallyRejected" to "review_commit_tally_rejected",
		"commitTallyPending" to "review_commit_tally_pending",
		"commitPendingWarning" to "review_commit_pending_warning",
		"commitAction" to "review_commit_confirm_action",
		"commitFailed" to "review_commit_failed",
		"commitResultTitle" to "review_commit_result_title",
		"commitResultDraft" to "review_commit_result_draft",
		"outcomeApplied" to "review_commit_outcome_applied",
		"outcomeDiverged" to "review_commit_outcome_diverged",
		"outcomeUnchanged" to "review_commit_outcome_unchanged",
		"outcomeSceneMissing" to "review_commit_outcome_scene_missing",
		"commitDone" to "review_commit_done",
		"doneMark" to "review_done_mark",
		"doneMarked" to "review_done_marked",
		"submit" to "review_submit",
		"sceneTallyOne" to "review_meta_one_scene",
		"sceneTallyMany" to "review_meta_scenes",
		"welcomeTitle" to "review_welcome_title",
		"welcomeIntro" to "review_welcome_intro",
		"welcomeHow" to "review_welcome_how",
		"welcomeSubmitNote" to "review_welcome_submit_note",
		"welcomeAction" to "review_welcome_action",
	)
	val strings = buildMap {
		for ((jsonKey, msgKey) in keys) put(jsonKey, msg(msgKey))
	}
	return jsonIsland(
		reviewJson.encodeToString(
			kotlinx.serialization.builtins.MapSerializer(
				kotlinx.serialization.serializer<String>(),
				kotlinx.serialization.serializer<String>(),
			),
			strings,
		)
	)
}

@Serializable
private data class ReviewErrorDto(val error: String)

private suspend fun ApplicationCall.respondJsonError(status: HttpStatusCode, message: String) {
	respondText(
		reviewJson.encodeToString(ReviewErrorDto.serializer(), ReviewErrorDto(message)),
		ContentType.Application.Json,
		status,
	)
}

private suspend fun ApplicationCall.resolveProject(
	projectsRepository: ProjectsRepository,
	userId: Long,
): com.darkrockstudios.apps.hammer.projects.ProjectWithSyncDate? {
	val projectNameParam = parameters["projectName"]
	if (projectNameParam.isNullOrBlank()) {
		respond(HttpStatusCode.BadRequest)
		return null
	}
	val project = projectsRepository.findProjectByUrlName(userId, projectNameParam)
	if (project == null) {
		respond(HttpStatusCode.NotFound)
	}
	return project
}

/**
 * True if [review] actually belongs to the project named in the URL. The review
 * is already scoped to the session user; this stops one of the user's reviews
 * from rendering or being acted on under a different project's URL.
 */
private suspend fun ApplicationCall.reviewMatchesUrlProject(
	projectDao: ProjectDao,
	review: ReviewRequest,
): Boolean {
	val urlName = parameters["projectName"] ?: return false
	val reviewProject = projectDao.getProjectByRowId(review.projectId) ?: return false
	// urlName arrives percent-decoded; match the exact name, then the legacy dash-slug form.
	return reviewProject.name == urlName ||
		reviewProject.name == ProjectName.legacyUrlNameToName(urlName)
}

/** As [reviewMatchesUrlProject] but for routes that only have the review id. */
private suspend fun ApplicationCall.reviewBelongsToUrlProject(
	reviewRepository: ReviewRepository,
	projectDao: ProjectDao,
	userId: Long,
	reviewId: Long,
): Boolean {
	val review = (reviewRepository.getReview(userId, reviewId) as? ServerResult.Success)?.data ?: return false
	return reviewMatchesUrlProject(projectDao, review)
}

// 410 Gone rather than 404: the StatusPages plugin swallows non-API 404s and
// replaces them with the generic notfound page.
private suspend fun ApplicationCall.respondReviewError(message: String) {
	respondReviewErrorModel(msg("review_page_error_title"), message, "fa-link-slash")
}

/** A friendly, kind-specific error page; revoked/expired name the author and story. */
private suspend fun ApplicationCall.respondReviewErrorKind(
	review: ReviewRequest,
	kind: String,
	accountsRepository: AccountsRepository,
	projectDao: ProjectDao,
) {
	val authorName = runCatching {
		accountsRepository.getAccount(review.userId).pen_name?.ifBlank { null }
	}.getOrNull() ?: msg("review_error_the_author")
	val projectName = projectDao.getProjectByRowId(review.projectId)?.name
	if (projectName == null) {
		respondReviewErrorPage("invalid")
		return
	}
	respondReviewErrorModel(
		title = msg("review_error_${kind}_title"),
		body = msg("review_error_${kind}_body", authorName, projectName),
		icon = if (kind == "expired") "fa-hourglass-end" else "fa-ban",
	)
}

private suspend fun ApplicationCall.respondReviewErrorPage(kind: String) {
	respondReviewErrorModel(
		title = msg("review_error_${kind}_title"),
		body = msg("review_error_${kind}_body"),
		icon = "fa-link-slash",
	)
}

private suspend fun ApplicationCall.respondReviewErrorModel(title: String, body: String, icon: String) {
	val model = withDefaults(
		mapOf(
			"page_stylesheet" to "/assets/css/review.css",
			"errorTitle" to title,
			"errorMessage" to body,
			"errorIcon" to icon,
		)
	)
	respond(HttpStatusCode.Gone, MustacheContent("review-error.mustache", model))
}

private fun ApplicationCall.constructBaseUrl(): String = publicBaseUrl()

private fun ApplicationCall.constructReviewUrl(token: String): String =
	"${constructBaseUrl()}/review/$token"

internal fun formatReviewDate(instant: Instant): String =
	DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH)
		.withZone(ZoneOffset.UTC)
		.format(instant.toJavaInstant())

/** Card models for the Editorial Reviews panel, newest first, revoked requests hidden. */
suspend fun ApplicationCall.reviewCards(
	reviewRepository: ReviewRepository,
	userId: Long,
	projectId: ProjectId,
): List<Map<String, Any?>> {
	val reviews = (reviewRepository.getReviewsForProject(userId, projectId)
		as? ServerResult.Success)?.data ?: emptyList()

	val now = kotlin.time.Clock.System.now()
	val visible = reviews.filter { it.status != ReviewStatus.CANCELED }

	// One grouped query for every card's scene progress, instead of one per card.
	val progressById = reviewRepository.getSceneProgress(visible.map { it.id })

	return visible.map { review -> reviewCardModel(review, now, progressById[review.id]) }
}

/** Builds the model for the Editorial Reviews sidebar panel partial. */
suspend fun ApplicationCall.reviewPanelModel(
	reviewRepository: ReviewRepository,
	projectName: String,
	userId: Long,
	projectId: ProjectId,
): Map<String, Any?> {
	val reviewModels = reviewCards(reviewRepository, userId, projectId)
	return withDefaults(
		mapOf(
			"projectNameForUrl" to ProjectName.formatForUrl(projectName),
			"reviews" to reviewModels,
			"hasReviews" to reviewModels.isNotEmpty(),
		)
	)
}

private suspend fun ApplicationCall.reviewCardModel(
	review: ReviewRequest,
	now: Instant,
	progress: Pair<Long, Long>?,
): Map<String, Any?> {
	val isExpired = review.expires != null && now > review.expires &&
		review.status != ReviewStatus.SUBMITTED && review.status != ReviewStatus.RESOLVED

	val statusKey = if (isExpired) "expired" else review.status.toStringId()
	val statusLabel = msg("review_status_$statusKey")

	val metaParts = mutableListOf<String>()
	metaParts += msg("review_meta_sent", formatReviewDate(review.created))
	when {
		review.submittedAt != null ->
			metaParts += msg("review_meta_submitted", formatReviewDate(review.submittedAt))

		review.openedAt != null ->
			metaParts += msg("review_meta_opened", formatReviewDate(review.openedAt))

		else -> metaParts += msg("review_meta_not_opened")
	}
	if (review.expires != null && !isExpired &&
		review.status != ReviewStatus.SUBMITTED && review.status != ReviewStatus.RESOLVED
	) {
		metaParts += msg("review_meta_expires", formatReviewDate(review.expires))
	}

	// While the editor is working, show how far through the scenes they are
	val showProgress = !isExpired &&
		(review.status == ReviewStatus.OPENED || review.status == ReviewStatus.IN_PROGRESS)
	var progressPct = 0
	if (showProgress) {
		val (done, total) = progress ?: (0L to 0L)
		metaParts += msg("review_meta_progress", done, total)
		progressPct = if (total > 0) (done * 100 / total).toInt() else 0
	}

	val canRevoke = !isExpired && review.status in setOf(
		ReviewStatus.SENT, ReviewStatus.OPENED, ReviewStatus.IN_PROGRESS
	)

	return mapOf(
		"id" to review.id,
		"label" to review.label,
		"email" to review.reviewerEmail,
		"meta" to metaParts.joinToString(" · "),
		"status" to statusLabel,
		"statusClass" to "review-card__status--$statusKey",
		"isSubmitted" to (review.status == ReviewStatus.SUBMITTED),
		"isResolved" to (review.status == ReviewStatus.RESOLVED),
		"hasProgress" to showProgress,
		"progressPct" to progressPct,
		"canRevoke" to canRevoke,
	)
}
