package com.darkrockstudios.apps.hammer.operations.core

import com.darkrockstudios.apps.hammer.common.data.account.AccountUseCase
import com.darkrockstudios.apps.hammer.common.data.account.ServerSetupResult
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.data.globalsettings.parseServerUrl
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.FromStdin
import com.darkrockstudios.apps.hammer.operations.Operation
import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.invalidInput
import com.darkrockstudios.apps.hammer.operations.NoInput
import com.darkrockstudios.apps.hammer.operations.operation
import kotlinx.serialization.Serializable
import okio.IOException

// Account operations are never agent-visible: an agent has no business with the writer's credentials.
internal fun accountOperations(): List<Operation<*, *>> = listOf(
	operation<AccountStatusInput, AccountStatus>(
		name = "account.status",
		description = "The sync server and account this install uses, and optionally whether the server still accepts it.",
		access = Access.Read,
		scope = OperationScope.Account,
	) { input ->
		val settings = koinGet<GlobalSettingsStore>().serverSettings
		val loggedIn = settings != null && settings.userId > -1 && settings.bearerToken != null
		AccountStatus(
			server = settings?.url,
			ssl = settings?.ssl,
			email = settings?.email,
			loggedIn = loggedIn,
			tokenValid = if (input.check && loggedIn) koinGet<AccountUseCase>().testAuth() else null,
		)
	},
	operation<LoginInput, AccountStatus>(
		name = "account.login",
		description = "Log in to a sync server with an existing account. Accounts are created in the app.",
		access = Access.Write,
		scope = OperationScope.Account,
	) { input ->
		val server = parseServerUrl(input.url)
		val current = koinGet<GlobalSettingsStore>().serverSettings
		if (current != null && current.userId > -1 && (current.url != server.host || current.ssl != server.ssl)) {
			// Projects stay linked by the old server's ids; carrying them to another server confuses both.
			invalidInput("Logged in to ${current.url}. Run 'hammer account logout' first, as the app requires.")
		}
		when (val result = koinGet<AccountUseCase>().setupServer(server.host, input.email, input.password, create = false, ssl = server.ssl)) {
			ServerSetupResult.Success -> Unit
			is ServerSetupResult.TermsRequired -> {
				koinGet<GlobalSettingsStore>().deleteServerSettings()
				invalidInput("The server asks you to accept its terms first. Log in once from the Hammer app.")
			}
			is ServerSetupResult.Failure -> {
				val message = result.displayMessage?.text(koinGet<StrRes>()) ?: result.exception?.message ?: "Login failed"
				// Not reaching the server says nothing about the credentials.
				if (result.exception is IOException) throw IOException("Could not reach ${server.host}: $message")
				throw OperationException(OperationException.Kind.Unauthorized, message)
			}
		}
		val settings = koinGet<GlobalSettingsStore>().serverSettings
		AccountStatus(server = settings?.url, ssl = settings?.ssl, email = settings?.email, loggedIn = true, tokenValid = true)
	},
	operation<NoInput, AccountStatus>(
		name = "account.logout",
		description = "Forget the sync server and unlink every project from it, as removing the server in Settings does.",
		access = Access.Write,
		scope = OperationScope.Account,
	) {
		koinGet<GlobalSettingsStore>().deleteServerSettings()
		val projects = koinGet<ProjectsRepository>()
		projects.getProjects().forEach { projects.removeProjectId(it) }
		AccountStatus(server = null, ssl = null, email = null, loggedIn = false, tokenValid = null)
	},
)

@Serializable
data class AccountStatusInput(
	/** Ask the server whether the stored token is still good; needs the network. */
	val check: Boolean = false,
)

@Serializable
data class LoginInput(
	/** The server's address, with `https://` or `http://` optional. */
	val url: String,
	val email: String,
	@FromStdin(secret = true)
	val password: String,
)

@Serializable
data class AccountStatus(
	val server: String?,
	val ssl: Boolean?,
	val email: String?,
	val loggedIn: Boolean,
	/** Null unless checked. */
	val tokenValid: Boolean?,
)
