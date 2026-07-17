package com.darkrockstudios.apps.hammer.frontend.admin

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.withDefaults
import com.darkrockstudios.apps.hammer.patreon.PatreonSyncService
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlin.math.ceil
import kotlin.time.Duration.Companion.days

internal fun Route.whiteListRoutes(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	serverConfig: ServerConfig,
	clock: Clock,
) {
	route("/whitelist") {
		whitelistUserFragment(whiteListRepository)
		whitelistAdd(whiteListRepository, clock)
		whitelistRemove(whiteListRepository)
		whitelistToggle(whiteListRepository, configRepository, serverConfig)
		whitelistEditReason(whiteListRepository)
		whitelistEditExpiry(whiteListRepository, clock)
	}
}

/** The `never` preset and a blank custom date both mean "no expiry". */
internal const val EXPIRY_PRESET_NEVER = "never"
internal const val EXPIRY_PRESET_CUSTOM = "custom"

/**
 * Resolves the add/edit form's expiry controls to an instant, or null for "never".
 *
 * A day preset (`7`, `30`, `90`) is added to [base] — so on the add form [base] is
 * "now" and the entry expires N days out, while on the edit form [base] is the entry's
 * current expiry and the preset extends it by N more days. A custom date is the
 * `yyyy-MM-dd` emitted by `<input type="date">`, interpreted absolutely as the *end* of
 * that day in the server's zone. Returns [ExpiryParse.Invalid] for an unparseable date.
 */
internal sealed interface ExpiryParse {
	data class Parsed(val expires: Instant?) : ExpiryParse
	data object Invalid : ExpiryParse
}

internal fun parseExpiry(preset: String?, customDate: String?, base: Instant): ExpiryParse {
	return when (preset?.trim()?.lowercase()) {
		null, "", EXPIRY_PRESET_NEVER -> ExpiryParse.Parsed(null)
		EXPIRY_PRESET_CUSTOM -> parseCustomExpiryDate(customDate)
		else -> {
			val days = preset.trim().toLongOrNull() ?: return ExpiryParse.Invalid
			if (days <= 0) ExpiryParse.Invalid else ExpiryParse.Parsed(base + days.days)
		}
	}
}

private fun parseCustomExpiryDate(customDate: String?): ExpiryParse {
	val raw = customDate?.trim().orEmpty()
	if (raw.isEmpty()) return ExpiryParse.Parsed(null)
	return try {
		val endOfDay = LocalDate.parse(raw)
			.plusDays(1)
			.atStartOfDay(ZoneId.systemDefault())
			.toInstant()
		ExpiryParse.Parsed(endOfDay.toKotlinInstant())
	} catch (_: DateTimeParseException) {
		ExpiryParse.Invalid
	}
}

private suspend fun isPatreonActive(configRepository: ConfigRepository, serverConfig: ServerConfig): Boolean {
	val patreonConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
	val patreonFeatureEnabled = serverConfig.patreonEnabled == true
	return patreonFeatureEnabled && patreonConfig.enabled && patreonConfig.patreonUrl.isNotBlank()
}

private fun Route.whitelistToggle(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	serverConfig: ServerConfig
) {
	hx.post("/toggle") {
		val enabled = whiteListRepository.useWhiteList()
		val patreonActive = isPatreonActive(configRepository, serverConfig)

		// Prevent disabling whitelist when Patreon is active
		if (enabled && patreonActive) {
			call.respond(HttpStatusCode.Forbidden, "")
			return@post
		}

		whiteListRepository.setWhiteListEnabled(!enabled)

		call.response.header(HxResponseHeaders.Refresh, "true")
		call.respond(HttpStatusCode.OK, "")
	}
}

