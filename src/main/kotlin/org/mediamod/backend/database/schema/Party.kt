package org.mediamod.backend.database.schema

data class Party(val host: User, val code: String, val requestSecret: String, val currentTrack: String, val participants: Array<User>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Party

        if (host != other.host) return false
        if (code != other.code) return false
        if (requestSecret != other.requestSecret) return false
        if (currentTrack != other.currentTrack) return false
        if (!participants.contentEquals(other.participants)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + code.hashCode()
        result = 31 * result + requestSecret.hashCode()
        result = 31 * result + currentTrack.hashCode()
        result = 31 * result + participants.contentHashCode()
        return result
    }
}