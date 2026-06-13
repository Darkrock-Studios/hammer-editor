package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.database.ProjectDao
import com.darkrockstudios.apps.hammer.frontend.utils.*
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.review.ReviewInviteMailer
import com.darkrockstudios.apps.hammer.review.ReviewParagraphs
import com.darkrockstudios.apps.hammer.review.ReviewRepository
import com.darkrockstudios.apps.hammer.review.ReviewRequest
import com.darkrockstudios.apps.hammer.review.ReviewStatus
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

			val result = reviewRepository.openReviewByToken(token)
			when (result) {
				is ServerResult.Failure -> {
					call.respondReviewError(
						result.displayMessageText(call) ?: call.msg("api_review_token_error_invalid"),
					)
				}

				is ServerResult.Success -> {
					val review = result.data
					val scenes = reviewRepository.getReviewScenes(review)
					val suggestionsByScene = reviewRepository.getSuggestionsByScene(review)
					val project = projectDao.getProjectByRowId(review.projectId)
					val account = accountsRepository.getAccount(review.userId)
					val authorName = account.pen_name?.ifBlank { null } ?: "the author"
					val locked = review.status == ReviewStatus.SUBMITTED ||
						review.status == ReviewStatus.RESOLVED

					val sceneDtos = scenes.map { scene ->
						val paragraphs = ReviewParagraphs.split(scene.snapshotContent)
							.mapIndexedNotNull { i, text ->
								if (text.isBlank()) null else ReviewParaDto(i, text)
							}
						val suggestions = (suggestionsByScene[scene.id] ?: emptyList()).map { it.toDto() }
						ReviewSceneDto(
							reviewSceneId = scene.id,
							sceneId = scene.sceneId,
							name = scene.sceneName,
							paragraphs = paragraphs,
							suggestions = suggestions,
						)
					}
					val appData = ReviewAppData(
						token = token,
						locked = locked,
						scenes = sceneDtos,
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
							"reviewData" to reviewJson.encodeToString(ReviewAppData.serializer(), appData),
						)
					)
					call.respond(MustacheContent("review.mustache", model))
				}
			}
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

		post("/submit") {
			val token = call.parameters["token"].orEmpty()
			when (val result = reviewRepository.submitReview(token)) {
				is ServerResult.Success -> call.respondText("""{"ok":true}""", ContentType.Application.Json)
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
	val scenes: List<ReviewSceneDto>,
)

@Serializable
private data class ReviewSceneDto(
	val reviewSceneId: Long,
	val sceneId: Int,
	val name: String,
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
)

private fun com.darkrockstudios.apps.hammer.review.ReviewSuggestion.toDto() = ReviewSuggestionDto(
	id = id,
	reviewSceneId = reviewSceneId,
	type = type.toStringId(),
	paragraph = paragraph,
	start = startOffset,
	end = endOffset,
	replacement = replacementText,
	reason = reason,
)

private val reviewJson = kotlinx.serialization.json.Json { encodeDefaults = true }

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
	val projectName = ProjectName.decodeFromUrl(projectNameParam)
	val project = projectsRepository.getProjectByName(userId, projectName)
	if (project == null) {
		respond(HttpStatusCode.NotFound)
	}
	return project
}

// 410 Gone rather than 404: the StatusPages plugin swallows non-API 404s and
// replaces them with the generic notfound page.
private suspend fun ApplicationCall.respondReviewError(message: String) {
	val model = withDefaults(
		mapOf(
			"page_stylesheet" to "/assets/css/review.css",
			"errorMessage" to message,
		)
	)
	respond(HttpStatusCode.Gone, MustacheContent("review-error.mustache", model))
}

private fun ApplicationCall.constructReviewUrl(token: String): String {
	val scheme = request.origin.scheme
	val host = request.host()
	val port = request.port()
	return if (port == 80 || port == 443) {
		"$scheme://$host/review/$token"
	} else {
		"$scheme://$host:$port/review/$token"
	}
}

internal fun formatReviewDate(instant: Instant): String =
	DateTimeFormatter.ofPattern("MMM d, yyyy")
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
	return reviews
		.filter { it.status != ReviewStatus.CANCELED }
		.map { review -> reviewCardModel(review, now) }
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
		"canRevoke" to canRevoke,
	)
}
