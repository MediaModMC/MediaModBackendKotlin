package org.mediamod.backend.database.schema

data class User(
    val _id: String,
    var username: String,
    var requestSecret: String,
    var mods: Array<String>,
    var online: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (_id != other._id) return false
        if (username != other.username) return false
        if (!mods.contentEquals(other.mods)) return false
        if (online != other.online) return false

        return true
    }

    override fun hashCode(): Int {
        var result = _id.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + mods.contentHashCode()
        result = 31 * result + online.hashCode()
        return result
    }
}