package org.mediamod.backend.database

import com.mongodb.MongoClientSettings
import org.bson.UuidRepresentation
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import org.mediamod.backend.database.schema.Party
import org.mediamod.backend.database.schema.User
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("mediamod.Database")

class MMDatabase {
    private val client: CoroutineClient
    private val database: CoroutineDatabase

    private val usersCollection: CoroutineCollection<User>
    private val partiesCollection: CoroutineCollection<Party>

    init {
        logger.info("Initialising...")
        val start = System.currentTimeMillis()

        // Initialize the client
        client = KMongo.createClient(
            MongoClientSettings
                .builder()
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build()
        ).coroutine

        // Set the database and collections
        database = client.getDatabase("mediamod")
        usersCollection = database.getCollection("users")
        partiesCollection = database.getCollection("parties")

        val stop = System.currentTimeMillis()
        logger.info("Initialised in " + (stop - start) + "ms")
    }

    /**
     * Makes a user object and puts it into the users collection
     *
     * @param uuid: The user's UUID
     * @param username: The user's Minecraft name
     * @param currentMod: The mod that sent the request
     *
     * @return The request secret that the mod will need to make future requests
     */
    suspend fun createUser(uuid: String, username: String, currentMod: String): String {
        val user = User(uuid, username, UUID.randomUUID().toString(), arrayOf(currentMod), true)
        usersCollection.insertOne(user)

        return user.requestSecret
    }

    /**
     * Marks an existing user as online, called when the mod is initialising
     *
     * @param uuid: The user's UUID
     * @return The request secret that the mod will need to make future requests
     */
    suspend fun loginUser(uuid: UUID): String {
        val user = usersCollection.findOneById(uuid.toString()) ?: return ""

        user.online = true
        user.requestSecret = UUID.randomUUID().toString()

        updateUser(user)
        return user.requestSecret
    }

    /**
     * Checks if a user already exists in the database
     *
     * @param uuid: The user's UUID
     *
     * @return Whether the user is in the collection or not
     */
    suspend fun doesUserExist(uuid: UUID) = usersCollection.findOneById(uuid.toString()) != null

    /**
     * Returns the user from the database with the corresponding UUID
     *
     * @param uuid: The user's uuid
     *
     * @return The player from the database, which will be nullable as they may not be in the database
     */
    suspend fun getUser(uuid: UUID) = usersCollection.findOneById(uuid.toString())

    /**
     * Returns a boolean depending on if the user object in the database was updated
     *
     * @param user: The updated user object
     * @return A boolean which indicates if the request was acknowledged
     */
    suspend fun updateUser(user: User) = usersCollection.updateOneById(user._id, user).wasAcknowledged()

    /**
     * Returns the amount of online users in the database
     *
     * @return The number of online users
     */
    suspend fun getOnlineUsersCount() = usersCollection.countDocuments(User::online eq true)

    /**
     * Returns the amount of users in the database
     *
     * @return The number users
     */
    suspend fun getAllUsersCount() = usersCollection.countDocuments()
}
