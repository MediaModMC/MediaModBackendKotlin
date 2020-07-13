package dev.cbyrne.mediamod.backend

import com.uchuhimo.konf.Config
import dev.cbyrne.mediamod.backend.config.ConfigurationSpec
import dev.cbyrne.mediamod.backend.mongo.Database
import dev.cbyrne.mediamod.backend.mongo.Mod
import dev.cbyrne.mediamod.backend.mongo.ModUser
import dev.cbyrne.mediamod.backend.mongo.Version
import dev.cbyrne.mediamod.backend.party.PartyManager
import dev.cbyrne.mediamod.backend.spotify.SpotifyHandler
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.async
import org.litote.kmongo.findOneById
import org.litote.kmongo.updateOneById
import org.slf4j.LoggerFactory
import java.io.File
import java.text.DateFormat

data class StatsResponse(val allUsers: Int, val allOnlineUsers: Int, val mods: MutableList<ModSpecificStats>)
data class ModSpecificStats(val modID: String, val users: Int, val onlineUsers: Int)
data class RegisterRequest(val uuid: String?, val currentMod: String?, val version: String?)
data class AshconResponse(val uuid: String?, val username: String?)
data class OfflineRequest(val uuid: String?, val secret: String?)
data class PartyStartRequest(val uuid: String?)
data class PartyStartResponse(val code: String, val secret: String)
data class PartyJoinRequest(val code: String?, val uuid: String?)
data class PartySongChangeRequest(val code: String?, val uuid: String?, val secret: String?, val trackId: String?)
data class ClientIDResponse(val clientID: String)

object MediaModBackend {
    val logger = LoggerFactory.getLogger("Backend")
    private lateinit var database: Database
    private lateinit var partyManager: PartyManager
    private lateinit var config: Config

