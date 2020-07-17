package org.mediamod.backend.routing.api

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
import org.mediamod.backend.database
import org.mediamod.backend.database.schema.Track
import org.mediamod.backend.logger
import java.util.*

/**
 * The body sent when the mod is starting a party
 *
 * @param secret: The user's secret they received when POSTing '/api/register'
 * @param uuid: The user's UUID
 * @param currentTrack: The user's current track on Spotify
 */
data class PartyStartRequest(val secret: String?, val uuid: String?, val currentTrack: String?)

/**
 * The body sent when the mod is querying the current track info
 *
 * @param secret: The user's secret they received when POSTing '/api/register'
 * @param uuid: The user's UUID
 * @param partyCode: The party invite code
 */
data class PartyStatusRequest(val secret: String?, val uuid: String?, val partyCode: String?)

/**
 * The body sent when the user requests to leave / stop a party
 *
 * @param secret: The user's secret they received when POSTing '/api/register'
 * @param uuid: The user's UUID
 * @param partyCode: The party invite code
 * @param partySecret: If the user is the host, this needs to be provided. This will delete the party from the database if it matches
 */
data class PartyLeaveRequest(val secret: String?, val uuid: String?, val partyCode: String?, val partySecret: String?)

/**
 * The body sent when the user requests to join a party
 *
 * @param secret: The user's secret they received when POSTing '/api/register'
 * @param uuid: The user's UUID
 * @param partyCode: The party invite code
 */
data class PartyJoinRequest(val secret: String?, val uuid: String?, val partyCode: String?)

/**
 * The body sent when the mod requests to update the track information on the server
 *
 * @param secret: The user's secret they received when POSTing '/api/register'
 * @param uuid: The user's UUID
 * @param partyCode: The party invite code
 * @param partySecret: The party secret provided to the host when POSTing '/api/party/start'
 */
data class PartyUpdateRequest(val secret: String?, val uuid: String?, val partyCode: String?, val partySecret: String?, val currentTrack: String?)

fun Routing.party() {
    /**
     * Starts a party with the provided UUID as the host
     * Returns a JSON Object of the party secret and code
     */
    post("/api/party/start") {
        val request = call.receiveOrNull<PartyStartRequest>()

        if (request == null) {
            logger.warn("Received null request for /api/party/start")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.uuid?.length != 36) {
            logger.warn("Received invalid UUID for /api/party/start (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret?.length != 36) {
            logger.warn("Received invalid secret for /api/party/start (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if (user == null) {
            logger.warn("User returned null for /api/spotify/token (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (user.requestSecret == request.secret) {
            val map = database.createParty(user._id, Gson().fromJson(request.currentTrack, Track::class.java))

            if (map == null) {
                logger.warn("Failed to create party! (request: $request)")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
                return@post
            }

            call.respond(map)
        } else {
            logger.warn("Received invalid secret for /api/party/start (got ${request.secret}, expected ${user.requestSecret}})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }
    }

    post("/api/party/leave") {
        val request = call.receiveOrNull<PartyLeaveRequest>()

        if (request == null) {
            logger.warn("Received null request for /api/party/leave")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Request"))
            return@post
        }

        if (request.uuid?.length != 36) {
            logger.warn("Received invalid UUID for /api/party/leave (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.partyCode?.length != 6) {
            logger.warn("Received invalid party code for /api/party/leave (code = ${request.partyCode})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret?.length != 36) {
            logger.warn("Received invalid secret for /api/party/leave (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if (user == null) {
            logger.warn("User returned null for /api/spotify/token (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (user.requestSecret == request.secret) {
            logger.info("Removing $user.username from party...")
            val success = database.leaveParty(user._id, request.partyCode, request.partySecret)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Successfully left party!"))
            } else {
                logger.warn("Failed to remove $user from party!")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid party or user"))
            }
        } else {
            logger.warn("Received invalid secret for /api/party/leave (got ${request.secret}, expected ${user.requestSecret}})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }
    }

    post("/api/party/status") {
        val request = call.receiveOrNull<PartyStatusRequest>()

        if (request == null) {
            logger.warn("Received null request for /api/party/status")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Request"))
            return@post
        }

        if (request.uuid?.length != 36) {
            logger.warn("Received invalid UUID for /api/party/status (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.partyCode?.length != 6) {
            logger.warn("Received invalid party code for /api/party/status (code = ${request.partyCode})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret?.length != 36) {
            logger.warn("Received invalid secret for /api/party/status (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if (user == null) {
            logger.warn("User returned null for /api/party/status (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (user.requestSecret == request.secret) {
            val party = database.getParty(request.partyCode)

            if (party == null) {
                logger.warn("User tried to get status of a party that doesn't exist!")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "That party doesn't exist"))
                return@post
            }

            call.respond(mapOf("track" to party.currentTrack))
        } else {
            logger.warn("Received invalid secret for /api/party/status (got ${request.secret}, expected ${user.requestSecret}})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }
    }

    post("/api/party/update") {
        val request = call.receiveOrNull<PartyUpdateRequest>()

        if (request == null) {
            logger.warn("Received null request for /api/party/update")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Request"))
            return@post
        }

        if (request.uuid?.length != 36) {
            logger.warn("Received invalid UUID for /api/party/update (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.partyCode?.length != 6) {
            logger.warn("Received invalid party code for /api/party/update (code = ${request.partyCode})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret?.length != 36) {
            logger.warn("Received invalid secret for /api/party/update (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        if (request.partySecret?.length != 36) {
            logger.warn("Received invalid party secret for /api/party/update (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if (user == null) {
            logger.warn("User returned null for /api/party/update (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (user.requestSecret == request.secret) {
            val party = database.getParty(request.partyCode)

            if(party?.requestSecret == request.partySecret) {
                party.currentTrack = Gson().fromJson(request.currentTrack, Track::class.java)
                database.updateParty(party)
            } else {
                logger.warn("Received invalid party secret for /api/party/update (got ${request.secret}, expected ${user.requestSecret}})")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
                return@post
            }
        } else {
            logger.warn("Received invalid secret for /api/party/update (got ${request.secret}, expected ${user.requestSecret}})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }
    }

    post("/api/party/join") {
        val request = call.receiveOrNull<PartyJoinRequest>()

        if (request == null) {
            logger.warn("Received null request for /api/party/join")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Request"))
            return@post
        }

        if (request.uuid?.length != 36) {
            logger.warn("Received invalid UUID for /api/party/join (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.partyCode?.length != 6) {
            logger.warn("Received invalid party code for /api/party/join (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (request.secret?.length != 36) {
            logger.warn("Received invalid secret for /api/party/join (secret = ${request.secret})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }

        val user = database.getUser(UUID.fromString(request.uuid))
        if (user == null) {
            logger.warn("User returned null for /api/party/join (uuid = ${request.uuid})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid UUID"))
            return@post
        }

        if (user.requestSecret == request.secret) {
            val host = database.joinParty(user._id, request.partyCode)

            if (host != null) {
                call.respond(HttpStatusCode.OK, mapOf("success" to true, "host" to host))
            } else {
                logger.warn("Failed to remove $user from party!")
                call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "host" to ""))
            }
        } else {
            logger.warn("Received invalid secret for /api/spotify/token (got ${request.secret}, expected ${user.requestSecret}})")
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Secret"))
            return@post
        }
    }
}
