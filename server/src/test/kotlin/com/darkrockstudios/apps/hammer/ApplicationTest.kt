package com.darkrockstudios.apps.hammer

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.plugins.configureRouting
import com.darkrockstudios.apps.hammer.plugins.configureSecurity
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import org.junit.Before
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest : BaseTest() {
    private lateinit var accountsRepository: AccountsRepository

    @Before
    override fun setup() {
        super.setup()

        accountsRepository = mockk()
        val testModule = module {
            single { accountsRepository }
        }
        setupKoin(testModule)
    }

    @Test
    fun testRoot() = testApplication {
        application {
            configureSecurity()
            configureRouting()
        }
        client.get("/teapot").apply {
            assertEquals(HttpStatusCode.fromValue(418), status)
            assertEquals("I'm a little Tea Pot", bodyAsText())
        }
    }
}
