package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.BaseError
import com.darkrockstudios.apps.hammer.common.data.CreateResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer

enum class EntryError : BaseError {
	NONE,
	NAME_TOO_LONG
}

typealias EntryResult = CreateResult<EntryContainer, EntryError>