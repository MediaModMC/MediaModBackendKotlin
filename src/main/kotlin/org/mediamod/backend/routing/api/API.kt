package org.mediamod.backend.routing.api

import io.ktor.application.call
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
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
 */
data class RegisterRequest(val uuid: String?, val mod: String?)

fun Routing.api() {
    post("/api/register") {
        val request = call.receiveOrNull<RegisterRequest>()
        if (request == null) {
            logger.warn("Recieved null request for /api/register")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Bad Request"))
            return@post
        }

        if (request.uuid == null || request.uuid.length != 36) {
            logger.warn("Recieved invalid UUID for /api/register! (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.mod == null) {
            logger.warn("Recieved null mod for /api/register")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Mod"))
            return@post
        }

        val uuid = UUID.fromString(request.uuid)
        if (database.doesUserExist(uuid)) {
            // User already exists in database, just update the existing user to be online
            call.respond(mapOf("secret" to database.loginUser(uuid)))
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

            logger.info("Inserting ${ashconResponse.username} into database...")
            call.respond(mapOf("secret" to database.createUser(uuid.toString(), ashconResponse.username, request.mod)))
        }
    }

    post("/api/offline") {
        call.respond(HttpStatusCode.Companion.NotImplemented, mapOf("message" to "Not Implemented"))
    }
}