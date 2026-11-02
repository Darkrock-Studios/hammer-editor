package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef

/**
 * Dictionary words never need a decision: a conflict keeps both sides. Kept verbatim, because the
 * merged set is uploaded: a word a newer build stored under laxer rules than this one accepts as
 * input would otherwise be deleted server-side for every other device.
 */
fun mergeDictionaryWords(local: ProjectData, server: ProjectData): Set<String> =
	local.dictionaryWords + server.dictionaryWords

/**
 * Union by id for the conflict screen: on a same-id difference the def from the preferred side
 * wins. The preferred side's order comes first, then the defs only the other side has.
 */
fun mergeTimelinesByChoice(
	local: List<TimelineDef>,
	server: List<TimelineDef>,
	preferLocal: Boolean,
): List<TimelineDef> {
	val (preferred, other) = if (preferLocal) local to server else server to local
	val preferredIds = preferred.mapTo(HashSet()) { it.id }
	return preferred + other.filter { it.id !in preferredIds }
}

/**
 * Timelines merge per def by id: a def added, changed or removed locally since [base] keeps the
 * local outcome (an edit also survives an incoming removal), the rest take the incoming def. Order
 * is incoming order, then local-only defs.
 */
fun mergeTimelines(
	base: List<TimelineDef>,
	local: List<TimelineDef>,
	incoming: List<TimelineDef>,
): List<TimelineDef> {
	val baseById = base.associateBy { it.id }
	val localById = local.associateBy { it.id }
	val incomingIds = incoming.mapTo(HashSet()) { it.id }

	val merged = incoming.mapNotNull { incomingDef ->
		val localDef = localById[incomingDef.id]
		val baseDef = baseById[incomingDef.id]
		when {
			localDef == null -> if (baseDef != null) null else incomingDef
			localDef != baseDef -> localDef
			else -> incomingDef
		}
	}
	val localOnly = local.filter { it.id !in incomingIds && it != baseById[it.id] }
	return merged + localOnly
}

/** True when something other than the auto-merged dictionary differs. */
fun ProjectData.differsOutsideDictionary(other: ProjectData): Boolean =
	copy(dictionaryWords = other.dictionaryWords) != other

/**
 * Re-applies the edits made between [base] and [local] on top of [incoming]: a field the user
 * changed keeps its local value, the rest take the incoming value, dictionary words carry
 * the local additions and removals over as a delta, and timelines merge by id ([mergeTimelines]).
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
		coverImageId = pick(base.coverImageId, local.coverImageId, incoming.coverImageId),
		timelines = mergeTimelines(base.timelines, local.timelines, incoming.timelines),
		dictionaryWords = incoming.dictionaryWords +
			(local.dictionaryWords - base.dictionaryWords) -
			(base.dictionaryWords - local.dictionaryWords),
	)
}
