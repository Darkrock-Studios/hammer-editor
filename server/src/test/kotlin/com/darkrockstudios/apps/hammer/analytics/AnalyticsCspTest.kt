package com.darkrockstudios.apps.hammer.analytics

import com.darkrockstudios.apps.hammer.AnalyticsConfig
import com.darkrockstudios.apps.hammer.AnalyticsProviderType
import com.darkrockstudios.apps.hammer.GoogleConfig
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.UmamiConfig
import com.darkrockstudios.apps.hammer.plugins.configureHTTP
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AnalyticsCspTest {

	private suspend fun ApplicationTestBuilder.cspFor(config: ServerConfig): String {
		application {
			configureHTTP(config)
			routing {
				get("/") { call.respondText("ok") }
			}
		}
		val response: HttpResponse = client.get("/")
		return response.headers["Content-Security-Policy"] ?: ""
	}

	@Test
	fun `umami host is added to script-src and connect-src`() = testApplication {
		val config = ServerConfig(
			analytics = AnalyticsConfig(
				type = AnalyticsProviderType.UMAMI,
				umami = UmamiConfig(websiteId = "abc-123"),
			)
		)
		val csp = cspFor(config)
		val scriptSrc = csp.split(";").first { it.trim().startsWith("script-src") }
		val connectSrc = csp.split(";").first { it.trim().startsWith("connect-src") }
		assertContains(scriptSrc, "https://cloud.umami.is")
		// Cloud events go to the gateway origin, so that — not the script host — must be in connect-src.
		assertContains(connectSrc, "https://gateway.umami.is")
	}

	@Test
	fun `google hosts are added to script-src, connect-src, and img-src`() = testApplication {
		val config = ServerConfig(
			analytics = AnalyticsConfig(
				type = AnalyticsProviderType.GOOGLE,
				google = GoogleConfig(measurementId = "G-ABC123"),
			)
		)
		val csp = cspFor(config)
		val scriptSrc = csp.split(";").first { it.trim().startsWith("script-src") }
		val connectSrc = csp.split(";").first { it.trim().startsWith("connect-src") }
		val imgSrc = csp.split(";").first { it.trim().startsWith("img-src") }
		assertContains(scriptSrc, "https://*.googletagmanager.com")
		assertContains(connectSrc, "https://*.google-analytics.com")
		assertContains(imgSrc, "https://*.google-analytics.com")
	}

	@Test
	fun `csp is unchanged when analytics disabled`() = testApplication {
		val csp = cspFor(ServerConfig())
		assertContains(csp, "script-src 'self' https://unpkg.com 'unsafe-inline' 'unsafe-eval'")
		assertContains(csp, "connect-src 'self'")
		assertFalse(csp.contains("umami"))
	}
}
