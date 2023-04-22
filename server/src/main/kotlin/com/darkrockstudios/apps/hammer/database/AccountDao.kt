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

	suspend fun getAccount(id: Long): Account? = withContext(ioDispatcher) {
		val query = queries.getAccount(id)
		return@withContext query.executeAsOneOrNull()
	}

	suspend fun findAccount(email: String): Account? = withContext(ioDispatcher) {
		val query = queries.findAccount(email)
		return@withContext query.executeAsOneOrNull()
	}

	suspend fun createAccount(email: String, salt: String, hashedPassword: String, isAdmin: Boolean): Long =
		withContext(ioDispatcher) {
			val newId = queries.transactionWithResult {
				queries.createAccount(email = email, salt = salt, password_hash = hashedPassword, isAdmin = isAdmin)
				val rowId = queries.lastInsertedRowId().executeAsOne()
				val account = queries.getByRowId(rowId).executeAsOne()
				account.id
			}

			return@withContext newId
		}

	suspend fun numAccounts(): Long = withContext(ioDispatcher) {
		val query = queries.count()
		return@withContext query.executeAsOne()
	}
}