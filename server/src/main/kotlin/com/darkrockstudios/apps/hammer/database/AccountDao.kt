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

	suspend fun getAllAccounts(): List<Account> = withContext(ioDispatcher) {
		val query = queries.getAllAccount()
		return@withContext query.executeAsList()
	}

	suspend fun getAccount(id: Long): Account? = withContext(ioDispatcher) {
		val query = queries.getAccount(id)
		return@withContext query.executeAsOneOrNull()
	}

	suspend fun findAccount(email: String): Account? = withContext(ioDispatcher) {
		val query = queries.findAccount(email)
		return@withContext query.executeAsOneOrNull()
	}

	suspend fun createAccount(
		email: String,
		salt: String,
		hashedPassword: String,
		cipherSecret: String,
		isAdmin: Boolean
	): Long =
		withContext(ioDispatcher) {
			val newId = queries.transactionWithResult {
				queries.createAccount(
					email = email,
					salt = salt,
					cipher_secret = cipherSecret,
					password_hash = hashedPassword,
					is_admin = isAdmin
				)
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

	suspend fun updatePenName(userId: Long, penName: String?) = withContext(ioDispatcher) {
		queries.updatePenName(penName, userId)
	}

	suspend fun isPenNameAvailable(penName: String, excludeUserId: Long? = null): Boolean =
		withContext(ioDispatcher) {
			val isTaken = queries.isPenNameTaken(penName, excludeUserId).executeAsOne()
			return@withContext !isTaken
		}

	suspend fun findAccountByPenName(penName: String): Account? = withContext(ioDispatcher) {
		val query = queries.findAccountByPenName(penName)
		return@withContext query.executeAsOneOrNull()
	}
}