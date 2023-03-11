package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

class AccountDao(
    database: Database,
) : KoinComponent {

    private val ioDispatcher by injectIoDispatcher()
    private val queries = database.serverDatabase.accountQueries

    suspend fun findAccount(email: String): Account? = withContext(ioDispatcher) {
        val query = queries.findAccount(email)
        return@withContext query.executeAsOneOrNull()
    }

    suspend fun createAccount(email: String, salt: String, hashedPassword: String) = withContext(ioDispatcher) {
        queries.createAccount(email = email, salt = salt, password_hash = hashedPassword)
    }
}