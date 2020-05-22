package dev.cbyrne.mediamod.backend.mongo

import org.litote.kmongo.*
import org.slf4j.LoggerFactory

class Database {
    private val client = KMongo.createClient()
    private val usersCollection = client.getDatabase("statistics").getCollection<ModUser>("users")
    private val logger = LoggerFactory.getLogger("Database")

    fun getUserCount(): Int = usersCollection.countDocuments().toInt()
    fun getOnlineUserCount(): Int = usersCollection.find(ModUser::online eq true).count()
    fun getUser(uuid: String): ModUser? = usersCollection.findOne(ModUser::_id eq uuid)

    fun insertUser(user: ModUser) {
        usersCollection.insertOne(user)
    }

    fun updateUser(user: ModUser) {
        usersCollection.updateOneById(user._id, user)
    }

    init {
        logger.info("Initialising...")
        logger.info("All user count: " + getUserCount())
        logger.info("Online user count: " + getOnlineUserCount())

        logger.info("Ready!")
    }
}