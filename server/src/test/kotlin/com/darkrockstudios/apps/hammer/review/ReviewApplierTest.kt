package com.darkrockstudios.apps.hammer.review

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ReviewApplierTest {

	private var nextId = 1L

	private fun suggestion(
		type: ReviewSuggestionType,
		paragraph: Int,
		start: Int,
		end: Int,
		replacement: String? = null,
		status: ReviewSuggestionStatus = ReviewSuggestionStatus.ACCEPTED,
	) = ReviewSuggestion(
		id = nextId++,
		reviewSceneId = 1L,
		type = type,
		paragraph = paragraph,
		startOffset = start,
		endOffset = end,
		originalText = "",
		replacementText = replacement,
		reason = null,
		status = status,
		created = Instant.fromEpochSeconds(0),
		updated = Instant.fromEpochSeconds(0),
	)

	@Test
	fun `accepted delete removes the range`() {
		val result = ReviewApplier.applyAccepted(
			"The quick brown fox",
			listOf(suggestion(ReviewSuggestionType.DELETE, 0, 4, 10)),
		)
		assertEquals("The brown fox", result)
	}

	@Test
	fun `accepted reword replaces the range`() {
		val result = ReviewApplier.applyAccepted(
			"The quick brown fox",
			listOf(suggestion(ReviewSuggestionType.REWORD, 0, 4, 9, replacement = "sluggish")),
		)
		assertEquals("The sluggish brown fox", result)
	}

	@Test
	fun `accepted insert splices at the caret`() {
		val result = ReviewApplier.applyAccepted(
			"The fox jumped.",
			listOf(suggestion(ReviewSuggestionType.INSERT, 0, 7, 7, replacement = " gracefully")),
		)
		assertEquals("The fox gracefully jumped.", result)
	}

	@Test
	fun `multiple edits in one paragraph apply right-to-left`() {
		val result = ReviewApplier.applyAccepted(
			"The quick brown fox jumps over the lazy dog",
			listOf(
				suggestion(ReviewSuggestionType.DELETE, 0, 4, 10), // "quick "
				suggestion(ReviewSuggestionType.REWORD, 0, 35, 39, replacement = "sleepy"), // "lazy"
				suggestion(ReviewSuggestionType.INSERT, 0, 19, 19, replacement = " happily"),
			),
		)
		assertEquals("The brown fox happily jumps over the sleepy dog", result)
	}

	@Test
	fun `edits land in their own paragraphs and newlines round-trip`() {
		val snapshot = "First paragraph.\n\nSecond paragraph."
		val result = ReviewApplier.applyAccepted(
			snapshot,
			listOf(
				suggestion(ReviewSuggestionType.REWORD, 0, 0, 5, replacement = "Opening"),
				suggestion(ReviewSuggestionType.DELETE, 2, 0, 7),
			),
		)
		assertEquals("Opening paragraph.\n\nparagraph.", result)
	}

	@Test
	fun `pending rejected and comments leave the text untouched`() {
		val snapshot = "The quick brown fox"
		val result = ReviewApplier.applyAccepted(
			snapshot,
			listOf(
				suggestion(ReviewSuggestionType.DELETE, 0, 4, 10, status = ReviewSuggestionStatus.PENDING),
				suggestion(ReviewSuggestionType.REWORD, 0, 10, 15, replacement = "red", status = ReviewSuggestionStatus.REJECTED),
				suggestion(ReviewSuggestionType.COMMENT, 0, 16, 19, status = ReviewSuggestionStatus.RESOLVED),
			),
		)
		assertEquals(snapshot, result)
	}

	@Test
	fun `same-position carets apply in display order`() {
		val result = ReviewApplier.applyAccepted(
			"abcdef",
			listOf(
				suggestion(ReviewSuggestionType.INSERT, 0, 3, 3, replacement = "A"),
				suggestion(ReviewSuggestionType.INSERT, 0, 3, 3, replacement = "B"),
			),
		)
		assertEquals("abcABdef", result)
	}

	@Test
	fun `out-of-range edits are skipped defensively`() {
		val snapshot = "Short"
		val result = ReviewApplier.applyAccepted(
			snapshot,
			listOf(
				suggestion(ReviewSuggestionType.DELETE, 0, 2, 99),
				suggestion(ReviewSuggestionType.DELETE, 7, 0, 3),
			),
		)
		assertEquals(snapshot, result)
	}
}
