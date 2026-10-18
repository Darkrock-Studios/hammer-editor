package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.account.AdminPasswordResetResult
import com.darkrockstudios.apps.hammer.account.PasswordResetRepository
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidationResult
import com.darkrockstudios.apps.hammer.base.validate.PasswordValidator
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.utilities.getRootDataDirectory
import com.github.ajalt.clikt.core.CliktCommand
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class ResetAdminPasswordCommand(
	private val resetter: AdminPasswordResetter = DatabaseAdminPasswordResetter(),
	private val configLoader: (String?) -> ServerConfig = { loadServerConfig(it) },
) : CliktCommand(name = "reset-admin-password") {
	override fun help(context: Context) =
		"Set a new password for the admin account. The password is read from stdin, or a hidden " +
			"prompt when run in a terminal; it is never taken from the command line. Signs the " +
			"account out of every device. With embedded storage the server must be stopped first."

	private val configPath by option(
		"-c", "--config",
		help = "Server config; selects the database. Defaults to the one the server itself would load",
	)

	@Suppress("unused")
	private val devMode by option("-d", "--dev", help = "Accepted for parity with the server command; the database is the same either way").flag()

	private val email by option(
		"-e", "--email",
		help = "Which admin to reset; required when the server has more than one",
	)

	override fun run() {
		val config = configLoader(configPath)
		val newPassword = readPassword()

		val result = try {
			resetter.reset(config, email, newPassword)
		} catch (e: DatabaseUnavailableException) {
			throw CliktError(
				"Could not open the database: ${e.cause?.message ?: e.message}\n" +
					"With embedded storage the running server holds the data directory; stop it and retry."
			)
		}

		when (result) {
			is AdminPasswordResetResult.Success ->
				echo("Password updated for ${result.email}. Every device has been signed out.")

			is AdminPasswordResetResult.NoAdmin ->
				throw CliktError("No admin account exists on this server.")

			is AdminPasswordResetResult.MultipleAdmins -> throw CliktError(
				"This server has several admins; pass --email to pick one:\n" +
					result.emails.joinToString("\n") { "  $it" }
			)

			is AdminPasswordResetResult.NoSuchAccount ->
				throw CliktError("No account with the email ${result.email}.")

			is AdminPasswordResetResult.NotAdmin ->
				throw CliktError("${result.email} is not an admin; this command only resets admin accounts.")

			is AdminPasswordResetResult.PasswordRejected ->
				throw CliktError("Password rejected: ${describe(result.result)}")
		}
	}

	private fun readPassword(): String {
		val terminal = currentContext.terminal
		if (!terminal.terminalInfo.inputInteractive) {
			return terminal.readLineOrNull(hideInput = false)
				?.takeIf { it.isNotEmpty() }
				?: throw CliktError("No password on stdin; pipe the new password in or run from a terminal.")
		}

		echo("New admin password: ", trailingNewline = false)
		val first = terminal.readLineOrNull(hideInput = true)
			?.takeIf { it.isNotEmpty() }
			?: throw CliktError("No password entered.")
		echo("Repeat the password: ", trailingNewline = false)
		val second = terminal.readLineOrNull(hideInput = true)
		if (first != second) throw CliktError("The passwords do not match.")
		return first
	}

	private fun describe(result: PasswordValidationResult): String = when (result) {
		PasswordValidationResult.TOO_SHORT -> "at least ${PasswordValidator.MIN_LENGTH} characters are required."
		PasswordValidationResult.TOO_LONG -> "at most ${PasswordValidator.MAX_LENGTH} characters are allowed."
		else -> result.name.lowercase().replace('_', ' ')
	}
}

/** Runs one admin reset against the database [ServerConfig] points at. */
fun interface AdminPasswordResetter {
	fun reset(config: ServerConfig, email: String?, newPassword: String): AdminPasswordResetResult
}

/** The database could not be opened; with embedded storage that usually means the server is running. */
class DatabaseUnavailableException(cause: Throwable) : Exception(cause.message, cause)

class DatabaseAdminPasswordResetter : AdminPasswordResetter {
	override fun reset(config: ServerConfig, email: String?, newPassword: String): AdminPasswordResetResult {
		refuseMissingEmbeddedData(config)
		// The DAOs are KoinComponents and resolve their dispatcher from the global context.
		val koinApp = startKoin {
			modules(
				mainModule(KtorSimpleLogger("ResetAdminPassword")),
				module { single { config } },
			)
		}
		val database: Database = koinApp.koin.get()
		try {
			try {
				database.initialize()
			} catch (e: IllegalStateException) {
				throw DatabaseUnavailableException(e)
			} catch (e: PoolInitializationException) {
				throw DatabaseUnavailableException(e)
			}
			val repository: PasswordResetRepository = koinApp.koin.get()
			return runBlocking { repository.resetAdminPassword(email, newPassword) }
		} finally {
			database.close()
			stopKoin()
		}
	}

	/** Opening an absent embedded data directory would initdb a fresh, admin-less database. */
	private fun refuseMissingEmbeddedData(config: ServerConfig) {
		if (config.storage.type != StorageMode.EMBEDDED) return
		val dataDir = getRootDataDirectory(FileSystem.SYSTEM) / config.storage.embedded.dataDirName
		val hasData = FileSystem.SYSTEM.listOrNull(dataDir)?.isNotEmpty() == true
		if (!hasData) {
			throw DatabaseUnavailableException(
				IllegalStateException("no embedded database at $dataDir; check --config and which user runs the command")
			)
		}
	}
}
