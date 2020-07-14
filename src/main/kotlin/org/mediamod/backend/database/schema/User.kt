package org.mediamod.backend.database.schema

import java.util.*

data class User(val uuid: UUID, val username: String, val mods: Array<String>, val offline: Boolean) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (uuid != other.uuid) return false
        if (username != other.username) return false
        if (!mods.contentEquals(other.mods)) return false
        if (offline != other.offline) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + mods.contentHashCode()
        result = 31 * result + offline.hashCode()
        return result
    }
}