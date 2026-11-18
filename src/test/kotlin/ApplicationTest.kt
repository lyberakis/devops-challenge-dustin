import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun happyFlow() = testApplication {
        application { module("test") }

        val health = client.get("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals(ContentType.Application.Json, health.contentType()?.withoutParameters())
        assertEquals(Json.parseToJsonElement("""{"status":"healthy"}"""), Json.parseToJsonElement(health.bodyAsText()))

        val greeting = client.get("/")
        assertEquals(HttpStatusCode.OK, greeting.status)
        assertEquals(ContentType.Application.Json, greeting.contentType()?.withoutParameters())
        assertEquals(
            Json.parseToJsonElement("""{"message":"Hello, World!","environment":"test"}"""),
            Json.parseToJsonElement(greeting.bodyAsText()),
        )
    }
}
