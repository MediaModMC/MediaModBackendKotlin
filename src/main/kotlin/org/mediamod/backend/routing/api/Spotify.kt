package org.mediamod.backend.routing.api

import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import org.mediamod.backend.config
import org.mediamod.backend.database
import org.mediamod.backend.logger
import java.lang.Exception
import java.util.*

/**
 * The body sent when the user is requesting to exchange a code for a Spotify Token
 *
 * @param code: The code to exchange, provided by Spotify
 * @param secret: The user's request secret for this session
 * @param uuid: The user's UUID
 */
data class SpotifyTokenRequest(val code: String?, val secret: String?, val uuid: String?)

/**
 * The body sent when the mod is requesting to get a new access token using a refresh token
 *
 * @param refresh_token: The refresh token provided when POSTing '/api/spotify/token'
 * @param secret: The user's request secret for this session
 * @param uuid: The user's UUID
 */
data class SpotifyRefreshRequest(val refresh_token: String?, val secret: String?, val uuid: String?)

/**
 * The body sent when the mod is getting the Spotify Client ID for authorisation
 *
 * @param secret: The user's request secret for this session
 * @param uuid: The user's UUID
 */
data class SpotifyClientIDRequest(val secret: String?, val uuid: String?)

/**
 * The response received from Spotify when exchanging a code for a token
 *
 * @param access_token: An access token that can be provided in subsequent calls to Spotify Web API services
 * @param token_type: How the access token may be used: always “Bearer”.
 * @param scope: A space-separated list of scopes which have been granted for this access token
 * @param expires_in: The time period (in seconds) for which the access token is valid.
 * @param refresh_token: A token that can be sent to the Spotify Accounts service in place of an authorization code once the current access token expires
 */
data class SpotifyRequestResponse(val access_token: String?, val token_type: String, val scope: String, val expires_in: Int, val refresh_token: String)

fun Routing.spotify() {
    val spotifyHttp = HttpClient(Apache) {
        install(Auth) {
            basic {
                username = config.spotifyClientID
                password = config.spotifyClientSecret
                sendWithoutRequest = true
            }
        }
        install(JsonFeature) {
            serializer = GsonSerializer {
                serializeNulls()
                disableHtmlEscaping()
                setPrettyPrinting()
            }
        }
    }

    post("/api/spotify/token") {
        val request = call.receiveOrNull<SpotifyTokenRequest>()

        if(request == null) {
            logger.warn("Received null request for /api/spotify/token")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Bad Request"))
            return@post
        }

        if(request.code == null) {
            logger.warn("Received null code for /api/spotify/token")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.uuid == null || request.uuid.length != 36) {
            logger.warn("Received invalid UUID for /api/spotify/token (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret == null || request.secret.length != 36) {
            logger.warn("Received invalid secret for /api/spotify/token (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if(user == null) {
            logger.warn("User returned null for /api/spotify/token (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if(user.requestSecret == request.secret) {
            try {
                val response: SpotifyRequestResponse = spotifyHttp.post("https://accounts.spotify.com/api/token") {
                    body = FormDataContent(Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", request.code)
                        append("redirect_uri", config.spotifyRedirectURI)
                    })
                }

                if(response.access_token == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
                    return@post
                }

                call.respond(mapOf("access_token" to response.access_token, "refresh_token" to response.refresh_token))
            } catch (e: Exception) {
                logger.warn("Error whilst making request with code ${request.code}")
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Code"))
            }
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
        }
    }

    post("/api/spotify/refresh") {
        val request = call.receiveOrNull<SpotifyRefreshRequest>()

        if(request == null) {
            logger.warn("Received null request for /api/spotify/refresh")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Bad Request"))
            return@post
        }

        if(request.refresh_token == null) {
            logger.warn("Received null refresh token for /api/spotify/refresh")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.uuid == null || request.uuid.length != 36) {
            logger.warn("Received invalid UUID for /api/spotify/refresh (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret == null || request.secret.length != 36) {
            logger.warn("Received invalid secret for /api/spotify/refresh (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if(user == null) {
            logger.warn("User returned null for /api/spotify/refresh (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if(user.requestSecret == request.secret) {
            try {
                val response: SpotifyRequestResponse = spotifyHttp.post("https://accounts.spotify.com/api/token") {
                    body = FormDataContent(Parameters.build {
                        append("grant_type", "refresh_token")
                        append("refresh_token", request.refresh_token)
                    })
                }

                if(response.access_token == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
                    return@post
                }

                call.respond(mapOf("access_token" to response.access_token, "refresh_token" to request.refresh_token))
            } catch (e: Exception) {
                logger.warn("Error whilst making request with token ${request.refresh_token}")
                e.printStackTrace()
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Token"))
            }
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
        }
    }

    post("/api/spotify/clientid") {
        val request = call.receiveOrNull<SpotifyClientIDRequest>()

        if(request == null) {
            logger.warn("Received null request for /api/spotify/token")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Bad Request"))
            return@post
        }

        if (request.uuid == null || request.uuid.length != 36) {
            logger.warn("Received invalid UUID for /api/spotify/token (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret == null || request.secret.length != 36) {
            logger.warn("Received invalid secret for /api/spotify/token (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if(user == null) {
            logger.warn("User returned null for /api/spotify/token (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if(user.requestSecret == request.secret) {
            call.respond(mapOf("clientID" to config.spotifyClientID))
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
        }
    }
}