package org.mediamod.backend.routing.api

import io.ktor.application.call
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
import kotlinx.coroutines.async
import org.mediamod.backend.database
import org.mediamod.backend.http
import org.mediamod.backend.logger
import java.util.*

/**
 * Information that is sent back from Ashcon when querying 'https://api.ashcon.app/mojang/v2/user/uuid'
 *
 * @param username: The user's username
 * @param uuid: The user's uuid
 */
data class AshconResponse(val uuid: String?, val username: String?)

/**
 * Body that is sent when a POST Request is sent to '/api/register'
 *
 * @param uuid: The user's UUID
 * @param mod: The modid of the mod that sent the request
 */
data class RegisterRequest(val uuid: String?, val mod: String?, val serverID: String?)

/**
 * Body that is sent when a POST Request is sent to '/api/register'
 *
 * @param uuid: The user's UUID
 * @param secret: The user's authentication secret they received when POSTing '/api/register'
 */
data class OfflineRequest(val uuid: String?, val secret: String?)

/**
 * Response that is received when sending a HTTP GET to "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=username&serverId=hash&ip=ip"
 *
 * @see "https://wiki.vg/Protocol_Encryption#Authentication"
 */
data class SessionResponse(val id: String?, val name: String?)

fun Routing.api() {
    post("/api/register") {
        val request = call.receiveOrNull<RegisterRequest>()
        if (request == null) {
            logger.warn("Received null request for /api/register")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Bad Request"))
            return@post
        }

        if (request.uuid?.length != 36) {
            logger.warn("Received invalid UUID for /api/register! (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.mod == null) {
            logger.warn("Received null mod for /api/register")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Mod"))
            return@post
        }

        if(request.serverID == null) {
            logger.warn("Received null serverID for /api/register")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid serverID"))
            return@post
        }

        val uuid = UUID.fromString(request.uuid)
        val user = database.getUser(uuid)
        if (user != null) {
            var mojangResponse: SessionResponse?

            try {
                mojangResponse = http.get("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" + user.username + "&serverId=" + request.serverID)
            } catch (e: Exception) {
                logger.error("Error when querying mojang for $uuid: ${e.localizedMessage}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal Server Error"))
                return@post
            }

            if(mojangResponse == null || mojangResponse.id != request.uuid.replace("-", "") || mojangResponse.name != user.username) {
                logger.warn("mojangResponse didn't match expected! (response = $mojangResponse, expected $user)")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid serverID"))
                return@post
            } else {
                call.respond(mapOf("secret" to database.loginUser(uuid)))
            }
        } else {
            // User does not exist in database, we must get the user's username and create a new user
            val ashconResponse: AshconResponse?
            try {
                ashconResponse = http.get("https://api.ashcon.app/mojang/v2/user/$uuid")
            } catch (e: Exception) {
                logger.error("Error when querying Ashcon for $uuid: ${e.localizedMessage}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal Server Error"))
                return@post
            }

            if (ashconResponse == null) {
                logger.error("Ashcon response was null for $uuid!")
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal Server Error"))
                return@post
            }

            if (ashconResponse.username == null) {
                logger.error("Ashcon username was null for $uuid!")
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal Server Error"))
                return@post
            }

            if (ashconResponse.uuid == null || ashconResponse.uuid != uuid.toString()) {
                logger.error("Ashcon uuid was invalid for ${ashconResponse.username}! (Expected $uuid but got ${ashconResponse.uuid})")
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal Server Error"))
                return@post
            }

            var mojangResponse: SessionResponse?

            try {
                mojangResponse = http.get("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" + ashconResponse.username + "&serverId=" + request.serverID)
            } catch (e: Exception) {
                logger.error("Error when querying Mojang for $uuid: ${e.localizedMessage}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal Server Error"))
                return@post
            }

            if(mojangResponse == null || mojangResponse.id != request.uuid.replace("-", "") || mojangResponse.name != ashconResponse.username) {
                logger.warn("mojangResponse didn't match expected! (response = $mojangResponse, expected $ashconResponse)")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid serverID"))
                return@post
            }

            logger.info("Inserting ${ashconResponse.username} into database...")
            call.respond(mapOf("secret" to database.createUser(uuid.toString(), ashconResponse.username, request.mod)))
        }
    }

    post("/api/offline") {
        val request = call.receiveOrNull<OfflineRequest>()

        if (request == null) {
            logger.warn("Received null request for /api/offline")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Bad Request"))
            return@post
        }

        if (request.uuid == null || request.uuid.length != 36) {
            logger.warn("Received invalid UUID for /api/offline (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret == null || request.secret.length != 36) {
            logger.warn("Received invalid secret for /api/offline (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if(user == null) {
            logger.warn("User returned null for /api/offline (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if(user.requestSecret == request.secret) {
            async {
                logger.info("Marking ${user.username} as offline...")
                database.offlineUser(user)
            }

            call.respond(HttpStatusCode.OK, mapOf("message" to "OK"))
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
        }
    }
}