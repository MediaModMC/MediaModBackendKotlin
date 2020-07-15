import com.google.gson.Gson
import io.ktor.application.Application
import io.ktor.http.*
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.runBlocking
import org.mediamod.backend.database
import org.mediamod.backend.database.MMDatabase
import org.mediamod.backend.mainModule
import java.util.*
import kotlin.test.*

data class CreateResponse(val secret: String?)

class MMBackendTest {
    init {
        database = MMDatabase()
    }

    @Test
    fun testRoot() = withTestApplication(Application::mainModule) {
        with(handleRequest(HttpMethod.Get, "/")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertTrue(requestHandled)
        }
        with(handleRequest(HttpMethod.Get, "/index.html")) {
            assertFalse(requestHandled)
        }
    }

    @Test
    fun testCreateUser() = withTestApplication(Application::mainModule) {
        with(handleRequest(HttpMethod.Post, "/api/register") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(Gson().toJson(mapOf("uuid" to "82074fcd-6eef-4caf-bc95-4dac50485fb7", "mod" to "mediamod")))
        }) {
            assertTrue(requestHandled, "Request was handled")
            assertEquals(HttpStatusCode.OK, response.status(), "Status is OK")
            val responseDecoded: CreateResponse = Gson().fromJson(response.content, CreateResponse::class.java)
            assertNotNull(responseDecoded.secret, "Secret isn't null")
            assertEquals(responseDecoded.secret.length, 36, "Secret length equals 36")

            val user = runBlocking { database.getUser(UUID.fromString("82074fcd-6eef-4caf-bc95-4dac50485fb7")) } // Can't use suspend functions here.
            assertNotNull(user, "User exists in database")
            assertEquals(responseDecoded.secret, user.requestSecret, "Received secret is equal to database secret")
        }
        with(handleRequest(HttpMethod.Get, "/index.html")) {
            assertFalse(requestHandled)
        }
    }
}