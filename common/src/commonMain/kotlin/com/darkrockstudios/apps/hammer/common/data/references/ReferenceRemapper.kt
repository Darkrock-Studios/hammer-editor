package com.darkrockstudios.apps.hammer.common.data.references

interface ReferenceRemapper {
	suspend fun remapEntryReferences(oldEntryId: Int, newEntryId: Int)
}
