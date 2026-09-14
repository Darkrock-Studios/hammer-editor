package util

import com.darkrockstudios.apps.hammer.common.unredirectMsixPath
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The literal path the app sees, and the container path Explorer needs, taken from the Store
 * install that reported the broken open-logs button.
 */
private const val LOCAL_APP_DATA = "C:\\Users\\someone\\AppData\\Local"
private const val APP_VISIBLE = "$LOCAL_APP_DATA\\DarkrockStudios\\hammer\\0\\logs"
private const val CONTAINER =
	"$LOCAL_APP_DATA\\Packages\\DarkRockStudios.HammerEditor_eee5gzg80tyea" +
		"\\LocalCache\\Local\\DarkrockStudios\\hammer\\0\\logs"

class MsixPathsTest {

	@Test
	fun `a redirected path is rewritten into the package container`() {
		assertEquals(CONTAINER, unredirectMsixPath(APP_VISIBLE, LOCAL_APP_DATA))
	}

	@Test
	fun `a path already inside the container is left alone`() {
		assertEquals(CONTAINER, unredirectMsixPath(CONTAINER, LOCAL_APP_DATA))
	}

	@Test
	fun `a path outside local appdata is left alone`() {
		val outside = "C:\\Users\\someone\\Documents\\Hammer"
		assertEquals(outside, unredirectMsixPath(outside, LOCAL_APP_DATA))
	}

	@Test
	fun `a trailing separator on local appdata does not double up`() {
		assertEquals(CONTAINER, unredirectMsixPath(APP_VISIBLE, "$LOCAL_APP_DATA\\"))
	}

	/** Windows paths are case-insensitive, and the env var's casing is not guaranteed to match. */
	@Test
	fun `the local appdata prefix matches case insensitively`() {
		val lowercased = LOCAL_APP_DATA.lowercase()
		val expected = CONTAINER.replaceFirst(LOCAL_APP_DATA, lowercased)
		assertEquals(expected, unredirectMsixPath(APP_VISIBLE, lowercased))
	}

	@Test
	fun `a missing local appdata leaves the path alone`() {
		assertEquals(APP_VISIBLE, unredirectMsixPath(APP_VISIBLE, null))
		assertEquals(APP_VISIBLE, unredirectMsixPath(APP_VISIBLE, ""))
	}
}
