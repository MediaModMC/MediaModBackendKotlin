package dev.cbyrne.mediamod.backend.party

import dev.cbyrne.mediamod.backend.mongo.Database
import dev.cbyrne.mediamod.backend.mongo.ModUser
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.litote.kmongo.*
import java.util.*

class PartyManager {
    fun startParty(host: ModUser): MediaModParty? {
        val existingParty =  Database.partiesCollection.findOne(MediaModParty::participants `in` host._id)
        return if(existingParty == null) {
            val party = MediaModParty(generateCode(), host, UUID.randomUUID().toString(), mutableListOf(host._id), "")
            GlobalScope.launch {
                Database.partiesCollection.insertOne(party)
            }
            party
        } else {
            null
        }
    }

    fun stopParty(host: String, secret: String?) {
        if(secret == null) return

        val party = Database.partiesCollection.findOne(MediaModParty::requestSecret eq secret) ?: return
        if(party.host._id == host) {
            Database.partiesCollection.findOneAndDelete(MediaModParty::_id eq party._id)
        }
    }

    fun joinParty(code: String, user: String): HttpStatusCode {
        val party = Database.partiesCollection.findOneById(code) ?: return HttpStatusCode.BadRequest
        party.participants.add(user)

        GlobalScope.launch {
            Database.partiesCollection.updateOneById(code, party)
        }

        return HttpStatusCode.OK
    }

    private fun generateCode(): String {
        val alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val code = List(6) { alphabet.random() }.joinToString("")
        return if(Database.partiesCollection.findOneById(code) == null) {
            code
        } else {
            generateCode()
        }
    }
}