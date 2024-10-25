package com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository

open class InvalidSceneFilename(message: String, fileName: String) :
	IllegalStateException("$fileName failed to parse because: $message")

class InvalidSceneBufferFilename(message: String, fileName: String) :
	InvalidSceneFilename(message, fileName)
