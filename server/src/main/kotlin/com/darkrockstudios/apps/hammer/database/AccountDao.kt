package com.darkrockstudios.apps.hammer.database

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.GetAccountsPaginatedSortByCreated
import com.darkrockstudios.apps.hammer.account.SortDirection
import com.darkrockstudios.apps.hammer.account.UserSortField
import com.darkrockstudios.apps.hammer.utilities.injectIoDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
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
			// INSERT then look up the new row by its unique email. (We avoid
			// RETURNING here because various test fixtures call `createAccount`
			// without `.executeAsOne()`; in SqlDelight, a RETURNING query is
			// lazy and a missing terminal makes the INSERT silently never run.)
			queries.transactionWithResult {
				queries.createAccount(
					email = email,
					cipher_secret = cipherSecret,
					password_hash = hashedPassword,
					is_admin = isAdmin
				)
				queries.findAccount(email).executeAsOne().id
			}
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

	suspend fun getAccountsPaginated(
		page: Int,
		pageSize: Int,
		sortBy: UserSortField = UserSortField.CREATED,
		sortDirection: SortDirection = SortDirection.DESCENDING
	): List<GetAccountsPaginatedSortByCreated> =
		withContext(ioDispatcher) {
			val offset = page * pageSize
			val limit = pageSize.toLong()
			val offsetLong = offset.toLong()
			// Postgres-dialect SqlDelight infers `:ascending = 1` as Int (literal `1`).
			val ascending = if (sortDirection == SortDirection.ASCENDING) 1 else 0

			return@withContext when (sortBy) {
				UserSortField.CREATED -> queries.getAccountsPaginatedSortByCreated(ascending, limit, offsetLong)
					.executeAsList()

				UserSortField.LAST_SYNC -> queries.getAccountsPaginatedSortByLastSync(ascending, limit, offsetLong)
					.executeAsList().map {
						GetAccountsPaginatedSortByCreated(
							id = it.id,
							email = it.email,
							pen_name = it.pen_name,
							created = it.created,
							last_sync = it.last_sync,
							most_recent_sync = it.most_recent_sync,
							project_count = it.project_count
						)
					}

				UserSortField.PROJECT_COUNT -> queries.getAccountsPaginatedSortByProjectCount(
					ascending,
					limit,
					offsetLong
				).executeAsList().map {
					GetAccountsPaginatedSortByCreated(
						id = it.id,
						email = it.email,
						pen_name = it.pen_name,
						created = it.created,
						last_sync = it.last_sync,
						most_recent_sync = it.most_recent_sync,
						project_count = it.project_count
					)
				}
			}
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
	val created: Instant,
)