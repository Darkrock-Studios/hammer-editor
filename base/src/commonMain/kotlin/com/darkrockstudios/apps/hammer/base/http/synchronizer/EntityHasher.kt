package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.appmattus.crypto.Algorithm
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import korlibs.crypto.encoding.Base64
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
		date: String?,
		tags: Set<String>,
	): String {
		val buf = buff()
		val d = Algorithm.MurmurHash3_X64_128().createDigest()
		d.update(id, buf)
		d.update(order, buf)
		d.update(content, buf)
		if (date != null) d.update(date, buf)
		tags.sorted().forEach { tag -> d.update(tag, buf) }
		return d.digest().base64Url
	}

	fun hashEncyclopediaEntry(
		id: Int,
		name: String,
		entryType: String,
		text: String,
		tags: Set<String>,
		image: ApiProjectEntity.EncyclopediaEntryEntity.Image?,
		aliases: List<String>,
		excludeFromDictionary: Boolean = false,
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

		if (image != null) {
			d.update(Base64.decode(image.base64, url = true))
			d.update(image.fileExtension, buf)
		}

		// Zero bytes at the default (false): pre-existing hashes must stay identical.
		if (excludeFromDictionary) {
			d.update(1, buf)
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
}