package com.darkrockstudios.apps.hammer.common.data.tagindex

interface TagSuggesting {
	fun suggestTags(prefix: String, limit: Int = 5): List<String>
}
