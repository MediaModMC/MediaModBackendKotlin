package org.mediamod.backend.database.schema

data class Track (
        val _id: String,
        val timestamp: Int,
        val paused: Boolean
)
