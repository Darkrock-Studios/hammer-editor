package com.darkrockstudios.apps.hammer.database

import app.cash.sqldelight.db.SqlDriver

interface Database {
	val serverDatabase: ServerDatabase
	val driver: SqlDriver

	fun initialize()
	fun close()
}