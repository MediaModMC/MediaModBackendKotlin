package org.mediamod.backend

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.mediamod.backend.database.MMDatabase
import org.mediamod.backend.routing.api.api
import org.mediamod.backend.routing.api.spotify
import org.mediamod.backend.routing.root
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.DateFormat

val logger: Logger = LoggerFactory.getLogger("mediamod.Backend")
lateinit var database: MMDatabase

object MMBackend {
    fun start() {
        logger.info("Starting...")
        val start = System.currentTimeMillis()

        database = MMDatabase()
        embeddedServer(Netty, 3000, watchPaths = listOf("MMBackendKt"), module = Application::mainModule).start()

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
        this.root()
        this.api()
        this.spotify()
    }
}

fun main() {
    MMBackend.start()
}
