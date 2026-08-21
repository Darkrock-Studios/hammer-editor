package com.darkrockstudios.apps.hammer.plugin

import com.darkrockstudios.apps.hammer.utilities.Msg
import io.ktor.server.application.*

/**
 * Where an [AllowedUsersSource] notice is rendered. Each slot is inserted unescaped, so
 * sources return trusted HTML, typically from their own mustache template.
 */
enum class NoticeSlot {
	/** Info panel on the web login page. */
	LOGIN,

	/** Info panel on the web signup page. */
	SIGNUP,

	/** Instance notice band on the home page. */
	HOME_BANNER,

	/** Social/links row in the site footer. */
	FOOTER,
}

/**
 * A system that decides who belongs on the allowed-users list, e.g. a membership service
 * that syncs subscribers onto it.
 *
 * The [id] doubles as the `WhiteListRepository` reason tag: a source must only add and
 * remove entries carrying its own tag, so manually-added entries and other sources'
 * entries survive its syncs.
 */
interface AllowedUsersSource {
	val id: String

	/** Fully configured and turned on. Inactive sources contribute no notices. */
	suspend fun isActive(): Boolean

	/**
	 * HTML for [slot]. Null falls back to the stock template content for that slot; an
	 * empty string replaces the stock content with nothing.
	 */
	suspend fun notice(call: ApplicationCall, slot: NoticeSlot): String?

	/**
	 * Shown by the native client when signup/login is rejected because the user is not
	 * on the allowed list. Null falls back to the stock rejection message.
	 */
	suspend fun rejectionMessage(): Msg?
}
