package dev.cbyrne.mediamod.backend

import dev.cbyrne.mediamod.backend.spotify.SpotifyHandler
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.text.DateFormat

data class Response(val key: String, val value: String)
data class SpotifyResponse(val accessToken: String, val expiresIn: Int, val refreshToken: String)

fun main(args: Array<String>) {
    embeddedServer(Netty, 3000) {
        install(ContentNegotiation) {
            gson {
                setDateFormat(DateFormat.LONG)
                setPrettyPrinting()
            }
        }
        routing {
            get("/") {
                call.respond(Response("status", "OK"))
            }

            get("/api/spotify/getToken") {
                if (call.parameters["code"] != null && call.parameters["code"]?.isNotBlank() != false) {
                    call.respond(SpotifyHandler().getTokensFromCode(call.parameters["code"]!!))
                } else {
                    call.respond(HttpStatusCode.BadRequest, Response("error", "no code provided"))
                }
            }
        }
    }.start(wait = true)
}