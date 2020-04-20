package dev.cbyrne.mediamod.backend.spotify

import com.google.gson.JsonObject
import com.uchuhimo.konf.Config
import dev.cbyrne.mediamod.backend.SpotifyResponse
import dev.cbyrne.mediamod.backend.config.ConfigurationSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.Json
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import java.io.File

class SpotifyHandler {
    val config = Config { addSpec(ConfigurationSpec) }.from.json.file(File("config.json"))

    val http = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = GsonSerializer {
                serializeNulls()
                disableHtmlEscaping()
                setPrettyPrinting()
            }
        }
    }

    val auth: String = java.util.Base64.getEncoder()
            .encodeToString("${config[ConfigurationSpec.spotifyClientID]}:${config[ConfigurationSpec.spotifyClientSecret]}".toByteArray())

    suspend fun getTokensFromCode(code: String): JsonObject {
        return http.post("https://accounts.spotify.com/api/token") {
            this.body = FormDataContent(Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", config[ConfigurationSpec.spotifyRedirectURI])
            })
            this.header("Authorization", "Basic $auth")
        }
    }
}