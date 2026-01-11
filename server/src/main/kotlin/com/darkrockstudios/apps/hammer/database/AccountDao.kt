package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.GetAccountsPaginated
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
		hashedPassword: String,
		cipherSecret: String,
		isAdmin: Boolean
	): Long =
		withContext(ioDispatcher) {
			val newId = queries.transactionWithResult {
				queries.createAccount(
					email = email,
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

	suspend fun getAccountsPaginated(page: Int, pageSize: Int): List<GetAccountsPaginated> =
		withContext(ioDispatcher) {
			val offset = page * pageSize
			return@withContext queries.getAccountsPaginated(
				limit = pageSize.toLong(),
				offset = offset.toLong()
			).executeAsList()
		}

	suspend fun updatePassword(userId: Long, hashedPassword: String) = withContext(ioDispatcher) {
		queries.updatePassword(hashedPassword, userId)
	}

	suspend fun updateBio(userId: Long, bio: String?) = withContext(ioDispatcher) {
		queries.updateBio(bio?.trim(), userId)
	}

	suspend fun getBio(userId: Long): String? = withContext(ioDispatcher) {
		return@withContext queries.getBio(userId).executeAsOneOrNull()?.bio
	}

	suspend fun updateCommunityMember(userId: Long, isCommunityMember: Boolean) = withContext(ioDispatcher) {
		queries.updateCommunityMember(isCommunityMember, userId)
	}

	suspend fun getCommunityMember(userId: Long): Boolean = withContext(ioDispatcher) {
		return@withContext queries.getCommunityMember(userId).executeAsOneOrNull() ?: false
	}

	suspend fun getCommunityAuthors(page: Int, pageSize: Int): List<CommunityAuthor> =
		withContext(ioDispatcher) {
			val offset = page * pageSize
			return@withContext queries.getCommunityAuthors(
				limit = pageSize.toLong(),
				offset = offset.toLong()
			).executeAsList().map { row ->
				CommunityAuthor(
					id = row.id,
					penName = row.pen_name!!,
					bio = row.bio,
					created = row.created
				)
			}
		}

	suspend fun countCommunityAuthors(): Long = withContext(ioDispatcher) {
		return@withContext queries.countCommunityAuthors().executeAsOne()
	}
}

data class CommunityAuthor(
	val id: Long,
	val penName: String,
	val bio: String?,
	val created: String
)