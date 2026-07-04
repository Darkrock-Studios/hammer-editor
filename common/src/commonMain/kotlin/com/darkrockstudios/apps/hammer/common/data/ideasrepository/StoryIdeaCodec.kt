package com.darkrockstudios.apps.hammer.common.data.ideasrepository

import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import kotlin.time.Instant

/**
 * Encodes a [StoryIdea] to/from its at-rest file form: markdown with a TOML front-matter
 * block delimited by `+++` fences (the Hugo TOML front-matter convention).
 *
 * ```
 * +++
 * id = "0198c9a1-…"
 * title = "The Lighthouse Keeper's Daughter"
 * created = "2026-07-03T14:22:05Z"
 * updated = "2026-07-03T14:31:48Z"
 * tags = [ "gothic", "coastal" ]
 * +++
 *
 * What if the light itself was the inheritance...
 * ```
 *
 * Absent optionals are omitted from the block. Everything after the closing fence and its
 * single blank separator line is the content, verbatim — a `+++` line inside the content is
 * fine because only the first closing fence terminates the block.
 */
class StoryIdeaCodec(toml: Toml) {
	// Absent optionals must be omitted from the block: TOML has no null, and the design
	// doc requires absent fields to not appear at all.
	private val toml: Toml = Toml(from = toml) { explicitNulls = false }

	fun encode(idea: StoryIdea): String {
		val frontMatter = IdeaFrontMatter(
			id = idea.id,
			created = idea.created,
			updated = idea.updated,
			title = idea.title,
			tags = idea.tags,
			promoted = idea.promoted,
			archived = idea.archived,
		)
		val block = toml.encodeToString(IdeaFrontMatter.serializer(), frontMatter).trim()
		return buildString {
			append(FENCE)
			append('\n')
			append(block)
			append('\n')
			append(FENCE)
			append('\n')
			append('\n')
			append(idea.content)
		}
	}

	/**
	 * @throws IdeaCodecException when the fences are missing or the front matter fails to
	 * parse. Prefer [decodeOrNull] when a malformed file should be skipped rather than fail.
	 */
	fun decode(text: String): StoryIdea {
		val newlineIndices = ArrayList<Int>()
		var searchFrom = 0
		while (true) {
			val idx = text.indexOf('\n', searchFrom)
			if (idx < 0) break
			newlineIndices.add(idx)
			searchFrom = idx + 1
		}

		fun lineAt(start: Int, endExclusive: Int) = text.substring(start, endExclusive).trimEnd('\r')

		// The first non-blank line must be the opening fence
		var lineStart = 0
		var lineIndex = 0
		var openFenceEnd = -1
		while (lineIndex <= newlineIndices.size) {
			val lineEnd = newlineIndices.getOrNull(lineIndex) ?: text.length
			val line = lineAt(lineStart, lineEnd)
			if (line.isNotBlank()) {
				if (line != FENCE) throw IdeaCodecException("Missing opening fence")
				openFenceEnd = lineEnd + 1
				break
			}
			lineStart = lineEnd + 1
			lineIndex++
		}
		if (openFenceEnd < 0 || openFenceEnd > text.length) {
			throw IdeaCodecException("Missing opening fence")
		}

		// Find the closing fence
		lineStart = openFenceEnd
		lineIndex++
		var blockEnd = -1
		var bodyStart = -1
		while (lineIndex <= newlineIndices.size) {
			val lineEnd = newlineIndices.getOrNull(lineIndex) ?: text.length
			if (lineAt(lineStart, lineEnd) == FENCE) {
				blockEnd = lineStart
				bodyStart = minOf(lineEnd + 1, text.length)
				break
			}
			lineStart = lineEnd + 1
			lineIndex++
		}
		if (blockEnd < 0) throw IdeaCodecException("Missing closing fence")

		val block = text.substring(openFenceEnd, blockEnd)
			.lines()
			.joinToString("\n") { it.trimEnd('\r') }

		// tomlkt does not funnel every decode failure through SerializationException: malformed
		// input can also throw IllegalArgumentException (numeric coercion, type-mismatch casts)
		// or IllegalStateException (parser errors such as a malformed date-time).
		val frontMatter = try {
			toml.decodeFromString(IdeaFrontMatter.serializer(), block)
		} catch (e: SerializationException) {
			throw IdeaCodecException("Malformed front matter", e)
		} catch (e: IllegalArgumentException) {
			throw IdeaCodecException("Malformed front matter", e)
		} catch (e: IllegalStateException) {
			throw IdeaCodecException("Malformed front matter", e)
		}

		// Skip the single blank separator line encode() writes
		var content = text.substring(bodyStart)
		if (content.startsWith("\r\n")) {
			content = content.substring(2)
		} else if (content.startsWith("\n")) {
			content = content.substring(1)
		}

		return StoryIdea(
			id = frontMatter.id,
			created = frontMatter.created,
			updated = frontMatter.updated,
			title = frontMatter.title,
			content = content,
			tags = frontMatter.tags,
			promoted = frontMatter.promoted,
			archived = frontMatter.archived,
		)
	}

	fun decodeOrNull(text: String, onError: (Exception) -> Unit = {}): StoryIdea? {
		return try {
			decode(text)
		} catch (e: IdeaCodecException) {
			onError(e)
			null
		}
	}

	companion object {
		const val FENCE = "+++"
	}
}

class IdeaCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class IdeaFrontMatter(
	val id: IdeaId,
	val created: Instant,
	val updated: Instant,
	val title: String? = null,
	val tags: Set<String> = emptySet(),
	val promoted: Instant? = null,
	val archived: Instant? = null,
)
