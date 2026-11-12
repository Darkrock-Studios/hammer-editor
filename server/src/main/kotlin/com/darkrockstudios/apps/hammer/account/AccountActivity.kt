package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.Account
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository

/**
 * Whether the account may use the server: not pending deletion, and an admin or on the allowed users
 * list. The deletion check precedes the admin allowance so a soft-deleted admin is locked out too.
 */
suspend fun Account.isActive(whiteListRepository: WhiteListRepository): Boolean =
	deleted_at == null && (is_admin || whiteListRepository.isOnWhiteList(email))
