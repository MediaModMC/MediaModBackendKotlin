package dev.cbyrne.mediamod.backend.party

import dev.cbyrne.mediamod.backend.mongo.ModUser

data class MediaModParty(val _id: String, val host: ModUser, val participants: MutableList<String>)