package cli

import com.darkrockstudios.apps.hammer.desktop.cli.Cli
import com.darkrockstudios.apps.hammer.operations.cli.CliIo
import okio.Buffer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Only what runs before Hammer starts: finding the command and reading its options. */
class CliTest {

	private val stdout = Buffer()
	private val stderr = Buffer()

	private fun run(vararg args: String) = Cli.run(args.toList(), CliIo(Buffer(), stdout, stderr))

	@Test
	fun `app launch options are not CLI calls`() {
		assertTrue(Cli.isInvocation(arrayOf("project", "list")))
		assertEquals(false, Cli.isInvocation(arrayOf("--project", "Novel")))
		assertEquals(false, Cli.isInvocation(emptyArray()))
	}

	@Test
	fun `help lists every operation`() {
		assertEquals(Cli.EXIT_OK, run("help"))
		val help = stdout.readUtf8()
		assertTrue("scene read" in help)
		assertTrue("project export" in help)
	}

	@Test
	fun `an operation's help lists its options`() {
		assertEquals(Cli.EXIT_OK, run("project", "export", "--help"))
		val help = stdout.readUtf8()
		assertTrue("--project <string> (required)" in help)
		assertTrue("--scene-ids <integer>, repeatable" in help)
		assertTrue("--out <file>" in help)
	}

	@Test
	fun `scene text comes from stdin`() {
		assertEquals(Cli.EXIT_OK, run("scene", "write", "--help"))
		val help = stdout.readUtf8()
		assertTrue("--markdown <string>, or stdin" in help)
		assertTrue("--mode <draft|live> (required)" in help)
	}

	@Test
	fun `destructive commands need confirming`() {
		assertEquals(Cli.EXIT_USAGE, run("scene", "delete", "--project", "X", "--id", "1"))
		assertTrue("--confirm" in stderr.readUtf8())

		assertEquals(Cli.EXIT_USAGE, run("scene", "delete", "--confirm=yes", "--project", "X", "--id", "1"))
		assertTrue("takes no value" in stderr.readUtf8())

		assertEquals(Cli.EXIT_OK, run("scene", "delete", "--help"))
		assertTrue("--confirm" in stdout.readUtf8())
	}

	@Test
	fun `file inputs come from --in`() {
		assertEquals(Cli.EXIT_OK, run("entry", "image", "set", "--help"))
		assertTrue("--in <file>" in stdout.readUtf8())

		assertEquals(Cli.EXIT_USAGE, run("note", "read", "--project", "X", "--id", "1", "--in", "notes.txt"))
		assertTrue("no file input" in stderr.readUtf8())
	}

	@Test
	fun `unknown commands and options are usage errors`() {
		assertEquals(Cli.EXIT_USAGE, run("scene", "reed"))
		assertTrue("Unknown command 'scene reed'" in stderr.readUtf8())

		assertEquals(Cli.EXIT_USAGE, run("scene", "read", "--project", "X", "--colour", "red"))
		assertTrue("has no option --colour" in stderr.readUtf8())

		assertEquals(Cli.EXIT_USAGE, run("scene", "read", "--project", "X", "--id", "twelve"))
		assertTrue("--id needs a whole number" in stderr.readUtf8())
	}

	@Test
	fun `passwords are never taken from the command line`() {
		assertEquals(Cli.EXIT_USAGE, run("account", "login", "--url", "hammer.ink", "--email", "a@b.c", "--password", "hunter2"))
		assertTrue("never the command line" in stderr.readUtf8())

		assertEquals(Cli.EXIT_USAGE, run("account", "login", "--json", """{"url":"h","email":"e","password":"hunter2"}"""))
		assertTrue("never the command line" in stderr.readUtf8())

		assertEquals(Cli.EXIT_OK, run("account", "login", "--help"))
		assertTrue("<password> from stdin or HAMMER_PASSWORD" in stdout.readUtf8())
	}
}
