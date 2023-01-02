package com.darkrockstudios.apps.hammer.common.data.id

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.id.provider.IdProvider

abstract class IdRepository {
	private val idProviders = mutableMapOf<ProjectDef, IdProvider>()

	fun getIdProvider(projectDef: ProjectDef): IdProvider {
		val provider = idProviders[projectDef]
		return if (provider != null) {
			provider
		} else {
			val newProvider = createNewProvider(projectDef)
			idProviders[projectDef] = newProvider
			newProvider
		}
	}

	fun close(projectDef: ProjectDef) {
		idProviders.remove(projectDef)
	}

	protected abstract fun createNewProvider(projectDef: ProjectDef): IdProvider
}