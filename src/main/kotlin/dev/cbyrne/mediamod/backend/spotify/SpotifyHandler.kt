package dev.cbyrne.mediamod.backend.spotify

import com.uchuhimo.konf.Config
import dev.cbyrne.mediamod.backend.SpotifyResponse
import dev.cbyrne.mediamod.backend.config.ConfigurationSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.Json
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

    val client = HttpClient(Apache) {
        Json {
            serializer = GsonSerializer()
        }
    }

    public suspend fun getTokensFromCode(code: String): SpotifyResponse {
        return client.post("https://accounts.spotify.com/api/token") {
            header("Accept", "application/json")
            body = FormDataContent(Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", "http://localhost:9103/callback/")
                append("client_id", config[ConfigurationSpec.spotifyClientID])
                append("client_secret", config[ConfigurationSpec.spotifyClientSecret])
            })
        }
    }
}