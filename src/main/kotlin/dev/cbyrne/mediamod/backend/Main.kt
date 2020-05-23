package dev.cbyrne.mediamod.backend

import dev.cbyrne.mediamod.backend.mongo.Database
import dev.cbyrne.mediamod.backend.mongo.Mod
import dev.cbyrne.mediamod.backend.mongo.ModUser
import dev.cbyrne.mediamod.backend.mongo.Version
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
import org.slf4j.LoggerFactory
import java.text.DateFormat

data class StatsResponse(val allUsers: Int, val allOnlineUsers: Int, val mods: MutableList<ModSpecificStats>)
data class ModSpecificStats(val modID: String, val users: Int, val onlineUsers: Int)
data class RegisterRequest(val uuid: String?, val currentMod: String?, val version: String?)
data class AshconResponse(val uuid: String?, val username: String?)
data class OfflineRequest(val uuid: String?)

object MediaModBackend {
    val logger = LoggerFactory.getLogger("Backend")
    private lateinit var database: Database
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
        System.setProperty("org.litote.mongo.test.mapping.service", "org.litote.kmongo.jackson.JacksonClassMappingTypeService")
        logger.info("Starting...")
        database = Database()

        embeddedServer(Netty, 3000) {
            install(ContentNegotiation) {
                gson {
                    setDateFormat(DateFormat.LONG)
                    setPrettyPrinting()
                }
            }

            routing {
                get("/") {
                    call.respond(HttpStatusCode.OK, "OK")
                }

                get("/stats") {
                    val modSpecificStats = mutableListOf<ModSpecificStats>()
                    Mod.values().forEach {
                        modSpecificStats.add(ModSpecificStats(it.modid, database.getUserCountForMod(it), database.getOnlineUserCountForMod(it)))
                    }

                    call.respond(HttpStatusCode.OK, StatsResponse(database.getUserCount(), database.getOnlineUserCount(), modSpecificStats))
                }

                post("/offline") {
                    val request = call.receiveOrNull<OfflineRequest>()
                    when {
                        request == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }
                        request.uuid == null -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }
                        request.uuid.length != 36 -> {
                            call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                            return@post
                        }
                        else -> {
                            val databaseUser = database.getUser(request.uuid)
                            if (databaseUser == null) {
                                call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                                return@post
                            }


                            async {
                                databaseUser.online = false
                                database.updateUser(databaseUser)
                            }

                            call.respond(HttpStatusCode.OK, "Success")
                        }
                    }
                }

                post("/register") {
                    val user = call.receiveOrNull<RegisterRequest>()

                    if (user == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                        return@post
                    }


                    if (user.uuid == null || user.currentMod == null || user.version == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid Request")
                        return@post
                    }

                    val currentVersion = Version.values().find { it.version == user.version }
                    val currentMod = Mod.values().find { it.modid == user.currentMod }
                    if (currentMod == null || currentVersion == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid Mod")
                        return@post
                    }

                    if (user.uuid.length != 36) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid UUID")
                        return@post
                    }

                    val databaseUser = database.getUser(user.uuid)
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
                        }
                    } else {
                        // Verify UUID and username
                        val ashconResponse: AshconResponse =
                                http.get("https://api.ashcon.app/mojang/v2/user/" + user.uuid)
                        if (ashconResponse.username == null || ashconResponse.uuid == null) {
                            // User doesn't exist
                            call.respond(HttpStatusCode.BadRequest, "Provided user doesn't exist")
                            return@post
                        }

                        if (ashconResponse.uuid != user.uuid) {
                            call.respond(HttpStatusCode.BadRequest, "Provided uuid doesn't match")
                            return@post
                        }

                        async {
                            database.insertUser(ModUser(user.uuid, ashconResponse.username, arrayOf(currentMod), arrayOf(currentVersion.version)))
                        }
                    }

                    call.respond(HttpStatusCode.OK, "Success")
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