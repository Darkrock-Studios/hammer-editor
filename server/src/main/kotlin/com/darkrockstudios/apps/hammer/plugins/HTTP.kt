package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.routing.*

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header(HAMMER_PROTOCOL_HEADER, HAMMER_PROTOCOL_VERSION.toString())
    }
    install(IgnoreTrailingSlash)
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }
}