    private val http = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = GsonSerializer {
                serializeNulls()
                disableHtmlEscaping()
                setPrettyPrinting()
            }
        }
    }

    fun start() {
        logger.info("Starting...")
        System.setProperty("org.litote.mongo.test.mapping.service", "org.litote.kmongo.jackson.JacksonClassMappingTypeService")

        database = Database
        partyManager = PartyManager()

        if(!File("config.json").exists()) {
            logger.error("config.json doesn't exist!")
            System.exit(-1)
        } else {
            config = Config { addSpec(ConfigurationSpec) }.from.json.file(File("config.json"))
        }

        embeddedServer(Netty, 3000) {
            install(ContentNegotiation) {
                gson {
                    setDateFormat(DateFormat.LONG)
                    setPrettyPrinting()
                }
            }

            routing {
                get("/") {
                    call.respond("OK")
                }

                get("/clientID") {
                    call.respond(ClientIDResponse(config[ConfigurationSpec.spotifyClientID]))
                }

                get("/stats") {
                    val modSpecificStats = mutableListOf<ModSpecificStats>()
                    Mod.values().forEach {
                        modSpecificStats.add(ModSpecificStats(it.modid, database.getUserCountForMod(it), database.getOnlineUserCountForMod(it)))
                    }

                    call.respond(StatsResponse(database.getUserCount(), database.getOnlineUserCount(), modSpecificStats))
                }

                post("/startParty") {
                    val request = call.receiveOrNull<PartyStartRequest>()

                    when {
                        request == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        request.uuid == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        request.uuid.length != 32 -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        else -> {
                            val databaseUser = database.getUser(request.uuid)

                            if (databaseUser == null) {
                                call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                                return@post
                            }

                            val party = partyManager.startParty(databaseUser)
                            if(party == null) {
                                call.respond(HttpStatusCode.BadRequest, "Invalid Request (party may already exist with host)")
                                return@post
                            }

                            call.respond(PartyStartResponse(party._id, party.requestSecret))
                        }
                    }
                }

                post("/joinParty") {
                    val request = call.receiveOrNull<PartyJoinRequest>()

                    when {
                        request == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        request.uuid == null || request.code == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        request.uuid.length != 32 || request.code.length != 6 -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        else -> call.respond(partyManager.joinParty(request.code, request.uuid))
                    }
                }

                post("/songChange") {
                    val request = call.receiveOrNull<PartySongChangeRequest>()

                    when {
                        request == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        request.uuid == null || request.code == null || request.trackId == null || request.secret == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                            request.uuid.length != 32 || request.code.length != 6 || request.secret.length != 36 -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        else -> {
                            val party = Database.partiesCollection.findOneById(request.code)
                            if(party == null) {
                                call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                                return@post
                            }

                            if(party.host._id == request.uuid && party.requestSecret == request.secret) {
                                // User is host and secret is valid
                                logger.info("User is host and secret is valid!")
                                async {
                                    party.track = request.trackId
                                    Database.partiesCollection.updateOneById(party._id, party)
                                }

                                call.respond(HttpStatusCode.OK)
                                return@post
                            } else {
                                logger.warn("User is either not host or secret is not valid!")
                                call.respond(HttpStatusCode.Forbidden, "You are not allowed to do that!")
                                return@post
                            }
                        }
                    }
                }

                post("/leaveParty") {
                    val request = call.receiveOrNull<PartyStartRequest>()

                    when {
                        request == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        request.uuid == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        request.uuid.length != 32 -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }

                        else -> {
                            async {
                                // TODO: Leave party impl
                            }

                            call.respond(HttpStatusCode.OK)
                        }
                    }
                }

                post("/offline") {
                    val request = call.receiveOrNull<OfflineRequest>()
                    when {
                        request == null -> {
                            logger.warn("/offline - request body is null")
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }
                        request.uuid == null -> {
                            logger.warn("/offline - request uuid is null")
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }
                        request.uuid.replace("-", "").length != 32 -> {
                            logger.warn("/offline - request uuid length != 32 (recieved ${request.uuid.replace("-", "").length})")
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }
                        else -> {
                            val strippedUUID = request.uuid.replace("-", "")
                            val databaseUser = database.getUser(strippedUUID)
                            if (databaseUser == null) {
                                logger.warn("/offline - database user doesn't exist (could not find $strippedUUID)")
                                call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                                return@post
                            }

                            async {
                                databaseUser.online = false
                                database.updateUser(databaseUser)
                                partyManager.stopParty(strippedUUID, request.secret)
                            }

                            call.respond("Success")
                        }
                    }
                }

                post("/register") {
                    val user = call.receiveOrNull<RegisterRequest>()

                    if (user == null) {
                        logger.warn("/register - request body is null")
                        call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                        return@post
                    }

                    if (user.uuid == null || user.currentMod == null || user.version == null) {
                        logger.warn("/register - request has insufficient information (${user.uuid}, ${user.currentMod} or ${user.version} is null)")
                        call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                        return@post
                    }

                    val currentVersion = Version.values().find { it.version == user.version }
                    val currentMod = Mod.values().find { it.modid == user.currentMod }
                    if (currentMod == null || currentVersion == null) {
                        logger.warn("/register - request has bad mod id ($currentMod) or version ($currentVersion)")
                        call.respond(HttpStatusCode.BadRequest, "Invalid Mod")
                        return@post
                    }

                    val strippedUUID = user.uuid.replace("-", "")
                    if (strippedUUID.length != 32) {
                        logger.warn("/register - stripped UUID is not correct length (${strippedUUID.length} instead of 32")
                        call.respond(HttpStatusCode.BadRequest, "Invalid UUID")
                        return@post
                    }

                    val databaseUser = database.getUser(strippedUUID)
                    if (databaseUser != null) {
                        // User already exists, no verification. Just update mods
                        if (!databaseUser.mods.contains(currentMod)) {
                            async {
                                databaseUser.mods = databaseUser.mods.plus(currentMod)
                                database.updateUser(databaseUser)
                            }
                        } else if (!databaseUser.versions.contains(user.version)) {
                            async {
                                databaseUser.versions = databaseUser.versions.plus(currentVersion.version)
                                database.updateUser(databaseUser)
                            }
                        } else if (!databaseUser.online) {
                            databaseUser.online = true
                            database.updateUser(databaseUser)
                        }
                    } else {
                        try {
                            // Verify UUID and username
                            val ashconResponse: AshconResponse =
                                    http.get("https://api.ashcon.app/mojang/v2/user/$strippedUUID")
                            if (ashconResponse.username == null || ashconResponse.uuid == null) {
                                logger.warn("/register - User does not exist ($strippedUUID)")
                                call.respond(HttpStatusCode.BadRequest, "Provided user doesn't exist")
                                return@post
                            }

                            if (ashconResponse.uuid.replace("-", "") != strippedUUID) {
                                logger.warn("/register - Provided UUID ($strippedUUID) does not match Ashcon UUID (${ashconResponse.uuid.replace("-", "")})")
                                call.respond(HttpStatusCode.BadRequest, "Provided uuid doesn't match")
                                return@post
                            }

                            async {
                                logger.info("/register - Inserting new user into database")
                                database.insertUser(ModUser(strippedUUID, ashconResponse.username, arrayOf(currentMod), arrayOf(currentVersion.version)))
                            }
                        } catch (e: Exception) {
                            logger.warn("/register - User does not exist ($strippedUUID)")
                            call.respond(HttpStatusCode.NotFound, "Provided user doesn't exist")
                            return@post
                        }
                    }

                    call.respond("Success")
                }

                get("/api/spotify/getToken") {
                    if (call.parameters["code"] != null && call.parameters["code"]?.isNotBlank() != false) {
                        call.respond(SpotifyHandler().getTokensFromCode(call.parameters["code"]!!))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "No auth code provided")
                    }
                }

                get("/api/spotify/refreshToken") {
                    if (call.parameters["token"] != null && call.parameters["token"]?.isNotBlank() != false) {
                        call.respond(SpotifyHandler().getRefreshToken(call.parameters["token"]!!))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "No refresh token provided")
                    }
                }
            }
        }.start(wait = true)
    }
}

fun main() {
    MediaModBackend.start()
}