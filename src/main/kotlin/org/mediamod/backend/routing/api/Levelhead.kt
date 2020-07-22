package org.mediamod.backend.routing.api

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import kotlinx.coroutines.launch
import org.mediamod.backend.config
import org.mediamod.backend.database
import org.mediamod.backend.database.schema.LevelheadTrack
import org.mediamod.backend.logger
import java.util.*

/**
 * The body received when the client is updating the Levelhead song information
 */
data class LevelheadUpdateRequest(val uuid: String?, val secret: String?, val track: String?)

fun Routing.levelhead() {
    get("/api/levelhead/songInformation") {
        val uuid = call.request.queryParameters["uuid"]
        val secret = call.request.queryParameters["secret"]

        if (uuid == null || uuid.length != 36) {
            // logger.warn("Received invalid UUID for /api/levelhead/songInformation (uuid = ${uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@get
        }

        if (secret == null || secret.length != 96 || secret != config.sk1erLevelheadSecret) {
            logger.warn("Received invalid secret for /api/levelhead/songInformation (secret = ${secret})")
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val user = database.getUser(UUID.fromString(uuid))
        if (user == null) {
            // logger.warn("User returned null for /api/levelhead/songInformation (uuid = ${uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User does not exist"))
            return@get
        }

        if (user.levelheadTrack != null) {
            call.respond(HttpStatusCode.OK, mapOf("track" to user.levelheadTrack))
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "User is not playing a track"))
        }
    }

    post("/api/levelhead/update") {
        val request = call.receiveOrNull<LevelheadUpdateRequest>()

        if (request == null) {
            logger.warn("Received null request for /api/levelhead/update")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Bad Request"))
            return@post
        }

        if (request.uuid == null || request.uuid.length != 36) {
            logger.warn("Received invalid UUID for /api/levelhead/update (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret == null || request.secret.length != 36) {
            logger.warn("Received invalid secret for /api/levelhead/update (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if (user == null) {
            logger.warn("User returned null for /api/levelhead/update (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (user.requestSecret == request.secret) {
            launch {
                user.levelheadTrack = Gson().fromJson(request.track, LevelheadTrack::class.java)
                database.updateUser(user)
            }

            call.respond(HttpStatusCode.OK, mapOf("message" to "OK"))
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
        }
    }
}
