package org.mediamod.backend.database

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import org.litote.kmongo.KMongo
import org.litote.kmongo.getCollection
import org.mediamod.backend.database.schema.Party
import org.mediamod.backend.database.schema.User
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("mediamod.Database")

class MMDatabase {
    private val client: MongoClient
    private val database: MongoDatabase

    private val usersCollection: MongoCollection<User>
    private val partiesCollection: MongoCollection<Party>

    init {
        logger.info("Initialising...")
        val start = System.currentTimeMillis()

        client = KMongo.createClient()
        database = client.getDatabase("mediamod")
        usersCollection = database.getCollection<User>("users")
        partiesCollection = database.getCollection<Party>("parties")

        val stop = System.currentTimeMillis()
        logger.info("Initialised in " + (stop - start) + "ms")
    }
}