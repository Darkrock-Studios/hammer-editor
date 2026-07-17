package com.darkrockstudios.apps.hammer.base.http

import kotlinx.serialization.Serializable

@Serializable
data class TermsOfServiceChallenge(
	val text: String,
	/** SHA-256 hex of the terms of service text, echoed back on acceptance */
	val version: String,
)
