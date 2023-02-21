package com.darkrockstudios.apps.hammer.common.data.text

// bold, italic, underline, strike through, and hyperlinks
sealed class Entity(
	val start: Int,
	val end: Int
) {
	class Bold(start: Int, end: Int) : Entity(start, end)
	class Underline(start: Int, end: Int) : Entity(start, end)
	class Italic(start: Int, end: Int) : Entity(start, end)
	class StrikeThrough(start: Int, end: Int) : Entity(start, end)
	class Hyperlink(val url: String, start: Int, end: Int) : Entity(start, end)
}

data class Result(val copy: String, val entities: List<Entity>)

fun parseMarkdown(source: String): Result {
	var boldIndex = Int.MIN_VALUE
	var underlineIndex = Int.MIN_VALUE
	var italicIndex = Int.MIN_VALUE
	var strikeThroughIndex = Int.MIN_VALUE
	var hyperlinkIndex = Int.MIN_VALUE

	val entities = mutableListOf<Entity>()

	val sb = StringBuilder()
	var index = 0
	var sanitizedIndex = 0

	while (index < source.length) {
		when (val value = source[index]) {
			Typo.BOLD.head() -> if (isBold(index, source)) {
				boldIndex = parseBold(boldIndex, sanitizedIndex, entities)
			}

			Typo.UNDERLINE.head(), Typo.ITALIC.head() -> {
				if (isUnderline(index, source)) {
					underlineIndex = parseUnderline(underlineIndex, sanitizedIndex, entities)
				} else {
					italicIndex = parseItalics(italicIndex, sanitizedIndex, entities)
				}
			}

			Typo.STRIKE_THROUGH.head() -> if (isStrikeThrough(index, source)) {
				strikeThroughIndex =
					parseStrikeThrough(strikeThroughIndex, sanitizedIndex, entities)
			}

			Typo.HYPERLINK.head() -> hyperlinkIndex = sanitizedIndex
			Typo.HYPERLINK.tail() -> {
				if (hyperlinkIndex != Int.MIN_VALUE) {
					index = parseHyperlink(hyperlinkIndex, index, sanitizedIndex, source, entities)
					hyperlinkIndex = Int.MIN_VALUE
				}
			}

			else -> {
				sb.append(value)
				sanitizedIndex += 1
			}
		}
		index++
	}

	return Result(sb.toString(), entities)
}

private enum class Typo(
	val openTag: String,
	val closeTag: String
) {
	BOLD("**", "**"),
	UNDERLINE("__", "__"),
	ITALIC("_", "_"),
	STRIKE_THROUGH("~~", "~~"),
	HYPERLINK("[", "]")
}

private fun Typo.head() = openTag.first()
private fun Typo.tail() = closeTag.last()

private fun isBold(index: Int, string: String) = isTypo(index, string, Typo.BOLD)

private fun isUnderline(index: Int, string: String) = isTypo(index, string, Typo.UNDERLINE)

private fun isStrikeThrough(index: Int, string: String) = isTypo(index, string, Typo.STRIKE_THROUGH)

private fun isTypo(index: Int, string: String, typo: Typo) =
	index + 1 <= string.length - 1 && string[index + 1] == typo.head()

private fun parseBold(index: Int, sanitizedIndex: Int, entities: MutableList<Entity>): Int =
	if (index == Int.MIN_VALUE) {
		sanitizedIndex
	} else {
		entities.add(Entity.Bold(index, sanitizedIndex))
		Int.MIN_VALUE
	}

private fun parseUnderline(index: Int, sanitizedIndex: Int, entities: MutableList<Entity>): Int =
	if (index == Int.MIN_VALUE) {
		sanitizedIndex
	} else {
		entities.add(Entity.Underline(index, sanitizedIndex))
		Int.MIN_VALUE
	}

private fun parseItalics(index: Int, sanitizedIndex: Int, entities: MutableList<Entity>): Int =
	if (index == Int.MIN_VALUE) {
		sanitizedIndex
	} else {
		entities.add(Entity.Italic(index, sanitizedIndex))
		Int.MIN_VALUE
	}

private fun parseStrikeThrough(
	index: Int,
	sanitizedIndex: Int,
	entities: MutableList<Entity>
): Int =
	if (index == Int.MIN_VALUE) {
		sanitizedIndex
	} else {
		entities.add(Entity.StrikeThrough(index, sanitizedIndex))
		Int.MIN_VALUE
	}

private fun parseHyperlink(
	openIndex: Int,
	currentIndex: Int,
	sanitizedIndex: Int,
	source: String,
	entities: MutableList<Entity>
): Int =
	// check if the next char is '('
	if (currentIndex + 1 <= source.length && source[currentIndex + 1] == '(') {
		val endOfUrlIndex = source.indexOf(')', startIndex = currentIndex + 1)
		// check if we have a ')'
		if (endOfUrlIndex != -1) {
			val url = source.substring(currentIndex + 2, endOfUrlIndex)
			entities.add(Entity.Hyperlink(url, openIndex, sanitizedIndex))
			endOfUrlIndex
		} else currentIndex
	} else currentIndex