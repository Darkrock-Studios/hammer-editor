package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.database.WhiteListDao
import io.mockk.every
import io.mockk.mockk

class FakeWhiteListDao : WhiteListDao(mockk {
	every { serverDatabase.whiteListQueries } returns mockk()
}) {
	private val whitelist = mutableSetOf<String>()

	override suspend fun isWhiteListed(email: String): Boolean {
		return whitelist.contains(email)
	}

	override suspend fun addToWhiteList(email: String) {
		whitelist.add(email)
	}

	override suspend fun removeFromWhiteList(email: String) {
		whitelist.remove(email)
	}

	override suspend fun getAllWhiteListedEmails(): List<String> {
		return whitelist.sorted()
	}

	override suspend fun getWhiteListCount(): Long {
		return whitelist.size.toLong()
	}

	override suspend fun getWhiteListPaginated(limit: Long, offset: Long): List<String> {
		return whitelist.sorted().drop(offset.toInt()).take(limit.toInt())
	}
}
