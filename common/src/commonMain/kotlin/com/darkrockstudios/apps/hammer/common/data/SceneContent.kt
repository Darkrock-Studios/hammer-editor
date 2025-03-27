package com.darkrockstudios.apps.hammer.common.data

data class SceneContent(
	val scene: SceneItem,
	val markdown: String? = null,
	val platformRepresentation: PlatformRichText? = null
) {
	init {
		validate()
	}

	private fun validate() {
		if (markdown == null && platformRepresentation == null) {
			throw IllegalArgumentException("markdown and platformRepresentation can not both be null")
		} else if (markdown != null && platformRepresentation != null) {
			throw IllegalArgumentException("markdown and platformRepresentation can not both be non-null")
		}
	}

	fun coerceMarkdown(): String {
		return if (markdown != null) {
			markdown
		} else if (platformRepresentation != null) {
			platformRepresentation.convertToMarkdown()
		} else {
			throw IllegalStateException("SceneContent had no content")
		}
	}

	override fun equals(other: Any?): Boolean {
		return if (other is SceneContent) {
			if (markdown != null && other.markdown != null) {
				markdown == other.markdown
			} else if (platformRepresentation != null && other.platformRepresentation != null) {
				platformRepresentation == other.platformRepresentation
			} else if (markdown != null && other.markdown == null) {
				markdown == other.platformRepresentation!!.convertToMarkdown()
			} else if (markdown == null && other.markdown != null) {
				platformRepresentation!!.convertToMarkdown() == other.markdown
			} else {
				false
			}
		} else {
			false
		}
	}

	override fun hashCode(): Int {
		var result = scene.hashCode()
		result = 31 * result + (markdown?.hashCode() ?: 0)
		result = 31 * result + (platformRepresentation?.hashCode() ?: 0)
		return result
	}
}

interface PlatformRichText {
	fun convertToMarkdown(): String
	fun compare(text: PlatformRichText): Boolean

	/**
	 * Note: straight comparison used to be good enough, but now SceneContent does not contain
	 * a snapshot, but instead a live object which updates as the UI updates it. I don't think
	 * this will cause problems? But if it does, hopefully this comment will help me remember
	 * where all the trouble started.
	 *
	 * This function should return true of the internal state object is the exact same object
	 * as the one being compared to.
	 */
	fun stateCompare(text: PlatformRichText?): Boolean
}