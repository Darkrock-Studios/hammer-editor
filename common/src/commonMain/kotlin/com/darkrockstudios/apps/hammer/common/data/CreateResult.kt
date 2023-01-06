package com.darkrockstudios.apps.hammer.common.data

data class CreateResult<T, E : BaseError>(val instance: T?, val error: E) {
	constructor(error: E) : this(null, error)
}