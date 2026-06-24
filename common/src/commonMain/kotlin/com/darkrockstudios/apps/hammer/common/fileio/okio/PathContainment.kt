package com.darkrockstudios.apps.hammer.common.fileio.okio

import okio.Path

/**
 * Containment backstop against path traversal. Returns true only when this path,
 * once `..`/`.` segments are collapsed, equals [root] or sits nested beneath it.
 * A non-descendant (sibling escape, absolute reach-out, `..` climb) returns false.
 */
fun Path.isWithin(root: Path): Boolean {
	val normalizedRoot = root.normalized()
	val normalizedCandidate = normalized()

	if (normalizedCandidate.isAbsolute != normalizedRoot.isAbsolute) return false

	if (normalizedCandidate == normalizedRoot) return true

	val rootSegments = normalizedRoot.segments
	val candidateSegments = normalizedCandidate.segments
	if (candidateSegments.size <= rootSegments.size) return false

	return candidateSegments.subList(0, rootSegments.size) == rootSegments
}
