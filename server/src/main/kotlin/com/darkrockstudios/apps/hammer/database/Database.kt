package com.darkrockstudios.apps.hammer.database

interface Database {
	val serverDatabase: ServerDatabase

	fun initialize()
	fun close()
}