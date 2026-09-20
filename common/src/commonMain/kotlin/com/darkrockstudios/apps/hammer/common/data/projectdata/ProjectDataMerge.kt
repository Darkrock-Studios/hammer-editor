package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData

/**
 * Dictionary words never need a decision: a conflict keeps both sides. Kept verbatim, because the
 * merged set is uploaded: a word a newer build stored under laxer rules than this one accepts as
 * input would otherwise be deleted server-side for every other device.
 */
fun mergeDictionaryWords(local: ProjectData, server: ProjectData): Set<String> =
	local.dictionaryWords + server.dictionaryWords

/** True when something other than the auto-merged dictionary differs. */
fun ProjectData.differsOutsideDictionary(other: ProjectData): Boolean =
	copy(dictionaryWords = other.dictionaryWords) != other

/**
 * Re-applies the edits made between [base] and [local] on top of [incoming]: a field the user
 * changed keeps its local value, the rest take the incoming value, and dictionary words carry
 * the local additions and removals over as a delta.
 */
fun reapplyLocalEdits(base: ProjectData, local: ProjectData, incoming: ProjectData): ProjectData {
	fun <T> pick(baseValue: T, localValue: T, incomingValue: T): T =
		if (localValue != baseValue) localValue else incomingValue

	return incoming.copy(
		authorName = pick(base.authorName, local.authorName, incoming.authorName),
		theme = pick(base.theme, local.theme, incoming.theme),
		wordCountGoal = pick(base.wordCountGoal, local.wordCountGoal, incoming.wordCountGoal),
		tags = pick(base.tags, local.tags, incoming.tags),
		language = pick(base.language, local.language, incoming.language),
		encyclopediaDictionary = pick(base.encyclopediaDictionary, local.encyclopediaDictionary, incoming.encyclopediaDictionary),
		dictionaryWords = incoming.dictionaryWords +
			(local.dictionaryWords - base.dictionaryWords) -
			(base.dictionaryWords - local.dictionaryWords),
	)
}
