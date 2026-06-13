package com.darkrockstudios.apps.hammer.review

import kotlin.time.Instant

enum class ReviewStatus {
	SENT,
	OPENED,
	IN_PROGRESS,
	SUBMITTED,
	RESOLVED,
	CANCELED;

	fun toStringId(): String = name.lowercase()

	companion object {
		fun fromString(string: String?): ReviewStatus? =
			entries.find { it.name.equals(string?.trim(), ignoreCase = true) }
	}
}

enum class ReviewSuggestionType {
	DELETE,
	REWORD,
	INSERT,
	COMMENT;

	fun toStringId(): String = name.lowercase()

	companion object {
		fun fromString(string: String?): ReviewSuggestionType? =
			entries.find { it.name.equals(string?.trim(), ignoreCase = true) }
	}
}

enum class ReviewSuggestionStatus {
	PENDING,
	ACCEPTED,
	REJECTED,
	RESOLVED;

	fun toStringId(): String = name.lowercase()

	companion object {
		fun fromString(string: String?): ReviewSuggestionStatus? =
			entries.find { it.name.equals(string?.trim(), ignoreCase = true) }
	}
}

data class ReviewRequest(
	val id: Long,
	val userId: Long,
	val projectId: Long,
	val reviewerEmail: String,
	val label: String,
	val note: String?,
	val status: ReviewStatus,
	val created: Instant,
	val expires: Instant?,
	val openedAt: Instant?,
	val lastActiveAt: Instant?,
	val submittedAt: Instant?,
	val resolvedAt: Instant?,
)

/** [snapshotContent] is the decrypted markdown snapshot taken at request time. */
data class ReviewScene(
	val id: Long,
	val reviewRequestId: Long,
	val sceneId: Int,
	val draftId: Int,
	val sceneName: String,
	val sceneOrder: Int,
	val snapshotContent: String,
	val reviewerDone: Boolean,
)

/** What happened to one reviewed scene when the author committed the review. */
enum class ReviewCommitOutcome {
	/** Working scene still matched the snapshot; it now carries the revised text. */
	APPLIED,

	/** Scene changed since the review was sent; only the draft was created. */
	DIVERGED,

	/** No accepted edits changed this scene's text. */
	UNCHANGED,

	/** The scene no longer exists in the project. */
	SCENE_MISSING;

	fun toStringId(): String = name.lowercase()
}

data class ReviewCommitScene(
	val sceneId: Int,
	val sceneName: String,
	val outcome: ReviewCommitOutcome,
)

data class ReviewCommitResult(
	val draftName: String,
	val scenes: List<ReviewCommitScene>,
)

data class ReviewSuggestion(
	val id: Long,
	val reviewSceneId: Long,
	val type: ReviewSuggestionType,
	val paragraph: Int,
	val startOffset: Int,
	val endOffset: Int,
	val originalText: String,
	val replacementText: String?,
	val reason: String?,
	val status: ReviewSuggestionStatus,
	val created: Instant,
	val updated: Instant,
)
