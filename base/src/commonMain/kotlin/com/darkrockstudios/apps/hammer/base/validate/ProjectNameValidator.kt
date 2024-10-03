package com.darkrockstudios.apps.hammer.base.validate

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * TODO This should be expanded so client and server can share the same validation
 */
const val MAX_PROJECT_NAME_LENGTH = 128

@OptIn(ExperimentalContracts::class)
fun validateProjectName(name: String?): Boolean {
	contract {
		returns(true) implies (name != null)
	}
	return if (name.isNullOrBlank()) {
		false
	} else {
		name.length <= MAX_PROJECT_NAME_LENGTH
	}
}