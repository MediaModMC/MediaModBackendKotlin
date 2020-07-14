package org.mediamod.backend.routing.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post

fun Routing.api() {
    post("/api/register") {
        call.respond(HttpStatusCode.Companion.NotImplemented, mapOf("message" to "Not Implemented"))
    }

    post("/api/offline") {
        call.respond(HttpStatusCode.Companion.NotImplemented, mapOf("message" to "Not Implemented"))
    }
}