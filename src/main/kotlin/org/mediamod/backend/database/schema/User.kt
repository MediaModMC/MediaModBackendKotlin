package org.mediamod.backend.database.schema

data class User(
        val _id: String,
        var username: String,
        var requestSecret: String,
        var mods: ArrayList<String>,
        var online: Boolean,
        var levelheadTrack: LevelheadTrack?
)