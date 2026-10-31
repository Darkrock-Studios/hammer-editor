package com.darkrockstudios.apps.hammer.e2e

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.ApiSceneType
import com.darkrockstudios.apps.hammer.e2e.util.E2eTestData
import com.darkrockstudios.apps.hammer.e2e.util.TestProject
import com.darkrockstudios.apps.hammer.e2e.util.WebEndToEndTest
import com.darkrockstudios.apps.hammer.frontend.utils.ProjectName
import com.darkrockstudios.apps.hammer.story.StoryImageSigner
import com.darkrockstudios.apps.hammer.utilities.DiskCache
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import korlibs.crypto.encoding.Base64
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Images embedded in story prose, as the web reader links and serves them. */
class StoryImagesTest : WebEndToEndTest() {

	private val email = "jane@test.com"
	private val password = "password123!@#"
	private val penName = "JaneAuthor"
	private val projectNameText = "Insurgency"
	private val projectUuid = Uuid.random()
	private val projectSegment = ProjectName.projectSegment(projectNameText, projectUuid.toString())

	private val png = java.util.Base64.getDecoder().decode(
		"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
	)
	private val svg = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""".encodeToByteArray()

	private fun userId(): Long =
		database().serverDatabase.accountQueries.findAccount(email).executeAsOne().id

	private fun projectRowId(): Long =
		database().serverDatabase.projectQueries.findProjectByName(userId(), projectNameText).executeAsOne().id

	private fun storyPath(password: String? = null): String =
		route("a/$penName/$projectSegment" + if (password != null) "?p=$password" else "")

	private fun imagePath(imageId: Int, query: String = ""): String =
		route("a/$penName/$projectSegment/image/$imageId$query")

	/** Scene 1 shows images 2 and 4; image 3 is in the project but no prose refers to it. */
	private fun seedStory() = runBlocking {
		seedWhitelistedAccount(email, password, penName)
		E2eTestData.createProject(TestProject(name = projectNameText, uuid = projectUuid, userId = userId()), database())
		insert(
			ApiProjectEntity.SceneEntity(
				id = 1,
				sceneType = ApiSceneType.Scene,
				order = 0,
				name = "Chapter One",
				path = listOf(0),
				content = "The road north.\n\n![Map](hammer://image/2)\n\n![Seal](hammer://image/4)\n\nThey set out.",
			)
		)
		insert(image(2, "Map", "png", png))
		insert(image(3, "Secret", "png", png))
		insert(image(4, "Seal", "svg", svg))
	}

	private fun image(id: Int, name: String, extension: String, bytes: ByteArray) = ApiProjectEntity.ImageEntity(
		id = id,
		name = name,
		fileExtension = extension,
		base64 = Base64.encode(bytes, url = true),
	)

	private fun insert(entity: ApiProjectEntity) =
		E2eTestData.insertEntity(userId(), projectRowId(), entity, database(), encryptor())

	private fun grantAccess(password: String?) {
		database().serverDatabase.projectAccessQueries.insertAccessReturningId(
			project_id = projectRowId(),
			access_password = password,
			expires_at = null,
		).executeAsOne()
	}

	private fun setProjectData(content: String, hash: String = "hash-$content") {
		database().serverDatabase.projectDataQueries.upsert(
			userId = userId(),
			projectId = projectRowId(),
			content = content,
			hash = hash,
			updatedAt = Clock.System.now(),
		)
	}

	private fun setCover(imageId: Int) = setProjectData("""{"coverImageId":$imageId}""")

	/** The cover's src on [body], attribute escaping undone; null when the page shows no cover. */
	private fun coverSrc(body: String): String? {
		if (!body.contains("story-cover") && !body.contains("reader-cover__image")) return null
		val src = Regex("""<img class="(?:story-cover|reader-cover__image)" src="([^"]*)"""").find(body)
		return assertNotNull(src, "cover markup should carry its image").groupValues[1]
			.replace("&#61;", "=")
			.replace("&amp;", "&")
	}

	/** The absolute URL the page links image [imageId] at, attribute escaping undone. */
	private suspend fun linkedImageUrl(pagePath: String, imageId: Int): String {
		val body = client().get(pagePath).bodyAsText()
		val src = Regex("""src="([^"]*/image/$imageId\?s=[^"]*)"""").find(body)
		assertNotNull(src, "the page should link image $imageId through a signed url")
		return route(src.groupValues[1].removePrefix("/").replace("&amp;", "&"))
	}

	@Test
	fun `a public page links its images through signed urls`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val body = client().get(storyPath()).bodyAsText()

		assertTrue(body.contains("/a/$penName/$projectSegment/image/2?s="), body)
		assertFalse(body.contains("hammer://"), "no reference may reach the browser unresolved")
		assertFalse(body.contains("/image/3"), "an unreferenced image is never linked")
	}

	@Test
	fun `a cached page still resolves its images when served again`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val first = linkedImageUrl(storyPath(), 2)
		val second = linkedImageUrl(storyPath(), 2)

		assertEquals(first, second)
		val cached = cachedFiles(DiskCache.STORY_HTML)
		assertEquals(1, cached.size)
		val cachedHtml = fileSystem.read(cached.single()) { readUtf8() }
		assertTrue(cachedHtml.contains("hammer://image/2"), "the cache keeps the reference, not a url")
		assertFalse(cachedHtml.contains("?s="), "the cache holds no signed url")
	}

	@Test
	fun `a signed image link serves the image`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val response = client().get(linkedImageUrl(storyPath(), 2))

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals("image/png", response.headers[HttpHeaders.ContentType])
		assertContentEquals(png, response.readRawBytes())
		assertEquals("inline", response.headers[HttpHeaders.ContentDisposition])
		assertTrue(assertNotNull(response.headers[HttpHeaders.ETag]).startsWith("W/\""))
	}

	@Test
	fun `a reader holding the image validator is answered 304`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		val url = linkedImageUrl(storyPath(), 2)

		val etag = assertNotNull(client().get(url).headers[HttpHeaders.ETag])
		val response = client().get(url) { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.NotModified, response.status)
		assertEquals(etag, response.headers[HttpHeaders.ETag])
	}

	@Test
	fun `a tampered signature is not found`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		val url = linkedImageUrl(storyPath(), 2)
		val signature = url.substringAfter("?s=")
		val tampered = (if (signature[0] == 'A') 'B' else 'A') + signature.drop(1)

		assertEquals(HttpStatusCode.NotFound, client().get(imagePath(2, "?s=$tampered")).status)
		assertEquals(HttpStatusCode.NotFound, client().get(imagePath(2)).status)
		// A signature is for one image only.
		assertEquals(HttpStatusCode.NotFound, client().get(imagePath(3, "?s=$signature")).status)
	}

	@Test
	fun `an image no page links is not found`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		assertEquals(HttpStatusCode.NotFound, client().get(imagePath(3)).status)
		assertEquals(HttpStatusCode.NotFound, client().get(imagePath(3, "?s=forged")).status)
	}

	@Test
	fun `a valid signature does not open a story the reader cannot see`(): Unit = runBlocking {
		doStartServer()
		seedStory() // never published, never shared
		val signature = StoryImageSigner(tokenHasher()).sign(ProjectId(projectUuid.toString()), 2)

		assertEquals(HttpStatusCode.NotFound, client().get(imagePath(2, "?s=$signature")).status)
	}

	@Test
	fun `a private share's image needs the share's password`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = "secret")

		val url = linkedImageUrl(storyPath(password = "secret"), 2)
		assertTrue(url.endsWith("&p=secret"), url)

		assertEquals(HttpStatusCode.OK, client().get(url).status)
		assertEquals(HttpStatusCode.NotFound, client().get(url.removeSuffix("&p=secret")).status)
		assertEquals(HttpStatusCode.NotFound, client().get(url.replace("&p=secret", "&p=wrong")).status)
	}

	@Test
	fun `an svg image is served sandboxed`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)

		val response = client().get(linkedImageUrl(storyPath(), 4))

		assertEquals(HttpStatusCode.OK, response.status)
		assertEquals("image/svg+xml", response.headers[HttpHeaders.ContentType])
		assertEquals(
			listOf("default-src 'none'; style-src 'unsafe-inline'; sandbox"),
			response.headers.getAll("Content-Security-Policy"),
		)
		assertEquals("nosniff", response.headers["X-Content-Type-Options"])
	}

	@Test
	fun `the author's image route needs a session`(): Unit = runBlocking {
		doStartServer()
		seedStory()

		val anonymous = HttpClient { followRedirects = false }
		val response = try {
			anonymous.get(route("story/$projectSegment/image/2"))
		} finally {
			anonymous.close()
		}

		assertNotEquals(HttpStatusCode.OK, response.status)
		assertNotEquals("image/png", response.headers[HttpHeaders.ContentType])
	}

	@Test
	fun `the author sees every image of their own story`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		val authed = login(email, password)
		try {
			val page = authed.get(route("story/$projectSegment/read?chapter=1")).bodyAsText()
			assertTrue(page.contains("""src="/story/$projectSegment/image/2""""), page)
			assertTrue(page.contains("""src="/story/$projectSegment/image/4""""), page)
			assertFalse(page.contains("hammer://"), "no reference may reach the browser unresolved")

			val image = authed.get(route("story/$projectSegment/image/3"))
			assertEquals(HttpStatusCode.OK, image.status)
			assertContentEquals(png, image.readRawBytes())

			// A scene's id is not an image's.
			assertEquals(HttpStatusCode.NotFound, authed.get(route("story/$projectSegment/image/1")).status)
			assertEquals(HttpStatusCode.NotFound, authed.get(route("story/$projectSegment/image/99")).status)
		} finally {
			authed.close()
		}
	}

	@Test
	fun `the author's page shows the cover`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		setCover(3)
		val authed = login(email, password)
		try {
			val page = authed.get(route("story/$projectSegment")).bodyAsText()
			assertEquals("/story/$projectSegment/image/3", coverSrc(page), page)
			assertTrue(Regex("""<img class="story-cover" src="[^"]*" alt="">""").containsMatchIn(page), page)

			val image = authed.get(route("story/$projectSegment/image/3"))
			assertEquals(HttpStatusCode.OK, image.status)
			assertContentEquals(png, image.readRawBytes())
		} finally {
			authed.close()
		}
	}

	@Test
	fun `a public page shows the cover through a signed url`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		// No prose refers to image 3, so only the cover's own signature can open it.
		setCover(3)

		val page = client().get(storyPath()).bodyAsText()
		val src = assertNotNull(coverSrc(page))
		assertTrue(src.startsWith("/a/$penName/$projectSegment/image/3?s="), src)
		assertTrue(Regex("""<img class="reader-cover__image" src="[^"]*" alt="" fetchpriority="high">""").containsMatchIn(page), page)

		val response = client().get(route(src.removePrefix("/")))
		assertEquals(HttpStatusCode.OK, response.status)
		assertContentEquals(png, response.readRawBytes())
	}

	@Test
	fun `a story without a cover shows none`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		setProjectData("""{"authorName":"Jane"}""")

		assertNull(coverSrc(client().get(storyPath()).bodyAsText()))
		val authed = login(email, password)
		try {
			assertNull(coverSrc(authed.get(route("story/$projectSegment")).bodyAsText()))
		} finally {
			authed.close()
		}
	}

	@Test
	fun `a cover whose image the server lacks shows none`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		// Scene 1 exists, but is not an image.
		for (coverId in listOf(99, 1)) {
			setCover(coverId)

			assertNull(coverSrc(client().get(storyPath()).bodyAsText()), "cover $coverId")
			val authed = login(email, password)
			try {
				assertNull(coverSrc(authed.get(route("story/$projectSegment")).bodyAsText()), "cover $coverId")
			} finally {
				authed.close()
			}
		}
	}

	@Test
	fun `a private share's cover needs the share's password`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = "secret")
		setCover(3)

		val src = assertNotNull(coverSrc(client().get(storyPath(password = "secret")).bodyAsText()))
		assertTrue(src.endsWith("&p=secret"), src)
		val url = route(src.removePrefix("/"))

		assertEquals(HttpStatusCode.OK, client().get(url).status)
		assertEquals(HttpStatusCode.NotFound, client().get(url.removeSuffix("&p=secret")).status)
		assertEquals(HttpStatusCode.NotFound, client().get(url.replace("&p=secret", "&p=wrong")).status)
	}

	@Test
	fun `a public page is not revalidated over a cover being set`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		val etag = assertNotNull(client().get(storyPath()).headers[HttpHeaders.ETag])

		setCover(3)
		val response = client().get(storyPath()) { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.OK, response.status)
		assertNotNull(coverSrc(response.bodyAsText()))
	}

	@Test
	fun `a public page is not revalidated over its cover's image arriving`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		setCover(5)
		val before = client().get(storyPath())
		assertNull(coverSrc(before.bodyAsText()))
		val etag = assertNotNull(before.headers[HttpHeaders.ETag])

		insert(image(5, "Cover", "png", png))
		val response = client().get(storyPath()) { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.OK, response.status)
		assertNotNull(coverSrc(response.bodyAsText()))
	}

	@Test
	fun `a public page with a cover is answered 304 while nothing changes`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		setCover(3)

		val first = client().get(storyPath())
		assertNotNull(coverSrc(first.bodyAsText()))
		val etag = assertNotNull(first.headers[HttpHeaders.ETag])
		val second = client().get(storyPath()) { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.NotModified, second.status)
	}

	@Test
	fun `a public page is not revalidated over its cover being cleared`(): Unit = runBlocking {
		doStartServer()
		seedStory()
		grantAccess(password = null)
		setCover(3)
		val before = client().get(storyPath())
		assertNotNull(coverSrc(before.bodyAsText()))
		val etag = assertNotNull(before.headers[HttpHeaders.ETag])

		setProjectData("""{"authorName":"Jane"}""")
		val response = client().get(storyPath()) { header(HttpHeaders.IfNoneMatch, etag) }

		assertEquals(HttpStatusCode.OK, response.status)
		assertNull(coverSrc(response.bodyAsText()))
	}
}
