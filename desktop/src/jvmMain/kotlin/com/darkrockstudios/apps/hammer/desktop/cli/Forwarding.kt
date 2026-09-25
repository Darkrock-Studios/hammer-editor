package com.darkrockstudios.apps.hammer.desktop.cli

import com.darkrockstudios.apps.hammer.operations.OperationException
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import io.github.aakira.napier.Napier
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.Timer
import kotlin.concurrent.timerTask
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Lets CLI calls run inside the running app, against its live state, over a Unix domain socket, and
 * a second launch of the app hand its arguments to the first. Each connection carries one request
 * line and one reply line.
 */
object Forwarding {
	private const val SOCKET_DIRECTORY = "run"
	private const val SOCKET_NAME = "hammer.sock"

	/**
	 * Inside a directory only this user can enter, so the socket is private from the moment it exists,
	 * whatever the config directory's own permissions.
	 */
	fun socketPath(configDirectory: File): Path = File(File(configDirectory, SOCKET_DIRECTORY), SOCKET_NAME).toPath()

	/** Operations that stay with the app while it runs: its own sync and login must not be raced. */
	private val APP_ONLY = setOf("account", "sync")

	/** Moving a project's directory would race anything in the app that opens it, such as a sync. */
	private val APP_ONLY_OPERATIONS = setOf("project.rename", "project.delete")

	private val json = Json { ignoreUnknownKeys = true }

	/** An operation to run, or, when [launch] is set, a second launch's arguments. */
	@Serializable
	private class Request(
		val operation: String = "",
		val input: JsonElement = JsonNull,
		val launch: List<String>? = null,
	)

	@Serializable
	private class Reply(val output: JsonElement? = null, val exitCode: Int = 0, val error: Error? = null) {
		@Serializable
		class Error(val kind: String, val message: String)
	}

	/** The app's side. [onLaunch] gets the arguments of each second launch handed over. */
	class Server(
		private val socket: Path,
		private val registry: () -> OperationRegistry,
		private val onLaunch: (args: List<String>) -> Unit,
	) : AutoCloseable {
		private val channel: ServerSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)

		/** Binds, replacing a socket file a crashed app left behind, and serves on [scope] until closed. */
		fun start(scope: CoroutineScope) {
			privateDirectory(socket.parent)
			Files.deleteIfExists(socket)
			channel.bind(UnixDomainSocketAddress.of(socket))
			scope.launch(Dispatchers.IO) {
				while (true) {
					val client = try {
						channel.accept()
					} catch (e: AsynchronousCloseException) {
						break
					} catch (e: ClosedChannelException) {
						break
					}
					launch {
						// Closing the channel is what ends a blocked read, if the request never comes.
						val watchdog = launch {
							delay(REQUEST_TIMEOUT)
							client.close()
						}
						client.use { serve(it, watchdog) }
					}
				}
			}
		}

		private suspend fun serve(client: SocketChannel, watchdog: Job) {
			val reader = Channels.newReader(client, Charsets.UTF_8).buffered()
			val writer = Channels.newWriter(client, Charsets.UTF_8).buffered()
			val reply = try {
				val line = try {
					reader.readLine()
				} catch (e: IOException) {
					null
				} ?: return
				watchdog.cancel()
				val request = json.decodeFromString(Request.serializer(), line)
				val launch = request.launch
				when {
					launch != null -> {
						onLaunch(launch)
						Reply(output = JsonNull)
					}
					request.operation.substringBefore('.') in APP_ONLY || request.operation in APP_ONLY_OPERATIONS ->
						Reply(error = Reply.Error(REFUSED, APP_ONLY_MESSAGE))
					else -> {
						val output = registry().dispatch(request.operation, request.input)
						Reply(output = output, exitCode = registry().exitCode(request.operation, output))
					}
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: OperationException) {
				Reply(error = Reply.Error(e.kind.name, e.message.orEmpty()))
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.e(e) { "Forwarded CLI call failed" }
				Reply(error = Reply.Error(FAILED, e.message ?: "Failed"))
			}
			writer.write(json.encodeToString(Reply.serializer(), reply))
			writer.newLine()
			writer.flush()
		}

		override fun close() {
			channel.close()
			Files.deleteIfExists(socket)
		}

		private fun privateDirectory(directory: Path) {
			Files.createDirectories(directory)
			try {
				Files.setPosixFilePermissions(
					directory,
					setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
				)
			} catch (e: UnsupportedOperationException) {
				// Windows: the directory inherits the per-user ACL of the user's profile.
			}
		}
	}

	/** Thrown when the app answers but will not run the call, since the call is its own. */
	class Refused(message: String) : Exception(message)

	/**
	 * Runs [operation] in the app, returning its output and exit code, or null when no app is listening,
	 * so the caller can run it headless instead.
	 */
	fun dispatch(socket: Path, operation: String, input: JsonElement): Pair<JsonElement, Int>? {
		val client = try {
			SocketChannel.open(UnixDomainSocketAddress.of(socket))
		} catch (e: IOException) {
			return null
		}
		// A hung app must not hang every CLI call and agent with it.
		val timeout = Timer(true).apply { schedule(timerTask { client.close() }, REPLY_TIMEOUT.inWholeMilliseconds) }
		val reply = client.use {
			val writer = Channels.newWriter(client, Charsets.UTF_8).buffered()
			writer.write(json.encodeToString(Request.serializer(), Request(operation, input)))
			writer.newLine()
			writer.flush()
			val line = Channels.newReader(client, Charsets.UTF_8).buffered().readLine()
				?: throw IOException("The running app closed the connection without replying")
			json.decodeFromString(Reply.serializer(), line)
		}
		timeout.cancel()
		val error = reply.error ?: return reply.output!! to reply.exitCode
		val kind = OperationException.Kind.entries.firstOrNull { it.name == error.kind }
		throw when {
			kind != null -> OperationException(kind, error.message)
			error.kind == REFUSED -> Refused(error.message)
			else -> IOException(error.message)
		}
	}

	/**
	 * Hands a second launch's [args] to the app listening on [socket]. False when no app answers or it
	 * refuses, so this launch can start as usual.
	 */
	fun handOff(socket: Path, args: List<String>): Boolean {
		val client = try {
			SocketChannel.open(UnixDomainSocketAddress.of(socket))
		} catch (e: IOException) {
			return false
		}
		val timeout = Timer(true).apply { schedule(timerTask { client.close() }, HAND_OFF_TIMEOUT.inWholeMilliseconds) }
		return try {
			client.use {
				val writer = Channels.newWriter(client, Charsets.UTF_8).buffered()
				writer.write(json.encodeToString(Request.serializer(), Request(launch = args)))
				writer.newLine()
				writer.flush()
				val line = Channels.newReader(client, Charsets.UTF_8).buffered().readLine() ?: return false
				json.decodeFromString(Reply.serializer(), line).error == null
			}
		} catch (e: IOException) {
			Napier.w(e) { "Could not hand this launch to the running app" }
			false
		} finally {
			timeout.cancel()
		}
	}

	private const val REFUSED = "Refused"
	private const val FAILED = "Failed"
	private val REQUEST_TIMEOUT = 10.seconds
	private val REPLY_TIMEOUT = 10.minutes
	private val HAND_OFF_TIMEOUT = 5.seconds
	private const val APP_ONLY_MESSAGE =
		"Hammer is open, and this has to wait until it is closed. Use the app, or close it and run this again."
}
