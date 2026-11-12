package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

/** Services the sync server offers signed-in users, under `/api/services/{userId}/<service>`. */
const val SERVICES_ROUTE = "services"

object HammerService {
	const val LANGUAGETOOL = "languagetool"
}

@Serializable
data class ServicesResponse(
	/** The [HammerService] names this server has configured. */
	val services: List<String>,
)
