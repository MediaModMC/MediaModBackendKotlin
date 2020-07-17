package org.mediamod.backend.database.schema

/**
 * A data class for the Levelhead Integration to return the name and artist of the current track
 */
data class LevelheadTrack(
        val name: String?,
        val artist: String?
)
