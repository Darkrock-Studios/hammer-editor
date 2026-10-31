package com.darkrockstudios.apps.hammer.common.data.references

interface ReferenceRemapper {
	/** Returns the ids of the scenes whose stored references were rewritten. */
	suspend fun remapEntryReferences(oldEntryId: Int, newEntryId: Int): Set<Int>
}
