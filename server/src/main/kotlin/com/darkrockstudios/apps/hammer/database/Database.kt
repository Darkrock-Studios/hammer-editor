package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.ServerDatabase

interface Database {
    val serverDatabase: ServerDatabase

    fun initialize()
    fun close()
}