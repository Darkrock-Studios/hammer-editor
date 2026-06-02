package com.darkrockstudios.apps.hammer.analytics

import com.darkrockstudios.apps.hammer.AnalyticsConfig
import com.darkrockstudios.apps.hammer.AnalyticsProviderType
import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.UmamiConfig
import com.darkrockstudios.apps.hammer.plugins.configureHTTP
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
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
		assertContains(connectSrc, "https://cloud.umami.is")
	}

	@Test
	fun `csp is unchanged when analytics disabled`() = testApplication {
		val csp = cspFor(ServerConfig())
		assertContains(csp, "script-src 'self' https://unpkg.com 'unsafe-inline' 'unsafe-eval'")
		assertContains(csp, "connect-src 'self'")
		assertFalse(csp.contains("umami"))
	}
}
