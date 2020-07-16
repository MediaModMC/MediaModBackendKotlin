package org.mediamod.backend.database.schema

data class Party(
        val _id: String,
        val host: User,
        val requestSecret: String,
        var participants: ArrayList<String>,
        var currentTrack: Track?
)