@OptIn(ExperimentalKtorApi::class)
private fun Route.whitelistAdd(whiteListRepository: WhiteListRepository, clock: Clock) {
	hx.post("/add") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()
		val reason = params["reason"]?.trim().orEmpty()
		val page = params["page"]?.toIntOrNull() ?: 0
		val sortOldestFirst = params["sortOldestFirst"]?.toBoolean() ?: false

		// Validate email format
		if (email.isEmpty()) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_emailrequired"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		if (!whiteListRepository.validateEmail(email)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_emailinvalid"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		val actualReason = reason.ifEmpty { "Added by admin" }

		// Validate reason length
		if (!whiteListRepository.validateReason(actualReason)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_reasontoolong"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		val parsedExpiry = parseExpiry(params["expiryPreset"], params["expiryDate"], clock.now())
		if (parsedExpiry !is ExpiryParse.Parsed || !whiteListRepository.validateExpiry(parsedExpiry.expires)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_expiryinvalid"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		// All validation passed
		whiteListRepository.addToWhiteList(email, actualReason, parsedExpiry.expires)

		val model = getWhitelistModel(call, whiteListRepository, page, sortOldestFirst)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

private fun Route.whitelistRemove(whiteListRepository: WhiteListRepository) {
	hx.post("/remove") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()
		val page = params["page"]?.toIntOrNull() ?: 0
		val sortOldestFirst = params["sortOldestFirst"]?.toBoolean() ?: false

		if (email.isNotEmpty()) {
			whiteListRepository.removeFromWhiteList(email)
		}

		val model = getWhitelistModel(call, whiteListRepository, page, sortOldestFirst)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

private fun Route.whitelistEditReason(whiteListRepository: WhiteListRepository) {
	hx.post("/edit-reason") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()
		val reason = params["reason"]?.trim().orEmpty()
		val page = params["page"]?.toIntOrNull() ?: 0
		val sortOldestFirst = params["sortOldestFirst"]?.toBoolean() ?: false

		if (email.isEmpty()) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_emailrequired"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		if (!whiteListRepository.validateReason(reason)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_reasontoolong"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		whiteListRepository.updateReason(email, reason)

		val model = getWhitelistModel(call, whiteListRepository, page, sortOldestFirst)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

private fun Route.whitelistEditExpiry(whiteListRepository: WhiteListRepository, clock: Clock) {
	hx.post("/edit-expiry") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()
		val page = params["page"]?.toIntOrNull() ?: 0
		val sortOldestFirst = params["sortOldestFirst"]?.toBoolean() ?: false

		if (email.isEmpty()) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_emailrequired"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		// Day presets extend the entry's current expiry, so a lapsed or never-expiring
		// entry extends from now, and a still-valid one extends from its remaining time.
		val now = clock.now()
		val currentExpiry = whiteListRepository.getEntry(email)?.expires
		val extendFrom = maxOf(now, currentExpiry ?: now)

		val parsedExpiry = parseExpiry(params["expiryPreset"], params["expiryDate"], extendFrom)
		if (parsedExpiry !is ExpiryParse.Parsed || !whiteListRepository.validateExpiry(parsedExpiry.expires)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_expiryinvalid"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		whiteListRepository.updateExpiry(email, parsedExpiry.expires)

		val model = getWhitelistModel(call, whiteListRepository, page, sortOldestFirst)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

internal fun Route.whitelistUserFragment(whiteListRepository: WhiteListRepository) {
	hx.get("/user-fragment") {
		val model = getWhitelistModel(call, whiteListRepository)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

internal suspend fun getWhitelistModel(
	call: ApplicationCall,
	whiteListRepository: WhiteListRepository,
	page: Int? = null,
	sortOldestFirst: Boolean? = null
): MutableMap<String, Any> {
	val queryPage = call.request.queryParameters["page"]?.toIntOrNull()
	val actualPage = page ?: queryPage ?: 0

	val querySortOldestFirst = call.request.queryParameters["sortOldestFirst"]?.toBoolean()
	val actualSortOldestFirst = sortOldestFirst ?: querySortOldestFirst ?: false

	val pageSize = 10
	val totalCount = whiteListRepository.getWhiteListCount()
	val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
	val currentPage = if (totalPages > 0) actualPage.coerceIn(0, totalPages - 1) else 0

	val whitelistEntries =
		whiteListRepository.getWhiteListWithAccountStatus(currentPage, pageSize, actualSortOldestFirst)
	val whitelistItems = whitelistEntries.map { entry ->
		mapOf(
			"email" to entry.email,
			"dateAdded" to (formatDateFromTimestamp(entry.date_added)
				?: call.msg("admin_whitelist_date_added_unknown")),
			"reason" to entry.reason,
			"hasAccount" to entry.has_account,
			"hasExpiry" to (entry.expires != null),
			"expires" to (entry.expires?.let { formatDateFromTimestamp(it) }
				?: call.msg("admin_whitelist_expiry_never")),
			// Prefills the edit dialog's <input type="date">, which only accepts yyyy-MM-dd.
			"expiresRaw" to (entry.expires?.let { formatDateInputValue(it) } ?: ""),
			// Patreon sync owns its entries' lifecycle, so expiry isn't the admin's to set.
			"isPatreon" to (entry.reason == PatreonSyncService.WHITELIST_REASON),
		)
	}

	val whitelist = mutableMapOf<String, Any>()
	whitelist["items"] = whitelistItems
	whitelist["currentPage"] = currentPage
	whitelist["currentPageDisplay"] = currentPage + 1
	whitelist["totalPages"] = totalPages
	whitelist["hasNextPage"] = currentPage < totalPages - 1
	whitelist["hasPrevPage"] = currentPage > 0
	whitelist["nextPage"] = currentPage + 1
	whitelist["prevPage"] = currentPage - 1
	whitelist["enabled"] = whiteListRepository.useWhiteList()
	whitelist["sortOldestFirst"] = actualSortOldestFirst
	whitelist["sortNewestFirst"] = !actualSortOldestFirst

	val model = call.withDefaults()
	model["whitelist"] = whitelist

	return model
}

private suspend fun getWhitelistModelWithError(
	call: ApplicationCall,
	whiteListRepository: WhiteListRepository,
	page: Int,
	errorMessage: String,
	sortOldestFirst: Boolean? = null
): MutableMap<String, Any> {
	val model = getWhitelistModel(call, whiteListRepository, page, sortOldestFirst)
	model["error"] = errorMessage
	return model
}

private fun formatDateFromTimestamp(instant: kotlin.time.Instant): String? {
	return try {
		val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
		val zoned = instant.toJavaInstant().atZone(ZoneId.systemDefault())
		formatter.format(zoned)
	} catch (_: Exception) {
		null
	}
}

/**
 * The date an entry expires *on*, as `yyyy-MM-dd`. Expiry instants are stored at the
 * end of the chosen day, so the stored instant is stepped back to land on the date
 * the admin actually picked.
 */
private fun formatDateInputValue(instant: kotlin.time.Instant): String {
	return try {
		instant.toJavaInstant()
			.minusMillis(1)
			.atZone(ZoneId.systemDefault())
			.toLocalDate()
			.toString()
	} catch (_: Exception) {
		""
	}
}
