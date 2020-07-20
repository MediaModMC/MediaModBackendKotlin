package org.mediamod.backend

import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.mediamod.backend.database.MMDatabase
import org.mediamod.backend.routing.api.api
import org.mediamod.backend.routing.api.levelhead
import org.mediamod.backend.routing.api.party
import org.mediamod.backend.routing.api.spotify
import org.mediamod.backend.routing.root
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.text.DateFormat
import kotlin.system.exitProcess

data class MMConfig(val spotifyClientID: String, val spotifyClientSecret: String, val spotifyRedirectURI: String, val sk1erLevelheadSecret: String)

fun main() = MMBackend.start()

lateinit var config: MMConfig

val http = HttpClient(Apache) {
    install(JsonFeature) {
        serializer = GsonSerializer {
            serializeNulls()
            disableHtmlEscaping()
            setPrettyPrinting()
        }
    }
}

val logger: Logger = LoggerFactory.getLogger("mediamod.Backend")

lateinit var database: MMDatabase

object MMBackend {
    fun start() {
        logger.info("Starting...")
        val start = System.currentTimeMillis()

        val file = File("config.json")
        if (!file.exists()) {
            logger.error("Configuration file doesn't exist!")
            exitProcess(-1)
        }

        val factory = ConfigFactory.parseFile(file)
        config = factory.extract()

        database = MMDatabase()
        embeddedServer(Netty, 3001, watchPaths = listOf("MMBackendKt"), module = Application::mainModule).start()

        val stop = System.currentTimeMillis()
        logger.info("Ready! (" + (stop - start) + "ms)")
    }
}

fun Application.mainModule() {
    install(ContentNegotiation) {
        gson {
            setDateFormat(DateFormat.LONG)
            setPrettyPrinting()
        }
    }

    routing {
        root()
        api()
        spotify()
        party()
        levelhead()
    }
}
