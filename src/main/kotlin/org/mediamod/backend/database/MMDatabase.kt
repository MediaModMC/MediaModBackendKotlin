package org.mediamod.backend.database

import com.mongodb.MongoClientSettings
import org.bson.UuidRepresentation
import org.litote.kmongo.contains
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import org.mediamod.backend.database.schema.Party
import org.mediamod.backend.database.schema.Track
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
        val user = User(uuid, username, UUID.randomUUID().toString(), arrayListOf(currentMod), true, null)
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
     * Marks a user as offline, called when the client is shutting down
     * This expires the requestSecret so when they come online again a new one can be generated
     * Also removes the user from any participating parties or stops the party if they are the host
     *
     * @param user: The user to mark as offline
     */
    suspend fun offlineUser(user: User) {
        val party = partiesCollection.findOne(Party::participants contains user._id)

        if (party != null) {
            if (party.host._id == user._id) {
                partiesCollection.findOneAndDelete(Party::host eq user)
            } else {
                party.participants.remove(user._id)
                updateParty(party)
            }
        }

        user.online = false
        user.requestSecret = ""
        user.levelheadTrack = null

        updateUser(user)
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
     * Returns a boolean depending on if the party object in the database was updated
     *
     * @param party: The updated party object
     * @return A boolean which indicates if the request was acknowledged
     */
    suspend fun updateParty(party: Party) = partiesCollection.updateOneById(party._id, party).wasAcknowledged()

    /**
     * Returns the amount of online users in the database
     *
     * @return The number of online users
     */
    suspend fun getOnlineUsersCount() = usersCollection.countDocuments(User::online eq true)

    /**
     * Returns the amount of users in the database
     *
     * @return The number of users
     */
    suspend fun getAllUsersCount() = usersCollection.countDocuments()

    /**
     * Creates a party and returns the party secret and code if successful
     *
     * @param uuid: The host's UUID
     */
    suspend fun createParty(uuid: String, track: Track?): Map<String, String>? {
        val hostUser = getUser(UUID.fromString(uuid)) ?: return null

        val party = Party(generatePartyCode(), hostUser, UUID.randomUUID().toString(), arrayListOf(uuid), track)
        partiesCollection.insertOne(party)

        return mapOf("secret" to party.requestSecret, "code" to party._id)
    }

    /**
     * Removes a user from a party
     * Can also delete the party if the uuid provided is the host and the partySecret matches the one in the database
     *
     * @return: Successful
     */
    suspend fun leaveParty(uuid: String, partyCode: String, partySecret: String?): Boolean {
        val party = partiesCollection.findOneById(partyCode) ?: return false
        val user = getUser(UUID.fromString(uuid)) ?: return false

        if (party.host._id == user._id && party.requestSecret == partySecret) {
            partiesCollection.deleteOneById(partyCode)
        } else if (party.participants.contains(uuid)) {
            party.participants.remove(uuid)
            updateParty(party)
        }

        return true
    }

    /**
     * Adds a user into the party
     *
     * @return: Successful
     */
    suspend fun joinParty(uuid: String, partyCode: String): Party? {
        val party = partiesCollection.findOneById(partyCode) ?: return null

        party.participants.add(uuid)
        partiesCollection.updateOneById(partyCode, party)

        return party
    }

    suspend fun getParty(code: String): Party? = partiesCollection.findOneById(code)

    /**
     * Returns a 6 digit alphanumeric code for a MediaMod Party
     */
    private suspend fun generatePartyCode(): String {
        val alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val code = List(6) { alphabet.random() }.joinToString("")
        return if (partiesCollection.findOneById(code) == null) {
            code
        } else {
            generatePartyCode()
        }
    }
}
