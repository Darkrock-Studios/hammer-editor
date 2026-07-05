package com.darkrockstudios.apps.hammer.base.http.synchronizer

import com.appmattus.crypto.Algorithm
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import korlibs.crypto.encoding.base64Url

/**
 * Content hash for [StoryIdea] sync, following the same evolution rules as [ProjectDataHasher]:
 * nullable fields get a leading `0`/`1` presence byte (null must hash differently from epoch 0 /
 * empty string), and the tag set contributes zero bytes when empty (sorted with a size prefix
 * otherwise) so a future field added with an absent default cannot disturb existing baselines.
 */
object IdeaHasher {
	fun hash(idea: StoryIdea): String {
		val buf = ByteArray(4)
		val d = Algorithm.MurmurHash3_X64_128().createDigest()

		d.update(idea.id.id, buf)
		d.update(idea.created.epochSeconds, buf)
		d.update(idea.updated.epochSeconds, buf)

		if (idea.title != null) {
			d.update(1, buf)
			d.update(idea.title, buf)
		} else {
			d.update(0, buf)
		}

		d.update(idea.content, buf)

		if (idea.tags.isNotEmpty()) {
			d.update(idea.tags.size, buf)
			idea.tags.sorted().forEach { tag -> d.update(tag, buf) }
		}

		if (idea.promoted != null) {
			d.update(1, buf)
			d.update(idea.promoted.epochSeconds, buf)
		} else {
			d.update(0, buf)
		}

		if (idea.archived != null) {
			d.update(1, buf)
			d.update(idea.archived.epochSeconds, buf)
		} else {
			d.update(0, buf)
		}

		return d.digest().base64Url
	}
}
