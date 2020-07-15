package org.mediamod.backend.routing

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import org.mediamod.backend.database

fun Routing.root() {
    get("/") {
        call.respond(mapOf("message" to "OK"))
    }

    get("/stats") {
        call.respond(mapOf("allUsers" to database.getAllUsersCount(), "allOnlineUsers" to database.getOnlineUsersCount()))
    }
}