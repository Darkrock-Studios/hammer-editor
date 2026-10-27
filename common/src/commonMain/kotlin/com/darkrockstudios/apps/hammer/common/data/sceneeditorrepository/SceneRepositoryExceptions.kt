package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

open class InvalidSceneFilename(message: String, fileName: String, cause: Throwable? = null) :
	IllegalStateException("$fileName failed to parse because: $message", cause)
