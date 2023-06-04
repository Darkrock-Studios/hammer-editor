package com.darkrockstudios.apps.hammer.common.data.encyclopediarepository

import com.darkrockstudios.apps.hammer.common.data.BaseError
import com.darkrockstudios.apps.hammer.common.data.CreateResult
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryContainer
import com.darkrockstudios.apps.hammer.common.fileio.HPath

enum class EntryError : BaseError {
	NONE,
	NAME_TOO_LONG,
	NAME_INVALID_CHARACTERS,
	TAG_TOO_LONG,
}

typealias EntryResult = CreateResult<EntryContainer, EntryError>

open class EntryLoadError(val path: HPath, override val cause: Throwable?) : Exception("Failed to load entry: $path")