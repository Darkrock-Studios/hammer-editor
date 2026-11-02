package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.appmattus.crypto.Algorithm
import com.appmattus.crypto.Digest
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import korlibs.crypto.encoding.base64Url
import kotlin.time.Instant

object EntityHasher {
	private fun buff() = ByteArray(4)

	fun hashScene(
		id: Int,
		order: Int,
		path: List<Int>,
		name: String,
		type: ApiSceneType,
		content: String,
		outline: String,
		notes: String,
		archived: Boolean,
		confirmedReferences: Set<Int>,
		dismissedReferences: Set<Int>,
		tags: Set<String>,
		created: Instant?,
		lastEdited: Instant?,
	): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(order, buf)
		d.update(name, buf)
		d.update(type.ordinal, buf)
		d.update(content, buf)
		for (segment in path) {
			d.update(segment, buf)
		}
		d.update(outline, buf)
		d.update(notes, buf)
		d.update(if (archived) 1 else 0, buf)
		// Size prefix delimits the two sections - without it, {confirmed=[7], dismissed=[]}
		// would hash identically to {confirmed=[], dismissed=[7]} and confirm/dismiss
		// transitions would never propagate through sync.
		d.update(confirmedReferences.size, buf)
		for (ref in confirmedReferences.sorted()) {
			d.update(ref, buf)
		}
		d.update(dismissedReferences.size, buf)
		for (ref in dismissedReferences.sorted()) {
			d.update(ref, buf)
		}
		tags.sorted().forEach { tag -> d.update(tag, buf) }
		// Nullable: a missing timestamp must hash differently from epoch 0.
		d.update(if (created != null) 1 else 0, buf)
		if (created != null) d.update(created.epochSeconds, buf)
		d.update(if (lastEdited != null) 1 else 0, buf)
		if (lastEdited != null) d.update(lastEdited.epochSeconds, buf)
		return d.digest().base64Url
	}

	fun hashNote(
		id: Int,
		created: Instant,
		content: String,
		tags: Set<String>,
	): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(created.epochSeconds, buf)
		d.update(content, buf)
		tags.sorted().forEach { tag -> d.update(tag, buf) }
		return d.digest().base64Url
	}

	fun hashTimelineEvent(
		id: Int,
		order: Int,
		content: String,
		label: String?,
		tags: Set<String>,
		kind: TimelineKind = TimelineKind.EVENT,
		subject: Int? = null,
		references: Set<Int> = emptySet(),
		lane: String? = null,
		milestone: Boolean = false,
		start: StoryDate? = null,
		end: StoryDate? = null,
		title: String? = null,
		cachedTitle: String? = null,
	): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(order, buf)
		d.update(content, buf)
		if (label != null) d.update(label, buf)
		tags.sorted().forEach { tag -> d.update(tag, buf) }

		// Each block below contributes zero bytes at its default, so events from before these
		// fields keep their stored hash. The negative markers cannot be read as a char code
		// continuing the bare tags above, and keep the blocks apart from each other.
		if (kind != TimelineKind.EVENT) {
			d.update(-1, buf)
			d.update(kind.code, buf)
		}
		if (subject != null) {
			d.update(-2, buf)
			d.update(subject, buf)
		}
		if (references.isNotEmpty()) {
			d.update(-3, buf)
			d.update(references.size, buf)
			references.sorted().forEach { d.update(it, buf) }
		}
		if (lane != null) {
			d.update(-4, buf)
			d.update(lane.length, buf)
			d.update(lane, buf)
		}
		if (milestone) {
			d.update(-5, buf)
		}
		if (start != null) {
			d.update(-6, buf)
			d.updateStoryDate(start, buf)
		}
		if (end != null) {
			d.update(-7, buf)
			d.updateStoryDate(end, buf)
		}
		if (title != null) {
			d.update(-8, buf)
			d.update(title.length, buf)
			d.update(title, buf)
		}
		if (cachedTitle != null) {
			d.update(-9, buf)
			d.update(cachedTitle.length, buf)
			d.update(cachedTitle, buf)
		}
		return d.digest().base64Url
	}

	private fun Digest<*>.updateStoryDate(date: StoryDate, buf: ByteArray) {
		update(date.rungs.size, buf)
		date.rungs.keys.sorted().forEach { unit ->
			update(unit.length, buf)
			update(unit, buf)
			update(date.rungs.getValue(unit), buf)
		}
	}

	fun hashEncyclopediaEntry(
		id: Int,
		name: String,
		entryType: String,
		text: String,
		tags: Set<String>,
		aliases: List<String>,
		excludeFromDictionary: Boolean = false,
		images: List<Int> = emptyList(),
	): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(name, buf)
		d.update(entryType, buf)
		d.update(text, buf)

		val sortedTags = tags.sorted()
		sortedTags.forEach { tag ->
			d.update(tag, buf)
		}

		aliases.forEach { alias ->
			d.update(alias, buf)
		}

		// Zero bytes at the default (false): pre-existing hashes must stay identical.
		if (excludeFromDictionary) {
			d.update(1, buf)
		}

		// Zero bytes when empty, so entries without images keep their stored hash. Not sorted: the
		// order is the display order and the first image is the primary one.
		if (images.isNotEmpty()) {
			d.update(images.size, buf)
			images.forEach { d.update(it, buf) }
		}

		return d.digest().base64Url
	}

	fun hashSceneDraft(
		id: Int,
		created: Instant,
		name: String,
		content: String,
		sceneId: Int,
	): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(sceneId, buf)
		d.update(created.epochSeconds, buf)
		d.update(name, buf)
		d.update(content, buf)
		return d.digest().base64Url
	}

	/** [bytes] are the decoded raw image bytes, not the base64 wire form. */
	fun hashImage(
		id: Int,
		name: String,
		fileExtension: String,
		bytes: ByteArray,
	): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		// Length prefixes keep adjacent fields from bleeding into each other ("ab"+"c" vs "a"+"bc").
		d.update(name.length, buf)
		d.update(name, buf)
		d.update(fileExtension.length, buf)
		d.update(fileExtension, buf)
		d.update(bytes.size, buf)
		d.update(bytes)
		return d.digest().base64Url
	}
}
