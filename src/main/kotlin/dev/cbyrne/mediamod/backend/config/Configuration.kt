package dev.cbyrne.mediamod.backend.config

import com.uchuhimo.konf.ConfigSpec

object ConfigurationSpec : ConfigSpec() {
    val spotifyClientID by required<String>()
    val spotifyClientSecret by required<String>()
    val spotifyRedirectURI by required<String>()
}