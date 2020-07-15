import com.google.gson.Gson
import io.ktor.application.Application
import io.ktor.http.*
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.jupiter.api.*
import org.mediamod.backend.database
import org.mediamod.backend.database.MMDatabase
import org.mediamod.backend.logger
import org.mediamod.backend.mainModule
import java.util.*
import kotlin.test.assertEquals

data class CreateResponse(val secret: String?)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MMBackendTest {
    init {
        database = MMDatabase()
    }

    @Test
    fun `root returns 200`() = withTestApplication(Application::mainModule) {
        with(handleRequest(HttpMethod.Get, "/")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertTrue(requestHandled)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class RegisterTests {
        private var databaseSecret = ""
        private var receivedSecret = ""

        @Test
        fun `puts a user into the database`() = withTestApplication(Application::mainModule) {
            with(handleRequest(HttpMethod.Post, "/api/register") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Gson().toJson(mapOf("uuid" to "82074fcd-6eef-4caf-bc95-4dac50485fb7", "mod" to "mediamod")))
            }) {
                assertTrue(requestHandled)
                assertEquals(HttpStatusCode.OK, response.status())

                val responseDecoded: CreateResponse = Gson().fromJson(response.content, CreateResponse::class.java)

                assertNotNull(responseDecoded.secret)
                receivedSecret = responseDecoded.secret!!

                val dbUser = runBlocking { database.getUser(UUID.fromString("82074fcd-6eef-4caf-bc95-4dac50485fb7")) }
                assertNotNull(dbUser)
                databaseSecret = dbUser!!.requestSecret
            }
        }

        @Test
        fun `secret is valid`() {
            assertEquals(receivedSecret.length, 36)
            assertEquals(databaseSecret.length, 36)
            assertEquals(receivedSecret, databaseSecret)
        }

        @Test
        fun testOfflineUser() = withTestApplication(Application::mainModule) {
            with(handleRequest(HttpMethod.Post, "/api/offline") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(Gson().toJson(mapOf("uuid" to "82074fcd-6eef-4caf-bc95-4dac50485fb7", "secret" to receivedSecret)))
            }) {
                assertTrue(requestHandled)
                assertEquals(HttpStatusCode.OK, response.status(), "Status is OK")

                val user = runBlocking { database.getUser(UUID.fromString("82074fcd-6eef-4caf-bc95-4dac50485fb7")) } // Can't use suspend functions here.
                assertNotNull(user)
                assertEquals(user!!.requestSecret, "")
                assertFalse(user.online)
            }
        }
    }
}