package com.darkrockstudios.apps.hammer.review

/**
 * Applies a review's accepted suggestions to a scene snapshot, producing the
 * revised content. Mirrors `applyAccepted` in review-logic.js: within each
 * paragraph the accepted edits are spliced right-to-left so earlier offsets
 * stay valid; comments and pending/rejected suggestions leave the text alone.
 */
object ReviewApplier {

	fun applyAccepted(snapshot: String, suggestions: List<ReviewSuggestion>): String {
		val paragraphs = ReviewParagraphs.split(snapshot).toMutableList()
		suggestions
			.filter { it.status == ReviewSuggestionStatus.ACCEPTED && it.type != ReviewSuggestionType.COMMENT }
			.groupBy { it.paragraph }
			.forEach { (index, edits) ->
				val text = paragraphs.getOrNull(index) ?: return@forEach
				paragraphs[index] = applyToParagraph(text, edits)
			}
		return paragraphs.joinToString("\n")
	}

	private fun applyToParagraph(text: String, edits: List<ReviewSuggestion>): String {
		var result = text
		edits
			.sortedWith(compareByDescending<ReviewSuggestion> { it.startOffset }.thenByDescending { it.endOffset })
			.forEach { edit ->
				if (edit.startOffset < 0 || edit.endOffset > result.length || edit.startOffset > edit.endOffset) {
					return@forEach
				}
				val replacement = when (edit.type) {
					ReviewSuggestionType.DELETE -> ""
					else -> edit.replacementText.orEmpty()
				}
				result = result.take(edit.startOffset) + replacement + result.drop(edit.endOffset)
			}
		return result
	}
}
