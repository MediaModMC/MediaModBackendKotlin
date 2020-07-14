package org.mediamod.backend.routing.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get

fun Routing.spotify() {
    get("/api/spotify/") {
        call.respond(HttpStatusCode.Companion.NotImplemented, mapOf("message" to "Not Implemented"))
    }
}