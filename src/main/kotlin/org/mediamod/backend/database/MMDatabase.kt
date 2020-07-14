package org.mediamod.backend.database

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import org.litote.kmongo.KMongo
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import org.mediamod.backend.database.schema.Party
import org.mediamod.backend.database.schema.User
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

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

    /**
     * Makes a user object and puts it into the users collection
     *
     * @param uuid: The user's UUID stripped of any dashes
     * @param username: The user's Minecraft name
     * @param currentMod: The mod that sent the request
     *
     * @return The request secret that the mod will need to make future requests
     */
    fun createUser(uuid: String, username: String, currentMod: String): String {
        val user = User(UUID.fromString(uuid), username, UUID.randomUUID().toString(), arrayOf(currentMod), true)
        usersCollection.insertOne(user)

        return user.requestSecret
    }

    /**
     * Marks an existing user as online, called when the mod is initialising
     *
     * @param uuid: The user's UUID
     * @return The request secret that the mod will need to make future requests
     */
    fun loginUser(uuid: UUID): String {
        val user = usersCollection.findOne(User::_id eq uuid) ?: return ""
        return user.requestSecret
    }

    /**
     * Checks if a user already exists in the database
     *
     * @param uuid: The user's UUID
     */
    fun doesUserExist(uuid: UUID): Boolean {
        return usersCollection.findOne(User::_id eq uuid) != null
    }
}